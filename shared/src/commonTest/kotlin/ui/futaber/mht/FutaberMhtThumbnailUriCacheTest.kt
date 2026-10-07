package com.valoser.futacha.shared.ui.futaber.mht

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMhtThumbnailUriCacheTest {
    private fun jpeg(seed: Int, size: Int = 64) =
        ByteArray(size) { ((it + seed) % 251).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }

    private fun entry(
        fileName: String = "may-27-1.mht",
        savedAt: Long = 1_000L,
        thumbnail: ByteArray? = jpeg(1)
    ) = FutaberMhtEntry(
        fileName = fileName, relativePath = "futaber_mht/$fileName", sizeBytes = 10, title = "t", boardName = "b",
        boardKey = null, boardUrl = null, threadId = "1", savedAtMillis = savedAt, postCount = 1,
        withFullImages = false, thumbnail = thumbnail
    )

    private class Counter {
        var calls = 0
        val encode: (ByteArray) -> String = { bytes -> calls++; futaberMhtEncodeThumbnailUri(bytes) }
    }

    @Test
    fun sameEntryGivesTheSameTextAndEncodesOnce() {
        val counter = Counter()
        val cache = FutaberMhtThumbnailUriCache(encode = counter.encode)
        val first = cache.uriOf(entry())
        val second = cache.uriOf(entry()) // a rebuilt entry with the same content
        assertEquals(first, second)
        assertEquals(1, counter.calls)
        assertTrue(first!!.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun changedContentOrSavedTimeGivesAnotherText() {
        val counter = Counter()
        val cache = FutaberMhtThumbnailUriCache(encode = counter.encode)
        val base = cache.uriOf(entry())
        val otherThumb = cache.uriOf(entry(thumbnail = jpeg(2)))
        val otherTime = cache.uriOf(entry(savedAt = 2_000L, thumbnail = jpeg(3)))
        assertNotEquals(base, otherThumb)
        assertNotEquals(base, otherTime)
        assertEquals(3, counter.calls)
        // Same bytes, but the file was saved again: it is looked up afresh, never answered from the old file's picture.
        cache.uriOf(entry(savedAt = 3_000L))
        assertEquals(4, counter.calls)
    }

    @Test
    fun dropsTheLeastRecentlyUsedBeyondTheLimit() {
        val counter = Counter()
        val cache = FutaberMhtThumbnailUriCache(capacity = FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE, encode = counter.encode)
        val entries = (0 until FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE).map { entry("f$it.mht", thumbnail = jpeg(it)) }
        entries.forEach { cache.uriOf(it) }
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE, cache.sizeForTest())
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE, counter.calls)

        cache.uriOf(entries[0]) // used again, so the second one is now the oldest
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE, counter.calls)
        cache.uriOf(entry("extra.mht", thumbnail = jpeg(500))) // one over the limit
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE, cache.sizeForTest())
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE + 1, counter.calls)

        cache.uriOf(entries[0]) // kept
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE + 1, counter.calls)
        cache.uriOf(entries[1]) // dropped: encoded again
        assertEquals(FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE + 2, counter.calls)
    }

    @Test
    fun entryWithoutThumbnailStaysNull() {
        val counter = Counter()
        val cache = FutaberMhtThumbnailUriCache(encode = counter.encode)
        assertNull(cache.uriOf(entry(thumbnail = null)))
        assertEquals(0, counter.calls)
        assertEquals(0, cache.sizeForTest())
        assertEquals("", futaberMhtThumbnailUri(entry(thumbnail = null)))
    }

    @Test
    fun publicFunctionMatchesTheDirectEncoding() {
        val e = entry("direct.mht", thumbnail = jpeg(9))
        assertEquals(futaberMhtEncodeThumbnailUri(e.thumbnail!!), futaberMhtThumbnailUri(e))
        assertEquals(futaberMhtThumbnailUri(e), futaberMhtThumbnailUri(e))
    }
}
