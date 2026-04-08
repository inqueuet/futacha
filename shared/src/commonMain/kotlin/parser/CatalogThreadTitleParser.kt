package com.valoser.futacha.shared.parser

private val catalogThreadTitleBlockquoteRegex = Regex(
    pattern = "<blockquote(?:\\s[^>]*)?>(.*?)</blockquote>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val catalogThreadTitleSubjectRegex = Regex(
    pattern = "<span[^>]*class=['\"][^'\"]*csb[^'\"]*['\"][^>]*>(.*?)</span>",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val catalogThreadTitleLineBreakRegex = Regex("(?i)<br\\s*/?>")
private val catalogThreadTitleParagraphEndRegex = Regex("(?i)</p>")
private val catalogThreadTitleTagRegex = Regex("<[^>]+>")

internal fun extractCatalogDisplayTitleFromThreadHead(html: String): String? {
    val messageTitle = catalogThreadTitleBlockquoteRegex
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::sanitizeCatalogThreadTitleHtml)
    if (!messageTitle.isNullOrBlank()) {
        return messageTitle
    }
    return catalogThreadTitleSubjectRegex
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::sanitizeCatalogThreadTitleHtml)
}

private fun sanitizeCatalogThreadTitleHtml(value: String): String? {
    val normalized = value
        .replace(catalogThreadTitleLineBreakRegex, "\n")
        .replace(catalogThreadTitleParagraphEndRegex, "\n\n")
    val withoutTags = catalogThreadTitleTagRegex.replace(normalized, "")
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.ifBlank { null }
}
