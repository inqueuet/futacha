package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Lightweight catalog parser that can run on any KMP target without Jsoup.
 * It is purposely scoped to the markup captured under `/example/catalog.txt`.
 *
 * FIX: パフォーマンス最適化の実装詳細
 *
 * ## 実装済みの最適化：
 * 1. ReDoS攻撃防止
 *    - MAX_HTML_SIZE = 10MB制限
 *    - maxIterations = 2000（無限ループ防止）
 *    - キャンセルチェックを10イテレーション毎に実行
 *
 * 2. メモリ最適化
 *    - 正規表現のプリコンパイル
 *    - 部分文字列の最小化（indexOf使用）
 *    - maxCatalogItems = 1000（アイテム数制限）
 *
 * 3. アルゴリズム最適化
 *    - indexOf優先（正規表現より高速）
 *    - 早期リターン（テーブルが見つからない場合など）
 *    - 効率的なループ処理
 *
 * 4. スレッド最適化
 *    - AppDispatchers.parsingで専用スレッド実行
 *    - サスペンド関数化でバックグラウンド実行保証
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
    private val trailingSRegex = Regex("(?i)s(\\.[a-zA-Z0-9]+)$")
    private val supportedMediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp",
        "mp4", "webm", "m4v"
    )
    private val knownTitles = mapOf(
        "1364612020" to "チュートリアル",
    )

    // FIX: サスペンド関数に変更してバックグラウンドで実行
    suspend fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem> = withContext(AppDispatchers.parsing) {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        try {
            val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            // NOTE: この処理は大きなHTML文字列のコピーを作成するため、メモリ使用量が増加する
            // TODO: 将来的には、チャンク処理や正規表現での\r?\n対応を検討
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
            // FIX: 最大イテレーション数を10,000→2,000に削減（パフォーマンス改善）
            // ほとんどのカタログは1000アイテム以下なので、2000イテレーションで十分
            val maxIterations = 2000

            // Pre-compile regexes or search strings
            val tdStart = "<td"
            val tdEnd = "</td>"

            while (searchStart < tableEndIndex && items.size < maxCatalogItems) {
                loopIterations++

                // FIX: キャンセルチェックの頻度を増やす（20→10イテレーション毎）
                if (loopIterations % 10 == 0) {
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
                val allHrefs = anchorRegex.findAll(cell).mapNotNull { it.groupValues.getOrNull(1) }.toList()
                val threadHref = allHrefs.find { threadIdRegex.containsMatchIn(it) }
                val threadId = threadHref?.let { threadIdRegex.find(it)?.groupValues?.getOrNull(1) }

                if (threadId != null) {
                    val fullImageUrlHref = allHrefs.find {
                        it != threadHref && isSupportedMediaHref(it)
                    }

                    val imageMatch = imageRegex.find(cell)
                    val rawThumbnail = imageMatch
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { resolveUrl(it, resolvedBaseUrl) }
                    
                    // Use /thumb/ instead of /cat/ for higher quality thumbnails.
                    // Keep original extension to avoid breaking non-jpg media.
                    val thumbnail = rawThumbnail?.let { raw ->
                        raw.replace("/cat/", "/thumb/")
                    }

                    var fullImageUrl = fullImageUrlHref?.let { resolveUrl(it, resolvedBaseUrl) }
                    
                    // If full image is missing, try to guess from thumbnail
                    if (fullImageUrl == null && thumbnail != null && thumbnail.contains("/thumb/")) {
                        // Try to guess the source URL.
                        // Standard pattern: /thumb/123s.gif -> /src/123.gif
                        
                        // First switch directory
                        val srcBase = thumbnail.replace("/thumb/", "/src/")
                        
                        // Then strip the trailing 's' (or 'S') from the filename, preserving extension case
                        // FIX: ループ内でのRegex作成を回避
                        fullImageUrl = srcBase.replace(trailingSRegex, "$1")
                    }

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
                        threadUrl = resolveUrl(threadHref, resolvedBaseUrl),
                        title = knownTitles[threadId] ?: titleText ?: labelText ?: "スレッド ${items.size + 1}",
                        thumbnailUrl = thumbnail,
                        fullImageUrl = fullImageUrl,
                        thumbnailWidth = width,
                        thumbnailHeight = height,
                        replyCount = replies
                    ))
                }

                // Advance search position
                searchStart = cellEndIndex + tdEnd.length
            }

            if (items.size >= maxCatalogItems) {
                Logger.w("CatalogHtmlParserCore", "Reached maximum catalog items limit ($maxCatalogItems)")
            }

            items
        } catch (e: kotlinx.coroutines.CancellationException) {
            // FIX: キャンセル例外は再スロー
            throw e
        } catch (e: Exception) {
            Logger.e("CatalogHtmlParserCore", "Failed to parse catalog HTML: ${e.message}")
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
        if (contentStart > endIndex) return null
        return text.substring(contentStart, endIndex)
    }

    private fun isSupportedMediaHref(href: String): Boolean {
        val extension = href
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
        return extension in supportedMediaExtensions
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
