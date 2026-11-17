package com.valoser.futacha.shared.util

import okio.Buffer

internal const val MAX_PICKED_IMAGE_BYTES = 8_192_000L
internal const val DEFAULT_PICKED_IMAGE_FILE_NAME = "image.jpg"

/** Size-less File Provider files grow by actual bytes read, never by the permitted maximum. */
internal fun readUnknownSizePickedFile(
    maxBytes: Long,
    readChunk: (buffer: ByteArray, requestedBytes: Int) -> Int
): ByteArray? {
    require(maxBytes in 1..Int.MAX_VALUE.toLong())
    val output = Buffer()
    val chunk = ByteArray(minOf(64 * 1024L, maxBytes).toInt())
    while (true) {
        // One byte past the limit distinguishes an exact-size file from an oversized one.
        val requested = minOf(chunk.size.toLong(), maxBytes - output.size + 1).toInt()
        val count = readChunk(chunk, requested)
        if (count < 0) return null
        require(count <= requested) { "File reader exceeded the requested byte count" }
        if (count == 0) return if (output.size == 0L) null else output.readByteArray()
        if (count > maxBytes - output.size) return null
        output.write(chunk, 0, count)
    }
}

internal fun isPickedImagePayloadSizeValid(
    dataLength: Long,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): Boolean = dataLength in 1..maxBytes

internal fun buildPickedImageData(
    bytes: ByteArray,
    fileName: String?,
    fallbackFileName: String = DEFAULT_PICKED_IMAGE_FILE_NAME,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): ImageData? {
    if (!isPickedImagePayloadSizeValid(bytes.size.toLong(), maxBytes)) {
        return null
    }
    val resolvedFileName = fileName?.trim().orEmpty().ifBlank { fallbackFileName }
    return ImageData(bytes, resolvedFileName)
}

internal fun normalizePickedImageData(
    imageData: ImageData?,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): ImageData? {
    imageData ?: return null
    return imageData.takeIf { isPickedImagePayloadSizeValid(it.bytes.size.toLong(), maxBytes) }
}
