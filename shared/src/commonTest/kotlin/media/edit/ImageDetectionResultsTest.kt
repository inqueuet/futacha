package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.video.model.MosaicBounds
import kotlinx.coroutines.*
import kotlin.test.*

class ImageDetectionResultsTest {
    private val candidate = Detection(MosaicBounds(.5f, .5f, .25f, .25f), "女性器候補", .8f)

    @Test fun candidatesAppendAtomicallyAndRequireReviewEvenWhenNoneFound() {
        val original = ImageEditDocument(regions = listOf(EditRegion(1), EditRegion(8)))
        val next = original.withDetections(listOf(candidate))
        assertEquals(original.regions, next.regions.take(2))
        assertEquals(2, next.regions.last().id)
        assertEquals("女性器候補", next.regions.last().label)
        assertEquals(.8f, next.regions.last().confidence)
        assertTrue(next.needsReview)
        assertFailsWith<IllegalStateException> { next.requireExportReady() }
        next.copy(reviewed = true).requireExportReady()
        assertTrue(original.withDetections(emptyList()).needsReview)
        assertFailsWith<IllegalStateException> { original.withDetections(List(15) { candidate }) }
        assertEquals(2, original.regions.size)
    }

    @Test fun everyPixelEditRevokesReviewAndUndoRestoresTheExactApprovedImage() {
        val history = ImageEditHistory()
        history.change { it.withDetections(listOf(candidate)) }
        history.change { it.copy(reviewed = true) }
        val approved = history.document.value
        for (change in listOf<(ImageEditDocument) -> ImageEditDocument>(
            { it.copy(regions = emptyList()) },
            { it.copy(regions = it.regions.map { region -> region.copy(style = EditStyle.BLACK) }) },
            { it.copy(strokes = listOf(EditStroke(listOf(EditPoint(.3f, .4f)), .1f, false))) },
            { it.copy(brushBlockFraction = .1f) },
            { it.copy(brushOpacity = .5f) }
        )) {
            history.change(change)
            assertTrue(history.document.value.needsReview)
            history.undo(); assertEquals(approved, history.document.value)
            history.redo(); assertTrue(history.document.value.needsReview)
            history.undo()
        }
    }

    @Test fun imageAnalysisDoesNotEnlargeAndUsesNeutralTransparencyWithoutMutatingInput() = runBlocking {
        val pixels = intArrayOf(0x00ff0000, 0xff0000ff.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt())
        val raster = EditRaster(2, 2, pixels.copyOf())
        val frame = imageAnalysisFrame(raster, 640)
        assertEquals(2, frame.width); assertEquals(2, frame.height)
        assertContentEquals(byteArrayOf(127, 127, 127, 0, 0, -1, -1, 0, 0, 0, -1, 0), frame.rgb)
        assertContentEquals(pixels, raster.argb)
        val small = imageAnalysisFrame(raster, 1)
        assertEquals(1, small.width); assertEquals(1, small.height)
        assertTrue(small.rgb.all { (it.toInt() and 255) in 95..96 })
    }

    @Test fun imageAnalysisKeepsAspectRatioAndBoundsPixelMemory() = runBlocking {
        val wide = EditRaster(2048, 1024, IntArray(2048 * 1024) { -1 })
        val normal = imageAnalysisFrame(wide, 640)
        assertEquals(640, normal.width); assertEquals(320, normal.height)
        val tiled = imageAnalysisFrame(wide, 960)
        assertEquals(960, tiled.width); assertEquals(480, tiled.height)
        assertTrue(tiled.rgb.all { it == (-1).toByte() })
    }
}
