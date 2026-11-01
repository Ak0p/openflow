package ro.akop.openflow.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.akop.openflow.AppContainer
import ro.akop.openflow.core.audio.Recorder
import ro.akop.openflow.core.audio.WavWriter
import ro.akop.openflow.core.data.Transcript
import ro.akop.openflow.core.data.TranscriptRepository
import ro.akop.openflow.core.post.TextPostProcessor
import ro.akop.openflow.core.stt.PcmAudio
import ro.akop.openflow.core.stt.SttError
import ro.akop.openflow.core.stt.SttEvent
import ro.akop.openflow.core.stt.TranscriptionEngine
import java.io.File
import java.util.UUID

enum class RecordPhase {
    Preparing,
    Recording,
    Transcribing,
    Saved,
    Failed,
}

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.Preparing,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val previewText: String = "",
    val engineName: String = "",
    val error: String? = null,
) {
    val isBusy: Boolean
        get() = phase == RecordPhase.Preparing || phase == RecordPhase.Transcribing
}

class RecordViewModel(
    private val recorder: Recorder,
    private val engine: TranscriptionEngine,
    private val repository: TranscriptRepository,
    private val postProcessor: TextPostProcessor,
    private val recordingsDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState(engineName = engine.displayName))
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private val _saved = Channel<String>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var statusJob: Job? = null
    private var workJob: Job? = null
    private val segments = StringBuilder()

    fun start() {
        val phase = _state.value.phase
        if (phase == RecordPhase.Recording || phase == RecordPhase.Transcribing) return

        segments.clear()
        _state.value = RecordUiState(phase = RecordPhase.Preparing, engineName = engine.displayName)

        if (!recorder.start()) {
            fail("The microphone is busy or unavailable.")
            return
        }

        _state.value = RecordUiState(phase = RecordPhase.Recording, engineName = engine.displayName)
        statusJob = viewModelScope.launch {
            recorder.status.collect { status ->
                if (_state.value.phase != RecordPhase.Recording) return@collect
                _state.update { it.copy(elapsedMs = status.elapsedMs, level = status.level) }
                if (status.limitReached) stop()
            }
        }
    }

    fun stop() {
        if (_state.value.phase != RecordPhase.Recording) return

        statusJob?.cancel()
        statusJob = null

        val audio = recorder.stop()
        if (audio == null || audio.durationMs < MIN_DURATION_MS) {
            fail("That was too short. Give it a full sentence.")
            return
        }

        _state.update { it.copy(phase = RecordPhase.Transcribing, level = 0f, previewText = "") }
        transcribe(audio)
    }

    /** Called when the screen goes away or the app is backgrounded mid-recording. */
    fun interrupt() {
        if (_state.value.phase == RecordPhase.Recording) {
            statusJob?.cancel()
            statusJob = null
            recorder.stop()
            fail("Recording stopped because the app went to the background.")
        }
    }

    fun cancel() {
        statusJob?.cancel()
        statusJob = null
        workJob?.cancel()
        workJob = null
        recorder.stop()
        _state.update { it.copy(phase = RecordPhase.Preparing, level = 0f, elapsedMs = 0) }
    }

    private fun transcribe(audio: PcmAudio) {
        workJob = viewModelScope.launch {
            val startedAt = clock()
            var finalText: String? = null
            var audioMs = audio.durationMs
            var inferenceMs = 0L

            try {
                engine.transcribe(audio).collect { event ->
                    when (event) {
                        is SttEvent.Partial -> _state.update { it.copy(previewText = event.text) }

                        is SttEvent.Segment -> {
                            if (segments.isNotEmpty()) segments.append(' ')
                            segments.append(event.text)
                            _state.update { it.copy(previewText = segments.toString()) }
                        }

                        is SttEvent.Done -> {
                            finalText = event.text
                            audioMs = event.audioMs
                            inferenceMs = event.inferenceMs
                        }

                        is SttEvent.Failed -> throw TranscriptionFailure(event.error)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                fail(failure.message ?: "Transcription failed.")
                return@launch
            }

            val text = finalText?.takeIf { it.isNotBlank() }
            if (text == null) {
                fail("No speech detected. Try again a little closer to the microphone.")
                return@launch
            }

            persist(
                audio = audio,
                rawText = text,
                audioMs = audioMs,
                inferenceMs = if (inferenceMs > 0) inferenceMs else clock() - startedAt,
            )
        }
    }

    private suspend fun persist(audio: PcmAudio, rawText: String, audioMs: Long, inferenceMs: Long) {
        val id = newId()
        val text = postProcessor.process(rawText).ifBlank { rawText }

        val wav = withContext(ioDispatcher) {
            runCatching { WavWriter.write(audio, File(recordingsDir, "$id.wav")) }.getOrNull()
        }

        repository.add(
            Transcript(
                id = id,
                text = text,
                createdAt = clock(),
                durationMs = audioMs,
                engineId = engine.id,
                inferenceMs = inferenceMs,
                audioPath = wav?.absolutePath,
            ),
        )

        _state.update { it.copy(phase = RecordPhase.Saved) }
        _saved.send(id)
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = RecordPhase.Failed, error = message, level = 0f) }
    }

    override fun onCleared() {
        statusJob?.cancel()
        workJob?.cancel()
        recorder.stop()
    }

    private class TranscriptionFailure(error: SttError) :
        Exception(
            when (error) {
                SttError.NotReady -> "The speech model is not loaded."
                SttError.NoSpeech -> "No speech detected."
                SttError.PermissionDenied -> "Microphone permission is missing."
                is SttError.Unexpected -> error.message
            },
        )

    companion object {
        private const val MIN_DURATION_MS = 350L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecordViewModel(
                    recorder = container.recorder,
                    engine = container.engine(),
                    repository = container.transcripts,
                    postProcessor = container.postProcessor(),
                    recordingsDir = container.recordingsDir,
                )
            }
        }
    }
}
