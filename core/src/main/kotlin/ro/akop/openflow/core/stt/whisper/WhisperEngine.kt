package ro.akop.openflow.core.stt.whisper

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import ro.akop.openflow.core.stt.PcmAudio
import ro.akop.openflow.core.stt.SttError
import ro.akop.openflow.core.stt.SttEvent
import ro.akop.openflow.core.stt.TranscriptionEngine
import java.io.File
import kotlin.math.sqrt

/**
 * Offline Whisper via sherpa-onnx.
 *
 * Whole utterances are decoded at once, after Silero VAD cuts them at pauses.
 * This avoids the classic "growing buffer + whisper_full in a loop" trap, which is
 * several times slower than real time and ends in an ANR.
 */
class WhisperEngine(
    private val modelDir: File,
    private val model: SpeechModel = WhisperTiny.model,
    private val threads: Int = DEFAULT_THREADS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TranscriptionEngine {

    override val id: String = model.id

    override val displayName: String = model.displayName

    override suspend fun isReady(): Boolean {
        val required = listOf(model.encoderFile, model.decoderFile, model.tokensFile)
        return required.all { File(modelDir, it).isFile }
    }

    override fun transcribe(audio: PcmAudio, language: String?): Flow<SttEvent> = flow {
        if (!isReady()) {
            emit(SttEvent.Failed(SttError.NotReady))
            return@flow
        }
        if (audio.samples.isEmpty()) {
            emit(SttEvent.Failed(SttError.NoSpeech))
            return@flow
        }

        val startedAt = System.nanoTime()

        val segments = try {
            withContext(dispatcher) { decode(audio, language) }
        } catch (failure: Throwable) {
            emit(SttEvent.Failed(SttError.Unexpected(failure.message ?: "Inference failed")))
            return@flow
        }

        if (segments.isEmpty()) {
            emit(SttEvent.Failed(SttError.NoSpeech))
            return@flow
        }

        segments.forEach { segment ->
            emit(SttEvent.Segment(segment.text, segment.startMs, segment.endMs))
        }

        emit(
            SttEvent.Done(
                text = segments.joinToString(" ") { it.text },
                audioMs = audio.durationMs,
                inferenceMs = (System.nanoTime() - startedAt) / 1_000_000,
            ),
        )
    }

    private fun decode(audio: PcmAudio, language: String?): List<DecodedSegment> {
        val recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(modelDir, model.encoderFile).absolutePath,
                        decoder = File(modelDir, model.decoderFile).absolutePath,
                        // Empty means "detect the language" in sherpa-onnx; "auto" is rejected.
                        language = language.orEmpty(),
                        task = "transcribe",
                    ),
                    tokens = File(modelDir, model.tokensFile).absolutePath,
                    numThreads = threads,
                    debug = false,
                    modelType = "whisper",
                ),
            ),
        )

        val vad = createVad()

        try {
            // Silero VAD decides whether there is anything to transcribe at all. Feeding it
            // the whole clip at once under-detects, so it gets 512-sample windows.
            val speech = vad?.let { runVad(it, audio) }
                ?: if (audio.isAudible()) listOf(0 to audio.toFloats()) else emptyList()

            if (speech.isEmpty()) return emptyList()

            return speech.mapNotNull { (startSample, samples) ->
                if (samples.isEmpty()) return@mapNotNull null

                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.trim()
                    if (text.isEmpty()) {
                        null
                    } else {
                        DecodedSegment(
                            text = text,
                            startMs = startSample * 1_000L / SAMPLE_RATE,
                            endMs = (startSample + samples.size) * 1_000L / SAMPLE_RATE,
                        )
                    }
                } finally {
                    stream.release()
                }
            }
        } finally {
            vad?.release()
            recognizer.release()
        }
    }

    private fun createVad(): Vad? {
        val vadPath = model.vadFile?.let { File(modelDir, it) } ?: return null
        if (!vadPath.isFile) return null

        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadPath.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.35f,
                minSpeechDuration = 0.2f,
                windowSize = 512,
                maxSpeechDuration = 20f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            debug = false,
        )
        return runCatching { Vad(assetManager = null, config = config) }.getOrNull()
    }

    private fun runVad(vad: Vad, audio: PcmAudio): List<Pair<Int, FloatArray>> {
        val samples = audio.toFloats()
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + VAD_WINDOW, samples.size)
            vad.acceptWaveform(samples.copyOfRange(offset, end))
            offset = end
        }
        vad.flush()

        val chunks = mutableListOf<Pair<Int, FloatArray>>()
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            if (segment.samples.isNotEmpty()) {
                chunks += segment.start to segment.samples
            }
        }
        return chunks
    }

    private fun PcmAudio.toFloats(): FloatArray =
        FloatArray(samples.size) { index -> samples[index] / 32_768f }

    /** Cheap guard so a silent room never reaches Whisper (it hallucinates on silence). */
    private fun PcmAudio.isAudible(): Boolean {
        if (samples.isEmpty()) return false
        var sum = 0.0
        for (sample in samples) {
            val value = sample / 32_768.0
            sum += value * value
        }
        return sqrt(sum / samples.size) > 0.002
    }

    private data class DecodedSegment(
        val text: String,
        val startMs: Long,
        val endMs: Long,
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val VAD_WINDOW = 512
        val DEFAULT_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
