package ro.akop.openflow

import android.content.Context
import ro.akop.openflow.core.audio.AudioRecorder
import ro.akop.openflow.core.data.InMemoryTranscriptRepository
import ro.akop.openflow.core.data.TranscriptRepository
import ro.akop.openflow.core.post.NoOpTextPostProcessor
import ro.akop.openflow.core.post.RuleBasedTextPostProcessor
import ro.akop.openflow.core.post.TextPostProcessor
import ro.akop.openflow.core.stt.FakeEngine
import ro.akop.openflow.core.stt.TranscriptionEngine
import ro.akop.openflow.core.stt.whisper.ModelStore
import ro.akop.openflow.core.stt.whisper.WhisperEngine
import ro.akop.openflow.core.stt.whisper.WhisperTiny
import ro.akop.openflow.data.AppSettings
import ro.akop.openflow.data.EngineChoice
import java.io.File

/**
 * Manual dependency container. Small on purpose: the demo has one screen graph and
 * no need for a DI framework yet.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = AppSettings(appContext)

    val transcripts: TranscriptRepository = InMemoryTranscriptRepository()

    val demoEngine: TranscriptionEngine = FakeEngine()

    val modelStore = ModelStore(File(appContext.filesDir, "models"))

    /** Real on-device engine; reports `isReady() == false` until the model is downloaded. */
    val localEngine: TranscriptionEngine =
        WhisperEngine(modelDir = modelStore.directoryFor(WhisperTiny.model))

    val recorder = AudioRecorder()

    val recordingsDir: File = File(appContext.filesDir, "recordings")

    private val ruleBasedPostProcessor = RuleBasedTextPostProcessor()
    private val noOpPostProcessor = NoOpTextPostProcessor()

    fun engine(choice: EngineChoice = settings.engineChoice.value): TranscriptionEngine =
        when (choice) {
            EngineChoice.DEMO -> demoEngine
            EngineChoice.ON_DEVICE -> localEngine
        }

    fun isEngineAvailable(choice: EngineChoice): Boolean = when (choice) {
        EngineChoice.DEMO -> true
        EngineChoice.ON_DEVICE -> modelStore.isComplete(WhisperTiny.model)
    }

    fun postProcessor(): TextPostProcessor =
        if (settings.postProcessing.value) ruleBasedPostProcessor else noOpPostProcessor
}
