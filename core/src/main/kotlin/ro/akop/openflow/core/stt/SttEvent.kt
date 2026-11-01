package ro.akop.openflow.core.stt

/** Mono PCM samples, 16-bit. */
class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
) {
    val durationMs: Long
        get() = if (sampleRate == 0) 0 else samples.size * 1_000L / sampleRate
}

sealed interface SttEvent {
    /** Live text while the user is still speaking. Optional for offline engines. */
    data class Partial(val text: String) : SttEvent

    /** A finished utterance. Engines may emit several of these for one recording. */
    data class Segment(
        val text: String,
        val startMs: Long,
        val endMs: Long,
    ) : SttEvent

    data class Done(
        val text: String,
        val audioMs: Long,
        val inferenceMs: Long,
    ) : SttEvent

    data class Failed(val error: SttError) : SttEvent
}

sealed interface SttError {
    data object NotReady : SttError

    data object NoSpeech : SttError

    data object PermissionDenied : SttError

    data class Unexpected(val message: String) : SttError
}
