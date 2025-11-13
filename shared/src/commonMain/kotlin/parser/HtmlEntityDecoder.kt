package com.valoser.futacha.shared.parser

internal object HtmlEntityDecoder {
    private val namedEntityMap = mapOf(
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " "
    )

    private val namedEntityRegex = Regex("&([a-zA-Z]+);")
    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")

    fun decode(value: String): String {
        var result = value
        result = namedEntityRegex.replace(result) { match ->
            namedEntityMap[match.groupValues[1].lowercase()] ?: match.value
        }
        result = hexEntityRegex.replace(result) { match ->
            decodeCodePoint(match, radix = 16)
        }
        result = numericEntityRegex.replace(result) { match ->
            decodeCodePoint(match, radix = 10)
        }
        return result
    }

    private fun decodeCodePoint(match: MatchResult, radix: Int): String {
        val digits = match.groupValues.getOrNull(1) ?: return match.value
        val codePoint = runCatching { digits.toInt(radix) }.getOrNull() ?: return match.value
        if (!isAllowedCodePoint(codePoint)) return match.value
        return codePointToString(codePoint)
    }

    private fun isAllowedCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x20..0x10FFFF && codePoint !in 0xD800..0xDFFF
    }

    private fun codePointToString(codePoint: Int): String {
        return if (codePoint <= 0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val cpPrime = codePoint - 0x10000
            val highSurrogate = ((cpPrime shr 10) + 0xD800).toChar()
            val lowSurrogate = ((cpPrime and 0x3FF) + 0xDC00).toChar()
            buildString {
                append(highSurrogate)
                append(lowSurrogate)
            }
        }
    }
}
