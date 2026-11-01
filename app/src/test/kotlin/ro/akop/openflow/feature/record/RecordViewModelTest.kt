package ro.akop.openflow.feature.record

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ro.akop.openflow.core.data.InMemoryTranscriptRepository
import ro.akop.openflow.core.post.RuleBasedTextPostProcessor
import ro.akop.openflow.core.stt.FakeEngine
import ro.akop.openflow.core.stt.PcmAudio
import ro.akop.openflow.testing.MainDispatcherRule
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val oneSecondOfAudio = PcmAudio(ShortArray(16_000) { 1_200 }, 16_000)

    @Test
    fun `saves a transcript when the user stops`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = InMemoryTranscriptRepository()
        val viewModel = viewModel(repository, oneSecondOfAudio)

        viewModel.start()
        advanceUntilIdle()
        assertEquals(RecordPhase.Recording, viewModel.state.value.phase)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals("transcript-1", viewModel.saved.first())
        val transcript = repository.transcripts.value.single()
        assertEquals("Hello world.", transcript.text)
        assertEquals(1_000L, transcript.durationMs)
        assertEquals("fake", transcript.engineId)
        assertNotNull(transcript.audioPath)
        assertTrue(File(transcript.audioPath!!).exists())
    }

    @Test
    fun `rejects recordings that are too short`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = InMemoryTranscriptRepository()
        val viewModel = viewModel(repository, PcmAudio(ShortArray(64), 16_000))

        viewModel.start()
        advanceUntilIdle()
        viewModel.stop()
        advanceUntilIdle()

        assertEquals(RecordPhase.Failed, viewModel.state.value.phase)
        assertTrue(repository.transcripts.value.isEmpty())
    }

    private fun viewModel(
        repository: InMemoryTranscriptRepository,
        audio: PcmAudio,
    ): RecordViewModel {
        val recordingsDir = Files.createTempDirectory("openflow-recordings").toFile()
        return RecordViewModel(
            recorder = FakeRecorder(audio),
            engine = FakeEngine(response = "hello world", latencyMs = 0),
            repository = repository,
            postProcessor = RuleBasedTextPostProcessor(),
            recordingsDir = recordingsDir,
            ioDispatcher = mainDispatcherRule.dispatcher,
            clock = { 1_000L },
            newId = { "transcript-1" },
        )
    }
}
