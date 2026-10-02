package com.valoser.futacha.shared.ui.compat

/**
 * Pure helpers for the attachment "位置情報・EXIFを削除" / compression path.
 *
 * Re-encoding every image through a bitmap lost the EXIF orientation (portrait
 * photos were posted rotated), animation (GIF/APNG/animated WebP became one
 * frame) and transparency (alpha became black in JPEG). These helpers let the
 * platform encoder apply the orientation and strip metadata at the container
 * level when the pixels do not need to change (U-1).
 */
internal enum class CompatPostImageFormat(val extension: String?) {
    JPEG("jpg"),
    PNG("png"),
    GIF("gif"),
    WEBP("webp"),
    OTHER(null)
}

internal fun detectCompatPostImageFormat(bytes: ByteArray): CompatPostImageFormat {
    fun at(index: Int): Int = if (index < bytes.size) bytes[index].toInt() and 0xFF else -1
    fun ascii(offset: Int, text: String): Boolean =
        text.indices.all { at(offset + it) == text[it].code }
    return when {
        at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> CompatPostImageFormat.JPEG
        bytes.size >= 8 && PNG_SIGNATURE.indices.all { at(it) == PNG_SIGNATURE[it] } -> CompatPostImageFormat.PNG
        ascii(0, "GIF87a") || ascii(0, "GIF89a") -> CompatPostImageFormat.GIF
        ascii(0, "RIFF") && ascii(8, "WEBP") -> CompatPostImageFormat.WEBP
        else -> CompatPostImageFormat.OTHER
    }
}

/** Rotate clockwise by [rotationDegrees], then mirror horizontally when requested. */
internal data class CompatImageOrientation(val rotationDegrees: Int, val mirrorHorizontally: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrorHorizontally
}

/** Maps the EXIF Orientation tag (1-8) to the transform that displays the image upright. */
internal fun compatExifOrientationTransform(orientation: Int): CompatImageOrientation = when (orientation) {
    2 -> CompatImageOrientation(0, true)
    3 -> CompatImageOrientation(180, false)
    4 -> CompatImageOrientation(180, true)
    5 -> CompatImageOrientation(90, true)
    6 -> CompatImageOrientation(90, false)
    7 -> CompatImageOrientation(270, true)
    8 -> CompatImageOrientation(270, false)
    else -> CompatImageOrientation(0, false)
}

/**
 * Returns metadata-free bytes without re-encoding pixels, or null when the
 * image must go through the bitmap encoder (JPEG/HEIF, a non-upright
 * orientation, a malformed container, or a result above [maxBytes]).
 */
internal fun compatPostLosslessSanitizedImage(
    bytes: ByteArray,
    format: CompatPostImageFormat,
    orientation: CompatImageOrientation,
    maxBytes: Int
): ByteArray? {
    if (!orientation.isIdentity) return null
    val sanitized = when (format) {
        // GIF has no EXIF block; keeping the bytes preserves animation and transparency.
        CompatPostImageFormat.GIF -> bytes
        CompatPostImageFormat.PNG -> stripCompatPngMetadataChunks(bytes)
        CompatPostImageFormat.WEBP -> stripCompatWebpMetadataChunks(bytes)
        else -> null
    } ?: return null
    return sanitized.takeIf { it.size <= maxBytes }
}

internal fun compatPostSanitizedFileName(originalName: String, extension: String): String {
    val stem = originalName.substringBeforeLast('.', originalName).ifBlank { "attachment" }
    return "$stem.$extension"
}

private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val PNG_METADATA_CHUNKS = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")

/** Drops PNG text/EXIF/time chunks and any bytes after IEND; keeps APNG and alpha chunks. */
internal fun stripCompatPngMetadataChunks(bytes: ByteArray): ByteArray? {
    if (detectCompatPostImageFormat(bytes) != CompatPostImageFormat.PNG) return null
    val output = ArrayList<ByteArray>()
    output += bytes.copyOfRange(0, 8)
    var offset = 8
    while (true) {
        if (offset + 12 > bytes.size) return null
        val length = readBigEndianInt(bytes, offset)
        if (length < 0 || length.toLong() > bytes.size.toLong() - offset - 12) return null
        val type = bytes.decodeToString(offset + 4, offset + 8)
        val end = offset + 12 + length
        if (type !in PNG_METADATA_CHUNKS) output += bytes.copyOfRange(offset, end)
        offset = end
        if (type == "IEND") break
    }
    return concatenate(output)
}

private val WEBP_METADATA_CHUNKS = setOf("EXIF", "XMP ")
private const val WEBP_VP8X_EXIF_FLAG = 0x08
private const val WEBP_VP8X_XMP_FLAG = 0x04

/** Drops WebP EXIF/XMP chunks, clears their VP8X flags and rewrites the RIFF size. */
internal fun stripCompatWebpMetadataChunks(bytes: ByteArray): ByteArray? {
    if (detectCompatPostImageFormat(bytes) != CompatPostImageFormat.WEBP || bytes.size < 12) return null
    val riffEnd = readLittleEndianInt(bytes, 4).toLong() + 8
    if (riffEnd < 12 || riffEnd > bytes.size) return null
    val output = ArrayList<ByteArray>()
    output += bytes.copyOfRange(0, 12)
    var offset = 12
    while (offset < riffEnd) {
        if (offset + 8 > riffEnd) return null
        val type = bytes.decodeToString(offset, offset + 4)
        val size = readLittleEndianInt(bytes, offset + 4)
        if (size < 0) return null
        val paddedEnd = offset.toLong() + 8 + size + (size and 1)
        // Some encoders omit the final pad byte; accept a chunk ending exactly at the RIFF end.
        val end = when {
            paddedEnd <= riffEnd -> paddedEnd.toInt()
            offset.toLong() + 8 + size == riffEnd -> riffEnd.toInt()
            else -> return null
        }
        if (type !in WEBP_METADATA_CHUNKS) {
            val chunk = bytes.copyOfRange(offset, end)
            if (type == "VP8X" && size >= 1) {
                chunk[8] = (chunk[8].toInt() and (WEBP_VP8X_EXIF_FLAG or WEBP_VP8X_XMP_FLAG).inv()).toByte()
            }
            output += chunk
        }
        offset = end
    }
    val result = concatenate(output)
    writeLittleEndianInt(result, 4, result.size - 8)
    return result
}

private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
    bytes[offset + 2] = (value ushr 16).toByte()
    bytes[offset + 3] = (value ushr 24).toByte()
}

private fun concatenate(parts: List<ByteArray>): ByteArray {
    val result = ByteArray(parts.sumOf { it.size })
    var position = 0
    parts.forEach { part ->
        part.copyInto(result, position)
        position += part.size
    }
    return result
}
