package com.valoser.futacha.shared.ui.image

import okio.Buffer
import okio.BufferedSource

private const val COMPAT_ANIMATED_IMAGE_MAX_BYTES = 32L * 1024L * 1024L
private const val COMPAT_ANIMATED_IMAGE_READ_CHUNK_BYTES = 8L * 1024L

/**
 * Reads an animated image without allowing a hostile or corrupt response to
 * grow a single in-memory byte array without bound.
 */
internal fun BufferedSource.readBoundedCompatAnimatedImageBytes(
    maxBytes: Long = COMPAT_ANIMATED_IMAGE_MAX_BYTES,
): ByteArray {
    require(maxBytes in 1L..Int.MAX_VALUE.toLong()) { "Invalid animated image byte limit" }
    val output = Buffer()
    var totalBytes = 0L
    while (true) {
        val remainingWithSentinel = maxBytes - totalBytes + 1L
        val read = read(
            sink = output,
            byteCount = minOf(COMPAT_ANIMATED_IMAGE_READ_CHUNK_BYTES, remainingWithSentinel),
        )
        if (read == -1L) break
        totalBytes += read
        require(totalBytes <= maxBytes) { "Animated image exceeds $maxBytes bytes" }
    }
    return output.readByteArray()
}
