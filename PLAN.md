# OpenFlow — demo plan

Android on-device speech-to-text demo in the spirit of Wispr Flow: hold/tap, speak,
text appears — in the app as notes, and (step 2) in any text field via a minimal voice IME.

Scope of this repo: **product demo**. The `mobile-computing-m1` app is a separate project and
is out of scope here.

## Decisions

| Topic | Decision |
|---|---|
| Keyboard | Phase A first: in-app notes + copy/share + `ACTION_PROCESS_TEXT`. Minimal voice IME (`:ime`) as a follow-up phase, same engine. |
| Post-processing | `TextPostProcessor` seam + rule-based formatter (capitalisation, punctuation, "comma"/"new line" voice commands, filler words). No LLM in the demo. |
| Course constraints | Not applicable to this repo. Still keep the good habits: no XML layouts, Kotlin + Compose + M3, MVVM + UDF. |
| Model delivery | Downloaded at first run (progress + SHA-256). No model binary and no AAR committed. |

## Stack

AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, `compileSdk 35`, `minSdk 26`, `targetSdk 35`,
Navigation Compose (type-safe routes, kotlinx-serialization).
All of the above are already in the local Gradle cache, so the first build is fast and offline-safe.

## Modules

```
:core   Kotlin + Android audio: recorder, VAD, engine, model repository, domain, post-processing
:app    Compose UI: nav, home/record/detail/settings
:ime    (later) InputMethodService with a Compose input view
```

## Core contract

```kotlin
sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Segment(val text: String, val startMs: Long, val endMs: Long) : SttEvent
    data class Done(val fullText: String, val audioMs: Long, val inferenceMs: Long) : SttEvent
    data class Failed(val reason: SttError) : SttEvent
}

interface TranscriptionEngine {
    val id: String
    suspend fun isReady(): Boolean
    fun transcribe(pcm: PCMStream, lang: String?): Flow<SttEvent>
}

interface TextPostProcessor { suspend fun process(raw: String): String }
```

Implementations: `LocalWhisperEngine` (sherpa-onnx, Whisper tiny int8), `FakeEngine` (tests and
UI work before native is wired), later `RemoteStub` / LLM post-processor behind the same seams.

Design rules:
- Offline Whisper + VAD segmentation per utterance. Never the naive growing-buffer streaming loop
  (5x slower than real time, ANR).
- Log `audioMs` / `inferenceMs` from day one — these are the numbers for the latency comparison.
- Debug "transcribe sample WAV" path so the engine can be proven on the emulator without a mic.

## File tree

```
openflow/
├── PLAN.md
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
├── scripts/fetch-native.sh          # sherpa-onnx aar + models, gitignored
├── core/src/main/kotlin/ro/akop/openflow/core/
│   ├── audio/{AudioRecorder,VadSegmenter,WavIo}.kt
│   ├── stt/{TranscriptionEngine,SttEvent,LocalWhisperEngine,FakeEngine,ModelCatalog,ModelRepository}.kt
│   ├── post/{TextPostProcessor,RuleBasedPostProcessor,NoOpPostProcessor}.kt
│   └── data/{Transcript,TranscriptRepository}.kt
├── app/src/main/kotlin/ro/akop/openflow/
│   ├── MainActivity.kt, OpenFlowApp.kt, AppContainer.kt
│   ├── ui/theme/, ui/nav/{Routes.kt,AppNavHost.kt}
│   └── feature/{home,record,detail,settings}/
├── app/src/test/                    # FakeEngine, state reducer, post-processor
├── app/src/androidTest/             # one navigation test
└── ime/                             # later
```

Navigation uses `@Serializable` route classes and `SavedStateHandle` in ViewModels, so state
survives process death.

## Phases

| # | Phase | Output | Est. |
|---|---|---|---|
| 0 | Bootstrap: wrapper, version catalog, empty Compose app running on `emulator-5554` | green build | 30 m |
| 1 | Skeleton: models + in-memory repo + nav + Home/Detail + `RECORD_AUDIO` permission flow, with `FakeEngine` | full flow without native | 1–2 h |
| 2 | **Engine spike (gate)**: AAR + one `OfflineRecognizer` run on `test_wavs/0.wav`, measure RTF | proof it works | 2–3 h |
| 3 | Real mic: AudioRecord 16 kHz mono + VAD + segments → engine, WAV persisted | live dictation | 3–4 h |
| 4 | Model delivery: download with progress + checksum, Settings (tiny.en vs tiny multilingual) | no binary in git | 1–2 h |
| 5a | Copy / share sheet / `ACTION_PROCESS_TEXT` | insert elsewhere without an IME | 30 m |
| 5b | `:ime` minimal voice keyboard | Wispr-Flow-like | 4–6 h |
| 6 | Post-processing: seam + rule-based formatter | clean text | 1 h |
| 7 | JVM tests + one instrumented test + CI + README demo script | deliverable | 1–2 h |

## Risks and fallbacks

1. **sherpa-onnx packaging** (biggest). Order: JitPack coordinate → local AAR from the GitHub
   release (`sherpa-onnx-<ver>.aar`, ~48 MB) → Vosk (`com.alphacephei:vosk-android:0.3.75`,
   Maven Central, streaming, 40 MB model, lower accuracy but zero JNI).
2. **Emulator inference is slow** and not representative. Measure on a physical phone; use the
   sample WAV for demos.
3. **Compose inside an `InputMethodService`** needs manually provided
   `ViewTreeLifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner`. That is why the
   IME is an isolated module and not on the critical path.
4. **Model size** (75–150 MB) → download only, never bundled.
5. **Mic in the emulator** is unreliable → sample WAV plus optional `adb push`.

## Verification

`./gradlew :app:assembleDebug testDebugUnitTest` → `adb install` → screenshots of Home/Detail →
"transcribe sample" produces text from `test_wavs/0.wav` → log line with `audioMs`/`inferenceMs`
→ (5b) `adb shell ime enable/set` and type into a field.

## Status after the first build

Done: phases 0, 1, 2, 3, 4, 6. Copy/share (5a) is available from the detail screen; the
system-wide `ACTION_PROCESS_TEXT` hook and the `:ime` module are still open.

Verified on a Pixel emulator (arm64, API 37):

- Real transcription of `test_wavs/0.wav`: "After early nightfall, the yellow lamps would light
  up here and there, the squalid quarter of the brothels." — 0.56 s for 6.6 s of audio (RTF 0.08).
- Silence no longer produces a hallucination: VAD finds nothing, the UI shows "No speech detected".
- The full app path (record → engine → clean-up → detail) works with the on-device engine.

Corrections to the plan, found while building:

- The multilingual Whisper tiny decoder is 86 MB, not 27 MB: the model is ~99 MB, not ~72 MB.
- Silero VAD must be fed 512-sample windows; passing the whole clip at once under-detects.
- `sherpa-onnx` expects an empty language string for auto-detection; `"auto"` aborts the process.
- `sherpa-onnx` is consumed from JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.8`);
  no local AAR is needed, so the `scripts/fetch-native.sh` fallback was not created.

Model download from Hugging Face is exercised in the UI but was verified by copying the files
into app storage, not by a clean download on a physical device.

