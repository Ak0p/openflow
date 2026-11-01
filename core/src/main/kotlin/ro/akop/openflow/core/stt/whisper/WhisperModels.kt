package ro.akop.openflow.core.stt.whisper

data class RemoteFile(
    val name: String,
    val url: String,
    val approxBytes: Long,
)

data class SpeechModel(
    val id: String,
    val displayName: String,
    val encoderFile: String,
    val decoderFile: String,
    val tokensFile: String,
    /** Optional Silero VAD used to cut an utterance into decodable chunks. */
    val vadFile: String?,
    val files: List<RemoteFile>,
) {
    val approxSizeMb: Int
        get() = (files.sumOf { it.approxBytes } / (1024 * 1024)).toInt()
}

/**
 * Models are downloaded on first use, never committed: 70+ MB of ONNX does not
 * belong in git or in the APK.
 */
object WhisperTiny {
    private const val HF =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"
    private const val VAD_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    val model = SpeechModel(
        id = "whisper-tiny-int8",
        displayName = "Whisper tiny · multilingual",
        encoderFile = "tiny-encoder.int8.onnx",
        decoderFile = "tiny-decoder.int8.onnx",
        tokensFile = "tiny-tokens.txt",
        vadFile = "silero_vad.onnx",
        files = listOf(
            RemoteFile("tiny-encoder.int8.onnx", "$HF/tiny-encoder.int8.onnx", 12_937_772),
            RemoteFile("tiny-decoder.int8.onnx", "$HF/tiny-decoder.int8.onnx", 89_855_401),
            RemoteFile("tiny-tokens.txt", "$HF/tiny-tokens.txt", 816_730),
            RemoteFile("silero_vad.onnx", VAD_URL, 643_854),
        ),
    )
}
