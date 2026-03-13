package com.valoser.futacha.shared.ui.board

private val BR_TAG_REGEX = Regex("(?i)<br\\s*/?>")
private val P_TAG_REGEX = Regex("(?i)</p>")
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val HEX_ENTITY_REGEX = Regex("&#x([0-9a-fA-F]+);")
private val NUM_ENTITY_REGEX = Regex("&#(\\d+);")

internal fun messageHtmlToLines(html: String): List<String> {
    val normalized = html
        .replace(BR_TAG_REGEX, "\n")
        .replace(P_TAG_REGEX, "\n\n")
    val withoutTags = normalized.replace(HTML_TAG_REGEX, "")
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
