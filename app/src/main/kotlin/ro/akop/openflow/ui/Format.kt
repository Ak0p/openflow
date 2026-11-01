package ro.akop.openflow.ui

import java.util.concurrent.TimeUnit

fun formatDuration(durationMs: Long): String {
    val clamped = durationMs.coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(clamped)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "%ds".format(seconds)
}

fun formatElapsed(durationMs: Long): String {
    val clamped = durationMs.coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(clamped)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun engineLabel(engineId: String): String = when (engineId) {
    "fake" -> "Demo"
    "whisper-tiny-int8" -> "Whisper tiny · on-device"
    else -> engineId
}
