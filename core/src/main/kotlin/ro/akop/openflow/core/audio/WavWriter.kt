package ro.akop.openflow.core.audio

import ro.akop.openflow.core.stt.PcmAudio
import java.io.File
import java.io.FileOutputStream

/** Minimal 16-bit mono PCM WAV writer, used to keep the audio next to a transcript. */
object WavWriter {

    fun write(audio: PcmAudio, target: File): File {
        target.parentFile?.mkdirs()

        val byteRate = audio.sampleRate * 2
        val dataSize = audio.samples.size * 2

        FileOutputStream(target).use { out ->
            out.write("RIFF".toByteArray())
            out.write(intLe(36 + dataSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intLe(16))
            out.write(shortLe(1)) // PCM
            out.write(shortLe(1)) // mono
            out.write(intLe(audio.sampleRate))
            out.write(intLe(byteRate))
            out.write(shortLe(2)) // block align
            out.write(shortLe(16)) // bits per sample
            out.write("data".toByteArray())
            out.write(intLe(dataSize))
            out.write(shortArrayLe(audio.samples))
        }
        return target
    }

    private fun intLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    private fun shortArrayLe(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var index = 0
        for (sample in samples) {
            bytes[index++] = (sample.toInt() and 0xFF).toByte()
            bytes[index++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
