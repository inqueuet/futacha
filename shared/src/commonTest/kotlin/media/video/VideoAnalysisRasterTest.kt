package com.valoser.futacha.shared.media.video

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class VideoAnalysisRasterTest {
    @Test fun croppedPaddedBgraRotatesAndMirrorsWithoutSamplingPadding() {
        val bytes = ByteArray(7 + 32 * 4) { 99 }
        // 3x2 crop inside a padded 5x4 image, channel-distinct six pixels.
        for (y in 0..1) for (x in 0..2) {
            val value = y * 3 + x + 1; val index = 7 + (y + 1) * 32 + (x + 1) * 4
            bytes[index] = (value + 20).toByte(); bytes[index + 1] = (value + 10).toByte(); bytes[index + 2] = value.toByte()
        }
        val plane = AnalysisBytePlane(bytes.size, 32, 4, 7) { bytes[it] }
        val expected = mapOf(0 to listOf(1, 2, 3, 4, 5, 6), 90 to listOf(4, 1, 5, 2, 6, 3),
            180 to listOf(6, 5, 4, 3, 2, 1), 270 to listOf(3, 6, 2, 5, 1, 4))
        for ((rotation, red) in expected) for (mirror in listOf(false, true)) {
            val geometry = AnalysisRasterGeometry(5, 4, 1, 1, 3, 2, rotation, 1024, mirror)
            val frame = sampleBgraAnalysisFrame(321, geometry, plane, true)
            val values = if (mirror) red.map { if (it <= 3) 4 - it else 10 - it } else red
            assertEquals(values, frame.rgb.filterIndexed { i, _ -> i % 3 == 0 }.map { it.toInt() }, "$rotation/$mirror")
            assertEquals(values.map { it + 10 }, frame.rgb.filterIndexed { i, _ -> i % 3 == 1 }.map { it.toInt() })
            assertEquals(values.map { it + 20 }, frame.rgb.filterIndexed { i, _ -> i % 3 == 2 }.map { it.toInt() })
            assertEquals(321L, frame.timeUs)
            assertEquals(if (rotation % 180 == 0) 3 else 2, frame.width)
            assertEquals(if (rotation % 180 == 0) 2 else 3, frame.height)
        }
        val small = sampleBgraAnalysisFrame(1, AnalysisRasterGeometry(5, 4, 1, 1, 3, 2, 90, 2), plane, false)
        assertEquals(1, small.width); assertEquals(2, small.height); assertTrue(small.rgb.isEmpty()); assertEquals(2, small.gray.size)
    }

    @Test fun planarAndInterleavedYuvRespectCropRangeAndColorMatrix() {
        for (interleaved in listOf(false, true)) for (full in listOf(false, true)) for (bt709 in listOf(false, true)) {
            val y = ByteArray(5 + 8 * 4) { 33 }
            val uvStride = if (interleaved) 2 else 1
            val u = ByteArray(3 + 8 * 2) { 17 }; val v = ByteArray(4 + 8 * 2) { 29 }
            for (row in 0..3) for (x in 0..5) y[5 + row * 8 + x] = (if (full) 76 else 81).toByte()
            for (row in 0..1) for (x in 0..2) {
                u[3 + row * 8 + x * uvStride] = (if (full) 85 else 90).toByte()
                v[4 + row * 8 + x * uvStride] = (if (full) 255 else 240).toByte()
            }
            val planes = listOf(AnalysisBytePlane(y.size, 8, 1, 5) { y[it] },
                AnalysisBytePlane(u.size, 8, uvStride, 3) { u[it] }, AnalysisBytePlane(v.size, 8, uvStride, 4) { v[it] })
            val frame = sampleYuvAnalysisFrame(0, AnalysisRasterGeometry(6, 4, 1, 1, 4, 2, 90), planes[0], planes[1], planes[2], full, bt709, true)
            assertEquals(8 * 3, frame.rgb.size)
            for (i in 0 until 8) {
                assertTrue((frame.rgb[i * 3].toInt() and 255) >= 250)
                assertTrue((frame.rgb[i * 3 + 1].toInt() and 255) in if (bt709) 20..28 else 0..3)
                assertTrue((frame.rgb[i * 3 + 2].toInt() and 255) < 3)
            }
            assertTrue(frame.gray.all { (it.toInt() and 255) in 75..76 })
        }
    }

    @Test fun invalidPlaneBoundsAndCancellationNeverReadOutsideNativeBuffers() {
        var reads = 0
        val short = AnalysisBytePlane(15, 8, 4) { reads++; 0 }
        assertFailsWith<IllegalArgumentException> { sampleBgraAnalysisFrame(0, AnalysisRasterGeometry(2, 2), short, true) }
        assertEquals(0, reads)
        assertFailsWith<IllegalArgumentException> { AnalysisRasterGeometry(2, 2, 1, 1, 2, 1) }
        assertFailsWith<IllegalArgumentException> { AnalysisBytePlane(Int.MAX_VALUE, Int.MAX_VALUE, 4, 1) { error("out of bounds") }.requireRectangle(1, 2, 4) }
        val large = AnalysisBytePlane(64 * 64 * 4, 64 * 4, 4) { reads++; 1 }
        var checks = 0
        assertFailsWith<CancellationException> {
            sampleBgraAnalysisFrame(0, AnalysisRasterGeometry(64, 64), large, true) {
                if (++checks == 2) throw CancellationException("cancel during sampling")
            }
        }
        assertEquals(16 * 64 * 3, reads)
    }
}
