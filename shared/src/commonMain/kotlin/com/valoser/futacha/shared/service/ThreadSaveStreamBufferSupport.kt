package com.valoser.futacha.shared.service

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

internal data class ThreadSaveChannelReadConfig(
    val maxBytes: Int,
    val streamReadBufferBytes: Int,
    val maxZeroReadRetries: Int,
    val zeroReadBackoffMillis: Long,
    val readIdleTimeoutMillis: Long,
    val stalledMessage: String,
    val oversizeMessage: (Int) -> String,
    val bufferExpandFailureMessage: String,
    val onBytesRead: (Int) -> Unit = {}
)

internal suspend fun readThreadSaveChannelBytes(
    channel: ByteReadChannel,
    config: ThreadSaveChannelReadConfig
): ByteArray {
    require(config.maxBytes > 0) { "maxBytes must be positive" }
    require(config.streamReadBufferBytes > 0) { "streamReadBufferBytes must be positive" }
    require(config.maxZeroReadRetries > 0) { "maxZeroReadRetries must be positive" }
    val buffer = ByteArray(config.streamReadBufferBytes)
    var output = ByteArray(minOf(config.streamReadBufferBytes, config.maxBytes.coerceAtLeast(1)))
    var totalBytes = 0
    var zeroReadCount = 0
    var readLoopCount = 0L

    try {
        while (true) {
            coroutineContext.ensureActive()
            val read = withTimeoutOrNull(config.readIdleTimeoutMillis) {
                channel.readAvailable(buffer, 0, buffer.size)
            } ?: throw IllegalStateException(config.stalledMessage)

            if (read == -1) break
            if (read == 0) {
                zeroReadCount += 1
                if (zeroReadCount >= config.maxZeroReadRetries) {
                    throw IllegalStateException(config.stalledMessage)
                }
                delay(config.zeroReadBackoffMillis)
                continue
            }

            zeroReadCount = 0
            if (read > config.maxBytes - totalBytes) {
                val attemptedSize = (totalBytes.toLong() + read.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                throw IllegalStateException(config.oversizeMessage(attemptedSize))
            }
            val requiredSize = totalBytes + read
            config.onBytesRead(requiredSize)

            if (requiredSize > output.size) {
                var newSize = output.size
                while (newSize < requiredSize) {
                    newSize = (newSize * 2).coerceAtMost(config.maxBytes)
                    if (newSize == output.size) break
                }
                if (newSize < requiredSize) {
                    throw IllegalStateException(config.bufferExpandFailureMessage)
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
    } finally {
        runCatching { channel.cancel() }
    }

    return if (totalBytes == output.size) {
        output
    } else {
        output.copyOf(totalBytes)
    }
}
