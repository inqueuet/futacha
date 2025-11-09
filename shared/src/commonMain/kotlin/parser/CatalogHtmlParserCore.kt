package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem

/**
 * Lightweight catalog parser that can run on any KMP target without Jsoup.
 * It is purposely scoped to the markup captured under `/example/catalog.txt`.
 */
internal object CatalogHtmlParserCore {
    private const val DEFAULT_BASE_URL = "https://dat.2chan.net"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks

    private val tableRegex = Regex(
        pattern = "<table[^>]+id=['\"]cattable['\"][^>]*>([\\s\\S]*?)</table>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val cellRegex = Regex(
        pattern = "<td[^>]*>([\\s\\S]*?)</td>",
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
        pattern = "<font[^>]*>([\\s\\S]*?)</font>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val titleRegex = Regex(
        pattern = "<small[^>]*>([\\s\\S]*?)</small>",
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
            val tableBody = tableRegex.find(normalized)?.groupValues?.getOrNull(1) ?: return emptyList()
            cellRegex.findAll(tableBody)
                .mapIndexedNotNull { index, match ->
                    val cell = match.groupValues.getOrNull(1) ?: return@mapIndexedNotNull null
                    val href = anchorRegex.find(cell)?.groupValues?.getOrNull(1) ?: return@mapIndexedNotNull null
                    val threadId = threadIdRegex.find(href)?.groupValues?.getOrNull(1) ?: return@mapIndexedNotNull null
                    val thumbnail = imageRegex.find(cell)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { resolveUrl(it, resolvedBaseUrl) }
                    val titleText = titleRegex
                        .find(cell)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let(::cleanText)
                    val labelText = cellLabelRegex
                        .find(cell)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let(::cleanText)
                    val replies = labelText?.toIntOrNull() ?: 0
                    CatalogItem(
                        id = threadId,
                        threadUrl = resolveUrl(href, resolvedBaseUrl),
                        title = knownTitles[threadId] ?: titleText ?: labelText ?: "スレッド ${index + 1}",
                        thumbnailUrl = thumbnail,
                        replyCount = replies
                    )
                }
                .toList()
        } catch (e: Exception) {
            println("CatalogHtmlParserCore: Failed to parse catalog HTML: ${e.message}")
            throw ParserException("Failed to parse catalog HTML", e)
        }
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
        val decodedNumeric = numericEntityRegex.replace(withoutTags) { match ->
            val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@replace match.value
            if (value in 0..0xFFFF) {
                value.toChar().toString()
            } else {
                match.value
            }
        }
        val decodedHex = hexEntityRegex.replace(decodedNumeric) { match ->
            val value = match.groupValues.getOrNull(1)?.toIntOrNull(16) ?: return@replace match.value
            if (value in 0..0xFFFF) {
                value.toChar().toString()
            } else {
                match.value
            }
        }
        return decodedHex
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }
}
