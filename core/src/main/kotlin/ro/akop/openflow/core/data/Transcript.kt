package ro.akop.openflow.core.data

/**
 * A single dictation result. This is the unit of work the app manages.
 *
 * Kept free of Android and framework types so it can be used from ViewModels,
 * unit tests and later on a remote tier without changes.
 */
data class Transcript(
    val id: String,
    val text: String,
    /** Epoch millis. */
    val createdAt: Long,
    val durationMs: Long,
    val language: String? = null,
    val engineId: String = "unknown",
    val inferenceMs: Long? = null,
    /** Absolute path of the recorded WAV file, when the audio was kept. */
    val audioPath: String? = null,
)
