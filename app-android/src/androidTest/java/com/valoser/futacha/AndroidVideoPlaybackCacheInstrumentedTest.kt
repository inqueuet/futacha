package com.valoser.futacha

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.board.AndroidVideoPlaybackCache
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class AndroidVideoPlaybackCacheInstrumentedTest {
    @Test
    fun reopeningSmallMp4AndLargeWebmTwentyTimesUsesOneUpstreamResponse() {
        val smallMp4 = deterministicMediaBytes(64 * 1024, MP4_PREFIX)
        val largeWebm = deterministicMediaBytes(2 * 1024 * 1024, WEBM_PREFIX)
        LoopbackVideoServer(
            mapOf(
                "/small.mp4" to ResponseSpec(smallMp4, supportsRange = true),
                "/large.webm" to ResponseSpec(largeWebm, supportsRange = false)
            )
        ).use { server ->
            repeat(20) {
                // Recreate the factory as each viewer does. The process must still
                // retain one SimpleCache owner and reuse the same stored bytes.
                val factory = playbackFactory()
                assertArrayEquals(smallMp4, readAll(factory, server.url("/small.mp4")))
                assertArrayEquals(largeWebm, readAll(factory, server.url("/large.webm")))
            }

            assertEquals(1, server.requestCount("/small.mp4"))
            assertEquals(1, server.requestCount("/large.webm"))
            assertEquals(smallMp4.size.toLong(), server.plannedBodyBytes("/small.mp4"))
            assertEquals(largeWebm.size.toLong(), server.plannedBodyBytes("/large.webm"))
        }
    }

    @Test
    fun repeatedSeekRangeIsCachedForRangeAndNonRangeServers() {
        val rangeMp4 = deterministicMediaBytes(512 * 1024, MP4_PREFIX)
        val noRangeWebm = deterministicMediaBytes(512 * 1024, WEBM_PREFIX)
        val position = 4_096L
        val length = 8_192L
        LoopbackVideoServer(
            mapOf(
                "/seek.mp4" to ResponseSpec(rangeMp4, supportsRange = true),
                "/seek-no-range.webm" to ResponseSpec(noRangeWebm, supportsRange = false)
            )
        ).use { server ->
            val factory = playbackFactory()
            val expectedMp4 = rangeMp4.copyOfRange(position.toInt(), (position + length).toInt())
            val expectedWebm = noRangeWebm.copyOfRange(position.toInt(), (position + length).toInt())

            repeat(20) {
                assertArrayEquals(
                    expectedMp4,
                    readAll(factory, server.url("/seek.mp4"), position, length)
                )
                assertArrayEquals(
                    expectedWebm,
                    readAll(factory, server.url("/seek-no-range.webm"), position, length)
                )
            }

            assertEquals(1, server.requestCount("/seek.mp4"))
            assertEquals(1, server.requestCount("/seek-no-range.webm"))
            assertEquals(length, server.plannedBodyBytes("/seek.mp4"))
            assertEquals(noRangeWebm.size.toLong(), server.plannedBodyBytes("/seek-no-range.webm"))
        }
    }

    @Test
    fun missingVideoIsNotCachedAsAStaleSuccess() {
        LoopbackVideoServer(emptyMap()).use { server ->
            val factory = playbackFactory()
            repeat(2) {
                assertThrows(Exception::class.java) {
                    readAll(factory, server.url("/missing.mp4"))
                }
            }
            assertEquals(2, server.requestCount("/missing.mp4"))
        }
    }

    @Test
    fun localFileUriKeepsTheOriginalDirectReadPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "video-cache-local-${System.nanoTime()}.mp4")
        val original = deterministicMediaBytes(16 * 1024, MP4_PREFIX)
        val updated = original.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        }
        try {
            file.writeBytes(original)
            val url = Uri.fromFile(file).toString()
            assertArrayEquals(original, readAll(playbackFactory(), url))

            file.writeBytes(updated)
            assertArrayEquals(updated, readAll(playbackFactory(), url))
        } finally {
            file.delete()
        }
    }

    private fun playbackFactory(): DataSource.Factory =
        AndroidVideoPlaybackCache.createDataSourceFactory(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
}

@UnstableApi
private fun readAll(
    factory: DataSource.Factory,
    url: String,
    position: Long = 0L,
    length: Long = C.LENGTH_UNSET.toLong()
): ByteArray {
    val source = factory.createDataSource()
    val spec = DataSpec.Builder()
        .setUri(Uri.parse(url))
        .setPosition(position)
        .apply { if (length != C.LENGTH_UNSET.toLong()) setLength(length) }
        .build()
    return try {
        source.open(spec)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) break
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } finally {
        runCatching { source.close() }
    }
}

private data class ResponseSpec(
    val bytes: ByteArray,
    val supportsRange: Boolean
)

private class LoopbackVideoServer(
    private val responses: Map<String, ResponseSpec>
) : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val bodyBytes = ConcurrentHashMap<String, AtomicInteger>()
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(name = "video-cache-loopback", isDaemon = true) {
        while (running.get()) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching { handle(client) }
            runCatching { client.close() }
        }
    }

    fun url(path: String): String = "http://127.0.0.1:${socket.localPort}$path"

    fun requestCount(path: String): Int = requestCounts[path]?.get() ?: 0

    fun plannedBodyBytes(path: String): Long = (bodyBytes[path]?.get() ?: 0).toLong()

    private fun handle(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readAsciiLine(input) ?: return
        val rawTarget = requestLine.split(' ').getOrNull(1).orEmpty()
        val path = rawTarget.substringBefore('?')
        val headers = buildMap {
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) put(
                    line.substring(0, separator).trim().lowercase(),
                    line.substring(separator + 1).trim()
                )
            }
        }
        requestCounts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
        val response = responses[path]
        if (response == null) {
            writeResponse(client, "404 Not Found", emptyList(), ByteArray(0))
            return
        }

        val requestedRange = headers["range"]?.let(::parseByteRange)
        val servedRange = requestedRange?.takeIf { response.supportsRange }
        val body = if (servedRange == null) {
            response.bytes
        } else {
            val start = servedRange.first.coerceIn(0, response.bytes.lastIndex.toLong()).toInt()
            val end = servedRange.last.coerceIn(start.toLong(), response.bytes.lastIndex.toLong()).toInt()
            response.bytes.copyOfRange(start, end + 1)
        }
        bodyBytes.computeIfAbsent(path) { AtomicInteger() }.addAndGet(body.size)
        val extraHeaders = if (servedRange == null) {
            listOf("Accept-Ranges: ${if (response.supportsRange) "bytes" else "none"}")
        } else {
            listOf(
                "Accept-Ranges: bytes",
                "Content-Range: bytes ${servedRange.first}-${servedRange.last}/${response.bytes.size}"
            )
        }
        writeResponse(
            client,
            if (servedRange == null) "200 OK" else "206 Partial Content",
            extraHeaders,
            body
        )
    }

    private fun writeResponse(
        client: Socket,
        status: String,
        extraHeaders: List<String>,
        body: ByteArray
    ) {
        val output = BufferedOutputStream(client.getOutputStream())
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            extraHeaders.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.encodeToByteArray())
        output.write(body)
        output.flush()
    }

    override fun close() {
        running.set(false)
        runCatching { socket.close() }
        worker.join(2_000)
    }
}

private fun readAsciiLine(input: BufferedInputStream): String? {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = input.read()
        if (value < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.US_ASCII.name())
        if (value == '\n'.code) return bytes.toString(Charsets.US_ASCII.name()).trimEnd('\r')
        bytes.write(value)
    }
}

private fun parseByteRange(value: String): LongRange? {
    val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(value) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: Long.MAX_VALUE
    return start..end
}

private fun deterministicMediaBytes(size: Int, prefix: ByteArray): ByteArray =
    ByteArray(size) { index -> ((index * 31 + 17) and 0xff).toByte() }
        .also { bytes -> prefix.copyInto(bytes) }

private val MP4_PREFIX = byteArrayOf(
    0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
    0x69, 0x73, 0x6f, 0x6d, 0x00, 0x00, 0x02, 0x00
)

private val WEBM_PREFIX = byteArrayOf(
    0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 0x9f.toByte(), 0x42, 0x86.toByte(), 0x81.toByte()
)
