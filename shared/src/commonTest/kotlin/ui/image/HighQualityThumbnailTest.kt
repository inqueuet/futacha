package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighQualityThumbnailTest {
    private val original = "https://img.2chan.net/b/src/1790640387139.jpg"
    private val thumbnail = "https://img.2chan.net/b/thumb/1790640387139s.jpg"

    private fun plan(
        mode: HighQualityThumbnailMode = HighQualityThumbnailMode.CACHED,
        originalUrl: String? = original,
        thumbnailUrl: String? = thumbnail,
        width: Int? = 250,
        height: Int? = 249,
        box: Int = 875,
        sizeBytes: Long? = 78_559,
        unmetered: Boolean = false,
        crop: Boolean = false
    ) = planHighQualityThumbnail(
        mode = mode,
        originalUrl = originalUrl,
        thumbnailUrl = thumbnailUrl,
        thumbnailWidthPx = width,
        thumbnailHeightPx = height,
        boxWidthPx = box,
        boxHeightPx = box,
        originalSizeBytes = sizeBytes,
        isUnmeteredConnection = unmetered,
        crop = crop
    )

    @Test
    fun defaultModeFetchesOnUnmeteredNetworksAndReadsStoredLabels() {
        assertEquals(HighQualityThumbnailMode.UNMETERED, HighQualityThumbnailMode.DEFAULT)
        assertEquals(HighQualityThumbnailMode.UNMETERED, HighQualityThumbnailMode.fromStored(null))
        assertEquals(HighQualityThumbnailMode.UNMETERED, HighQualityThumbnailMode.fromStored("unknown"))
        HighQualityThumbnailMode.entries.forEach { mode ->
            assertEquals(mode, HighQualityThumbnailMode.fromStored(mode.storedValue))
            assertEquals(mode, HighQualityThumbnailMode.fromStored(mode.label))
        }
    }

    @Test
    fun stretchedThumbnailIsUpgradedToTheDisplayedSizeWithoutTraffic() {
        val result = assertNotNull(plan())
        assertEquals(original, result.url)
        assertFalse(result.allowNetwork)
        assertEquals(875, result.widthPx)
        assertEquals(872, result.heightPx)
        assertFalse(result.allowAnimation)
    }

    @Test
    fun offModeAndNearlyUnscaledThumbnailsKeepTheThumbnail() {
        assertNull(plan(mode = HighQualityThumbnailMode.OFF))
        // 5 catalog columns on a 411dp screen draw a 250px thumbnail at ~265px.
        assertNull(plan(box = 265))
        assertNull(plan(box = 374))
        assertNotNull(plan(box = 375))
    }

    @Test
    fun sourcesWithoutALargerStillAreNotUpgraded() {
        // An original of at most 250px is served unshrunk as its own thumbnail.
        assertNull(plan(width = 200, height = 120))
        assertNull(plan(originalUrl = "https://img.2chan.net/b/src/1790640387139.webm"))
        assertNull(plan(originalUrl = "https://example.com/src/1790640387139.jpg"))
        assertNull(plan(originalUrl = "file:///data/auto_save/src/1790640387139.jpg"))
        assertNull(plan(thumbnailUrl = original))
        assertNull(plan(thumbnailUrl = null))
        assertNull(plan(width = null))
    }

    @Test
    fun networkNeedsPermissionAndAStatedSizeWithinTheLimit() {
        assertFalse(assertNotNull(plan(mode = HighQualityThumbnailMode.UNMETERED)).allowNetwork)
        assertTrue(assertNotNull(plan(mode = HighQualityThumbnailMode.UNMETERED, unmetered = true)).allowNetwork)
        assertTrue(assertNotNull(plan(mode = HighQualityThumbnailMode.ALWAYS)).allowNetwork)
        assertTrue(
            assertNotNull(
                plan(mode = HighQualityThumbnailMode.ALWAYS, sizeBytes = HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES)
            ).allowNetwork
        )
        // Unknown or large files still use a cached original, never a download.
        assertFalse(assertNotNull(plan(mode = HighQualityThumbnailMode.ALWAYS, sizeBytes = null)).allowNetwork)
        assertFalse(
            assertNotNull(
                plan(mode = HighQualityThumbnailMode.ALWAYS, sizeBytes = HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES + 1)
            ).allowNetwork
        )
    }

    @Test
    fun croppedSlotsRequestEnoughPixelsToFillTheSlot() {
        val fit = assertNotNull(plan(width = 250, height = 141, box = 700))
        assertEquals(700, fit.widthPx)
        assertEquals(395, fit.heightPx)
        val crop = assertNotNull(plan(width = 250, height = 141, box = 700, crop = true))
        assertEquals(1241, crop.widthPx)
        assertEquals(700, crop.heightPx)
    }
}
