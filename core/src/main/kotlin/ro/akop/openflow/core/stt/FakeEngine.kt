package ro.akop.openflow.core.stt

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic engine used by the UI shell, unit tests and the emulator.
 * It exercises the exact same code path as the real engine, minus inference.
 */
class FakeEngine(
    private val response: String = DEFAULT_RESPONSE,
    private val latencyMs: Long = 700,
) : TranscriptionEngine {

    override val id: String = "fake"

    override val displayName: String = "Demo engine"

    override suspend fun isReady(): Boolean = true

    override fun transcribe(audio: PcmAudio, language: String?): Flow<SttEvent> = flow {
        if (audio.samples.isEmpty()) {
            emit(SttEvent.Failed(SttError.NoSpeech))
            return@flow
        }

        emit(SttEvent.Partial("transcribing…"))
        delay(latencyMs)

        val text = response
        emit(SttEvent.Segment(text = text, startMs = 0, endMs = audio.durationMs))
        emit(
            SttEvent.Done(
                text = text,
                audioMs = audio.durationMs,
                inferenceMs = latencyMs,
            ),
        )
    }

    private companion object {
        const val DEFAULT_RESPONSE =
            "This is what OpenFlow sounds like when the on-device engine is not loaded yet. " +
                "Everything around it — recording, permissions, history — is real."
    }
}
