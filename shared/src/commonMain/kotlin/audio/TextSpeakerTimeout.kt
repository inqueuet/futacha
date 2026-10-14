package com.valoser.futacha.shared.audio

internal const val TEXT_SPEAKER_MIN_TIMEOUT_MS = 30_000L
internal const val TEXT_SPEAKER_MAX_TIMEOUT_MS = 300_000L
private const val TEXT_SPEAKER_TIMEOUT_PER_CHAR_MS = 220L

internal fun calculateTextSpeakerTimeoutMillis(text: String, rate: Float = 1f): Long {
    val safeRate = rate.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f
    val speechBudgetMillis = (text.length.toLong() * TEXT_SPEAKER_TIMEOUT_PER_CHAR_MS / safeRate).toLong()
    return (TEXT_SPEAKER_MIN_TIMEOUT_MS + speechBudgetMillis)
        .coerceAtMost(TEXT_SPEAKER_MAX_TIMEOUT_MS)
}
