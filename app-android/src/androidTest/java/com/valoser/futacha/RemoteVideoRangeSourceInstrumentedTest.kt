@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.media.MediaMetadataRetriever
import com.valoser.futacha.shared.ui.image.RemoteVideoRangeSource
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** All HTTP is faked in memory. No network request, playback, or posting. */
class RemoteVideoRangeSourceInstrumentedTest {
    private class Connection(private val bytes: ByteArray, private val ranges: MutableList<LongRange>) : HttpURLConnection(URL("https://example.invalid/video.mp4")) {
        private fun range(): LongRange {
            val parts = getRequestProperty("Range").removePrefix("bytes=").split('-')
            return parts[0].toLong()..minOf(parts[1].toLong(), bytes.lastIndex.toLong())
        }
        override fun getResponseCode() = 206
        override fun getHeaderField(name: String?): String? = if (name == "Content-Range") range().let { "bytes ${it.first}-${it.last}/${bytes.size}" } else null
        override fun getInputStream() = range().let {
            ranges += it
            ByteArrayInputStream(bytes, it.first.toInt(), (it.last - it.first + 1).toInt())
        }
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
    }

    @Test fun actualRetrieverDecodesTheFixtureThroughTheRangeSource() {
        val bytes = VideoEditFixtures.bytes("landscape")
        val ranges = mutableListOf<LongRange>()
        RemoteVideoRangeSource("https://example.invalid/video.mp4", null, openConnection = { Connection(bytes, ranges) }).use { source ->
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull(frame)
                assertTrue(frame!!.width > 0)
                frame.recycle()
                assertTrue(ranges.isNotEmpty())
                assertTrue(ranges.all { it.last - it.first + 1 <= 262_144 })
            } finally { retriever.release() }
        }
    }

    @Test fun randomReadsCancelAndExpireWithoutOpeningAnotherConnection() {
        val bytes = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val ranges = mutableListOf<LongRange>()
        val job = Job()
        var now = 0L
        RemoteVideoRangeSource("https://example.invalid/video.mp4", job, { now }, { Connection(bytes, ranges) }).use { source ->
            val out = ByteArray(20)
            assertEquals(20, source.readAt(700_000, out, 0, out.size))
            assertArrayEquals(bytes.copyOfRange(700_000, 700_020), out)
            assertEquals(1, ranges.size)
            job.cancel()
            assertThrows(IOException::class.java) { source.readAt(0, out, 0, 1) }
            assertEquals(1, ranges.size)
        }
        RemoteVideoRangeSource("https://example.invalid/video.mp4", null, { now }, { Connection(bytes, ranges) }).use { source ->
            now = 20_001
            assertThrows(IOException::class.java) { source.readAt(0, ByteArray(1), 0, 1) }
            assertEquals(1, ranges.size)
        }
    }

    @Test fun totalTransferredBytesStayBoundedAcrossRandomReads() {
        val bytes = ByteArray(17 * 1024 * 1024)
        val ranges = mutableListOf<LongRange>()
        RemoteVideoRangeSource("https://example.invalid/video.mp4", null, openConnection = { Connection(bytes, ranges) }).use { source ->
            val out = ByteArray(1)
            repeat(64) { assertEquals(1, source.readAt(it * 262_144L, out, 0, 1)) }
            assertThrows(IOException::class.java) { source.readAt(64 * 262_144L, out, 0, 1) }
        }
    }
}
