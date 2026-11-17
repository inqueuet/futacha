package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.media.prompt.GenerationImageFixtures
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.*
import kotlin.test.*

class ImageEditDocumentTest {
    private fun raster(size: Int = 64) = EditRaster(size, size, IntArray(size * size) { 0xff000000.toInt() or (it * 301 and 0xffffff) })

    @Test fun undoCommitsOneDragAndRedoIsInvalidatedByNewWork() {
        val h = ImageEditHistory()
        h.preview { it.copy(regions = listOf(EditRegion(1))) }
        repeat(20) { n -> h.preview { it.copy(regions = listOf(EditRegion(1, bounds = EditBounds(n / 100f, .1f, .5f, .5f)))) } }
        assertFalse(h.canUndo)
        h.commit(); assertEquals(true to false, h.availability.value)
        h.undo(); assertTrue(h.document.value.regions.isEmpty()); assertEquals(false to true, h.availability.value)
        h.redo(); assertEquals(.19f, h.document.value.regions.single().bounds.left)
        h.undo(); h.change { it.copy(regions = listOf(EditRegion(2))) }
        assertFalse(h.canRedo)
    }
    @Test fun cancelledGestureRestoresCommittedPixelsAndHistoryIsBounded() {
        val h = ImageEditHistory()
        repeat(70) { n -> h.change { it.copy(regions = listOf(EditRegion(n))) } }
        h.preview { it.copy(regions = emptyList()) }; h.cancelGesture()
        assertEquals(69, h.document.value.regions.single().id)
        repeat(50) { h.undo() }; assertFalse(h.canUndo)
        assertEquals(19, h.document.value.regions.single().id)
    }
    @Test fun coordinatesIgnoreLetterboxAndClampMovementToTheImage() {
        val v = EditorViewport.fit(200, 100, 100, 100)
        assertEquals(EditorViewport(0f, 25f, 100f, 50f), v)
        assertNull(v.point(50f, 10f)); assertEquals(EditPoint(.5f, .5f), v.point(50f, 50f))
        assertEquals(EditPoint(1f, 0f), v.point(1000f, -100f, true))
        assertEquals(EditBounds(.5f, 0f, .5f, .2f), EditBounds(.9f, -.5f, .5f, .2f).constrained())
    }
    @Test fun malformedAndOversizedDocumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { EditPoint(Float.NaN, 0f) }
        assertFailsWith<IllegalArgumentException> { ImageEditDocument(regions = List(17) { EditRegion(it) }).validated() }
        assertFailsWith<IllegalArgumentException> { ImageEditDocument(regions = listOf(EditRegion(1), EditRegion(1))).validated() }
        assertFailsWith<IllegalArgumentException> { ImageEditDocument(brushOpacity = Float.NaN).validated() }
        assertFailsWith<IllegalArgumentException> { ImageEditDocument(strokes = listOf(EditStroke(List(32_769) { EditPoint(.5f, .5f) }, .1f, false))).validated() }
    }
    @Test fun brushInterpolatesSegmentsAndEraserRestoresOriginal() = runBlocking {
        val source = raster()
        val path = EditStroke(listOf(EditPoint(.1f, .5f), EditPoint(.9f, .5f)), .2f, false)
        val painted = renderImageEdit(source, ImageEditDocument(strokes = listOf(path), brushBlockFraction = .2f))
        // A pixel between the stroke endpoints and away from the mosaic sample center.
        assertNotEquals(source.argb[32 * 64 + 30], painted.argb[32 * 64 + 30])
        assertEquals(source.argb[0], painted.argb[0])
        val erased = renderImageEdit(source, ImageEditDocument(strokes = listOf(path, path.copy(erase = true))))
        assertContentEquals(source.argb, erased.argb)
    }
    @Test fun blackCoverCannotBeRevealedByOverlappingMosaicOrBrushErasure() = runBlocking {
        val source = raster()
        val original = source.argb.copyOf()
        val black = EditRegion(1, style = EditStyle.BLACK)
        val mosaic = EditRegion(2, bounds = EditBounds(0f, 0f, 1f, 1f), blockFraction = .2f)
        for (regions in listOf(listOf(black, mosaic), listOf(mosaic, black))) {
            val result = renderImageEdit(source, ImageEditDocument(regions = regions,
                strokes = listOf(EditStroke(listOf(EditPoint(.5f, .5f)), .3f, true))))
            assertEquals(0xff000000.toInt(), result.argb[32 * 64 + 32])
            assertNotEquals(0xff000000.toInt(), result.argb[0])
        }
        assertContentEquals(original, source.argb)
    }
    @Test fun opacityAndTransparentPixelsMatchJpegWhiteBackground() = runBlocking {
        val result = renderImageEdit(EditRaster(2, 2, intArrayOf(0, 0x80ff0000.toInt(), -1, 0xff123456.toInt())), ImageEditDocument())
        assertEquals(-1, result.argb[0]); assertEquals(0xffff7f7f.toInt(), result.argb[1]); assertEquals(0xff123456.toInt(), result.argb[3])
        val source = raster()
        assertContentEquals(source.argb, renderImageEdit(source, ImageEditDocument(brushOpacity = 0f,
            strokes = listOf(EditStroke(listOf(EditPoint(.5f, .5f)), .3f, false)))).argb)
    }
    @Test fun renderingHonorsCancellation(): Unit = runBlocking {
        val cancelled = Job().apply { cancel() }
        assertFailsWith<CancellationException> { withContext(cancelled) { renderImageEdit(raster(), ImageEditDocument()) } }
    }
    @Test fun acceptedFormatsAreStaticAndAnimationIsNotFlattenedSilently() {
        validateEditableImage(GenerationImageFixtures.jpeg); validateEditableImage(GenerationImageFixtures.webp)
        assertFailsWith<IllegalStateException> { validateEditableImage("GIF89a".encodeToByteArray()) }
        val webp = GenerationImageFixtures.webp.copyOf(); webp[20] = (webp[20].toInt() or 2).toByte()
        assertFailsWith<IllegalArgumentException> { validateEditableImage(webp) }
        assertFailsWith<IllegalArgumentException> { validateEditableImage(byteArrayOf(1, 2)) }
    }
    @Test fun savingCreatesANewFileAndGateFailureDeletesUncommittedOutput() = runBlocking {
        val fs = InMemoryFileSystem(); val target = SaveLocation.Path("editor")
        val image = ImageData(byteArrayOf(-1, -40, -1, -39), "edited-123-abc.jpg")
        var checks = 0
        assertFailsWith<IllegalStateException> { saveEditedImage(fs, target, image) { if (++checks == 2) error("OFF") } }
        assertFalse(fs.exists(target, "edited_images/${image.fileName}"))
        val saved = saveEditedImage(fs, target, image) {}
        assertContentEquals(image.bytes, fs.readBytes(target, saved).getOrThrow())
        assertEquals("edited_images/edited-123-abc.jpg", saved)
    }
}
