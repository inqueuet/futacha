package com.valoser.futacha.shared.ui.image

internal enum class CompatAnimatedImageFormat { GIF, PNG, WEBP }

/** Legacy animation decoders allocate their source canvas before display sizing. */
internal fun requireSafeCompatAnimatedImageCanvas(bytes: ByteArray, format: CompatAnimatedImageFormat) {
    fun byte(index: Int): Long = bytes.getOrNull(index)?.toLong()?.and(0xff) ?: error("Truncated animated image header")
    fun little(offset: Int, count: Int): Long = (0 until count).fold(0L) { value, index ->
        value or (byte(offset + index) shl (8 * index))
    }
    fun big(offset: Int): Long = (0..3).fold(0L) { value, index -> (value shl 8) or byte(offset + index) }
    val (width, height) = when (format) {
        CompatAnimatedImageFormat.GIF -> little(6, 2) to little(8, 2)
        CompatAnimatedImageFormat.PNG -> big(16) to big(20)
        CompatAnimatedImageFormat.WEBP -> (little(24, 3) + 1) to (little(27, 3) + 1)
    }
    require(width in 1..16384 && height in 1..16384 && width * height <= 16L * 1024 * 1024) {
        "Animated image canvas is too large or invalid: ${width}x${height}"
    }
}
