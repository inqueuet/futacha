package com.valoser.futacha.shared.util

internal fun encodeShiftJisDeterministically(
    text: String,
    encodeExact: (String) -> ByteArray?
): ByteArray {
    if (text.isEmpty()) return ByteArray(0)

    encodeExact(text)?.let { return it }

    val output = ArrayList<Byte>(text.length * 2)
    var index = 0
    while (index < text.length) {
        val chunkLength = text.codeUnitLengthAt(index)
        val chunk = text.substring(index, index + chunkLength)
        val encoded = encodeExact(chunk)
        if (encoded != null) {
            encoded.forEach(output::add)
        } else {
            output += SHIFT_JIS_REPLACEMENT_BYTE
        }
        index += chunkLength
    }
    return output.toByteArray()
}

internal const val SHIFT_JIS_REPLACEMENT_BYTE: Byte = 0x3F

internal fun String.codeUnitLengthAt(index: Int): Int {
    if (index !in indices) return 1
    val first = this[index]
    if (!first.isHighSurrogateCodeUnit()) return 1
    if (index + 1 >= length) return 1
    return if (this[index + 1].isLowSurrogateCodeUnit()) 2 else 1
}

internal fun Char.isHighSurrogateCodeUnit(): Boolean = this in '\uD800'..'\uDBFF'

internal fun Char.isLowSurrogateCodeUnit(): Boolean = this in '\uDC00'..'\uDFFF'
