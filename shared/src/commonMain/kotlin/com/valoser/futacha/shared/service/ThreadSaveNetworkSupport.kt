package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.media.source.OriginalMediaSource
import com.valoser.futacha.shared.util.MediaSaveSource
import com.valoser.futacha.shared.util.OriginalMediaSaveFailure
import com.valoser.futacha.shared.util.withOriginalMediaSaveSourceOrElse
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.HigherLayerRetryManaged
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.hasEpochDurationExceeded
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.describeUrlForLog
import com.valoser.futacha.shared.util.describeFailureForLog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

internal data class ThreadSaveHtmlFetchRequest(
    val boardUrl: String,
    val threadId: String,
    val threadHtmlFetchTimeoutMillis: Long,
    val maxThreadHtmlBytes: Long,
    val streamReadBufferBytes: Int,
    val maxZeroReadRetries: Int,
    val zeroReadBackoffMillis: Long,
    val readIdleTimeoutMillis: Long
)

internal data class ThreadSaveMediaDownloadRequest(
    val url: String,
    val target: ThreadSaveStorageTarget,
    val boardPath: String,
    val requestType: ThreadSaveMediaRequestType,
    val postId: String,
    val startedAtMillis: Long,
    val mediaRequestTimeoutMillis: Long,
    val maxFileSizeBytes: Long,
    val maxSaveDurationMs: Long,
    val streamReadBufferBytes: Int,
    val maxZeroReadRetries: Int,
    val zeroReadBackoffMillis: Long,
    val readIdleTimeoutMillis: Long,
    val writeTimeoutMillis: Long,
    val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val withMediaWriteLock: suspend (String, suspend () -> Long) -> Long
)

internal data class ThreadSaveResponseStreamRequest(
    val binaryTarget: ThreadSaveBinaryWriteTarget,
    val startedAtMillis: Long,
    val streamReadBufferBytes: Int,
    val maxFileSizeBytes: Long,
    val maxZeroReadRetries: Int,
    val zeroReadBackoffMillis: Long,
    val readIdleTimeoutMillis: Long,
    val writeTimeoutMillis: Long,
    val maxSaveDurationMs: Long,
    val nowMillis: () -> Long
)

internal suspend fun fetchThreadSaveHtml(
    httpClient: HttpClient,
    request: ThreadSaveHtmlFetchRequest
): Result<String> = withContext(AppDispatchers.io) {
    try {
        val html = withTimeoutOrNull(request.threadHtmlFetchTimeoutMillis) {
            val threadUrl = BoardUrlResolver.resolveThreadUrl(request.boardUrl, request.threadId)
            // Streamed so the size limit applies while the page is received.
            httpClient.prepareGet(threadUrl) {
                headers[HttpHeaders.Referrer] = BoardUrlResolver.resolveBoardBaseUrl(request.boardUrl)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw Exception("Fetch thread HTML failed: ${response.status}")
                }
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > request.maxThreadHtmlBytes) {
                    throw IllegalStateException("Thread HTML is too large: ${contentLength / 1024}KB")
                }
                val bodyBytes = readThreadSaveResponseBytesWithLimit(
                    response = response,
                    maxBytes = request.maxThreadHtmlBytes.toInt(),
                    streamReadBufferBytes = request.streamReadBufferBytes,
                    maxZeroReadRetries = request.maxZeroReadRetries,
                    zeroReadBackoffMillis = request.zeroReadBackoffMillis,
                    readIdleTimeoutMillis = request.readIdleTimeoutMillis
                )
                TextEncoding.decodeToString(bodyBytes, response.headers[HttpHeaders.ContentType])
            }
        } ?: throw IllegalStateException(
            "Fetch thread HTML timed out after ${request.threadHtmlFetchTimeoutMillis}ms"
        )
        Result.success(html)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal suspend fun readThreadSaveResponseBytesWithLimit(
    response: HttpResponse,
    maxBytes: Int,
    streamReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    readIdleTimeoutMillis: Long
): ByteArray {
    return readThreadSaveChannelBytes(
        channel = response.bodyAsChannel(),
        config = ThreadSaveChannelReadConfig(
            maxBytes = maxBytes,
            streamReadBufferBytes = streamReadBufferBytes,
            maxZeroReadRetries = maxZeroReadRetries,
            zeroReadBackoffMillis = zeroReadBackoffMillis,
            readIdleTimeoutMillis = readIdleTimeoutMillis,
            stalledMessage = "Thread HTML read stalled",
            oversizeMessage = { "Thread HTML is too large" },
            bufferExpandFailureMessage = "Failed to expand thread HTML buffer safely"
        )
    )
}

internal suspend fun streamThreadSaveSourceToStorage(
    source: MediaSaveSource,
    fileSystem: FileSystem,
    request: ThreadSaveResponseStreamRequest
): Long {
    cleanupThreadSaveBinaryWriteTarget(fileSystem, request.binaryTarget)
    val buffer = ByteArray(request.streamReadBufferBytes)
    var totalBytesRead = 0L
    var zeroReadCount = 0
    var readLoopCount = 0L

    writeThreadSaveBinaryStream(
        fileSystem = fileSystem,
        target = request.binaryTarget,
        writeTimeoutMillis = request.writeTimeoutMillis
    ) { sink ->
        while (true) {
            coroutineContext.ensureActive()
            val read = withTimeoutOrNull(request.readIdleTimeoutMillis) {
                source.read(buffer)
            } ?: throw IllegalStateException("Save aborted: media stream stalled")

            if (read == -1) break
            if (read == 0) {
                zeroReadCount += 1
                if (zeroReadCount >= request.maxZeroReadRetries) {
                    throw IllegalStateException("Save aborted: media stream stalled")
                }
                delay(request.zeroReadBackoffMillis)
                continue
            }

            zeroReadCount = 0
            val requiredSize = totalBytesRead + read
            if (requiredSize > request.maxFileSizeBytes) {
                throw ThreadSaveMediaDownloadFailure(
                    message = "Actual file size exceeds limit: ${requiredSize / 1024}KB",
                    retryable = false
                )
            }
            if (hasEpochDurationExceeded(
                    request.nowMillis(),
                    request.startedAtMillis,
                    request.maxSaveDurationMs
                )
            ) {
                throw IllegalStateException("Save aborted: exceeded time limit during download")
            }

            sink.write(buffer, 0, read)
            totalBytesRead = requiredSize

            readLoopCount += 1
            if (readLoopCount % 32L == 0L) {
                yield()
            }
        }
    }
    if (totalBytesRead <= 0L) {
        throw IllegalStateException("Downloaded media file is empty")
    }

    if (source.declaredSize > 0 && totalBytesRead != source.declaredSize) {
        throw IllegalStateException("Downloaded media file is incomplete")
    }
    return totalBytesRead
}

internal suspend fun downloadAndStoreThreadSaveMedia(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    logTag: String,
    request: ThreadSaveMediaDownloadRequest,
    originalMediaSource: OriginalMediaSource? = null
): Result<ThreadSaveLocalFileInfo> = withContext(AppDispatchers.io) {
    try {
        Result.success(
            withThreadSaveMediaSource(httpClient, request, originalMediaSource) { source ->
                val contentLength = source.declaredSize
                if (contentLength > request.maxFileSizeBytes) {
                    throw ThreadSaveMediaDownloadFailure(
                        message = "File too large: ${contentLength / 1024}KB " +
                            "(max: ${request.maxFileSizeBytes / 1024}KB)",
                        retryable = false
                    )
                }

                val extension = (
                    getThreadSaveExtensionFromUrl(request.url)
                        ?: getThreadSaveExtensionFromContentType(
                            source.contentType
                        )
                    ).lowercase()
                if (!isThreadSaveSupportedExtension(extension)) {
                    throw ThreadSaveMediaDownloadFailure(
                        message = "Unsupported file type: $extension",
                        retryable = false
                    )
                }

                val fileType = resolveThreadSaveFileType(request.requestType, extension)
                val fileName = resolveThreadSaveFileName(
                    url = request.url,
                    extension = extension,
                    postId = request.postId,
                    timestampMillis = request.nowMillis()
                )
                val relativePath = buildThreadSaveRelativePath(request.boardPath, fileType, fileName)

                if (hasEpochDurationExceeded(
                        request.nowMillis(),
                        request.startedAtMillis,
                        request.maxSaveDurationMs
                    )
                ) {
                    throw IllegalStateException("Save aborted: exceeded time limit during download")
                }

                val totalBytesRead = request.withMediaWriteLock(relativePath) {
                    val binaryTarget = resolveThreadSaveBinaryWriteTarget(request.target, relativePath)
                    var completed = false
                    try {
                        val writtenBytes = streamThreadSaveSourceToStorage(
                            source = source,
                            fileSystem = fileSystem,
                            request = ThreadSaveResponseStreamRequest(
                                binaryTarget = binaryTarget,
                                startedAtMillis = request.startedAtMillis,
                                streamReadBufferBytes = request.streamReadBufferBytes,
                                maxFileSizeBytes = request.maxFileSizeBytes,
                                maxZeroReadRetries = request.maxZeroReadRetries,
                                zeroReadBackoffMillis = request.zeroReadBackoffMillis,
                                readIdleTimeoutMillis = request.readIdleTimeoutMillis,
                                writeTimeoutMillis = request.writeTimeoutMillis,
                                maxSaveDurationMs = request.maxSaveDurationMs,
                                nowMillis = request.nowMillis
                            )
                        )
                        completed = true
                        writtenBytes
                    } finally {
                        if (!completed) {
                            cleanupThreadSaveBinaryWriteTarget(fileSystem, binaryTarget)
                        }
                    }
                }

                ThreadSaveLocalFileInfo(
                    relativePath = relativePath,
                    fileType = fileType,
                    byteSize = totalBytesRead
                )
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Logger.e(
            logTag,
            "Failed to download (${describeUrlForLog(request.url)}), type=${describeFailureForLog(t)}"
        )
        Result.failure(if (t is OriginalMediaSaveFailure) {
            ThreadSaveMediaDownloadFailure(t.message ?: "Original media save failed", retryable = false, cause = t)
        } else t)
    }
}

/** Preserve the legacy request policy for thumbnails/videos and pre-network cache failures. */
private suspend fun <T> withThreadSaveMediaSource(
    httpClient: HttpClient,
    request: ThreadSaveMediaDownloadRequest,
    originalMediaSource: OriginalMediaSource?,
    block: suspend (MediaSaveSource) -> T
): T = withOriginalMediaSaveSourceOrElse(originalMediaSource, request.url, block) {
    // Streamed: the file goes to disk as it arrives instead of being buffered
    // whole first. A stalled connection fails after the former request timeout
    // without data; the overall limit stays the save's duration budget.
    try {
        httpClient.prepareGet(request.url) {
            // The saver retries each item itself (MAX_RETRIES); letting the
            // client's HttpRequestRetry retry too multiplied the attempts.
            attributes.put(HigherLayerRetryManaged, true)
            headers[HttpHeaders.Accept] = "image/*,video/*;q=0.8,*/*;q=0.2"
            timeout {
                requestTimeoutMillis = request.maxSaveDurationMs
                socketTimeoutMillis = request.mediaRequestTimeoutMillis
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw ThreadSaveMediaDownloadFailure(
                    message = "Download failed: ${response.status}",
                    retryable = isThreadSaveMediaHttpStatusRetryable(response.status.value)
                )
            }
            val channel = response.bodyAsChannel()
            block(MediaSaveSource(
                contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) },
                declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L,
                read = { channel.readAvailable(it, 0, it.size) }
            ))
        }
    } catch (timeout: io.ktor.client.plugins.HttpRequestTimeoutException) {
        throw IllegalStateException("Download request timed out after ${request.mediaRequestTimeoutMillis}ms", timeout)
    }
}
