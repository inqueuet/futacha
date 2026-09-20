package com.valoser.futacha.shared.media.source

import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.NetworkResponse
import com.valoser.futacha.shared.network.configureImageRequests
import com.valoser.futacha.shared.network.isRetryableImageConnectionFailure
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okio.BufferedSink
import okio.IOException
import kotlin.random.Random
import com.valoser.futacha.shared.ui.board.logVideoHttpResponse

private class OriginalMediaRejectedResponse(message: String) : IOException(message)
private class OriginalMediaIncompleteResponse : IOException("Original media response is incomplete")
private class OriginalMediaWriteFailure(cause: IOException) : IOException("Cannot write the original media file", cause)

/** Owns a restricted media-only clone; the injected app client remains owned by its host. */
class KtorOriginalMediaDownloader(
    client: HttpClient,
    private val maxBytes: Long,
    private val waitBeforeRetry: suspend (Int, Throwable) -> Unit = { retry, failure ->
        val serverDelay = (failure as? HttpException)?.response?.headers?.get(HttpHeaders.RetryAfter)
            ?.toLongOrNull()?.coerceIn(0, 90)?.times(1000)
        delay(maxOf(serverDelay ?: 0, (1000L shl retry) + Random.nextLong(1000)))
    }
) : OriginalMediaDownloader, AutoCloseable {
    init { require(maxBytes > 0) }

    private val transport = client.config { configureImageRequests() }

    override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink): OriginalMediaInfo =
        download(request, sink) {}

    override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
        // One sequential transfer fills the shared prefix. Independent Range requests
        // could mix revisions or fetch the same bytes twice and are not accepted here.
        require(request.headers.keys.none { it.equals(HttpHeaders.Range, ignoreCase = true) })
        return transport.prepareGet(request.url) {
            // Shared with streaming saves: large originals may take longer than
            // the image client's total timeout. Connect/idle limits stay bounded.
            timeout { requestTimeoutMillis = 15 * 60_000L }
            request.headers.forEach { (name, value) -> headers.append(name, value) }
            headers.remove(HttpHeaders.AcceptEncoding)
            headers.append(HttpHeaders.AcceptEncoding, "identity")
        }.execute { response ->
            logVideoHttpResponse(response.call.request.url.toString(), response.status.value,
                listOf(HttpHeaders.ContentType, HttpHeaders.ContentLength, HttpHeaders.AcceptRanges, HttpHeaders.ContentRange)
                    .associateWith { response.headers[it] })
            if (response.status.value != 200) {
                val headers = NetworkHeaders.Builder().apply {
                    response.headers.forEach { name, values -> set(name, values) }
                }.build()
                throw HttpException(NetworkResponse(code = response.status.value, headers = headers))
            }
            val encoding = response.headers[HttpHeaders.ContentEncoding]
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                throw OriginalMediaRejectedResponse("Encoded original media response is not supported")
            }
            val lengthHeader = response.headers[HttpHeaders.ContentLength]
            val declaredLength = lengthHeader?.toLongOrNull()
            if (lengthHeader != null && (declaredLength == null || declaredLength !in 1..maxBytes)) {
                throw OriginalMediaRejectedResponse("Original media Content-Length is invalid or exceeds the limit")
            }
            val info = OriginalMediaInfo(
                mimeType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim(),
                sizeBytes = declaredLength ?: -1L,
                etag = response.headers[HttpHeaders.ETag],
                resolvedUrl = response.call.request.url.toString(),
                cacheable = response.headers.getAll(HttpHeaders.CacheControl).orEmpty()
                    .flatMap { it.split(',') }.none { it.trim().equals("no-store", ignoreCase = true) }
            )
            onHeaders(info)
            var received = 0L
            val buffer = ByteArray(64 * 1024)
            val channel = response.bodyAsChannel()
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count > maxBytes - received) throw OriginalMediaRejectedResponse("Original media exceeds the size limit")
                try { sink.write(buffer, 0, count); sink.emit() }
                catch (failure: IOException) { throw OriginalMediaWriteFailure(failure) }
                received += count
            }
            if (received == 0L || (declaredLength != null && declaredLength != received)) {
                throw OriginalMediaIncompleteResponse()
            }
            info.copy(sizeBytes = received)
        }
    }

    override suspend fun retryAfter(retry: Int, failure: Throwable): Boolean {
        if (failure is CancellationException) return false
        val retryable = when (failure) {
            is OriginalMediaRejectedResponse, is OriginalMediaWriteFailure -> false
            is OriginalMediaIncompleteResponse -> true
            is HttpException -> failure.response.code in setOf(500, 502, 503, 504)
            else -> isRetryableImageConnectionFailure(failure)
        }
        if (retryable) waitBeforeRetry(retry, failure)
        return retryable
    }

    override fun close() { transport.close() }
}
