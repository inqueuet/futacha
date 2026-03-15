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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

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
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
    }
    return withContext(AppDispatchers.io) {
        withTimeout(responseTotalTimeoutMillis) {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(responseReadBufferBytes)
            var output = ByteArray(minOf(responseReadBufferBytes, maxBytes.coerceAtLeast(1)))
            var totalBytes = 0
            var zeroReadCount = 0
            var readLoopCount = 0L
            var fullyConsumed = false
            try {
                while (true) {
                    coroutineContext.ensureActive()
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) {
                        fullyConsumed = true
                        break
                    }
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= maxZeroReadRetries) {
                            throw NetworkException("Response body read stalled")
                        }
                        delay(zeroReadBackoffMillis)
                        continue
                    }

                    zeroReadCount = 0
                    val requiredSize = totalBytes + read
                    if (requiredSize > maxBytes) {
                        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
                    }
                    if (requiredSize > output.size) {
                        var newSize = output.size
                        while (newSize < requiredSize) {
                            newSize = (newSize * 2).coerceAtMost(maxBytes)
                            if (newSize == output.size) break
                        }
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
        }
    }
}

internal suspend fun readHttpBoardApiResponseHeadBytesWithLimit(
    response: HttpResponse,
    maxLines: Int,
    maxBytes: Int,
    responseReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    responseTotalTimeoutMillis: Long
): ByteArray {
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
    }
    if (maxLines <= 0) {
        return readHttpBoardApiResponseBytesWithLimit(
            response = response,
            maxBytes = maxBytes,
            responseReadBufferBytes = responseReadBufferBytes,
            maxZeroReadRetries = maxZeroReadRetries,
            zeroReadBackoffMillis = zeroReadBackoffMillis,
            responseTotalTimeoutMillis = responseTotalTimeoutMillis
        )
    }
    return withContext(AppDispatchers.io) {
        withTimeout(responseTotalTimeoutMillis) {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(responseReadBufferBytes)
            var output = ByteArray(responseReadBufferBytes)
            var totalBytes = 0
            var lineCount = 0
            var zeroReadCount = 0
            var readLoopCount = 0L
            var fullyConsumed = false
            try {
                reading@ while (true) {
                    coroutineContext.ensureActive()
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) {
                        fullyConsumed = true
                        break
                    }
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= maxZeroReadRetries) {
                            throw NetworkException("Response head read stalled")
                        }
                        delay(zeroReadBackoffMillis)
                        continue
                    }

                    zeroReadCount = 0
                    var writeCount = read
                    for (i in 0 until read) {
                        if (buffer[i] == '\n'.code.toByte()) {
                            lineCount += 1
                            if (lineCount >= maxLines) {
                                writeCount = i + 1
                                break
                            }
                        }
                    }
                    val requiredSize = totalBytes + writeCount
                    if (requiredSize > maxBytes) {
                        throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
                    }
                    if (requiredSize > output.size) {
                        var newSize = output.size
                        while (newSize < requiredSize) {
                            newSize = (newSize * 2).coerceAtMost(maxBytes)
                            if (newSize == output.size) break
                        }
                        if (newSize < requiredSize) {
                            throw NetworkException("Failed to expand response head buffer safely")
                        }
                        output = output.copyOf(newSize)
                    }
                    buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = writeCount)
                    totalBytes = requiredSize
                    if (lineCount >= maxLines) break@reading
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
        }
    }
}

internal suspend fun readSmallHttpBoardApiResponseSummary(
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
    val decoded = try {
        TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        return null
    }
    return decoded
        .trim()
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.take(160)
}
