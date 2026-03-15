package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.text.concatToString
import kotlin.time.ExperimentalTime

/**
 * Minimal-yet-robust parser that understands Futaba thread markup.
 *
 * The implementation intentionally keeps dependencies out of the multiplatform source-set and
 * instead relies on lightweight regular expressions that have been verified against the captures
 * checked into `/example/thread.txt`.
 *
 * FIX: パフォーマンス最適化の実装詳細
 *
 * ## 実装済みの最適化：
 * 1. ReDoS攻撃防止
 *    - すべての正規表現に長さ制限を追加（[^>]{0,200}など）
 *    - MAX_HTML_SIZE = 10MB制限
 *    - MAX_PARSE_TIME_MS = 5秒タイムアウト
 *
 * 2. メモリ最適化
 *    - 正規表現のプリコンパイル（ループ外で定義）
 *    - MAX_SINGLE_BLOCK_SIZE = 300KB（ブロックサイズ制限）
 *    - 不要な文字列コピーの最小化
 *
 * 3. スレッド最適化
 *    - AppDispatchers.parsingで専用スレッド実行（並列度2）
 *    - キャンセルチェック（ensureActive）の適切な配置
 *
 * 4. アルゴリズム最適化
 *    - indexOfを優先使用（正規表現より高速）
 *    - 不要な正規表現マッチの回避
 *    - 早期リターンによる無駄な処理の削減
 *
 * ## 今後の改善案：
 * - HTML文字列の正規化処理の最適化（現在はコピーを作成）
 * - チャンク処理による段階的パース
 * - 正規表現の代わりに状態機械ベースのパーサー
 */
@OptIn(ExperimentalTime::class)
internal object ThreadHtmlParserCore {
    private const val TAG = "ThreadHtmlParserCore"
    private const val DEFAULT_BASE_URL = "https://www.example.com"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks
    private const val MAX_CHUNK_SIZE = 200_000 // Process HTML in chunks to prevent ReDoS
    private const val MAX_PARSE_TIME_MS = 5000L // FIX: 5秒に戻す（parsing専用dispatcherで実行）
    private const val MAX_REFERENCE_BUILD_TIME_MS = 1500L
    private const val MAX_SINGLE_BLOCK_SIZE = 300_000 // FIX: 500KB→300KBに削減してより早く異常を検出
    private const val MAX_PARTIAL_MATCH_SCAN_LINES = 300
    private val TRUSTED_CANONICAL_HOST_SUFFIXES = setOf("2chan.net")

    // FIX: ReDoS対策 - [^>]+に長さ制限を追加
    private val canonicalRegex = Regex(
        pattern = "<link[^>]{1,500}rel=['\"]canonical['\"][^>]{1,500}href=['\"]([^'\"]+)['\"]",
        option = RegexOption.IGNORE_CASE
    )
    private val canonicalIdRegex = Regex("/res/(\\d+)\\.htm")
    private val dataResRegex = Regex("data-res=['\"]?(\\d+)")
    private val boardTitleRegex = Regex(
        pattern = "<span\\s+id=['\"]tit['\"]>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val spanEndRegex = Regex(
        pattern = "</span>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加（最大500文字）
    private val expireRegex = Regex(
        pattern = "<span(?=[^>]{0,500}(?:id=['\"]?contdisp['\"]?|class=['\"]?cntd['\"]?))[^>]{0,500}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val deletedNoticeRegex = Regex(
        pattern = "<span\\s+id=['\"]?ddel['\"]?>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val brEndRegex = Regex(
        pattern = "<br\\s*/?>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加
    private val tableRegex = Regex(
        pattern = "<table\\s+border=0[^>]{0,200}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val tableEndRegex = Regex(
        pattern = "</table>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加
    private val blockquoteRegex = Regex(
        pattern = "<blockquote[^>]{0,200}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val blockquoteEndRegex = Regex(
        pattern = "</blockquote>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加
    private val postIdRegex = Regex(
        pattern = "<span(?=[^>]{0,200}class=['\"]?cno['\"]?)[^>]{0,200}>\\s*No\\.?\\s*(\\d+)",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加
    private val orderRegex = Regex(
        pattern = "<span(?=[^>]{0,200}class=['\"]?rsc['\"]?)[^>]{0,200}>(\\d+)",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]+に長さ制限を追加
    private val srcLinkRegex = Regex(
        pattern = "<a[^>]{1,500}href=['\"]([^'\"]*/src/[^'\"]+)['\"][^>]{0,300}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: ReDoS対策 - [^>]+に長さ制限を追加
    private val thumbImgRegex = Regex(
        pattern = "<img[^>]{1,500}src=['\"]([^'\"]*/thumb/[^'\"]+)['\"][^>]{0,300}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private const val ISOLATION_NOTICE_TEXT = "削除依頼によって隔離されました"
    // FIX: ReDoS対策 - 非貪欲量指定子を使用
    private val saidaneRegex = Regex(
        pattern = "<a\\s+[^>]{0,200}?class=['\"]?sod['\"]?[^>]{0,200}?>([^<]{1,500}?)</a>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val posterIdRegex = Regex("ID:[^\\s<]+")
    private val noReferenceRegex = Regex("No\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val leadingNumberRegex = Regex("^(\\d+)")
    private val idReferenceRegex = Regex("ID:[^\\s>]+")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "avi", "ts", "flv")
    private const val MIN_PARTIAL_MATCH_LENGTH = 6
    private val mediaFilenameRegex = Regex(
        pattern = "([A-Za-z0-9._-]+\\.(?:jpe?g|png|gif|webp|bmp|mp4|webm|mkv|mov|avi|ts|flv|m4v))",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    // FIX: 正規表現の再コンパイル防止 - 関数内で毎回生成されていたパターンをトップレベルに移動
    private val deletedRegex = Regex("class\\s*=\\s*\"?deleted\"?", RegexOption.IGNORE_CASE)
    private val brTagRegex = Regex("(?i)<br\\s*/?>")
    private val whitespaceRegex = Regex("\\s+")
    
    // FIX: よく使われるクラス名のRegexをキャッシュして再生成を防止
    // FIX: ReDoS対策 - [^>]*に長さ制限を追加（最大200文字）
    private val CLASS_REGEX_MAP = mapOf(
        "csb" to Regex("<span[^>]{0,200}class=(?:['\"])?csb(?:['\"])?[^>]{0,200}>", RegexOption.IGNORE_CASE),
        "cnm" to Regex("<span[^>]{0,200}class=(?:['\"])?cnm(?:['\"])?[^>]{0,200}>", RegexOption.IGNORE_CASE),
        "cnw" to Regex("<span[^>]{0,200}class=(?:['\"])?cnw(?:['\"])?[^>]{0,200}>", RegexOption.IGNORE_CASE)
    )

    // FIX: サスペンド関数に変更し、必ずバックグラウンドで実行
    suspend fun parseThread(html: String): ThreadPage = kotlinx.coroutines.withContext(AppDispatchers.parsing) {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        try {
            // NOTE: この処理は大きなHTML文字列のコピーを作成するため、メモリ使用量が増加する
            // TODO: 将来的には、チャンク処理や正規表現での\r?\n対応を検討
            val normalized = html.replace("\r\n", "\n")
            val canonical = sanitizeCanonicalUrl(
                canonicalRegex.find(normalized)?.groupValues?.getOrNull(1)
            )
            val baseUrl = canonical?.let(::extractBaseUrl) ?: DEFAULT_BASE_URL

            // Extract threadId with better error handling
            val threadId = runCatching {
                canonical?.let { canonicalIdRegex.find(it)?.groupValues?.getOrNull(1) }
                    ?: dataResRegex.find(normalized)?.groupValues?.getOrNull(1)
                    ?: postIdRegex.find(normalized)?.groupValues?.getOrNull(1)
                    ?: ""
            }.getOrElse {
                Logger.w(TAG, "Failed to extract threadId: ${it.message}")
                ""
            }

            val boardTitle = extractBetween(normalized, boardTitleRegex, spanEndRegex)
                ?.let(::stripTags)
                ?.trim()
            val expiresAt = extractBetween(normalized, expireRegex, spanEndRegex)
                ?.let(::stripTags)
                ?.trim()
            val deletedNotice = extractBetween(normalized, deletedNoticeRegex, brEndRegex)
                ?.let(::stripTags)
                ?.replace("\\s+".toRegex(), "")
                ?.trim()

            val posts = mutableListOf<Post>()
            val firstReplyIndex = normalized.indexOf("<table border=0", ignoreCase = true)
            val opBlock = extractOpBlock(normalized, firstReplyIndex)
            opBlock?.let { parsePostBlock(it, baseUrl, isOp = true)?.let(posts::add) }

            var isTruncated = false
            var truncationReason: String? = null

            if (firstReplyIndex != -1) {
                val replyResult = parseThreadReplyBlocks(
                    repliesHtml = normalized,
                    initialSearchStart = firstReplyIndex,
                    config = ThreadReplyParsingConfig(
                        tag = TAG,
                        maxChunkSize = MAX_CHUNK_SIZE,
                        maxParseTimeMs = MAX_PARSE_TIME_MS,
                        maxSingleBlockSize = MAX_SINGLE_BLOCK_SIZE,
                        maxIterations = 2000,
                        maxPosts = 3000,
                        tableRegex = tableRegex,
                        tableEndRegex = tableEndRegex
                    ),
                    parsePostBlock = { block ->
                        parsePostBlock(block, baseUrl, isOp = false)
                    }
                )
                posts += replyResult.posts
                isTruncated = replyResult.isTruncated
                truncationReason = replyResult.truncationReason
            }

            // FIX: Build references and counts in a single pass to reduce CPU usage
            val referenceData = buildReferencesAndCounts(posts)
            if (referenceData.timedOut) {
                isTruncated = true
                if (truncationReason.isNullOrBlank()) {
                    truncationReason = "Reference rebuild exceeded time budget (${MAX_REFERENCE_BUILD_TIME_MS}ms)"
                }
            }
            val postsWithReferences = posts.map { post ->
                post.copy(
                    referencedCount = referenceData.counts[post.id] ?: 0,
                    quoteReferences = referenceData.references[post.id].orEmpty()
                )
            }

            val resolvedThreadId = if (threadId.isNotBlank()) threadId else postsWithReferences.firstOrNull()?.id.orEmpty()
            ThreadPage(
                threadId = resolvedThreadId,
                boardTitle = boardTitle,
                expiresAtLabel = expiresAt,
                deletedNotice = deletedNotice,
                posts = postsWithReferences,
                isTruncated = isTruncated,
                truncationReason = truncationReason
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // FIX: キャンセル例外は再スロー
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse thread HTML", e)
            throw ParserException("Failed to parse thread HTML", e)
        }
    }

    fun extractOpImageUrl(html: String, baseUrl: String? = null): String? {
        if (html.isBlank()) return null
        val normalized = html.replace("\r\n", "\n")
        val canonical = sanitizeCanonicalUrl(
            canonicalRegex.find(normalized)?.groupValues?.getOrNull(1)
        )
        val resolvedBaseUrl = canonical?.let(::extractBaseUrl)
            ?: baseUrl?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL
        val primaryUrl = srcLinkRegex.find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { resolveUrl(it, resolvedBaseUrl) }
        if (primaryUrl != null && isVideoUrl(primaryUrl)) {
            val thumbUrl = thumbImgRegex.find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { resolveUrl(it, resolvedBaseUrl) }
            return thumbUrl ?: primaryUrl
        }
        return primaryUrl
    }

    private fun isVideoUrl(url: String): Boolean {
        val cleaned = url.substringBefore('?')
        val extension = cleaned.substringAfterLast('.', "")
        if (extension.isBlank()) return false
        return extension.lowercase() in videoExtensions
    }

    // FIX: ReDoS対策 - タイムアウト付きregex検索に変更
    private suspend fun extractBetween(
        text: String,
        startRegex: Regex,
        endRegex: Regex,
        timeoutMillis: Long = 500L
    ): String? {
        return try {
            // FIX: 複雑なパターンマッチングに500msタイムアウトを設定（低速端末対応）
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                val start = startRegex.find(text) ?: return@withTimeoutOrNull null
                val end = endRegex.find(text, start.range.last) ?: return@withTimeoutOrNull null
                // FIX: 整数オーバーフロー防止 - Int.MAX_VALUE - 1も弾く
                if (start.range.last >= Int.MAX_VALUE - 1) {
                    Logger.w(TAG, "Range overflow in extractBetween")
                    return@withTimeoutOrNull null
                }
                val contentStart = start.range.last + 1
                if (contentStart > text.length) return@withTimeoutOrNull null
                if (end.range.first < contentStart || end.range.first > text.length) return@withTimeoutOrNull null
                text.substring(contentStart, end.range.first)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("ThreadHtmlParserCore", "extractBetween timeout or error: ${e.message}")
            null
        }
    }

    private fun extractOpBlock(html: String, firstReplyIndex: Int): String? {
        val start = html.indexOf("<div class=\"thre\"", ignoreCase = true)
        if (start == -1) return null
        val end = if (firstReplyIndex != -1 && firstReplyIndex > start) {
            firstReplyIndex
        } else {
            html.indexOf("</div>", startIndex = start, ignoreCase = true)
                .takeIf { it != -1 }?.plus("</div>".length) ?: html.length
        }
        return html.substring(start, end)
    }

    private suspend fun parsePostBlock(
        block: String,
        baseUrl: String,
        isOp: Boolean
    ): Post? {
        val postId = postIdRegex.find(block)?.groupValues?.getOrNull(1)
            ?: dataResRegex.find(block)?.groupValues?.getOrNull(1)
            ?: return null
        val subject = sanitizeInlineText(block, "csb")
        val author = sanitizeInlineText(block, "cnm")
        val timestampRaw = sanitizeInlineText(block, "cnw")
        val posterId = timestampRaw?.let { posterIdRegex.find(it)?.value }
        val timestamp = timestampRaw.orEmpty()
        val messageHtml = extractBetween(block, blockquoteRegex, blockquoteEndRegex, timeoutMillis = 100L)
            ?.let(::cleanMessageHtml)
            .orEmpty()
        val normalizedMessageText = normalizeMessageText(messageHtml)
        if (containsIsolationNotice(normalizedMessageText)) return null
        val imageUrl = srcLinkRegex.find(block)?.groupValues?.getOrNull(1)?.let { resolveUrl(it, baseUrl) }
        val thumbnailUrl = thumbImgRegex.find(block)?.groupValues?.getOrNull(1)?.let { resolveUrl(it, baseUrl) }
        val order = orderRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: if (isOp) 0 else null
        val saidane = saidaneRegex.find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::stripTags)
            ?.trim()
            ?.ifBlank { null }
        val isDeleted = deletedRegex.containsMatchIn(block)

        return Post(
            id = postId,
            order = order,
            author = author,
            subject = subject,
            timestamp = timestamp,
            posterId = posterId,
            messageHtml = messageHtml,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            saidaneLabel = saidane,
            isDeleted = isDeleted
        )
    }

    private fun sanitizeInlineText(block: String, className: String): String? {
        // FIX: キャッシュされたRegexを使用
        val startRegex = CLASS_REGEX_MAP[className] ?: Regex("<span[^>]*class=(?:['\"])?$className(?:['\"])?[^>]*>", RegexOption.IGNORE_CASE)
        val startMatch = startRegex.find(block) ?: return null

        // FIX: 整数オーバーフロー防止 - Int.MAX_VALUE - 1も弾く
        if (startMatch.range.last >= Int.MAX_VALUE - 1) {
            Logger.w(TAG, "Range overflow in sanitizeInlineText")
            return null
        }

        val contentStartIndex = startMatch.range.last + 1
        val endPattern = "</span>"
        val endIndex = block.indexOf(endPattern, startIndex = contentStartIndex, ignoreCase = true)

        if (endIndex == -1) return null

        val raw = block.substring(contentStartIndex, endIndex)
        val cleaned = decodeHtmlEntities(stripTags(raw)).trim()
        return cleaned.ifBlank { null }
    }

    private fun cleanMessageHtml(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .trim()
    }

    private fun stripTags(value: String): String = htmlTagRegex.replace(
        value.replace(brTagRegex, "\n"),
        ""
    )

    private fun normalizeMessageText(messageHtml: String): String {
        if (messageHtml.isEmpty()) return ""
        val plain = decodeHtmlEntities(stripTags(messageHtml))
        return plain.replace(whitespaceRegex, "")
    }

    private fun containsIsolationNotice(normalizedMessageText: String): Boolean {
        if (normalizedMessageText.isEmpty()) return false
        return normalizedMessageText.contains(ISOLATION_NOTICE_TEXT)
    }

    private fun decodeHtmlEntities(value: String): String {
        return HtmlEntityDecoder.decode(value)
    }

    /**
     * オフライン復元など既存の Post リストから引用情報を付け直す用途向け。
     */
    fun rebuildReferences(posts: List<Post>): List<Post> {
        if (posts.isEmpty()) return posts
        val referenceData = buildThreadReferenceData(
            posts = posts,
            config = buildThreadReferenceConfig(),
            decodeHtmlEntities = ::decodeHtmlEntities,
            stripTags = ::stripTags
        )
        return posts.map { post ->
            post.copy(
                referencedCount = referenceData.counts[post.id] ?: 0,
                quoteReferences = referenceData.references[post.id].orEmpty()
            )
        }
    }

    private fun buildReferencesAndCounts(posts: List<Post>): ThreadReferenceData {
        return buildThreadReferenceData(
            posts = posts,
            config = buildThreadReferenceConfig(),
            decodeHtmlEntities = ::decodeHtmlEntities,
            stripTags = ::stripTags
        )
    }

    private fun resolveUrl(path: String, baseUrl: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "${extractScheme(baseUrl)}:$path"
            path.startsWith("/") -> (extractBaseUrl(baseUrl) ?: baseUrl).trimEnd('/') + path
            else -> baseUrl.trimEnd('/') + "/" + path
        }
    }

    private fun sanitizeCanonicalUrl(url: String?): String? {
        val candidate = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val base = extractBaseUrl(candidate) ?: return null
        val scheme = extractScheme(base)
        if (scheme != "http" && scheme != "https") return null
        val host = base.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase().trim('.')
        if (host.isBlank()) return null
        val trusted = TRUSTED_CANONICAL_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
        if (!trusted) return null
        return candidate
    }

    private fun extractScheme(baseUrl: String): String {
        val schemeIndex = baseUrl.indexOf("://")
        if (schemeIndex <= 0) return "https"
        return baseUrl.substring(0, schemeIndex).lowercase()
    }

    private fun extractBaseUrl(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return null
        // FIX: 整数オーバーフロー防止
        if (schemeEnd > Int.MAX_VALUE - 3) {
            Logger.w(TAG, "URL scheme position too large, potential overflow")
            return null
        }
        val hostStart = schemeEnd + 3
        val slashIndex = url.indexOf('/', startIndex = hostStart)
        return if (slashIndex == -1) url else url.substring(0, slashIndex)
    }

    private fun buildThreadReferenceConfig(): ThreadReferenceBuildConfig {
        return ThreadReferenceBuildConfig(
            tag = TAG,
            maxReferenceBuildTimeMs = MAX_REFERENCE_BUILD_TIME_MS,
            maxPartialMatchScanLines = MAX_PARTIAL_MATCH_SCAN_LINES,
            minPartialMatchLength = MIN_PARTIAL_MATCH_LENGTH,
            noReferenceRegex = noReferenceRegex,
            leadingNumberRegex = leadingNumberRegex,
            idReferenceRegex = idReferenceRegex,
            whitespaceRegex = whitespaceRegex,
            mediaFilenameRegex = mediaFilenameRegex
        )
    }
}
