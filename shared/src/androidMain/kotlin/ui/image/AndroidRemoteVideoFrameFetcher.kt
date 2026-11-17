package com.valoser.futacha.shared.ui.image

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import android.os.Build
import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The first frame of a remote video, read by [MediaMetadataRetriever] through a bounded random-access data source. The retriever reads only the
 * ranges it needs, where Coil's network fetcher + VideoFrameDecoder would download the whole file into the disk cache
 * first: an あぷ小 video of tens of MB for one thumbnail (audit 2026-11-17 M4). As iOS's AVFoundation path does.
 */
internal class AndroidRemoteVideoFrameFetcher(
    private val url: String,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult = remoteVideoFramePermits.withPermit {
        check(options.networkCachePolicy.readEnabled) { "Network access is disabled" }
        var owned: Bitmap? = null
        try {
            val requestJob = currentCoroutineContext()[Job]
            val bitmap = withContext(Dispatchers.IO) {
                readFirstFrame(url, options, requestJob).also { owned = it }
            } ?: error("No frame could be read from $url")
            currentCoroutineContext().ensureActive()
            ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.NETWORK).also { owned = null }
        } finally { owned?.recycle() }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val raw = data.toString()
            return if (isRemoteVideoThumbnailUrl(raw)) AndroidRemoteVideoFrameFetcher(raw, options) else null
        }
    }
}

private val remoteVideoFramePermits = Semaphore(2)
private val REMOTE_VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm")

/** An http(s) URL whose path ends in a video extension. */
internal fun isRemoteVideoThumbnailUrl(raw: String): Boolean {
    val lower = raw.trim().lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    val path = lower.substringBefore('#').substringBefore('?')
    return path.substringAfterLast('/').substringAfterLast('.', "") in REMOTE_VIDEO_EXTENSIONS
}

private fun readFirstFrame(url: String, options: Options, job: Job?): Bitmap? {
    val retriever = MediaMetadataRetriever()
    val source = RemoteVideoRangeSource(url, job)
    var frame: Bitmap? = null
    try {
        retriever.setDataSource(source)
        val width = options.size.width.pxOrElse { 512 }.coerceIn(1, 2048)
        val height = options.size.height.pxOrElse { 512 }.coerceIn(1, 2048)
        val decoded = if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
        } else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        frame = decoded
        val image = decoded ?: return null
        if (image.width <= width && image.height <= height) return image.also { frame = null }
        val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val scaled = Bitmap.createScaledBitmap(
            image, (image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1), true
        )
        if (scaled !== image) image.recycle()
        frame = null
        return scaled
    } catch (e: Exception) {
        return null
    } finally {
        frame?.recycle()
        runCatching { retriever.release() }
        source.close()
    }
}

/** Random-access ranges rather than a native URL connection: every network read has a deadline and byte budget. */
internal class RemoteVideoRangeSource(
    private val url: String,
    private val job: Job?,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }
) : MediaDataSource() {
    private val deadline = nowMillis() + 20_000
    private var totalRead = 0L
    private var length = -1L
    private var closed = false
    private var cachedAt = -1L
    private var cached = ByteArray(0)

    private fun remainingMillis(): Int {
        if (closed || job?.isActive == false) throw IOException("Video thumbnail cancelled")
        val left = deadline - nowMillis()
        if (left <= 0) throw IOException("Video thumbnail timed out")
        return minOf(left, 5_000).toInt()
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        remainingMillis()
        if (position < 0 || offset < 0 || size < 0 || offset > buffer.size - size) throw IOException("Invalid video range")
        if (size == 0) return 0
        if (length >= 0 && position >= length) return -1
        if (position < cachedAt || position >= cachedAt + cached.size) readRange(position)
        val start = (position - cachedAt).toInt()
        val count = minOf(size, cached.size - start)
        if (count <= 0) return -1
        cached.copyInto(buffer, offset, start, start + count)
        return count
    }

    private fun readRange(position: Long) {
        val connection = openConnection(url)
        try {
            connection.connectTimeout = remainingMillis()
            connection.readTimeout = remainingMillis()
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$position-${position + 262_143}")
            val status = connection.responseCode
            if (status == 206) {
                val match = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                    ?: throw IOException("Missing video content range")
                if (match.groupValues[1].toLong() != position) throw IOException("Wrong video range")
                length = match.groupValues[3].toLong()
            } else if (status == 200 && position == 0L) {
                length = connection.contentLengthLong
            } else throw IOException("Video range unavailable")
            val bytes = ByteArray(262_144)
            var used = 0
            connection.inputStream.use { input ->
                while (used < bytes.size) {
                    connection.readTimeout = remainingMillis()
                    val read = input.read(bytes, used, minOf(8192, bytes.size - used))
                    if (read < 0) break
                    if (read == 0) continue
                    used += read
                    totalRead += read
                    if (totalRead > 16L * 1024 * 1024) throw IOException("Video thumbnail byte limit exceeded")
                }
            }
            cachedAt = position
            cached = bytes.copyOf(used)
        } finally { connection.disconnect() }
    }

    @Synchronized
    override fun getSize(): Long {
        if (length < 0 && cachedAt < 0) readRange(0)
        return length
    }

    @Synchronized
    override fun close() { closed = true; cached = ByteArray(0) }
}
