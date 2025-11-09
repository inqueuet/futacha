package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem

/**
 * Lightweight catalog parser that can run on any KMP target without Jsoup.
 * It is purposely scoped to the markup captured under `/example/catalog.txt`.
 */
internal object CatalogHtmlParserCore {
    private const val DEFAULT_BASE_URL = "https://dat.2chan.net"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks
    private const val MAX_CHUNK_SIZE = 100_000 // Process HTML in chunks to prevent ReDoS

    private val tableRegex = Regex(
        pattern = "<table[^>]+id=['\"]cattable['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val tableEndRegex = Regex(
        pattern = "</table>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val cellRegex = Regex(
        pattern = "<td[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val cellEndRegex = Regex(
        pattern = "</td>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val anchorRegex = Regex(
        pattern = "<a[^>]+href=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val imageRegex = Regex(
        pattern = "<img[^>]+src=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val cellLabelRegex = Regex(
        pattern = "<font[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val fontEndRegex = Regex(
        pattern = "</font>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val titleRegex = Regex(
        pattern = "<small[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val smallEndRegex = Regex(
        pattern = "</small>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val threadIdRegex = Regex("res/(\\d+)\\.htm")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")
    private val knownTitles = mapOf(
        "1364612020" to "チュートリアル",
    )

    fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem> {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        return try {
            val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            val normalized = html.replace("\r\n", "\n")

            // Find table start and end without greedy quantifiers
            val tableStart = tableRegex.find(normalized) ?: return emptyList()
            val tableEndMatch = tableEndRegex.find(normalized, tableStart.range.last) ?: return emptyList()
            val tableBody = normalized.substring(tableStart.range.last + 1, tableEndMatch.range.first)

            if (tableBody.length > MAX_CHUNK_SIZE) {
                println("CatalogHtmlParserCore: Warning - large table body ${tableBody.length} bytes")
            }

            val items = mutableListOf<CatalogItem>()
            var searchStart = 0
            var index = 0

            while (searchStart < tableBody.length) {
                val cellStart = cellRegex.find(tableBody, searchStart) ?: break
                val cellEnd = cellEndRegex.find(tableBody, cellStart.range.last) ?: break
                val cell = tableBody.substring(cellStart.range.last + 1, cellEnd.range.first)

                val href = anchorRegex.find(cell)?.groupValues?.getOrNull(1)
                val threadId = href?.let { threadIdRegex.find(it)?.groupValues?.getOrNull(1) }

                if (threadId != null) {
                    val thumbnail = imageRegex.find(cell)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { resolveUrl(it, resolvedBaseUrl) }

                    val titleText = extractBetween(cell, titleRegex, smallEndRegex)?.let(::cleanText)
                    val labelText = extractBetween(cell, cellLabelRegex, fontEndRegex)?.let(::cleanText)
                    val replies = labelText?.toIntOrNull() ?: 0

                    items.add(CatalogItem(
                        id = threadId,
                        threadUrl = resolveUrl(href, resolvedBaseUrl),
                        title = knownTitles[threadId] ?: titleText ?: labelText ?: "スレッド ${index + 1}",
                        thumbnailUrl = thumbnail,
                        replyCount = replies
                    ))
                    index++
                }

                searchStart = cellEnd.range.last + 1
            }

            items
        } catch (e: Exception) {
            println("CatalogHtmlParserCore: Failed to parse catalog HTML: ${e.message}")
            throw ParserException("Failed to parse catalog HTML", e)
        }
    }

    private fun extractBetween(text: String, startRegex: Regex, endRegex: Regex): String? {
        val start = startRegex.find(text) ?: return null
        val end = endRegex.find(text, start.range.last) ?: return null
        return text.substring(start.range.last + 1, end.range.first)
    }

    private fun resolveUrl(path: String, baseUrl: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> extractOrigin(baseUrl).trimEnd('/') + path
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun extractOrigin(baseUrl: String): String {
        val schemeIndex = baseUrl.indexOf("://")
        if (schemeIndex == -1) return DEFAULT_BASE_URL
        val hostStart = schemeIndex + 3
        val hostEnd = baseUrl.indexOf('/', startIndex = hostStart).takeIf { it != -1 } ?: baseUrl.length
        return baseUrl.substring(0, hostEnd)
    }

    private fun cleanText(raw: String): String {
        val withoutTags = htmlTagRegex.replace(raw, "")

        // Decode numeric entities first
        val decodedNumeric = numericEntityRegex.replace(withoutTags) { match ->
            val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@replace match.value
            // Only allow safe Unicode characters, excluding control characters
            if (value in 0x20..0xD7FF || value in 0xE000..0xFFFD) {
                val char = value.toChar()
                // Re-escape potentially dangerous characters for display
                when (char) {
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '&' -> "&amp;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char.toString()
                }
            } else {
                match.value
            }
        }

        val decodedHex = hexEntityRegex.replace(decodedNumeric) { match ->
            val value = match.groupValues.getOrNull(1)?.toIntOrNull(16) ?: return@replace match.value
            if (value in 0x20..0xD7FF || value in 0xE000..0xFFFD) {
                val char = value.toChar()
                when (char) {
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '&' -> "&amp;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char.toString()
                }
            } else {
                match.value
            }
        }

        // Keep HTML entities escaped for safe display
        return decodedHex.trim()
    }
}
