package com.valoser.futacha.shared.util

private const val SHIFT_JIS_CONTENT_TYPE = "text/plain; charset=Shift_JIS"

internal data class ShiftJisSanitizationResult(
    val sanitizedText: String,
    val removedCodePointCount: Int,
    val escapedCodePointCount: Int = 0
)

internal fun sanitizeForShiftJis(text: String): ShiftJisSanitizationResult {
    if (text.isEmpty()) {
        return ShiftJisSanitizationResult(
            sanitizedText = text,
            removedCodePointCount = 0
        )
    }

    val sanitized = StringBuilder(text.length)
    var removed = 0
    var escaped = 0
    var index = 0
    while (index < text.length) {
        val chunkLength = text.codeUnitLengthAt(index)
        val chunk = text.substring(index, index + chunkLength)
        if (isRoundTripShiftJisSafe(chunk)) {
            sanitized.append(chunk)
        } else {
            val entity = chunk.toNumericCharacterReference()
            if (entity != null) {
                sanitized.append(entity)
                escaped += 1
            } else {
                removed += 1
            }
        }
        index += chunkLength
    }

    return ShiftJisSanitizationResult(
        sanitizedText = if (removed == 0 && escaped == 0) text else sanitized.toString(),
        removedCodePointCount = removed,
        escapedCodePointCount = escaped
    )
}

private fun isRoundTripShiftJisSafe(value: String): Boolean {
    if (value.isEmpty()) return true
    val encoded = TextEncoding.encodeToShiftJis(value)
    if (encoded.isEmpty()) return false
    val decoded = TextEncoding.decodeToString(encoded, SHIFT_JIS_CONTENT_TYPE)
    return decoded == value
}

private fun String.codeUnitLengthAt(index: Int): Int {
    if (index !in indices) return 1
    val first = this[index]
    if (!first.isHighSurrogateCodeUnit()) return 1
    if (index + 1 >= length) return 1
    return if (this[index + 1].isLowSurrogateCodeUnit()) 2 else 1
}

private fun Char.isHighSurrogateCodeUnit(): Boolean = this in '\uD800'..'\uDBFF'

private fun Char.isLowSurrogateCodeUnit(): Boolean = this in '\uDC00'..'\uDFFF'

private fun String.toNumericCharacterReference(): String? {
    val codePoint = toCodePointOrNull() ?: return null
    return "&#$codePoint;"
}

private fun String.toCodePointOrNull(): Int? {
    return when (length) {
        1 -> {
            val ch = this[0]
            if (ch.isHighSurrogateCodeUnit() || ch.isLowSurrogateCodeUnit()) return null
            ch.code
        }
        2 -> {
            val high = this[0]
            val low = this[1]
            if (!high.isHighSurrogateCodeUnit() || !low.isLowSurrogateCodeUnit()) return null
            0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
        else -> null
    }
}
