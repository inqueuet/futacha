package com.valoser.futacha.shared.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWheelZoomTest {
    private fun zoom(
        scale: Float = 1f,
        tx: Float = 0f,
        ty: Float = 0f,
        delta: Float,
        px: Float = 500f,
        py: Float = 300f
    ) = applyDesktopWheelZoom(
        scale = scale, translationX = tx, translationY = ty, scrollDeltaY = delta,
        pointerX = px, pointerY = py, viewportWidthPx = 1000f, viewportHeightPx = 600f,
        maxScale = 6f, fitThreshold = 1.05f
    )

    @Test
    fun scrollingUpZoomsInAndDownZoomsOut() {
        assertTrue(desktopWheelZoomFactor(-1f) > 1f)
        assertTrue(desktopWheelZoomFactor(1f) < 1f)
        assertEquals(1f, desktopWheelZoomFactor(0f))
        assertEquals(1f, desktopWheelZoomFactor(Float.NaN))
        // A runaway delta is limited to ten notches.
        assertEquals(desktopWheelZoomFactor(-10f), desktopWheelZoomFactor(-1_000f))
    }

    @Test
    fun zoomingAtTheCentreKeepsTheImageCentred() {
        val result = assertNotNull(zoom(delta = -3f))
        assertTrue(result.scale > 1.05f)
        assertEquals(0f, result.translationX)
        assertEquals(0f, result.translationY)
    }

    @Test
    fun theContentUnderThePointerStaysInPlace() {
        val px = 800f
        val py = 100f
        val result = assertNotNull(zoom(delta = -1f, px = px, py = py))
        // The point under the pointer keeps its screen position: t = c * (1 - s).
        assertEquals((px - 500f) * (1f - result.scale), result.translationX, 0.01f)
        assertEquals((py - 300f) * (1f - result.scale), result.translationY, 0.01f)
        // A second step from that state keeps the same point fixed.
        val again = assertNotNull(zoom(scale = result.scale, tx = result.translationX, ty = result.translationY, delta = -1f, px = px, py = py))
        val ratio = again.scale / result.scale
        assertEquals(result.translationX * ratio + (px - 500f) * (1f - ratio), again.translationX, 0.01f)
    }

    @Test
    fun zoomOutBackToFitResetsTheTranslation() {
        val result = assertNotNull(zoom(scale = 1.08f, tx = 40f, ty = -20f, delta = 3f))
        assertEquals(1f, result.scale)
        assertEquals(0f, result.translationX)
        assertEquals(0f, result.translationY)
    }

    @Test
    fun scaleIsLimitedAndTranslationStaysInsideTheViewport() {
        val atMax = zoom(scale = 6f, delta = -3f)
        assertNull(atMax)
        val near = assertNotNull(zoom(scale = 5.9f, tx = 0f, ty = 0f, delta = -10f, px = 1000f, py = 600f))
        assertEquals(6f, near.scale)
        assertTrue(abs(near.translationX) <= 1000f * 5f / 2f)
        assertTrue(abs(near.translationY) <= 600f * 5f / 2f)
        val corner = assertNotNull(zoom(scale = 2f, tx = 1000f, ty = 1000f, delta = -1f))
        assertTrue(corner.translationX <= 1000f * (corner.scale - 1f) / 2f)
        assertTrue(corner.translationY <= 600f * (corner.scale - 1f) / 2f)
    }

    @Test
    fun nothingHappensWithoutADeltaOrAViewport() {
        assertNull(zoom(delta = 0f))
        assertNull(zoom(scale = 1f, delta = 2f)) // already fit, zooming out
        assertNull(
            applyDesktopWheelZoom(1f, 0f, 0f, -1f, 0f, 0f, 0f, 600f, 6f, 1.05f)
        )
    }
}
