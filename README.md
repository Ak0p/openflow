# OpenFlow

On-device speech to text for Android — a working demo in the spirit of Wispr Flow.

Tap **Dictate**, speak, and the transcript lands in the app: copy it, share it, keep it.
Recording, inference and storage all happen on the phone. No account, no upload.

## Status

Working end to end:

- Compose + Material 3 UI: home list, dictation screen, transcript detail, settings.
- 16 kHz mono capture (`AudioRecord`, `VOICE_RECOGNITION` source) with level metering.
- Two engines behind one interface: a deterministic demo engine and **Whisper tiny
  (multilingual, int8) via sherpa-onnx**, with Silero VAD cutting the utterance into decodes.
- Model downloaded on first use (~99 MB, progress + resumable per file), stored app-private.
- Rule-based clean-up: capitalisation, punctuation, filler words, spoken commands
  ("comma", "new line", "punct").
- Runtime permission flow with rationale, graceful denial and a path to app settings.
- Unit tests for the state/formatting layer and an instrumented test that runs real inference.

Not built yet: voice keyboard (IME), persistence across launches, sync, LLM post-processing.

## Build

Requirements: JDK 17+ (built with 21), Android SDK 35, a device or emulator on API 26+.

```bash
./gradlew :app:installDebug
```

Then, on the device: **Settings → Model → Download**. When the download finishes the app
switches to the on-device engine automatically. The first transcription loads the model,
so expect a short delay on the very first run.

## Tests

```bash
./gradlew test                 # JVM: post-processing, repository, record flow with fakes
./scripts/push-test-model.sh   # pushes the model + a sample clip to /data/local/tmp
./gradlew :core:connectedDebugAndroidTest
```

The instrumented test runs Whisper on a real clip and asserts a non-empty transcript, logging
the real-time factor. It is skipped when the model has not been pushed.

## Measured (Pixel emulator, arm64, API 37)

| Path | Audio | Inference | RTF |
|---|---|---|---|
| Silero VAD + Whisper tiny int8 | 6.6 s | 0.56 s | 0.08 |
| Whisper tiny int8, whole clip | 6.6 s | 0.60 s | 0.09 |

Whisper hallucinates on silence, so the engine drops clips where VAD finds no speech and
rejects near-silent audio before touching the model.

## Architecture

```
:core   audio capture · VAD · TranscriptionEngine · model download · domain · post-processing
:app    Compose UI · navigation · ViewModels · manual DI container
```

```kotlin
interface TranscriptionEngine {
    val id: String
    suspend fun isReady(): Boolean
    fun transcribe(audio: PcmAudio, language: String?): Flow<SttEvent>   // Partial/Segment/Done/Failed
}
```

Implementations: `FakeEngine` (tests, UI before any model exists) and `WhisperEngine`
(sherpa-onnx + Silero VAD). A remote tier or an LLM post-processor slots in behind the same
seam — that is the point of the interface.

Data flow for one dictation: `AudioRecorder` → `PcmAudio` → engine → `Flow<SttEvent>` →
`RecordViewModel` collects segments, applies `TextPostProcessor`, writes the WAV, adds a
`Transcript` to the repository, and navigates to its detail screen.

## Known limits

- Transcripts are in memory: they survive configuration changes, not process death. Room is
  the next step.
- Long dictations are capped at two minutes by the recorder.
- The emulator microphone is noisy; measure latency and quality on a physical phone.
- `sherpa-onnx` requires an empty language string for auto-detect — "auto" makes the native
  layer exit.
