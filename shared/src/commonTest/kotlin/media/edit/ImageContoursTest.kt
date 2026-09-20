package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.media.analysis.ContourResult
import com.valoser.futacha.shared.media.video.model.MosaicMask
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ImageContoursTest {
    private val source = EditRaster(128, 64, IntArray(128 * 64) { 0xffddaa88.toInt() })
    private val box = EditRegion(1, EditBounds(0f, 0f, 1f, 1f), EditStyle.BLACK, contourMargin = 0)
    @Test fun onePercentImageBoundsAreNotExpandedToTheVideoMinimum() {
        val bounds = EditBounds(.99f, .4f, .01f, .01f)
        val contour = bounds.asMosaicBounds()
        assertEquals(.01f, contour.width); assertEquals(.01f, contour.height)
        assertEquals(.995f, contour.centerX, .000001f)
        val painted = EditRegion(1, bounds).paintContour(EditPoint(.995f, .405f), EditPoint(.995f, .405f), 2048, 2048, .2f, false)
        assertTrue(assertNotNull(painted.contour).contains(.5f, .5f))
        assertFalse(painted.contour.contains(.1f, .1f))
    }
    @Test fun manualContourCoversOnlyPaintedPixelsAndErasePreservesOtherRegions() = runBlocking {
        val painted = box.paintContour(EditPoint(.25f, .5f), EditPoint(.75f, .5f), 128, 64, .1f, false)
        val result = renderImageEdit(source, ImageEditDocument(regions = listOf(painted)))
        assertEquals(0xff000000.toInt(), result.argb[32 * 128 + 64]); assertEquals(source.argb[0], result.argb[0])
        val erased = painted.paintContour(EditPoint(.5f, .5f), EditPoint(.5f, .5f), 128, 64, .2f, true)
        val other = EditRegion(2, EditBounds(.48f, .48f, .04f, .04f), EditStyle.BLACK)
        val output = renderImageEdit(source, ImageEditDocument(regions = listOf(erased, other)))
        assertEquals(0xff000000.toInt(), output.argb[32 * 128 + 64])
        assertEquals(source.argb[32 * 128 + 57], output.argb[32 * 128 + 57])
    }
    @Test fun thinContoursSurviveDownsamplingAndMarginOnlyAddsCoverage() = runBlocking {
        val thin = MosaicMask.from { x, y -> x == 63 && y == 63 }
        val small = EditRaster(1, 1, intArrayOf(-1))
        assertEquals(0xff000000.toInt(), renderImageEdit(small, ImageEditDocument(regions = listOf(box.copy(contour = thin)))).argb.single())
        val before = renderImageEdit(source, ImageEditDocument(regions = listOf(box.copy(contour = thin))))
        val after = renderImageEdit(source, ImageEditDocument(regions = listOf(box.copy(contour = thin, contourMargin = 4))))
        assertTrue(after.argb.count { it == 0xff000000.toInt() } > before.argb.count { it == 0xff000000.toInt() })
        assertTrue(before.argb.indices.all { before.argb[it] != 0xff000000.toInt() || after.argb[it] == 0xff000000.toInt() })
    }
    @Test fun emptyContourCannotBeExportedEvenAfterReviewAndRectangleResetRestoresCoverage() = runBlocking {
        val empty = ImageEditDocument(regions = listOf(box.copy(contour = MosaicMask.EMPTY)), analysed = true, reviewed = true)
        assertTrue(empty.hasEmptyContour); assertFailsWith<IllegalStateException> { empty.requireExportReady() }
        val reset = empty.copy(regions = listOf(box)); reset.requireExportReady()
        assertTrue(renderImageEdit(source, reset).argb.all { it == 0xff000000.toInt() })
    }
    @Test fun contourGestureIsOneUndoAndAnyContourEditRevokesReview() {
        val history = ImageEditHistory()
        history.change { it.copy(regions = listOf(box), analysed = true, reviewed = true) }
        history.change { it.copy(reviewed = true) }
        val approved = history.document.value
        repeat(4) { index -> history.preview { it.copy(regions = listOf(it.regions.single().paintContour(
            EditPoint(.3f + .1f * index, .5f), EditPoint(.4f + .1f * index, .5f), 128, 64, .1f, false))) } }
        assertTrue(history.document.value.needsReview)
        history.commit(); history.undo(); assertSame(approved, history.document.value)
        history.redo(); assertNotNull(history.document.value.regions.single().contour)
        assertTrue(history.document.value.needsReview)
    }
    @Test fun automaticFallbackIsAtomicAndForcesReviewWithoutLosingManualPixels() {
        val existing = box.copy(contour = MosaicMask.FULL)
        val before = ImageEditDocument(regions = listOf(existing))
        assertFailsWith<IllegalArgumentException> { before.withContours(listOf(1, 2), listOf(ContourResult(null, true))) }
        assertSame(MosaicMask.FULL, before.regions.single().contour)
        val fallback = before.withContours(listOf(1), listOf(ContourResult(null, true)))
        assertTrue(fallback.needsReview); assertNull(fallback.regions.single().contour)
        assertTrue(fallback.regions.single().contourUncertain)
        val empty = before.withContours(listOf(1), listOf(ContourResult(MosaicMask.EMPTY, false)))
        assertNull(empty.regions.single().contour); assertTrue(empty.regions.single().contourUncertain)
    }
    @Test fun contourStaysAttachedToMovedBoundsAndOutsideStrokesDoNotCreateEmptyMasks() = runBlocking {
        val mask = MosaicMask.from { x, _ -> x < 64 }
        val left = box.copy(bounds = EditBounds(0f, 0f, .5f, 1f), contour = mask)
        val right = left.copy(bounds = EditBounds(.5f, 0f, .5f, 1f))
        val output = renderImageEdit(source, ImageEditDocument(regions = listOf(right)))
        assertEquals(source.argb[32 * 128 + 16], output.argb[32 * 128 + 16])
        assertEquals(0xff000000.toInt(), output.argb[32 * 128 + 80])
        assertEquals(source.argb[32 * 128 + 112], output.argb[32 * 128 + 112])
        val region = EditRegion(2)
        assertSame(region, region.paintContour(EditPoint(0f, 0f), EditPoint(.01f, .01f), 128, 64, .01f, false))
    }
}
