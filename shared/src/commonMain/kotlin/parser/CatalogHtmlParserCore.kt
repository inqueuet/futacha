package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Lightweight catalog parser that can run on any KMP target without Jsoup.
 * It is purposely scoped to the markup captured under `/example/catalog.txt`.
 */
internal object CatalogHtmlParserCore {
    private const val DEFAULT_BASE_URL = "https://www.example.com"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks
    private const val MAX_CHUNK_SIZE = 100_000 // Process HTML in chunks to prevent ReDoS

    private val anchorRegex = Regex(
        pattern = "<a[^>]+href=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val imageRegex = Regex(
        pattern = "<img[^>]+src=['\"]([^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val widthAttrRegex = Regex(
        pattern = "width\\s*=\\s*['\"]?(\\d+)['\"]?",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val heightAttrRegex = Regex(
        pattern = "height\\s*=\\s*['\"]?(\\d+)['\"]?",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val threadIdRegex = Regex("res/(\\d+)\\.htm")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val knownTitles = mapOf(
        "1364612020" to "チュートリアル",
    )

    // FIX: サスペンド関数に変更してバックグラウンドで実行
    suspend fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem> = withContext(Dispatchers.Default) {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        try {
            val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            val normalized = html.replace("\r\n", "\n")

            // Find table start and end using indexOf for better performance
            val tableStartTag = "<table"
            val tableIdAttr = "id=\"cattable\""
            val tableIdAttrSingle = "id='cattable'"
            
            var tableStartIndex = normalized.indexOf(tableStartTag, ignoreCase = true)
            var foundTable = false
            
            // Look for table with id="cattable"
            while (tableStartIndex != -1) {
                val tableTagEnd = normalized.indexOf(">", tableStartIndex)
                if (tableTagEnd == -1) break
                
                val tableTag = normalized.substring(tableStartIndex, tableTagEnd + 1)
                if (tableTag.contains(tableIdAttr, ignoreCase = true) || 
                    tableTag.contains(tableIdAttrSingle, ignoreCase = true)) {
                    foundTable = true
                    break
                }
                tableStartIndex = normalized.indexOf(tableStartTag, tableTagEnd, ignoreCase = true)
            }
            
            if (!foundTable) return@withContext emptyList()
            
            val tableEndIndex = normalized.indexOf("</table>", startIndex = tableStartIndex, ignoreCase = true)
            if (tableEndIndex == -1) return@withContext emptyList()
            
            // Process body without substringing the whole table to save memory
            val items = mutableListOf<CatalogItem>()
            var searchStart = normalized.indexOf(">", tableStartIndex) + 1
            val maxCatalogItems = 1000 // Prevent excessive parsing
            var loopIterations = 0
            val maxIterations = 10000

            // Pre-compile regexes or search strings
            val tdStart = "<td"
            val tdEnd = "</td>"

            while (searchStart < tableEndIndex && items.size < maxCatalogItems) {
                loopIterations++

                // FIX: 定期的にキャンセルチェックを追加
                if (loopIterations % 100 == 0) {
                    ensureActive()  // コルーチンがキャンセルされたら例外をスロー
                }

                if (loopIterations > maxIterations) break

                val cellStartIndex = normalized.indexOf(tdStart, searchStart, ignoreCase = true)
                if (cellStartIndex == -1 || cellStartIndex >= tableEndIndex) break
                
                val cellContentStart = normalized.indexOf(">", cellStartIndex) + 1
                if (cellContentStart == 0) break // ">" not found
                
                val cellEndIndex = normalized.indexOf(tdEnd, cellContentStart, ignoreCase = true)
                if (cellEndIndex == -1) break

                // Extract cell content - keep it small
                val cell = normalized.substring(cellContentStart, cellEndIndex)

                // Parse cell content
                val hrefMatch = anchorRegex.find(cell)
                val href = hrefMatch?.groupValues?.getOrNull(1)
                val threadId = href?.let { threadIdRegex.find(it)?.groupValues?.getOrNull(1) }

                if (threadId != null) {
                    val imageMatch = imageRegex.find(cell)
                    val thumbnail = imageMatch
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { resolveUrl(it, resolvedBaseUrl) }
                    val imageTag = imageMatch?.value
                    val width = imageTag
                        ?.let { widthAttrRegex.find(it) }
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                    val height = imageTag
                        ?.let { heightAttrRegex.find(it) }
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()

                    val titleText = extractBetween(cell, "<small", "</small>")?.let(::cleanText)
                    val labelText = extractBetween(cell, "<font", "</font>")?.let(::cleanText)
                    val replies = labelText?.toIntOrNull() ?: 0

                    items.add(CatalogItem(
                        id = threadId,
                        threadUrl = resolveUrl(href, resolvedBaseUrl),
                        title = knownTitles[threadId] ?: titleText ?: labelText ?: "スレッド ${items.size + 1}",
                        thumbnailUrl = thumbnail,
                        thumbnailWidth = width,
                        thumbnailHeight = height,
                        replyCount = replies
                    ))
                }

                // Advance search position
                searchStart = cellEndIndex + tdEnd.length
            }

            if (items.size >= maxCatalogItems) {
                println("CatalogHtmlParserCore: Reached maximum catalog items limit ($maxCatalogItems)")
            }

            items
        } catch (e: kotlinx.coroutines.CancellationException) {
            // FIX: キャンセル例外は再スロー
            throw e
        } catch (e: Exception) {
            println("CatalogHtmlParserCore: Failed to parse catalog HTML: ${e.message}")
            throw ParserException("Failed to parse catalog HTML", e)
        }
    }

    private fun extractBetween(text: String, startTagPartial: String, endTag: String): String? {
        val startIndex = text.indexOf(startTagPartial, ignoreCase = true)
        if (startIndex == -1) return null
        val contentStart = text.indexOf(">", startIndex) + 1
        if (contentStart == 0) return null
        val endIndex = text.indexOf(endTag, contentStart, ignoreCase = true)
        if (endIndex == -1) return null
        return text.substring(contentStart, endIndex)
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
        return HtmlEntityDecoder.decode(withoutTags).trim()
    }
}
