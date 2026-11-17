package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.compat.CompatPullLabel
import com.valoser.futacha.shared.ui.compat.compatPullLabel
import com.valoser.futacha.shared.ui.compat.compatPullContentOffsetPx
import com.valoser.futacha.shared.ui.compat.compatPullVisualProgress
import com.valoser.futacha.shared.ui.compat.updateCompatPullDrag
import com.valoser.futacha.shared.ui.compat.shouldScrollCompatCatalogToTopAfterRefresh
import com.valoser.futacha.shared.ui.compat.shouldTriggerCompatPullRefresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatCatalogScrollTest {
    @Test
    fun pullLabelChangesAtMeasuredHeaderThresholdInBothDirections() {
        assertEquals(CompatPullLabel.PULL, compatPullLabel(47.9f, 48f, refreshing = false))
        assertEquals(CompatPullLabel.RELEASE, compatPullLabel(48f, 48f, refreshing = false))
        assertEquals(CompatPullLabel.RELEASE, compatPullLabel(-48f, 48f, refreshing = false))
        assertEquals(CompatPullLabel.LOADING, compatPullLabel(1f, 48f, refreshing = true))
        assertFalse(shouldTriggerCompatPullRefresh(47.9f, 48f))
        assertTrue(shouldTriggerCompatPullRefresh(-48f, 48f))
    }

    @Test
    fun pullHintAndContentMoveGraduallyInsteadOfJumpingOnFirstPixel() {
        assertEquals(0.01f, compatPullVisualProgress(1f, 100f), absoluteTolerance = 0.0001f)
        assertEquals(0.5f, compatPullVisualProgress(50f, 100f), absoluteTolerance = 0.0001f)
        assertEquals(1f, compatPullVisualProgress(200f, 100f))
        assertEquals(1f, compatPullContentOffsetPx(1f))
        assertEquals(-12f, compatPullContentOffsetPx(-12f))
    }

    @Test
    fun completedCatalogRefreshResetsTheScrollPosition() {
        assertTrue(shouldScrollCompatCatalogToTopAfterRefresh(refreshSucceeded = true))
        assertFalse(shouldScrollCompatCatalogToTopAfterRefresh(refreshSucceeded = false))
    }

    @Test
    fun pullDragReversalCancelsBeforeRelease() {
        val pulled = updateCompatPullDrag(
            totalDrag = 220f,
            dragAmount = 0f,
            maxAbsDrag = 300f
        )
        assertEquals(220f, pulled.totalDrag)

        val partiallyCancelled = updateCompatPullDrag(
            totalDrag = pulled.totalDrag,
            dragAmount = -100f,
            maxAbsDrag = 300f
        )
        assertEquals(120f, partiallyCancelled.totalDrag)
        assertEquals(-100f, partiallyCancelled.consumedDrag)

        val fullyCancelled = updateCompatPullDrag(
            totalDrag = partiallyCancelled.totalDrag,
            dragAmount = -200f,
            maxAbsDrag = 300f
        )
        assertEquals(0f, fullyCancelled.totalDrag)
        assertEquals(-120f, fullyCancelled.consumedDrag)
    }
}
