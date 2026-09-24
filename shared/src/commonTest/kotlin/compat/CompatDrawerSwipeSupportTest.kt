package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatDrawerSwipeSupportTest {
    @Test
    fun alphaMatchesApkDistanceFormula() {
        assertEquals(1f, compatDrawerSwipeAlpha(0f, 1_000f))
        assertEquals(0.5f, compatDrawerSwipeAlpha(250f, 1_000f))
        assertEquals(0f, compatDrawerSwipeAlpha(500f, 1_000f))
        assertEquals(0f, compatDrawerSwipeAlpha(800f, 1_000f))
    }

    @Test
    fun distanceRequiresMoreThanHalfWidth() {
        assertFalse(
            shouldDismissCompatDrawerSwipe(
                rawDistancePx = 500f,
                widthPx = 1_000f,
                xVelocityPxPerSecond = 0f,
                yVelocityPxPerSecond = 0f,
                minFlingVelocityPxPerSecond = 800f,
                maxFlingVelocityPxPerSecond = 8_000f,
                swiping = true
            )
        )
        assertTrue(
            shouldDismissCompatDrawerSwipe(
                rawDistancePx = -501f,
                widthPx = 1_000f,
                xVelocityPxPerSecond = 0f,
                yVelocityPxPerSecond = 0f,
                minFlingVelocityPxPerSecond = 800f,
                maxFlingVelocityPxPerSecond = 8_000f,
                swiping = true
            )
        )
    }

    @Test
    fun flingRequiresHorizontalDirectionMatchAndBounds() {
        fun decide(xVelocity: Float, yVelocity: Float = 0f) =
            shouldDismissCompatDrawerSwipe(
                rawDistancePx = 100f,
                widthPx = 1_000f,
                xVelocityPxPerSecond = xVelocity,
                yVelocityPxPerSecond = yVelocity,
                minFlingVelocityPxPerSecond = 800f,
                maxFlingVelocityPxPerSecond = 8_000f,
                swiping = true
            )

        assertTrue(decide(800f))
        assertFalse(decide(-800f))
        assertFalse(decide(800f, 801f))
        assertFalse(decide(7_999f + 2f))
    }

    @Test
    fun nonSwipingGesturesNeverDismiss() {
        assertFalse(
            shouldDismissCompatDrawerSwipe(
                rawDistancePx = 900f,
                widthPx = 1_000f,
                xVelocityPxPerSecond = 9_000f,
                yVelocityPxPerSecond = 0f,
                minFlingVelocityPxPerSecond = 800f,
                maxFlingVelocityPxPerSecond = 8_000f,
                swiping = false
            )
        )
    }

    @Test
    fun visibleDrawerWidthFollowsPreviewAndSheetOffset() {
        assertEquals(40f, compatDrawerVisibleWidthPx(40f, isClosed = true, isOpen = false, currentOffset = -320f, widthPx = 320f))
        assertEquals(120f, compatDrawerVisibleWidthPx(0f, isClosed = false, isOpen = false, currentOffset = -200f, widthPx = 320f))
        assertEquals(0f, compatDrawerVisibleWidthPx(0f, isClosed = true, isOpen = false, currentOffset = -400f, widthPx = 320f))
        assertEquals(320f, compatDrawerVisibleWidthPx(0f, isClosed = false, isOpen = true, currentOffset = 10f, widthPx = 320f))
        assertEquals(320f, compatDrawerVisibleWidthPx(0f, isClosed = false, isOpen = true, currentOffset = Float.NaN, widthPx = 320f))
        assertEquals(0f, compatDrawerVisibleWidthPx(0f, isClosed = true, isOpen = false, currentOffset = Float.NaN, widthPx = 320f))
    }
}
