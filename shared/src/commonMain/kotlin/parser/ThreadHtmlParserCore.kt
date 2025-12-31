package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.ensureActive
import kotlin.text.concatToString
import kotlin.time.ExperimentalTime

/**
 * Minimal-yet-robust parser that understands Futaba thread markup.
 *
 * The implementation intentionally keeps dependencies out of the multiplatform source-set and
 * instead relies on lightweight regular expressions that have been verified against the captures
 * checked into `/example/thread.txt`.
 */
@OptIn(ExperimentalTime::class)
internal object ThreadHtmlParserCore {
    private const val TAG = "ThreadHtmlParserCore"
    private const val DEFAULT_BASE_URL = "https://www.example.com"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks
    private const val MAX_CHUNK_SIZE = 200_000 // Process HTML in chunks to prevent ReDoS
    private const val MAX_PARSE_TIME_MS = 5000L // FIX: 5秒に戻す（parsing専用dispatcherで実行）
    private const val MAX_SINGLE_BLOCK_SIZE = 300_000 // FIX: 500KB→300KBに削減してより早く異常を検出

    private val canonicalRegex = Regex(
        pattern = "<link[^>]+rel=['\"]canonical['\"][^>]+href=['\"]([^'\"]+)['\"]",
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
    private val expireRegex = Regex(
        pattern = "<span(?=[^>]*(?:id=['\"]?contdisp['\"]?|class=['\"]?cntd['\"]?))[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val deletedNoticeRegex = Regex(
        pattern = "<span\\s+id=['\"]ddel['\"]>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val brEndRegex = Regex(
        pattern = "<br></span>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val tableRegex = Regex(
        pattern = "<table\\s+border=0[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val tableEndRegex = Regex(
        pattern = "</table>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val blockquoteRegex = Regex(
        pattern = "<blockquote[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val blockquoteEndRegex = Regex(
        pattern = "</blockquote>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val postIdRegex = Regex(
        pattern = "<span(?=[^>]*class=['\"]?cno['\"]?)[^>]*>\\s*No\\.?\\s*(\\d+)",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val orderRegex = Regex(
        pattern = "<span(?=[^>]*class=['\"]?rsc['\"]?)[^>]*>(\\d+)",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val srcLinkRegex = Regex(
        pattern = "<a[^>]+href=['\"]([^'\"]*/src/[^'\"]+)['\"][^>]*>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val thumbImgRegex = Regex(
        pattern = "<img[^>]+src=['\"]([^'\"]*/thumb/[^'\"]+)['\"][^>]*>",
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
    private val CLASS_REGEX_MAP = mapOf(
        "csb" to Regex("<span[^>]*class=(?:['\"])?csb(?:['\"])?[^>]*>", RegexOption.IGNORE_CASE),
        "cnm" to Regex("<span[^>]*class=(?:['\"])?cnm(?:['\"])?[^>]*>", RegexOption.IGNORE_CASE),
        "cnw" to Regex("<span[^>]*class=(?:['\"])?cnw(?:['\"])?[^>]*>", RegexOption.IGNORE_CASE)
    )

    // FIX: サスペンド関数に変更し、必ずバックグラウンドで実行
    suspend fun parseThread(html: String): ThreadPage = kotlinx.coroutines.withContext(AppDispatchers.parsing) {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        try {
            val normalized = html.replace("\r\n", "\n")
            val canonical = canonicalRegex.find(normalized)?.groupValues?.getOrNull(1)
            val baseUrl = canonical?.let(::extractBaseUrl) ?: DEFAULT_BASE_URL

            // Extract threadId with better error handling
            val threadId = runCatching {
                canonical?.let { canonicalIdRegex.find(it)?.groupValues?.getOrNull(1) }
                    ?: dataResRegex.find(normalized)?.groupValues?.getOrNull(1)
                    ?: postIdRegex.find(normalized)?.groupValues?.getOrNull(1)
                    ?: ""
            }.getOrElse {
                println("ThreadHtmlParserCore: Failed to extract threadId: ${it.message}")
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
                val repliesHtml = normalized.substring(firstReplyIndex)
                var searchStart = 0
                var iterationCount = 0
                // FIX: イテレーション制限を引き上げ
                val maxIterations = 2000
                // FIX: 投稿数制限を引き上げ
                val maxPosts = 3000
                var lastSearchStart = -1 // Track previous position to detect stalling

                // FIX: パースタイムアウトでANRを防止
                val parseStartTime = kotlin.time.Clock.System.now().toEpochMilliseconds()

                while (searchStart < repliesHtml.length &&
                       iterationCount < maxIterations &&
                       posts.size < maxPosts) {
                    iterationCount++

                    // FIX: タイムアウトとキャンセルチェックを100回ごとに実行
                    if (iterationCount % 100 == 0) {
                        // キャンセルチェック
                        ensureActive()

                        val elapsed = kotlin.time.Clock.System.now().toEpochMilliseconds() - parseStartTime
                        if (elapsed > MAX_PARSE_TIME_MS) {
                            Logger.e(TAG, "Parse timeout exceeded ($elapsed ms), stopping parse")
                            isTruncated = true
                            truncationReason = "Parse timeout exceeded (${elapsed}ms > ${MAX_PARSE_TIME_MS}ms)"
                            break
                        }
                    }

                    // Safety check: detect if searchStart hasn't moved
                    if (searchStart == lastSearchStart) {
                        Logger.e(TAG, "Search position stalled at $searchStart, stopping parse")
                        isTruncated = true
                        truncationReason = "Parse error: search position stalled"
                        break
                    }
                    lastSearchStart = searchStart

                    // Find table start with safety check for regex performance
                    val tableStart = try {
                        tableRegex.find(repliesHtml, searchStart)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Regex exception during table start search", e)
                        isTruncated = true
                        truncationReason = "Parse error: regex exception"
                        break
                    } ?: break

                    // Find table end with safety check
                    val tableEnd = try {
                        tableEndRegex.find(repliesHtml, tableStart.range.last)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Regex exception during table end search", e)
                        isTruncated = true
                        truncationReason = "Parse error: regex exception"
                        break
                    } ?: break

                    // Safety check to prevent infinite loop from invalid ranges
                    if (tableEnd.range.last <= tableStart.range.last) {
                        Logger.e(TAG, "Invalid table range detected, stopping parse")
                        isTruncated = true
                        truncationReason = "Parse error: invalid table range"
                        break
                    }

                    // FIX: 整数オーバーフロー防止 - range.last + 1がオーバーフローしないかチェック
                    // Int.MAX_VALUE - 1の場合も+1でInt.MAX_VALUEになり配列アクセスで問題が起きるため弾く
                    if (tableEnd.range.last >= Int.MAX_VALUE - 1) {
                        Logger.e(TAG, "Range end position near Int.MAX_VALUE, cannot continue")
                        isTruncated = true
                        truncationReason = "Parse error: range overflow"
                        break
                    }

                    val block = repliesHtml.substring(tableStart.range.first, tableEnd.range.last + 1)

                    // FIX: Stricter size limits to prevent ReDoS
                    if (block.length > MAX_CHUNK_SIZE) {
                        Logger.w(TAG, "Large table block ${block.length} bytes")
                        // FIX: Reduce max single block size from 1MB to 500KB
                        if (block.length > MAX_SINGLE_BLOCK_SIZE) {
                            Logger.w(TAG, "Skipping block exceeding safe size limit (${block.length} > $MAX_SINGLE_BLOCK_SIZE)")
                            searchStart = tableEnd.range.last + 1
                            continue
                        }
                    }

                    if (block.contains("class=\"cno\"", ignoreCase = true) ||
                        block.contains("class=cno", ignoreCase = true)
                    ) {
                        parsePostBlock(block, baseUrl, isOp = false)?.let(posts::add)
                    }

                    val newSearchStart = tableEnd.range.last + 1

                    // Safety check to prevent searchStart from going backwards
                    if (newSearchStart <= searchStart) {
                        Logger.e(TAG, "Search position not advancing properly (old=$searchStart, new=$newSearchStart), stopping parse")
                        isTruncated = true
                        truncationReason = "Parse error: search position not advancing"
                        break
                    }

                    searchStart = newSearchStart
                }

                if (iterationCount >= maxIterations) {
                    Logger.w(TAG, "Reached maximum iteration limit ($maxIterations), thread may be truncated")
                    isTruncated = true
                    truncationReason = "Exceeded maximum iteration limit ($maxIterations)"
                }
                if (posts.size >= maxPosts) {
                    Logger.w(TAG, "Reached maximum post limit ($maxPosts), thread truncated")
                    isTruncated = true
                    truncationReason = "Thread has more than $maxPosts posts"
                }
            }

            // FIX: Build references and counts in a single pass to reduce CPU usage
            val referenceData = buildReferencesAndCounts(posts)
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
        val canonical = canonicalRegex.find(normalized)?.groupValues?.getOrNull(1)
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
                text.substring(start.range.last + 1, end.range.first)
            }
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

    // FIX: Combine reference counting and map building to avoid O(n²) complexity
    private data class ReferenceData(
        val counts: Map<String, Int>,
        val references: Map<String, List<QuoteReference>>
    )

    /**
     * オフライン復元など既存の Post リストから引用情報を付け直す用途向け。
     */
    fun rebuildReferences(posts: List<Post>): List<Post> {
        if (posts.isEmpty()) return posts
        val referenceData = buildReferencesAndCounts(posts)
        return posts.map { post ->
            post.copy(
                referencedCount = referenceData.counts[post.id] ?: 0,
                quoteReferences = referenceData.references[post.id].orEmpty()
            )
        }
    }

    private fun buildReferencesAndCounts(posts: List<Post>): ReferenceData {
        if (posts.isEmpty()) return ReferenceData(emptyMap(), emptyMap())

        val posterIdIndex = mutableMapOf<String, MutableSet<String>>()
        val messageLineIndex = mutableMapOf<String, LineTargets>()
        val mediaFileIndex = mutableMapOf<String, MutableSet<String>>()
        val counts = mutableMapOf<String, Int>()
        val references = mutableMapOf<String, MutableList<QuoteReference>>()

        // FIX: デコード/ストリップ処理をキャッシュして繰り返し処理を避ける
        val decodedMessages = mutableMapOf<String, List<String>>()

        // Single pass through posts in order; only以前の投稿を参照対象とする
        posts.forEach { post: Post ->
            if (post.messageHtml.isBlank()) return@forEach

            // デコード済みメッセージをキャッシュから取得または生成
            val lines = decodedMessages.getOrPut(post.id) {
                decodeHtmlEntities(stripTags(post.messageHtml))
                    .lines()
                    .map { it.trimStart() }
            }
            if (lines.isEmpty()) return@forEach

            val postReferences = mutableListOf<QuoteReference>()
            val referencedTargets = mutableSetOf<String>()
            val pendingContentLines = mutableListOf<String>()
            var pendingContentTargets: Set<String>? = null
            var isContentBlockInvalid = false

            fun flushPendingContentBlock() {
                val targets = pendingContentTargets
                if (pendingContentLines.isNotEmpty() &&
                    !isContentBlockInvalid &&
                    targets != null &&
                    targets.isNotEmpty()
                ) {
                    postReferences.add(
                        QuoteReference(
                            text = pendingContentLines.joinToString("\n").trim(),
                            targetPostIds = targets.toList()
                        )
                    )
                    referencedTargets += targets
                }
                pendingContentLines.clear()
                pendingContentTargets = null
                isContentBlockInvalid = false
            }

            lines.forEach { rawLine ->
                val trimmedLine = rawLine.trimStart()
                if (!(trimmedLine.startsWith(">") || trimmedLine.startsWith("＞"))) {
                    flushPendingContentBlock()
                    return@forEach
                }

                val resolution = resolveQuoteTargets(
                    quoteLine = trimmedLine,
                    posterIdIndex = posterIdIndex,
                    messageLineIndex = messageLineIndex,
                    mediaFileIndex = mediaFileIndex
                )
                if (resolution.isExplicit) {
                    flushPendingContentBlock()
                    if (resolution.targets.isNotEmpty()) {
                        postReferences.add(
                            QuoteReference(
                                text = trimmedLine.trim(),
                                targetPostIds = resolution.targets.toList()
                            )
                        )
                        referencedTargets += resolution.targets
                    }
                    return@forEach
                }

                if (resolution.targets.isEmpty()) {
                    flushPendingContentBlock()
                    return@forEach
                }

                val currentTargets = pendingContentTargets
                val updatedTargets = when {
                    currentTargets == null -> resolution.targets
                    else -> currentTargets.intersect(resolution.targets)
                }

                if (updatedTargets.isEmpty()) {
                    // Targets changed mid-block; close previous block and start a new one
                    flushPendingContentBlock()
                    pendingContentLines.add(trimmedLine.trim())
                    pendingContentTargets = resolution.targets
                    isContentBlockInvalid = false
                } else {
                    pendingContentLines.add(trimmedLine.trim())
                    pendingContentTargets = updatedTargets
                }
            }

            flushPendingContentBlock()

            if (postReferences.isNotEmpty()) {
                referencedTargets.forEach { targetId ->
                    if (targetId == post.id) return@forEach
                    counts[targetId] = counts[targetId]?.plus(1) ?: 1
                }
                references[post.id] = postReferences
            }

            // 現在の投稿を以降の引用解析の対象としてインデックスに追加
            addPosterIdToIndex(posterIdIndex, post)
            addMessageLinesToIndex(messageLineIndex, post)
            addMediaToIndex(mediaFileIndex, post)
        }

        return ReferenceData(counts, references)
    }

    private fun computeReferencedCounts(posts: List<Post>): Map<String, Int> {
        return buildReferencesAndCounts(posts).counts
    }

    private fun buildQuoteReferenceMap(posts: List<Post>): Map<String, List<QuoteReference>> {
        return buildReferencesAndCounts(posts).references
    }

    private fun resolveUrl(path: String, baseUrl: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> baseUrl.trimEnd('/') + path
            else -> baseUrl.trimEnd('/') + "/" + path
        }
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

    private fun addPosterIdToIndex(
        index: MutableMap<String, MutableSet<String>>,
        post: Post
    ) {
        val id = post.posterId?.takeIf { it.isNotBlank() } ?: return
        val normalized = id.trim()
        index.getOrPut(normalized) { mutableSetOf() }.add(post.id)
    }

    private fun addMessageLinesToIndex(
        index: MutableMap<String, LineTargets>,
        post: Post
    ) {
        if (post.messageHtml.isBlank()) return
        decodeHtmlEntities(stripTags(post.messageHtml))
            .lines()
            .forEach { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.isBlank()) return@forEach
                val isQuoted = trimmed.startsWith(">") || trimmed.startsWith("＞")
                val withoutMarkers = trimmed.trimStart { it == '>' || it == '＞' }.trim()
                if (withoutMarkers.isBlank()) return@forEach
                val normalized = normalizeQuoteText(withoutMarkers)
                if (normalized.isBlank()) return@forEach
                val bucket = index.getOrPut(normalized) { LineTargets() }
                if (isQuoted) bucket.quoted.add(post.id) else bucket.plain.add(post.id)
            }
    }

    private data class QuoteLineResolution(
        val targets: Set<String>,
        val isExplicit: Boolean
    )

    private fun resolveQuoteTargets(
        quoteLine: String,
        posterIdIndex: Map<String, Set<String>>,
        messageLineIndex: Map<String, LineTargets>,
        mediaFileIndex: Map<String, MutableSet<String>>
    ): QuoteLineResolution {
        val trimmed = quoteLine.trim()
        if (trimmed.isBlank()) return QuoteLineResolution(emptySet(), isExplicit = false)
        val content = trimmed.trimStart { it == '>' || it == '＞' }.trim()
        if (content.isBlank()) return QuoteLineResolution(emptySet(), isExplicit = false)
        val mediaTargets = resolveMediaTargets(content, mediaFileIndex)
        if (mediaTargets.isNotEmpty()) {
            return QuoteLineResolution(mediaTargets, isExplicit = true)
        }
        val explicitNumber = noReferenceRegex.find(content)?.groupValues?.getOrNull(1)
            ?: leadingNumberRegex.find(content)?.groupValues?.getOrNull(1)
        if (explicitNumber != null) {
            return QuoteLineResolution(setOf(explicitNumber), isExplicit = true)
        }
        val idMatch = idReferenceRegex.find(content)?.value
        if (idMatch != null) {
            val targets = posterIdIndex[idMatch].orEmpty()
            return QuoteLineResolution(targets, isExplicit = true)
        }
        val normalized = normalizeQuoteText(content)
        if (normalized.isBlank()) return QuoteLineResolution(emptySet(), isExplicit = false)
        val targets = messageLineIndex[normalized]?.resolve()
        if (!targets.isNullOrEmpty()) {
            return QuoteLineResolution(targets, isExplicit = false)
        }
        val partialTargets = findPartialLineTargets(normalized, messageLineIndex)
        return QuoteLineResolution(partialTargets, isExplicit = false)
    }

    private fun resolveMediaTargets(
        content: String,
        mediaFileIndex: Map<String, MutableSet<String>>
    ): Set<String> {
        if (mediaFileIndex.isEmpty()) return emptySet()
        val matches = mediaFilenameRegex.findAll(content)
        if (!matches.iterator().hasNext()) return emptySet()
        val targets = mutableSetOf<String>()
        matches.forEach { match ->
            val normalized = match.value.lowercase()
            targets += mediaFileIndex[normalized].orEmpty()
        }
        return targets
    }

    private fun normalizeQuoteText(value: String): String {
        return value.replace(whitespaceRegex, " ").trim()
    }

    private fun findPartialLineTargets(
        normalizedQuote: String,
        index: Map<String, LineTargets>
    ): Set<String> {
        if (normalizedQuote.length < MIN_PARTIAL_MATCH_LENGTH) return emptySet()
        if (index.isEmpty()) return emptySet()
        val targets = mutableSetOf<String>()
        index.forEach { (line, ids) ->
            if (line.length < MIN_PARTIAL_MATCH_LENGTH) return@forEach
            if (line.contains(normalizedQuote) || normalizedQuote.contains(line)) {
                targets += ids.resolve()
            }
        }
        return targets
    }

    private data class LineTargets(
        val plain: MutableSet<String> = mutableSetOf(),
        val quoted: MutableSet<String> = mutableSetOf()
    ) {
        fun resolve(): Set<String> = if (plain.isNotEmpty()) plain else quoted
    }

    private fun addMediaToIndex(
        index: MutableMap<String, MutableSet<String>>,
        post: Post
    ) {
        extractFileName(post.imageUrl)?.let { file ->
            index.getOrPut(file) { mutableSetOf() }.add(post.id)
        }
        extractFileName(post.thumbnailUrl)?.let { file ->
            index.getOrPut(file) { mutableSetOf() }.add(post.id)
        }
    }

    private fun extractFileName(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val cleaned = url.substringBefore('?').substringAfterLast('/', "")
        if (cleaned.isBlank()) return null
        val lower = cleaned.lowercase()
        val match = mediaFilenameRegex.matchEntire(lower) ?: return null
        return match.value
    }
}
