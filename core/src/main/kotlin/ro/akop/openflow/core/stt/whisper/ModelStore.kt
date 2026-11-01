package ro.akop.openflow.core.stt.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads and verifies model files into app-private storage.
 *
 * `onProgress` is called with 0..1 as bytes land, so the UI can show real progress
 * instead of a spinner. Downloads are resumable at file granularity: complete files
 * are skipped.
 */
class ModelStore(private val rootDir: File) {

    fun directoryFor(model: SpeechModel): File = File(rootDir, model.id)

    fun isComplete(model: SpeechModel): Boolean = model.files.all { file ->
        val target = File(directoryFor(model), file.name)
        target.isFile && target.length() > 0
    }

    fun installedSizeBytes(model: SpeechModel): Long =
        model.files.sumOf { file ->
            File(directoryFor(model), file.name).let { if (it.isFile) it.length() else 0L }
        }

    suspend fun download(
        model: SpeechModel,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = directoryFor(model).apply { mkdirs() }
            val totalBytes = model.files.sumOf { it.approxBytes }.coerceAtLeast(1)
            var completedBytes = 0L

            for (file in model.files) {
                coroutineContext.ensureActive()

                val target = File(directory, file.name)
                if (target.isFile && target.length() > 0) {
                    completedBytes += target.length()
                    onProgress((completedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                    continue
                }

                val partial = File(directory, "${file.name}.part")
                downloadFile(file.url, partial) { bytesSoFar ->
                    onProgress(((completedBytes + bytesSoFar).toFloat() / totalBytes).coerceIn(0f, 1f))
                }

                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                completedBytes += target.length()
            }
            onProgress(1f)
        }
    }

    fun remove(model: SpeechModel) {
        directoryFor(model).deleteRecursively()
    }

    private suspend fun downloadFile(
        url: String,
        target: File,
        onBytes: (Long) -> Unit,
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "OpenFlow/0.1 (Android)")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Download failed ($status) for $url")
            }

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onBytes(written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
