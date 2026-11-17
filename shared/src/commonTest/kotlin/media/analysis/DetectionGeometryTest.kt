package com.valoser.futacha.shared.media.analysis

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class DetectionGeometryTest {
    @Test fun modelSpecificPaddingAndCroppedTileCoordinatesRoundTrip() {
        val crop = DetectionWindow(300, 50, 400, 200)
        val real = DetectionTransform(DetectorModel.REAL, crop, 1000, 500)
        val anime = DetectionTransform(DetectorModel.ANIME, crop, 1000, 500)
        val r = real.bounds(160f, 80f, 80f, 40f)!!
        val a = anime.bounds(320f, 320f, 160f, 160f)!!
        assertEquals(r.centerX, a.centerX, .00001f); assertEquals(r.centerY, a.centerY, .00001f)
        assertEquals(.5f, r.centerX, .00001f); assertEquals(.3f, r.centerY, .00001f)
        assertEquals(.1f, r.width, .00001f); assertEquals(.1f, r.height, .00001f)
        assertNull(real.bounds(100f, 280f, 10f, 10f))
        assertNull(real.bounds(Float.NaN, 80f, 80f, 40f))
        assertFailsWith<IllegalArgumentException> { DetectionTransform(DetectorModel.REAL, DetectionWindow(500, 0, 100, 100), 500, 500) }
    }

    @Test fun targetClassesSurviveHigherOtherClassScoresAndOtherClassNms() {
        for (model in DetectorModel.entries) {
            val output = FloatArray((4 + model.classes) * model.candidates)
            candidate(output, model, 0, model.targets[0], .2f)
            candidate(output, model, 0, 0, .9f)
            candidate(output, model, 1, model.targets[0], .4f)
            candidate(output, model, 2, model.targets[1], .5f)
            val result = decodeDetections(output, model, transform(model), .15f, false)
            assertEquals(2, result.size)
            assertEquals(setOf("男性器候補", "女性器候補"), result.map { it.label }.toSet())
            assertEquals(.4f, result.first { it.label == model.label(model.targets[0]) }.score)
        }
    }

    @Test fun faceDetectionIsOptInAndMalformedScoresFailWithoutPartialResults() {
        val model = DetectorModel.REAL
        val output = FloatArray((4 + model.classes) * model.candidates)
        candidate(output, model, 0, 1, .8f)
        assertTrue(decodeDetections(output, model, transform(model), .15f, false).isEmpty())
        assertEquals("顔", decodeDetections(output, model, transform(model), .15f, true).single().label)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1.1f)) {
            candidate(output, model, 1, model.targets[0], invalid)
            assertFailsWith<IllegalStateException> { decodeDetections(output, model, transform(model), .15f, true) }
        }
        assertFailsWith<IllegalArgumentException> { decodeDetections(FloatArray(3), model, transform(model), .15f, false) }
    }

    @Test fun preprocessingUsesRgbPlanesHalfPixelSamplingAndModelSpecificPadding() {
        // One red pixel followed by one blue pixel. The three planes cannot be BGR or interleaved.
        val frame = AnalysisFrame(0, 2, 1, byteArrayOf(-1, 0, 0, 0, 0, -1))
        for (model in DetectorModel.entries) {
            val plane = model.edge * model.edge
            val output = FloatArray(3 * plane) { Float.NaN }
            val tx = DetectionTransform(model, DetectionWindow(0, 0, 2, 1), 2, 1)
            prepareDetectionInput(frame, tx, output) {}
            assertEquals(1f, output[0]); assertEquals(0f, output[plane]); assertEquals(0f, output[2 * plane])
            val x = model.edge / 2 - 1
            val blueWeight = (x + .5f) / tx.scaleX - .5f
            assertEquals(1 - blueWeight, output[x], .00001f)
            assertEquals(blueWeight, output[2 * plane + x], .00001f)
            if (model == DetectorModel.REAL) {
                assertEquals(0f, output[plane - 1]); assertEquals(0f, output[3 * plane - 1])
            } else assertEquals(1f, output[3 * plane - 1])
            assertTrue(output.all { it.isFinite() && it in 0f..1f })
        }
    }

    @Test fun preprocessingAndOutputDecodingHaveCancellationCheckpoints() {
        val model = DetectorModel.REAL
        val frame = AnalysisFrame(0, 2, 2, ByteArray(12))
        assertFailsWith<CancellationException> {
            prepareDetectionInput(frame, DetectionTransform(model, DetectionWindow(0, 0, 2, 2), 2, 2), FloatArray(320 * 320 * 3)) {
                throw CancellationException("cancelled")
            }
        }
        assertFailsWith<CancellationException> {
            decodeDetections(FloatArray(22 * 2100), model, transform(model), .15f, false) { throw CancellationException("cancelled") }
        }
    }

    private fun transform(model: DetectorModel) = DetectionTransform(model, DetectionWindow(0, 0, 640, 640), 640, 640)
    private fun candidate(out: FloatArray, model: DetectorModel, index: Int, cls: Int, score: Float) {
        val n = model.candidates
        out[index] = model.edge * .5f; out[n + index] = model.edge * .5f
        out[2 * n + index] = model.edge * .1f; out[3 * n + index] = model.edge * .1f
        out[(4 + cls) * n + index] = score
    }
}
