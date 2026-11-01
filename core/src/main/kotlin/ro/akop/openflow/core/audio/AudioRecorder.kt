package ro.akop.openflow.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.akop.openflow.core.stt.PcmAudio
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Mono 16 kHz PCM capture — the format Whisper-family models expect.
 *
 * Kept outside the ViewModel so the UI layer never touches AudioRecord directly,
 * and so the pipeline can be unit tested with [PcmAudio] fixtures.
 */
class AudioRecorder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val maxDurationMs: Long = 120_000,
) : Recorder {
    private val _status = MutableStateFlow(RecordingStatus())
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var record: AudioRecord? = null
    private val chunks = mutableListOf<ShortArray>()

    @Volatile
    private var capturing = false

    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        if (capturing) return true

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false

        val bufferBytes = maxOf(minBuffer, sampleRate / 10 * 2)
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes * 2,
            )
        }.getOrNull() ?: return false

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }

        record = audioRecord
        chunks.clear()
        capturing = true
        _status.value = RecordingStatus(isRecording = true)
        audioRecord.startRecording()

        job = scope.launch { readLoop(audioRecord) }
        return true
    }

    private suspend fun readLoop(audioRecord: AudioRecord) {
        val buffer = ShortArray(sampleRate / 10) // 100 ms
        val startedAt = SystemClock.elapsedRealtime()

        while (capturing) {
            val read = runCatching { audioRecord.read(buffer, 0, buffer.size) }.getOrDefault(-1)
            if (read <= 0) continue

            chunks += buffer.copyOf(read)

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val level = levelOf(buffer, read)
            _status.update {
                it.copy(
                    elapsedMs = elapsed,
                    level = level,
                    limitReached = elapsed >= maxDurationMs,
                )
            }

            if (elapsed >= maxDurationMs) {
                capturing = false
                _status.update { it.copy(isRecording = false, limitReached = true) }
                break
            }
        }
    }

    /** Stops capture and returns everything recorded, or null when nothing was captured. */
    override fun stop(): PcmAudio? {
        capturing = false
        job?.let { runCatching { it.cancel() } }
        job = null

        val audioRecord = record
        record = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }

        val total = chunks.sumOf { it.size }
        val samples = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        chunks.clear()

        _status.value = RecordingStatus(isRecording = false)
        return if (total == 0) null else PcmAudio(samples, sampleRate)
    }

    /** Call from ViewModel.onCleared(); a dangling AudioRecord leaks the microphone. */
    suspend fun release() {
        stop()
        scope.coroutineContext[Job]?.let { runCatching { it.cancelAndJoin() } }
    }

    private fun levelOf(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val value = buffer[i] / 32768.0
            sum += value * value
        }
        val rms = sqrt(sum / length.coerceAtLeast(1))
        val db = 20 * log10(rms.coerceAtLeast(1e-5))
        // -60 dB..0 dB -> 0..1
        return ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
