package com.valoser.futacha.shared.media.edit

import kotlin.test.*

class ImageEditorCameraTest {
    @Test fun fitPreservesLetterboxAndFullImageCoordinates() {
        val view = ImageEditorCamera().viewport(200, 100, 100, 100)
        assertEquals(EditorViewport(0f, 25f, 100f, 50f), view)
        assertNull(view.point(50f, 10f))
        assertEquals(EditPoint(.5f, .5f), view.point(50f, 50f))
    }

    @Test fun pinchKeepsTheTouchedImagePointUnderTheFingers() {
        val before = ImageEditorCamera()
        val touched = before.viewport(200, 100, 100, 100).point(25f, 50f)
        val after = before.transform(200, 100, 100, 100, 2f, 25f, 50f)
        assertEquals(touched, after.viewport(200, 100, 100, 100).point(25f, 50f))
        assertEquals(2f, after.zoom)
    }

    @Test fun panAndZoomBoundsCannotLoseTheImageOffScreen() {
        val zoomed = ImageEditorCamera().transform(200, 100, 100, 100, 100f, 50f, 50f)
        assertEquals(8f, zoomed.zoom)
        val right = zoomed.transform(200, 100, 100, 100, 1f, 50f, 50f, 10000f, 10000f)
            .viewport(200, 100, 100, 100)
        assertEquals(0f, right.left); assertEquals(0f, right.top)
        val left = zoomed.transform(200, 100, 100, 100, 1f, 50f, 50f, -10000f, -10000f)
            .viewport(200, 100, 100, 100)
        assertEquals(100f - left.width, left.left); assertEquals(100f - left.height, left.top)
        val fit = zoomed.transform(200, 100, 100, 100, .001f, 50f, 50f, 1000f, -1000f)
        assertEquals(ImageEditorCamera(), fit)
    }

    @Test fun zoomedPaintingAndRegionHitTestingReferToTheOriginalPixels() {
        val camera = ImageEditorCamera().transform(200, 100, 100, 100, 4f, 50f, 50f)
            .transform(200, 100, 100, 100, 1f, 50f, 50f, 40f, -20f)
        val viewport = camera.viewport(200, 100, 100, 100)
        val original = EditPoint(.4f, .6f)
        val visible = viewport.point(viewport.left + original.x * viewport.width, viewport.top + original.y * viewport.height)
        assertNotNull(visible)
        assertEquals(original.x, visible.x, .00001f); assertEquals(original.y, visible.y, .00001f)
        assertTrue(EditBounds(.35f, .55f, .1f, .1f).contains(visible))
    }

    @Test fun changingCanvasSizeKeepsTheVisibleCenterAndDoesNotModifyAnApprovedDocument() {
        val history = ImageEditHistory()
        history.change { it.copy(analysed = true, reviewed = true) }
        val approved = history.document.value
        val camera = ImageEditorCamera(4f, .4f, .6f)
        val a = camera.viewport(200, 100, 100, 100).point(50f, 50f)!!
        val b = camera.viewport(200, 100, 200, 100).point(100f, 50f)!!
        assertEquals(a.x, b.x, .00001f); assertEquals(a.y, b.y, .00001f)
        assertSame(approved, history.document.value)
        assertTrue(history.document.value.reviewed)
    }

    @Test fun invalidGesturesAreRejectedAndZeroSizeLayoutDoesNotDivideByZero() {
        val camera = ImageEditorCamera()
        assertEquals(camera, camera.transform(200, 100, 0, 0, 2f, 0f, 0f))
        assertFailsWith<IllegalArgumentException> { camera.transform(200, 100, 100, 100, Float.NaN, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { camera.transform(200, 100, 100, 100, -1f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { ImageEditorCamera(9f) }
    }
}
