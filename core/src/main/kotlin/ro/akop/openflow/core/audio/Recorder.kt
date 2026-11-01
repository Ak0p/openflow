package ro.akop.openflow.core.audio

import kotlinx.coroutines.flow.StateFlow
import ro.akop.openflow.core.stt.PcmAudio

data class RecordingStatus(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val limitReached: Boolean = false,
)

/**
 * Capture seam. The real implementation wraps AudioRecord; tests use a scripted fake,
 * which keeps the recording flow unit-testable on the JVM.
 */
interface Recorder {
    val status: StateFlow<RecordingStatus>

    fun start(): Boolean

    /** Stops capture and returns everything recorded, or null when nothing was captured. */
    fun stop(): PcmAudio?
}
