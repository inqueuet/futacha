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
    private val widthAttrRegex = Regex(
        pattern = "width\\s*=\\s*['\"]?(\\d+)['\"]?",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val heightAttrRegex = Regex(
        pattern = "height\\s*=\\s*['\"]?(\\d+)['\"]?",
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

            // Find table start and end without greedy quantifiers
            val tableStart = tableRegex.find(normalized) ?: return@withContext emptyList()
            val tableEndMatch = tableEndRegex.find(normalized, tableStart.range.last) ?: return@withContext emptyList()
            val tableBody = normalized.substring(tableStart.range.last + 1, tableEndMatch.range.first)

            if (tableBody.length > MAX_CHUNK_SIZE) {
                println("CatalogHtmlParserCore: Warning - large table body ${tableBody.length} bytes")
                // Limit processing to prevent ReDoS attacks
                if (tableBody.length > MAX_CHUNK_SIZE * 5) {
                    throw IllegalArgumentException("Table body size ${tableBody.length} exceeds safe processing limit")
                }
            }

            val items = mutableListOf<CatalogItem>()
            var searchStart = 0
            var index = 0
            val maxCatalogItems = 1000 // Prevent excessive parsing
            // FIX: 無限ループ防止
            var previousSearchStart = -1
            var loopIterations = 0
            val maxIterations = 10000

            while (searchStart < tableBody.length && items.size < maxCatalogItems) {
                loopIterations++

                // FIX: 定期的にキャンセルチェックを追加
                if (loopIterations % 100 == 0) {
                    ensureActive()  // コルーチンがキャンセルされたら例外をスロー
                }

                // FIX: 最大ループ回数チェック
                if (loopIterations > maxIterations) {
                    Logger.e("CatalogHtmlParserCore", "Maximum loop iterations exceeded")
                    break
                }

                // FIX: 位置が進んでいないことを検出
                if (searchStart == previousSearchStart) {
                    Logger.e("CatalogHtmlParserCore", "Search position not advancing (stuck at $searchStart)")
                    break
                }
                previousSearchStart = searchStart

                val cellStart = cellRegex.find(tableBody, searchStart) ?: break
                val cellEnd = cellEndRegex.find(tableBody, cellStart.range.last) ?: break

                // Safety check for invalid ranges
                if (cellEnd.range.last <= cellStart.range.last) {
                    println("CatalogHtmlParserCore: Invalid cell range detected")
                    break
                }

                val cell = tableBody.substring(cellStart.range.last + 1, cellEnd.range.first)

                val href = anchorRegex.find(cell)?.groupValues?.getOrNull(1)
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

                    val titleText = extractBetween(cell, titleRegex, smallEndRegex)?.let(::cleanText)
                    val labelText = extractBetween(cell, cellLabelRegex, fontEndRegex)?.let(::cleanText)
                    val replies = labelText?.toIntOrNull() ?: 0

                    items.add(CatalogItem(
                        id = threadId,
                        threadUrl = resolveUrl(href, resolvedBaseUrl),
                        title = knownTitles[threadId] ?: titleText ?: labelText ?: "スレッド ${index + 1}",
                        thumbnailUrl = thumbnail,
                        thumbnailWidth = width,
                        thumbnailHeight = height,
                        replyCount = replies
                    ))
                    index++
                }

                // FIX: 次の検索位置を設定（必ず前進することを保証）
                val nextSearchStart = cellEnd.range.last + 1
                if (nextSearchStart <= searchStart) {
                    Logger.e("CatalogHtmlParserCore", "Invalid next position: $nextSearchStart <= $searchStart")
                    break
                }
                searchStart = nextSearchStart
            }

            if (items.size >= maxCatalogItems) {
                println("CatalogHtmlParserCore: Reached maximum catalog items limit ($maxCatalogItems)")
            }

            if (loopIterations >= maxIterations) {
                Logger.w("CatalogHtmlParserCore", "Parsing terminated due to max iterations")
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
        return HtmlEntityDecoder.decode(withoutTags).trim()
    }
}
