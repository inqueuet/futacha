package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear

private val HEX_ENTITY_REGEX = Regex("&#x([0-9a-fA-F]+);")
private val NUM_ENTITY_REGEX = Regex("&#(\\d+);")

internal fun messageHtmlToLines(html: String): List<String> {
    val normalized = replaceHtmlBreakTags(html)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = decodeAllHtmlEntities(withoutTags)
    return decoded.lines()
}

internal fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}

private fun decodeAllHtmlEntities(value: String): String {
    var result = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")

    result = HEX_ENTITY_REGEX.replace(result) { match ->
        val hexValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { hexValue.toInt(16) }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    result = NUM_ENTITY_REGEX.replace(result) { match ->
        val numValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { numValue.toInt() }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    return result
}

private fun codePointToString(codePoint: Int): String {
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        "${high.toChar()}${low.toChar()}"
    }
}
