package com.valoser.futacha.shared.util

/** Returns a non-negative elapsed duration without overflowing on corrupt timestamps. */
internal fun safeEpochElapsedMillis(nowMillis: Long, startedAtMillis: Long): Long = when {
    nowMillis <= startedAtMillis -> 0L
    startedAtMillis < 0L && nowMillis > Long.MAX_VALUE + startedAtMillis -> Long.MAX_VALUE
    else -> nowMillis - startedAtMillis
}

/** Clock rollback invalidates throttles/caches instead of keeping them alive indefinitely. */
internal fun hasEpochIntervalElapsed(
    nowMillis: Long,
    startedAtMillis: Long,
    intervalMillis: Long
): Boolean {
    if (intervalMillis <= 0L || nowMillis < startedAtMillis) return true
    return safeEpochElapsedMillis(nowMillis, startedAtMillis) >= intervalMillis
}

internal fun isWithinEpochInterval(
    nowMillis: Long,
    startedAtMillis: Long,
    intervalMillis: Long
): Boolean {
    if (intervalMillis < 0L || nowMillis < startedAtMillis) return false
    return safeEpochElapsedMillis(nowMillis, startedAtMillis) <= intervalMillis
}

internal fun hasEpochDurationExceeded(
    nowMillis: Long,
    startedAtMillis: Long,
    maxDurationMillis: Long
): Boolean {
    if (maxDurationMillis < 0L || nowMillis < startedAtMillis) return true
    return safeEpochElapsedMillis(nowMillis, startedAtMillis) > maxDurationMillis
}

fun saturatingEpochSubtract(value: Long, durationMillis: Long): Long {
    val safeDuration = durationMillis.coerceAtLeast(0L)
    return if (value < Long.MIN_VALUE + safeDuration) Long.MIN_VALUE else value - safeDuration
}

fun saturatingEpochAdd(value: Long, durationMillis: Long): Long {
    val safeDuration = durationMillis.coerceAtLeast(0L)
    return if (value > Long.MAX_VALUE - safeDuration) Long.MAX_VALUE else value + safeDuration
}
