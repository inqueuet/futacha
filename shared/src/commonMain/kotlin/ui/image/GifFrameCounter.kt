package com.valoser.futacha.shared.ui.image

import okio.BufferedSource
import okio.IOException

private const val GIF_IMAGE_DESCRIPTOR = 0x2C
private const val GIF_EXTENSION = 0x21
private const val GIF_COLOR_TABLE_FLAG = 0x80
private const val GIF_COLOR_TABLE_SIZE_MASK = 0x07

/**
 * Counts GIF image descriptors without decoding their pixels.
 *
 * The reference APK uses this bounded scan before selecting its Android GIF
 * decoder. In particular, a valid one-frame GIF must remain a normal static
 * image instead of being reported as a failed animation.
 */
internal fun countGifFrames(source: BufferedSource, limit: Int = Int.MAX_VALUE): Int {
    require(limit > 0) { "limit must be positive" }
    var count = 0
    try {
        if (!source.request(13L)) return 0
        source.skip(10L)
        val packed = source.readByte().toInt() and 0xFF
        source.skip(2L)
        if (!source.skipGifColorTableIfPresent(packed)) return 0

        while (source.request(1L)) {
            when (source.readByte().toInt() and 0xFF) {
                GIF_IMAGE_DESCRIPTOR -> {
                    count++
                    if (count >= limit || !source.request(9L)) return count
                    source.skip(8L)
                    val imagePacked = source.readByte().toInt() and 0xFF
                    if (!source.skipGifColorTableIfPresent(imagePacked) || !source.request(1L)) {
                        return count
                    }
                    source.skip(1L) // LZW minimum code size.
                    if (!source.skipGifSubBlocks()) return count
                }
                GIF_EXTENSION -> {
                    if (!source.request(1L)) return count
                    source.skip(1L) // Extension label.
                    if (!source.skipGifSubBlocks()) return count
                }
                else -> return count
            }
        }
    } catch (_: IOException) {
        return count
    }
    return count
}

internal fun hasMultipleGifFrames(source: BufferedSource): Boolean =
    countGifFrames(source, limit = 2) >= 2

private fun BufferedSource.skipGifColorTableIfPresent(packed: Int): Boolean {
    if (packed and GIF_COLOR_TABLE_FLAG == 0) return true
    val byteCount = (1L shl ((packed and GIF_COLOR_TABLE_SIZE_MASK) + 1)) * 3L
    if (!request(byteCount)) return false
    skip(byteCount)
    return true
}

private fun BufferedSource.skipGifSubBlocks(): Boolean {
    while (request(1L)) {
        val byteCount = readByte().toInt() and 0xFF
        if (byteCount == 0) return true
        if (!request(byteCount.toLong())) return false
        skip(byteCount.toLong())
    }
    return false
}
