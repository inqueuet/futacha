package com.valoser.futacha.shared.util

internal const val MAX_PICKED_IMAGE_BYTES = 10L * 1024L * 1024L
internal const val DEFAULT_PICKED_IMAGE_FILE_NAME = "image.jpg"

internal fun isPickedImagePayloadSizeValid(
    dataLength: Long,
    maxBytes: Long = MAX_PICKED_IMAGE_BYTES
): Boolean = dataLength in 1..maxBytes

internal fun buildPickedImageData(
    bytes: ByteArray,
    fileName: String?,
    fallbackFileName: String = DEFAULT_PICKED_IMAGE_FILE_NAME
): ImageData? {
    if (!isPickedImagePayloadSizeValid(bytes.size.toLong())) {
        return null
    }
    val resolvedFileName = fileName?.trim().orEmpty().ifBlank { fallbackFileName }
    return ImageData(bytes, resolvedFileName)
}
