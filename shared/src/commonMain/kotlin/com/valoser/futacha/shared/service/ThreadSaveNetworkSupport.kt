package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
            val response: HttpResponse = httpClient.get(threadUrl) {
                headers[HttpHeaders.Referrer] = BoardUrlResolver.resolveBoardBaseUrl(request.boardUrl)
            }
            try {
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
            } finally {
                runCatching { response.bodyAsChannel().cancel() }
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

internal suspend fun streamThreadSaveResponseToStorage(
    response: HttpResponse,
    fileSystem: FileSystem,
    request: ThreadSaveResponseStreamRequest
): Long {
    val payload = readThreadSaveChannelBytes(
        channel = response.bodyAsChannel(),
        config = ThreadSaveChannelReadConfig(
            maxBytes = request.maxFileSizeBytes.toInt(),
            streamReadBufferBytes = request.streamReadBufferBytes,
            maxZeroReadRetries = request.maxZeroReadRetries,
            zeroReadBackoffMillis = request.zeroReadBackoffMillis,
            readIdleTimeoutMillis = request.readIdleTimeoutMillis,
            stalledMessage = "Save aborted: media stream stalled",
            oversizeMessage = { requiredSize ->
                "Actual file size exceeds limit: ${requiredSize / 1024}KB"
            },
            bufferExpandFailureMessage = "Failed to expand media buffer safely",
            onBytesRead = {
                if (request.nowMillis() - request.startedAtMillis > request.maxSaveDurationMs) {
                    throw IllegalStateException("Save aborted: exceeded time limit during download")
                }
            }
        )
    )
    writeThreadSaveBinaryFile(
        fileSystem = fileSystem,
        target = request.binaryTarget,
        payload = payload,
        writeTimeoutMillis = request.writeTimeoutMillis
    )

    return payload.size.toLong()
}

internal suspend fun downloadAndStoreThreadSaveMedia(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    logTag: String,
    request: ThreadSaveMediaDownloadRequest
): Result<ThreadSaveLocalFileInfo> = withContext(AppDispatchers.io) {
    try {
        Result.success(
            run {
                val response: HttpResponse = withTimeoutOrNull(request.mediaRequestTimeoutMillis) {
                    httpClient.get(request.url) {
                        headers[HttpHeaders.Accept] = "image/*,video/*;q=0.8,*/*;q=0.2"
                    }
                } ?: throw IllegalStateException(
                    "Download request timed out after ${request.mediaRequestTimeoutMillis}ms: ${request.url}"
                )
                try {
                    if (!response.status.isSuccess()) {
                        throw Exception("Download failed: ${response.status}")
                    }

                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                    if (contentLength > request.maxFileSizeBytes) {
                        throw Exception("File too large: ${contentLength / 1024}KB (max: 8000KB)")
                    }

                    val extension = (
                        getThreadSaveExtensionFromUrl(request.url)
                            ?: getThreadSaveExtensionFromContentType(
                                response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                            )
                        ).lowercase()
                    if (!isThreadSaveSupportedExtension(extension)) {
                        throw Exception("Unsupported file type: $extension")
                    }

                    val fileType = resolveThreadSaveFileType(request.requestType, extension)
                    val fileName = resolveThreadSaveFileName(
                        url = request.url,
                        extension = extension,
                        postId = request.postId,
                        timestampMillis = request.nowMillis()
                    )
                    val relativePath = buildThreadSaveRelativePath(request.boardPath, fileType, fileName)

                    if (request.nowMillis() - request.startedAtMillis > request.maxSaveDurationMs) {
                        throw IllegalStateException("Save aborted: exceeded time limit during download")
                    }

                    val totalBytesRead = request.withMediaWriteLock(relativePath) {
                        val binaryTarget = resolveThreadSaveBinaryWriteTarget(request.target, relativePath)
                        var completed = false
                        try {
                            val writtenBytes = streamThreadSaveResponseToStorage(
                                response = response,
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
                } finally {
                    runCatching { response.bodyAsChannel().cancel() }
                }
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Logger.e(logTag, "Failed to download ${request.url}: ${t.message}")
        Result.failure(t)
    }
}
