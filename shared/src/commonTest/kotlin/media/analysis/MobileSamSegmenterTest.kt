package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.model.MosaicBounds
import kotlinx.coroutines.*
import kotlin.test.*

internal class ContourTestTensor(override val shape: List<Long>) : InferenceTensor {
    override val size = inferenceTensorSize(shape)
    val data = FloatArray(size)
    var closed = false
    override fun write(values: FloatArray) { require(values.size == size); writeSlice(values, 0) }
    override fun writeSlice(values: FloatArray, offset: Int) { check(!closed); validateSlice(values, offset); values.copyInto(data, offset) }
    override fun read(): FloatArray { check(!closed); return data.copyOf() }
    override fun close() { closed = true }
}

class MobileSamSegmenterTest {
    private val box = MosaicBounds(.5f, .5f, .5f, .5f)
    private fun logits() = FloatArray(4 * 256 * 256) { index ->
        val x = index % 256; val y = index / 256 % 256
        if (index < 256 * 256 && x in 96..159 && y in 48..79) 1f else -1f
    }
    @Test fun nonSquareHwcInputPreservesRgbRangeAndBilinearSamples() {
        val frame = AnalysisFrame(0, 2, 1, byteArrayOf(-1, 0, 0, 0, 0, -1))
        val (w, h) = mobileSamDimensions(frame)
        assertEquals(1024 to 512, w to h)
        ContourTestTensor(listOf(h.toLong(), w.toLong(), 3)).use { input ->
            prepareMobileSamInput(frame, w, h, input) {}
            assertContentEquals(floatArrayOf(255f, 0f, 0f), input.data.copyOfRange(0, 3))
            assertContentEquals(floatArrayOf(0f, 0f, 255f), input.data.copyOfRange(input.size - 3, input.size))
            assertEquals(127.25f, input.data[512 * 3], .01f)
            assertEquals(127.75f, input.data[512 * 3 + 2], .01f)
        }
    }
    @Test fun contourSelectionRespectsImagePaddingAndRejectsBackground() {
        val masks = logits()
        // Higher-scored background candidate must not displace the bounded candidate.
        for (i in 256 * 256 until 2 * 256 * 256) masks[i] = 1f
        // Bottom/right padding is outside the image, not evidence of an object.
        for (y in 128..255) for (x in 0..255) masks[y * 256 + x] = 1f
        val result = selectMobileSamMask(box, 1024, 512, floatArrayOf(.9f, .99f, 0f, 0f), masks)
        val mask = assertNotNull(result.mask)
        assertFalse(result.uncertain); assertTrue(mask.contains(.5f, .5f)); assertFalse(mask.contains(.05f, .05f))
        assertEquals(.25f, mask.coverage, .01f)
    }
    @Test fun lowConfidenceEmptyNonFiniteAndWholeBoxOutputsFallBackToRectangle() {
        for ((scores, masks) in listOf(
            floatArrayOf(.49f, Float.NaN, Float.POSITIVE_INFINITY, 2f) to logits(),
            floatArrayOf(.9f, 0f, 0f, 0f) to FloatArray(4 * 256 * 256) { -1f },
            floatArrayOf(.9f, 0f, 0f, 0f) to logits().apply { this[0] = Float.NaN },
            floatArrayOf(.9f, 0f, 0f, 0f) to FloatArray(4 * 256 * 256) { 1f }
        )) {
            val result = selectMobileSamMask(box, 1024, 512, scores, masks)
            assertNull(result.mask); assertTrue(result.uncertain)
        }
        assertTrue(selectMobileSamMask(box, 1024, 512, floatArrayOf(.7f, 0f, 0f, 0f), logits()).uncertain)
    }
    private inner class Fixture(private val onDecode: suspend () -> Unit = {}) : AutoCloseable {
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true, videoEditorEnabled = true)) }
        val tensors = mutableListOf<ContourTestTensor>()
        var encoderRuns = 0; var decoderRuns = 0; var closedRuntimes = 0
        var embedding: InferenceTensor? = null
        val encoder = object : CpuInferenceSession {
            override val inputNames = setOf("input_image")
            override val outputNames = setOf("image_embeddings")
            override suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit) {
                validateRun(inputs, outputs); encoderRuns++; embedding = outputs.getValue("image_embeddings")
                assertEquals(listOf(512L, 1024L, 3L), inputs.getValue("input_image").shape)
            }
            override fun close() { closedRuntimes++ }
        }
        val decoder = object : CpuInferenceSession {
            override val inputNames = setOf("image_embeddings", "point_coords", "point_labels", "mask_input", "has_mask_input", "orig_im_size")
            override val outputNames = setOf("low_res_masks", "iou_predictions", "masks")
            override suspend fun run(inputs: Map<String, InferenceTensor>, outputs: Map<String, InferenceTensor>, onRunning: () -> Unit) {
                validateRun(inputs, outputs); decoderRuns++; onDecode()
                assertSame(embedding, inputs.getValue("image_embeddings"))
                assertContentEquals(floatArrayOf(512f, 1024f), inputs.getValue("orig_im_size").read())
                assertContentEquals(floatArrayOf(2f, 3f), inputs.getValue("point_labels").read())
                assertContentEquals(floatArrayOf(256f, 128f, 768f, 384f), inputs.getValue("point_coords").read())
                outputs.getValue("low_res_masks").write(logits())
                outputs.getValue("iou_predictions").write(floatArrayOf(.9f, 0f, 0f, 0f))
            }
            override fun close() { closedRuntimes++ }
        }
        val segmenter = MobileSamSegmenter.create(gate, gate.permit(MediaFeature.IMAGE_EDITOR)!!, encoder, decoder) { shape ->
            ContourTestTensor(shape).also { tensors += it }
        }
        suspend fun run() = segmenter.segment(AnalysisFrame(0, 8, 4, ByteArray(8 * 4 * 3) { 127 }), listOf(box, box))
        override fun close() = segmenter.close()
    }
    @Test fun oneEncodingServesMultipleBoxesAndAllBuffersAreReleased() = runBlocking {
        Fixture().use { f ->
            assertEquals(2, f.run().count { it.mask != null && !it.uncertain })
            assertEquals(1, f.encoderRuns); assertEquals(2, f.decoderRuns)
            assertTrue(f.tensors.last().closed, "Per-frame image tensor is released immediately")
            f.segmenter.close(); f.segmenter.close()
            assertEquals(2, f.closedRuntimes); assertTrue(f.tensors.all { it.closed })
        }
    }
    @Test fun ownFeatureOffCancelsAndStalePermitCannotResumeAfterReenable() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        Fixture { entered.complete(Unit); awaitCancellation() }.use { f ->
            val job = async { f.run() }
            withTimeout(5_000) { entered.await() }
            f.gate.update(MediaFeatureSettings(videoEditorEnabled = true))
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            f.gate.update(MediaFeatureSettings(imageEditorEnabled = true))
            assertFailsWith<CancellationException> { f.run() }
            assertEquals(1, f.decoderRuns)
        }
    }
    @Test fun cancellationAndDecoderFailureReturnNoPartialContourList() = runBlocking {
        Fixture { error("decoder failed") }.use { f ->
            assertFailsWith<IllegalStateException> { f.run() }
            assertEquals(1, f.decoderRuns)
            assertTrue(f.tensors.last().closed)
        }
        val entered = CompletableDeferred<Unit>()
        Fixture { entered.complete(Unit); awaitCancellation() }.use { f ->
            val job = async { f.run() }
            withTimeout(5_000) { entered.await() }
            f.gate.update(MediaFeatureSettings(imageEditorEnabled = true)) // unrelated setting
            yield(); assertTrue(job.isActive)
            f.close()
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled); assertTrue(f.tensors.all { it.closed })
        }
    }
}
