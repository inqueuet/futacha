package com.valoser.futacha.shared.ui.board

import kotlin.time.Clock

private const val HISTORY_THUMBNAIL_FAILURE_TTL_MILLIS = 10 * 60_000L
private const val HISTORY_THUMBNAIL_FAILURE_MAX_ENTRIES = 512

/**
 * Title images of dead threads answer 404/410, and Coil does not cache failures, so
 * every opening of the history drawer requested each of them again. Remembers those
 * URLs for a short while so the drawer shows the fallback icon without a request.
 * Only a missing image is recorded; transient failures are retried as before.
 * Accessed from the main thread (composition and its effects) only.
 */
internal class HistoryThumbnailFailureCache(
    private val ttlMillis: Long = HISTORY_THUMBNAIL_FAILURE_TTL_MILLIS,
    private val maxEntries: Int = HISTORY_THUMBNAIL_FAILURE_MAX_ENTRIES,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val failedAtByUrl = LinkedHashMap<String, Long>()

    fun isKnownMissing(url: String): Boolean {
        return retryAfterMillis(url) > 0L
    }

    fun retryAfterMillis(url: String): Long {
        val failedAt = failedAtByUrl[url] ?: return 0L
        val remaining = (ttlMillis - (nowMillis() - failedAt)).coerceIn(0L, ttlMillis)
        if (remaining == 0L) failedAtByUrl.remove(url)
        return remaining
    }

    fun forget(url: String) {
        failedAtByUrl.remove(url)
    }

    fun recordMissing(url: String) {
        failedAtByUrl.remove(url)
        failedAtByUrl[url] = nowMillis()
        while (failedAtByUrl.size > maxEntries) {
            failedAtByUrl.remove(failedAtByUrl.keys.first())
        }
    }
}

internal val historyThumbnailFailureCache = HistoryThumbnailFailureCache()
