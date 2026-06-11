package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear

private const val CATALOG_THREAD_TITLE_MAX_TAG_SCAN = 800
private const val CATALOG_THREAD_TITLE_MAX_BODY_CHARS = 20_000

internal fun extractCatalogDisplayTitleFromThreadHead(html: String): String? {
    val messageTitle = extractFirstTagBody(html, tagName = "blockquote")
        ?.let(::sanitizeCatalogThreadTitleHtml)
    if (!messageTitle.isNullOrBlank()) {
        return messageTitle
    }
    return extractFirstSpanBodyWithClass(html, className = "csb")
        ?.let(::sanitizeCatalogThreadTitleHtml)
}

private fun sanitizeCatalogThreadTitleHtml(value: String): String? {
    val normalized = replaceHtmlBreakTags(value)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.ifBlank { null }
}

private fun extractFirstTagBody(html: String, tagName: String): String? {
    val startToken = "<$tagName"
    val endToken = "</$tagName>"
    val startIndex = html.indexOf(startToken, ignoreCase = true)
    if (startIndex == -1) return null
    val tagEnd = findBoundedTagEnd(html, startIndex) ?: return null
    val contentStart = tagEnd + 1
    val endIndex = html.indexOf(endToken, startIndex = contentStart, ignoreCase = true)
    if (endIndex == -1 || endIndex < contentStart) return null
    return html.substring(contentStart, minOf(endIndex, contentStart + CATALOG_THREAD_TITLE_MAX_BODY_CHARS))
}

private fun extractFirstSpanBodyWithClass(html: String, className: String): String? {
    var searchStart = 0
    var scannedSpans = 0
    while (searchStart < html.length && scannedSpans < 200) {
        val startIndex = html.indexOf("<span", startIndex = searchStart, ignoreCase = true)
        if (startIndex == -1) return null
        scannedSpans += 1
        val tagEnd = findBoundedTagEnd(html, startIndex)
        if (tagEnd == null) {
            searchStart = startIndex + 5
            continue
        }
        val tag = html.substring(startIndex, tagEnd + 1)
        val contentStart = tagEnd + 1
        val endIndex = html.indexOf("</span>", startIndex = contentStart, ignoreCase = true)
        if (endIndex == -1) return null
        if (tag.contains(className, ignoreCase = true)) {
            return html.substring(contentStart, minOf(endIndex, contentStart + CATALOG_THREAD_TITLE_MAX_BODY_CHARS))
        }
        searchStart = endIndex + "</span>".length
    }
    return null
}

private fun findBoundedTagEnd(html: String, startIndex: Int): Int? {
    val limit = minOf(html.length, startIndex + CATALOG_THREAD_TITLE_MAX_TAG_SCAN)
    var index = startIndex + 1
    while (index < limit) {
        when (html[index]) {
            '>' -> return index
            '<' -> return null
        }
        index += 1
    }
    return null
}
