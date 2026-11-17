package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryThumbnailFailureCacheTest {
    @Test
    fun missingThumbnailIsSkippedUntilTheEntryExpires() {
        var now = 1_000L
        val cache = HistoryThumbnailFailureCache(ttlMillis = 60_000L, nowMillis = { now })
        val url = "https://may.2chan.net/b/thumb/1s.jpg"
        assertFalse(cache.isKnownMissing(url))

        cache.recordMissing(url)
        now += 59_999L
        assertTrue(cache.isKnownMissing(url))

        now += 1L
        assertFalse(cache.isKnownMissing(url))
    }

    @Test
    fun oldestEntriesAreEvictedBeyondTheLimit() {
        val cache = HistoryThumbnailFailureCache(maxEntries = 2, nowMillis = { 0L })
        cache.recordMissing("a")
        cache.recordMissing("b")
        cache.recordMissing("c")
        assertFalse(cache.isKnownMissing("a"))
        assertTrue(cache.isKnownMissing("b"))
        assertTrue(cache.isKnownMissing("c"))
    }
}
