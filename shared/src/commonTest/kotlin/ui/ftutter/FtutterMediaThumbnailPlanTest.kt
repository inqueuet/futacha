package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.unit.IntSize
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.image.HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES
import com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterMediaThumbnailPlanTest {
    private val post = CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "",
        imageUrl = "http://may.2chan.net/b/src/fixture.png", thumbnailUrl = "http://may.2chan.net/b/thumb/fixtures.jpg",
        thumbnailWidth = 250, thumbnailHeight = 250, imageFileSizeBytes = 1024)

    private fun plan(source: CompatPostSnapshot = post, mode: HighQualityThumbnailMode = HighQualityThumbnailMode.UNMETERED,
        box: IntSize = IntSize(600, 600), wifi: Boolean = true) =
        ftutterMediaThumbnailPlan(source, mode, box, decodedThumbnailWidth = 250, decodedThumbnailHeight = 250, unmetered = wifi)

    @Test fun enabledUpgradeUsesTheActualSlotAndAdvertisedSourceMetadata() {
        val plan = assertNotNull(plan())
        assertEquals("https://may.2chan.net/b/src/fixture.png", plan.url)
        assertTrue(plan.allowNetwork)
        assertEquals(600, plan.widthPx)
        assertEquals(600, plan.heightPx)
        assertFalse(plan.allowAnimation)
        assertNull(plan(box = IntSize(200, 200))) // The filmstrip need not fetch an already sharp thumbnail.
        assertNull(plan(box = IntSize.Zero))
    }

    @Test fun sharedConnectionPolicyControlsUpgradesAndOffMeansNoOriginal() {
        assertNull(plan(mode = HighQualityThumbnailMode.OFF))
        assertFalse(assertNotNull(plan(mode = HighQualityThumbnailMode.CACHED)).allowNetwork)
        assertFalse(assertNotNull(plan(wifi = false)).allowNetwork)
        assertTrue(assertNotNull(plan(mode = HighQualityThumbnailMode.ALWAYS, wifi = false)).allowNetwork)
    }

    @Test fun missingOrLargeAdvertisedFilesAreCacheOnlyEvenOnUnmeteredConnections() {
        listOf(null, 0L, HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES + 1).forEach { size ->
            assertFalse(assertNotNull(plan(source = post.copy(imageFileSizeBytes = size))).allowNetwork)
        }
        assertTrue(assertNotNull(plan(source = post.copy(imageFileSizeBytes = HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES))).allowNetwork)
    }

    @Test fun missingDimensionsUseDecodedThumbnailButVideosAndAlreadyOriginalSourcesDoNotUpgrade() {
        assertEquals(600, assertNotNull(plan(source = post.copy(thumbnailWidth = null, thumbnailHeight = null))).widthPx)
        assertNull(plan(source = post.copy(imageUrl = "https://may.2chan.net/b/src/fixture.webm")))
        assertNull(plan(source = post.copy(thumbnailUrl = post.imageUrl)))
        assertNull(plan(source = post.copy(imageUrl = "file:///private/fixture.png")))
        assertNull(plan(source = post.copy(thumbnailUrl = null)))
    }
}
