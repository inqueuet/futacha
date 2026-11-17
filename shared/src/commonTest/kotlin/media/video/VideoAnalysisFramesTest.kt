package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import kotlin.test.*

class VideoAnalysisFramesTest {
    private val frames = VideoFrameIndex(longArrayOf(50_000, 83_333, 116_667, 250_000, 350_000), 383_333)
    private fun frame(time: Long) = AnalysisFrame(time, 1, 1, byteArrayOf(1, 2, 3), byteArrayOf(2))

    @Test fun halfOpenRangesKeepRealVfrTimestampsAndRejectDroppedOrDuplicateFrames() {
        val request = VideoAnalysisRequest(frames, 83_334, 350_000, 640, true)
        assertEquals(2, request.firstIndex); assertEquals(4, request.endIndex)
        VideoAnalysisCursor(request).apply { accept(frame(116_667)); accept(frame(250_000)); finish() }
        assertFailsWith<IllegalStateException> { VideoAnalysisCursor(request).apply { accept(frame(116_667)); finish() } }
        assertFailsWith<IllegalStateException> { VideoAnalysisCursor(request).accept(frame(250_000)) }
        assertFailsWith<IllegalStateException> { VideoAnalysisCursor(request).apply { accept(frame(116_667)); accept(frame(116_667)) } }
        assertFailsWith<IllegalStateException> { VideoAnalysisCursor(request).apply { accept(frame(116_667)); accept(frame(250_000)); accept(frame(350_000)) } }
        assertFailsWith<IllegalStateException> { VideoAnalysisCursor(request).accept(frame(116_667).copy(gray = byteArrayOf())) }
        assertTrue(VideoAnalysisRequest(frames, 83_334, 116_667, 1, false).isEmpty)
        assertEquals(5, VideoAnalysisRequest(frames, 350_000, 383_333, 640, true).endIndex)
        assertFailsWith<IllegalArgumentException> { VideoAnalysisRequest(frames, 0, 383_334, 640, true) }
        assertFailsWith<IllegalArgumentException> { VideoAnalysisRequest(frames, 0, 100_000, 1025, true) }
    }

    @Test fun chunkedReadsShareOneDecoderAndPublishOnlyCompleteChunks() = kotlinx.coroutines.runBlocking {
        val info = VideoEditInfo(1, 1, frames, hasAudio = false, hdr = false, rotationDegrees = 0)
        // Descending, as reverse tracking reads; the empty middle range is dropped.
        val ranges = listOf(250_000L to 383_333L, 116_667L to 116_667L, 50_000L to 250_000L)
        var decoderRuns = 0
        val decodeAll: suspend (String, VideoEditInfo, List<VideoAnalysisRequest>, suspend (Int, AnalysisFrame) -> Unit) -> Unit =
            { _, _, requests, consume ->
                decoderRuns++
                requests.forEachIndexed { chunk, request ->
                    for (i in request.firstIndex until request.endIndex) consume(chunk, frame(frames.timeAt(i)))
                }
            }
        val chunks = ArrayList<List<Long>>()
        readVideoAnalysisFrameChunks("/video.mp4", info, ranges, 640, true, { chunks += it.map { f -> f.timeUs } }, decodeAll)
        assertEquals(1, decoderRuns)
        assertEquals(listOf(listOf(250_000L, 350_000L), listOf(50_000L, 83_333L, 116_667L)), chunks)

        // A dropped frame fails the chunk before it reaches the tracker.
        chunks.clear()
        assertFailsWith<IllegalStateException> {
            readVideoAnalysisFrameChunks("/video.mp4", info, ranges, 640, true, { chunks += it.map { f -> f.timeUs } }) { _, _, _, consume ->
                consume(0, frame(250_000)); consume(1, frame(50_000))
            }
        }
        assertTrue(chunks.isEmpty())
        // Chunks must arrive in request order.
        assertFailsWith<IllegalStateException> {
            readVideoAnalysisFrameChunks("/video.mp4", info, ranges, 640, true, { chunks += it.map { f -> f.timeUs } }) { _, _, _, consume ->
                consume(1, frame(50_000)); consume(0, frame(250_000))
            }
        }
        // A decoder that stops early leaves the last chunk unpublished.
        assertFailsWith<IllegalStateException> {
            readVideoAnalysisFrameChunks("/video.mp4", info, ranges, 640, true, { chunks += it.map { f -> f.timeUs } }) { _, _, _, consume ->
                consume(0, frame(250_000)); consume(0, frame(350_000)); consume(1, frame(50_000))
            }
        }
        assertEquals(listOf(listOf(250_000L, 350_000L)), chunks)
    }

    @Test fun extractorEditListMappingRequiresEverySampleAndUnchangedRelativeTiming() {
        val timeline = VideoDecodeTimeline(listOf(66_667, 33_333, 0, 200_000, 300_000), frames)
        assertEquals(50_000L, timeline.originalTime(0)); assertEquals(250_000L, timeline.originalTime(200_000))
        assertEquals(300_000L, timeline.decodedTime(4))
        assertFailsWith<IllegalStateException> { timeline.originalTime(123) }
        for (times in listOf(listOf(0L, 33_333, 66_667, 300_000), listOf(0L, 33_333, 33_333, 200_000, 300_000),
            listOf(0L, 33_333, 66_667, 210_000, 300_000))) {
            assertFailsWith<IllegalArgumentException> { VideoDecodeTimeline(times, frames) }
        }
    }
}
