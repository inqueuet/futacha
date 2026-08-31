package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatViewerGestureSupportTest {
    @Test
    fun horizontalSwipeMovesOnePageWithoutWrapping() {
        assertEquals(1, compatViewerSwipeTarget(0, -400f, 1_000f, 3))
        assertEquals(1, compatViewerSwipeTarget(2, 400f, 1_000f, 3))
        assertEquals(null, compatViewerSwipeTarget(0, 400f, 1_000f, 3))
        assertEquals(null, compatViewerSwipeTarget(2, -400f, 1_000f, 3))
        assertEquals(null, compatViewerSwipeTarget(0, -100f, 1_000f, 3))
    }

    @Test
    fun dismiss_acceptsEitherDirectionAtDistanceThreshold() {
        assertFalse(shouldDismissCompatViewer(199f, 0f, 1_000f))
        assertTrue(shouldDismissCompatViewer(200f, 0f, 1_000f))
        assertTrue(shouldDismissCompatViewer(-200f, 0f, 1_000f))
    }

    @Test
    fun dismiss_acceptsEitherDirectionAtVelocityThreshold() {
        assertFalse(shouldDismissCompatViewer(0f, 1_999f, 1_000f))
        assertTrue(shouldDismissCompatViewer(0f, 2_000f, 1_000f))
        assertTrue(shouldDismissCompatViewer(0f, -2_000f, 1_000f))
        assertFalse(shouldDismissCompatViewer(500f, 9_999f, 0f))
    }

    @Test
    fun rubberBandAndZoomBoundsMatchViewerContract() {
        assertEquals(30f, dampCompatViewerVerticalDrag(60f))
        assertEquals(-30f, dampCompatViewerVerticalDrag(-60f))
        assertEquals(30f, renderCompatViewerVerticalOffset(60f, dismissalAnimating = false))
        assertEquals(60f, renderCompatViewerVerticalOffset(60f, dismissalAnimating = true))
        assertEquals(0f, clampCompatViewerZoomOffset(100f, 1_000f, 1f))
        assertEquals(500f, clampCompatViewerZoomOffset(700f, 1_000f, 2f))
        assertEquals(-500f, clampCompatViewerZoomOffset(-700f, 1_000f, 2f))
    }

    @Test
    fun zoomTranslationKeepsExistingPanInTheNewScaleSpace() {
        val centered = compatViewerZoomTranslation(
            currentX = 100f,
            currentY = -80f,
            panX = 0f,
            panY = 0f,
            centroidX = 500f,
            centroidY = 500f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 1_000f,
            oldScale = 2f,
            newScale = 3f
        )
        assertEquals(150f, centered.first)
        assertEquals(-120f, centered.second)

        val zoomAtRight = compatViewerZoomTranslation(
            currentX = 0f,
            currentY = 0f,
            panX = 0f,
            panY = 0f,
            centroidX = 750f,
            centroidY = 250f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 1_000f,
            oldScale = 1f,
            newScale = 2f
        )
        assertEquals(-250f, zoomAtRight.first)
        assertEquals(250f, zoomAtRight.second)
    }
}
