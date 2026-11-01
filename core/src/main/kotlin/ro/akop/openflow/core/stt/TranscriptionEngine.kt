package ro.akop.openflow.core.stt

import kotlinx.coroutines.flow.Flow

/**
 * Everything above this interface is engine agnostic, which is what makes the
 * local / cloud / fake comparison possible: same call site, different implementation.
 */
interface TranscriptionEngine {
    val id: String

    val displayName: String

    /** Cheap check: model files present, native library loaded, etc. */
    suspend fun isReady(): Boolean

    fun transcribe(audio: PcmAudio, language: String? = null): Flow<SttEvent>
}
