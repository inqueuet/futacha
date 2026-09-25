package com.valoser.futacha.shared.media.video

import androidx.compose.ui.graphics.ImageBitmap
import com.valoser.futacha.shared.media.video.model.MosaicDocument
import com.valoser.futacha.shared.media.video.model.MosaicRegion
import kotlinx.coroutines.*
import kotlin.test.*

class VideoPreviewFrameCacheTest {
    private class FakeFrame(override val timeUs: Long, val rendered: MutableList<Pair<Long, MosaicDocument>>) : VideoPreviewFrame {
        var closed = false
        override suspend fun render(document: MosaicDocument): ImageBitmap {
            check(!closed); rendered += timeUs to document
            return ImageBitmap(1, 1)
        }
        override fun close() { closed = true }
    }

    @Test fun maskEditsReRenderTheDecodedFrameAndOnlyNewTimesDecode() = runBlocking<Unit> {
        val cache = VideoPreviewFrameCache()
        val rendered = ArrayList<Pair<Long, MosaicDocument>>()
        val decoded = ArrayList<FakeFrame>()
        suspend fun preview(time: Long, document: MosaicDocument) = cache.render(time, document) { adopt ->
            adopt(FakeFrame(time, rendered).also { decoded += it })
        }
        val empty = MosaicDocument()
        val masked = MosaicDocument(listOf(MosaicRegion("1", endUs = 1_000_000)))
        assertFalse(cache.has(0))
        preview(0, empty)
        assertTrue(cache.has(0))
        preview(0, masked); preview(0, empty)
        assertEquals(1, decoded.size, "document-only changes must not decode again")
        assertEquals(listOf(0L to empty, 0L to masked, 0L to empty), rendered)

        preview(33_333, masked)
        assertEquals(2, decoded.size)
        assertTrue(decoded[0].closed, "the replaced frame is released")
        assertFalse(cache.has(0)); assertTrue(cache.has(33_333))

        cache.close()
        withTimeout(5_000) { while (!decoded[1].closed) delay(10) }
        assertFalse(cache.has(33_333))
        assertFailsWith<IllegalStateException> { preview(33_333, masked) }
    }

    @Test fun failedDecodeKeepsNoFrameAndTheNextRenderDecodesAgain() = runBlocking<Unit> {
        val cache = VideoPreviewFrameCache()
        val rendered = ArrayList<Pair<Long, MosaicDocument>>()
        assertFailsWith<IllegalStateException> { cache.render(0, MosaicDocument()) { error("decoder failed") } }
        assertFalse(cache.has(0))
        var decodes = 0
        cache.render(0, MosaicDocument()) { adopt -> decodes++; adopt(FakeFrame(0, rendered)) }
        assertEquals(1, decodes)
        assertEquals(1, rendered.size)
    }
}
