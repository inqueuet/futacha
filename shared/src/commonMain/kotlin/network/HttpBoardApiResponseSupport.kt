package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private const val HTTP_BOARD_API_RESPONSE_READ_IDLE_TIMEOUT_MILLIS = 10_000L
private const val BOUNDED_HTTP_RESPONSE_READ_BUFFER_BYTES = 16 * 1024
private const val BOUNDED_HTTP_RESPONSE_MAX_ZERO_READ_RETRIES = 80
private const val BOUNDED_HTTP_RESPONSE_ZERO_READ_BACKOFF_MILLIS = 25L
private const val BOUNDED_HTTP_RESPONSE_TOTAL_TIMEOUT_MILLIS = 30_000L

/**
 * Shared bounded body reader for non-BoardApi HTTP features.
 *
 * Checking Content-Length alone is insufficient because the header is optional
 * and can be incorrect. Keep every compatibility/search response bounded while
 * it is being consumed so a malformed endpoint cannot allocate an unbounded
 * ByteArray before the caller gets a chance to validate it.
 */
internal suspend fun readBoundedHttpResponseBytes(
    response: HttpResponse,
    maxBytes: Int,
    totalTimeoutMillis: Long = BOUNDED_HTTP_RESPONSE_TOTAL_TIMEOUT_MILLIS
): ByteArray = readHttpBoardApiResponseBytesWithLimit(
    response = response,
    maxBytes = maxBytes,
    responseReadBufferBytes = minOf(BOUNDED_HTTP_RESPONSE_READ_BUFFER_BYTES, maxBytes.coerceAtLeast(1)),
    maxZeroReadRetries = BOUNDED_HTTP_RESPONSE_MAX_ZERO_READ_RETRIES,
    zeroReadBackoffMillis = BOUNDED_HTTP_RESPONSE_ZERO_READ_BACKOFF_MILLIS,
    responseTotalTimeoutMillis = totalTimeoutMillis
)

internal suspend fun readBoundedHttpResponseText(
    response: HttpResponse,
    maxBytes: Int,
    totalTimeoutMillis: Long = BOUNDED_HTTP_RESPONSE_TOTAL_TIMEOUT_MILLIS
): String {
    val bytes = readBoundedHttpResponseBytes(response, maxBytes, totalTimeoutMillis)
    return withContext(AppDispatchers.parsing) {
        TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
    }
}

internal suspend fun readHttpBoardApiResponseBodyAsString(
    response: HttpResponse,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): String {
    val bytes = readHttpBoardApiResponseBytesWithLimit(
        response = response,
        maxBytes = maxBytes,
        responseReadBufferBytes = responseReadBufferBytes,
        maxZeroReadRetries = maxZeroReadRetries,
        zeroReadBackoffMillis = zeroReadBackoffMillis,
        responseTotalTimeoutMillis = responseTotalTimeoutMillis
    )
    return withContext(AppDispatchers.parsing) {
        TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
    }
}

internal suspend fun readHttpBoardApiResponseHeadAsString(
    response: HttpResponse,
    maxLines: Int,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): String {
    val bytes = readHttpBoardApiResponseHeadBytesWithLimit(
        response = response,
        maxLines = maxLines,
        maxBytes = maxBytes,
        responseReadBufferBytes = responseReadBufferBytes,
        maxZeroReadRetries = maxZeroReadRetries,
        zeroReadBackoffMillis = zeroReadBackoffMillis,
        responseTotalTimeoutMillis = responseTotalTimeoutMillis
    )
    return withContext(AppDispatchers.parsing) {
        TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType]).trimEnd('\n', '\r')
    }
}

internal suspend fun readHttpBoardApiResponseBytesWithLimit(
    response: HttpResponse,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    require(responseReadBufferBytes > 0) { "responseReadBufferBytes must be positive" }
    require(maxZeroReadRetries > 0) { "maxZeroReadRetries must be positive" }
    require(responseTotalTimeoutMillis > 0L) { "responseTotalTimeoutMillis must be positive" }
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
    }
    return withContext(AppDispatchers.io) {
        // withTimeoutOrNull returns null only for its own deadline. A plain
        // withTimeout reported this internal budget as TimeoutCancellationException,
        // which callers rethrow as a cancellation of their own work (spinners
        // that never stop, a search that silently ends).
        withTimeoutOrNull(responseTotalTimeoutMillis) {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(responseReadBufferBytes)
            var output = ByteArray(
                initialHttpBoardApiResponseBodyBufferSize(
                    contentLength = contentLength,
                    maxBytes = maxBytes,
                    fallbackBufferBytes = responseReadBufferBytes
                )
            )
            var totalBytes = 0
            var zeroReadCount = 0
            var readLoopCount = 0L
            var fullyConsumed = false
            try {
                while (true) {
                    coroutineContext.ensureActive()
                    val read = withTimeoutOrNull(
                        httpBoardApiResponseReadIdleTimeout(responseTotalTimeoutMillis)
                    ) {
                        channel.readAvailable(buffer, 0, buffer.size)
                    } ?: throw httpBoardApiResponseReadStalled("body")
                    if (read == -1) {
                        fullyConsumed = true
                        break
                    }
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= maxZeroReadRetries) {
                            throw httpBoardApiResponseReadStalled("body")
                        }
                        delay(zeroReadBackoffMillis)
                        continue
                    }

                    zeroReadCount = 0
                    if (read > maxBytes - totalBytes) {
                        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
                    }
                    val requiredSize = totalBytes + read
                    if (requiredSize > output.size) {
                        val newSize = nextHttpBoardApiResponseBufferSize(
                            currentSize = output.size,
                            requiredSize = requiredSize,
                            maxBytes = maxBytes,
                            minimumGrowthSize = responseReadBufferBytes
                        )
                        if (newSize < requiredSize) {
                            throw NetworkException("Failed to expand response buffer safely")
                        }
                        output = output.copyOf(newSize)
                    }
                    buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = read)
                    totalBytes = requiredSize
                    readLoopCount += 1
                    if (readLoopCount % 32L == 0L) {
                        yield()
                    }
                }
                if (totalBytes == output.size) {
                    output
                } else {
                    output.copyOf(totalBytes)
                }
            } finally {
                if (!fullyConsumed) {
                    runCatching { channel.cancel() }
                }
            }
        } ?: throw httpBoardApiResponseReadTimeout("body", responseTotalTimeoutMillis)
    }
}

/**
 * The read budget expired: a network failure (retryable like other I/O
 * errors), not a cancellation of the caller.
 */
internal fun httpBoardApiResponseReadStalled(part: String): NetworkException {
    val message = "Response $part read stalled"
    return NetworkException(message, cause = IOException(message))
}

internal fun httpBoardApiResponseReadTimeout(part: String, timeoutMillis: Long): NetworkException {
    val message = "Response $part read timed out after $timeoutMillis ms"
    return NetworkException(message, cause = IOException(message))
}

internal suspend fun readHttpBoardApiResponseHeadBytesWithLimit(
    response: HttpResponse,
    maxLines: Int,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long,
    /** Return the first [maxBytes] instead of failing when the head is longer. */
    truncateAtMaxBytes: Boolean = false
): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    require(responseReadBufferBytes > 0) { "responseReadBufferBytes must be positive" }
    require(maxZeroReadRetries > 0) { "maxZeroReadRetries must be positive" }
    require(responseTotalTimeoutMillis > 0L) { "responseTotalTimeoutMillis must be positive" }
    if (maxLines <= 0 && !truncateAtMaxBytes) {
        return readHttpBoardApiResponseBytesWithLimit(
            response = response,
            maxBytes = maxBytes,
            responseReadBufferBytes = responseReadBufferBytes,
            maxZeroReadRetries = maxZeroReadRetries,
            zeroReadBackoffMillis = zeroReadBackoffMillis,
            responseTotalTimeoutMillis = responseTotalTimeoutMillis
        )
    }
    val effectiveMaxLines = if (maxLines <= 0) Int.MAX_VALUE else maxLines
    return withContext(AppDispatchers.io) {
        withTimeoutOrNull(responseTotalTimeoutMillis) {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(responseReadBufferBytes)
            var output = ByteArray(minOf(responseReadBufferBytes, maxBytes))
            var totalBytes = 0
            var lineCount = 0
            var zeroReadCount = 0
            var readLoopCount = 0L
            var fullyConsumed = false
            try {
                reading@ while (true) {
                    coroutineContext.ensureActive()
                    val read = withTimeoutOrNull(
                        httpBoardApiResponseReadIdleTimeout(responseTotalTimeoutMillis)
                    ) {
                        channel.readAvailable(buffer, 0, buffer.size)
                    } ?: throw httpBoardApiResponseReadStalled("head")
                    if (read == -1) {
                        fullyConsumed = true
                        break
                    }
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= maxZeroReadRetries) {
                            throw httpBoardApiResponseReadStalled("head")
                        }
                        delay(zeroReadBackoffMillis)
                        continue
                    }

                    zeroReadCount = 0
                    var writeCount = read
                    for (i in 0 until read) {
                        if (buffer[i] == '\n'.code.toByte()) {
                            lineCount += 1
                            if (lineCount >= effectiveMaxLines) {
                                writeCount = i + 1
                                break
                            }
                        }
                    }
                    var reachedByteLimit = false
                    if (writeCount > maxBytes - totalBytes) {
                        if (!truncateAtMaxBytes) {
                            throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
                        }
                        writeCount = maxBytes - totalBytes
                        reachedByteLimit = true
                    }
                    val requiredSize = totalBytes + writeCount
                    if (requiredSize > output.size) {
                        val newSize = nextHttpBoardApiResponseBufferSize(
                            currentSize = output.size,
                            requiredSize = requiredSize,
                            maxBytes = maxBytes,
                            minimumGrowthSize = responseReadBufferBytes
                        )
                        if (newSize < requiredSize) {
                            throw NetworkException("Failed to expand response head buffer safely")
                        }
                        output = output.copyOf(newSize)
                    }
                    buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = writeCount)
                    totalBytes = requiredSize
                    if (lineCount >= effectiveMaxLines || reachedByteLimit) break@reading
                    readLoopCount += 1
                    if (readLoopCount % 32L == 0L) {
                        yield()
                    }
                }
                if (totalBytes == output.size) {
                    output
                } else {
                    output.copyOf(totalBytes)
                }
            } finally {
                if (!fullyConsumed) {
                    runCatching { channel.cancel() }
                }
            }
        } ?: throw httpBoardApiResponseReadTimeout("head", responseTotalTimeoutMillis)
    }
}

internal fun initialHttpBoardApiResponseBodyBufferSize(
    contentLength: Long?,
    maxBytes: Int,
    fallbackBufferBytes: Int
): Int {
    val fallback = minOf(
        fallbackBufferBytes.coerceAtLeast(1),
        maxBytes.coerceAtLeast(1)
    )
    val knownLength = contentLength ?: return fallback
    return if (knownLength in 0..maxBytes.toLong()) {
        knownLength.toInt()
    } else {
        fallback
    }
}

internal fun nextHttpBoardApiResponseBufferSize(
    currentSize: Int,
    requiredSize: Int,
    maxBytes: Int,
    minimumGrowthSize: Int
): Int {
    if (requiredSize <= currentSize) return currentSize
    var newSize = if (currentSize > 0) {
        currentSize
    } else {
        minOf(minimumGrowthSize.coerceAtLeast(1), maxBytes.coerceAtLeast(1))
    }
    while (newSize < requiredSize) {
        val doubled = (newSize.toLong() * 2L)
            .coerceAtMost(maxBytes.toLong())
            .toInt()
        if (doubled <= newSize) break
        newSize = doubled
    }
    return newSize
}

private fun httpBoardApiResponseReadIdleTimeout(responseTotalTimeoutMillis: Long): Long {
    return HTTP_BOARD_API_RESPONSE_READ_IDLE_TIMEOUT_MILLIS
        .coerceAtMost(responseTotalTimeoutMillis)
        .coerceAtLeast(1L)
}

internal suspend fun readSmallHttpBoardApiResponseSummary(
    response: HttpResponse,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): String? {
    val decoded = readSmallHttpBoardApiResponseBody(
        response = response,
        responseReadBufferBytes = responseReadBufferBytes,
        maxZeroReadRetries = maxZeroReadRetries,
        zeroReadBackoffMillis = zeroReadBackoffMillis,
        responseTotalTimeoutMillis = responseTotalTimeoutMillis
    ) ?: return null
    return decoded
        .trim()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.take(160)
}

/** The decoded body of a small response (at most 64 KiB), or null when it cannot be read. */
internal suspend fun readSmallHttpBoardApiResponseBody(
    response: HttpResponse,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): String? {
    val bytes = try {
        readHttpBoardApiResponseBytesWithLimit(
            response = response,
            maxBytes = 64 * 1024,
            responseReadBufferBytes = responseReadBufferBytes,
            maxZeroReadRetries = maxZeroReadRetries,
            zeroReadBackoffMillis = zeroReadBackoffMillis,
            responseTotalTimeoutMillis = responseTotalTimeoutMillis
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        return null
    }
    return try {
        TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

/** The first [maxBytes] of a body (fewer when it ends earlier), decoded; the rest is not received. */
internal suspend fun readHttpBoardApiResponsePrefixAsString(
    response: HttpResponse,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): String {
    val bytes = readHttpBoardApiResponseHeadBytesWithLimit(
        response = response,
        maxLines = 0,
        maxBytes = maxBytes,
        responseReadBufferBytes = responseReadBufferBytes,
        maxZeroReadRetries = maxZeroReadRetries,
        zeroReadBackoffMillis = zeroReadBackoffMillis,
        responseTotalTimeoutMillis = responseTotalTimeoutMillis,
        truncateAtMaxBytes = true
    )
    // A cut inside a multi-byte character would make strict UTF-8 detection
    // fail and decode the whole prefix as Shift_JIS. Cut at the last line
    // break instead (0x0A is never part of a UTF-8 or Shift_JIS character).
    val lastNewline = if (bytes.size >= maxBytes) bytes.lastIndexOf('\n'.code.toByte()) else -1
    val complete = if (lastNewline >= 0) bytes.copyOf(lastNewline + 1) else bytes
    return withContext(AppDispatchers.parsing) {
        TextEncoding.decodeToString(complete, response.headers[HttpHeaders.ContentType])
    }
}
