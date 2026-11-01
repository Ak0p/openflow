package ro.akop.openflow.core.stt.whisper

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.akop.openflow.core.stt.PcmAudio
import ro.akop.openflow.core.stt.SttEvent
import java.io.File

/**
 * Real inference, on a device. The model files and a 16 kHz mono sample are expected in
 * /data/local/tmp/openflow-models (see scripts/push-test-model.sh). Skipped when absent so
 * the suite stays runnable on a plain emulator without the 100 MB download.
 */
@RunWith(AndroidJUnit4::class)
class WhisperEngineInstrumentedTest {

    private val sourceDir = File("/data/local/tmp/openflow-models")

    @Test
    fun transcribesTheSampleClip() = runBlocking {
        assumeTrue("Push the model to $sourceDir first", sourceDir.isDirectory)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(context.filesDir, "models-instrumented").apply {
            deleteRecursively()
            mkdirs()
        }

        for (name in REQUIRED_FILES) {
            File(sourceDir, name).copyTo(File(modelDir, name), overwrite = true)
        }

        val audio = readPcmWav(File(sourceDir, "sample.wav"))

        val withoutVadDir = File(context.filesDir, "models-instrumented-novad").apply {
            deleteRecursively()
            mkdirs()
        }
        for (name in REQUIRED_FILES - "silero_vad.onnx") {
            File(sourceDir, name).copyTo(File(withoutVadDir, name), overwrite = true)
        }
        val noVadEngine = WhisperEngine(withoutVadDir)
        val noVadDone = noVadEngine.transcribe(audio).toList()
            .filterIsInstance<SttEvent.Done>()
            .lastOrNull()
        Log.i(TAG, "noVad=\"${noVadDone?.text}\" inferenceMs=${noVadDone?.inferenceMs} rt=${noVadDone?.let { it.inferenceMs.toFloat() / it.audioMs }}")
        assertTrue("Whisper without VAD should produce text", !noVadDone?.text.isNullOrBlank())

        val engine = WhisperEngine(modelDir)
        assertTrue("Engine should see a complete model directory", engine.isReady())

        val startedAt = System.nanoTime()
        val events = engine.transcribe(audio).toList()
        val wallMs = (System.nanoTime() - startedAt) / 1_000_000

        val done = events.filterIsInstance<SttEvent.Done>().lastOrNull()
        Log.i(TAG, "wallMs=$wallMs events=$events")
        assertNotNull("No Done event, got: $events", done)

        val text = requireNotNull(done).text
        Log.i(
            TAG,
            "transcript=\"$text\" audioMs=${done.audioMs} inferenceMs=${done.inferenceMs} " +
                "rtf=${done.inferenceMs.toFloat() / done.audioMs}",
        )
        assertTrue("Transcript should not be empty", text.isNotBlank())
    }

    private fun readPcmWav(file: File): PcmAudio {
        val bytes = file.readBytes()
        var offset = 12
        var sampleRate = 16_000

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = intLe(bytes, offset + 4)

            when (id) {
                "fmt " -> sampleRate = intLe(bytes, offset + 12)
                "data" -> {
                    val end = (offset + 8 + size).coerceAtMost(bytes.size)
                    val data = bytes.copyOfRange(offset + 8, end)
                    val samples = ShortArray(data.size / 2) { index ->
                        shortLe(data, index * 2)
                    }
                    return PcmAudio(samples, sampleRate)
                }
            }
            offset += 8 + size + (size and 1)
        }
        error("No data chunk in ${file.name}")
    }

    private fun intLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun shortLe(bytes: ByteArray, offset: Int): Short =
        (((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)))
            .toShort()

    private companion object {
        const val TAG = "WhisperEngineTest"
        val REQUIRED_FILES = listOf(
            "tiny-encoder.int8.onnx",
            "tiny-decoder.int8.onnx",
            "tiny-tokens.txt",
            "silero_vad.onnx",
        )
    }
}
