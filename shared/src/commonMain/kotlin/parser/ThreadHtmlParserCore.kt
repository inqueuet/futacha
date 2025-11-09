package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.text.concatToString

/**
 * Minimal-yet-robust parser that understands Futaba thread markup.
 *
 * The implementation intentionally keeps dependencies out of the multiplatform source-set and
 * instead relies on lightweight regular expressions that have been verified against the captures
 * checked into `/example/thread.txt`.
 */
internal object ThreadHtmlParserCore {
    private const val DEFAULT_BASE_URL = "https://www.example.com"
    private const val MAX_HTML_SIZE = 10 * 1024 * 1024 // 10MB limit to prevent ReDoS attacks
    private const val MAX_CHUNK_SIZE = 200_000 // Process HTML in chunks to prevent ReDoS

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
        pattern = "<span(?=[^>]*class=['\"]?cntd['\"]?)[^>]*>",
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
    // Simplified regex to prevent ReDoS - match saidane links more carefully
    private val saidaneRegex = Regex(
        pattern = "<a[^>]*class=['\"]?sod['\"]?[^>]*>([^<]+)</a>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val posterIdRegex = Regex("ID:[^\\s<]+")
    private val noReferenceRegex = Regex("No\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val leadingNumberRegex = Regex("^(\\d+)")
    private val idReferenceRegex = Regex("ID:[^\\s>]+")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")

    fun parseThread(html: String): ThreadPage {
        if (html.length > MAX_HTML_SIZE) {
            throw IllegalArgumentException("HTML size exceeds maximum allowed size of $MAX_HTML_SIZE bytes")
        }

        return try {
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

            if (firstReplyIndex != -1) {
                val repliesHtml = normalized.substring(firstReplyIndex)
                var searchStart = 0
                var iterationCount = 0
                val maxIterations = 5000 // Reduced to prevent ANR - allows up to 5000 posts
                val maxPosts = 3000 // Additional safety: maximum number of posts to parse

                while (searchStart < repliesHtml.length &&
                       iterationCount < maxIterations &&
                       posts.size < maxPosts) {
                    iterationCount++
                    val tableStart = tableRegex.find(repliesHtml, searchStart) ?: break
                    val tableEnd = tableEndRegex.find(repliesHtml, tableStart.range.last) ?: break

                    // Safety check to prevent infinite loop from invalid ranges
                    if (tableEnd.range.last <= tableStart.range.last) {
                        println("ThreadHtmlParserCore: Invalid table range detected, stopping parse")
                        break
                    }

                    val block = repliesHtml.substring(tableStart.range.first, tableEnd.range.last + 1)

                    if (block.length > MAX_CHUNK_SIZE) {
                        println("ThreadHtmlParserCore: Warning - large table block ${block.length} bytes")
                        // Limit processing to prevent ReDoS attacks
                        if (block.length > MAX_CHUNK_SIZE * 5) {
                            println("ThreadHtmlParserCore: Skipping block exceeding safe size limit")
                            searchStart = tableEnd.range.last + 1
                            continue
                        }
                    }

                    if (block.contains("class=\"cno\"", ignoreCase = true) ||
                        block.contains("class=cno", ignoreCase = true)
                    ) {
                        parsePostBlock(block, baseUrl, isOp = false)?.let(posts::add)
                    }

                    searchStart = tableEnd.range.last + 1

                    // Safety check to prevent searchStart from going backwards or staying the same
                    if (searchStart <= tableEnd.range.last) {
                        println("ThreadHtmlParserCore: Search position not advancing, stopping parse")
                        break
                    }
                }

                if (iterationCount >= maxIterations) {
                    println("ThreadHtmlParserCore: Reached maximum iteration limit ($maxIterations), thread may be truncated")
                }
                if (posts.size >= maxPosts) {
                    println("ThreadHtmlParserCore: Reached maximum post limit ($maxPosts), thread truncated")
                }
            }

            val referenceCounts = computeReferencedCounts(posts)
            val quoteReferenceMap = buildQuoteReferenceMap(posts)
            val postsWithReferences = posts.map { post ->
                post.copy(
                    referencedCount = referenceCounts[post.id] ?: 0,
                    quoteReferences = quoteReferenceMap[post.id].orEmpty()
                )
            }

            val resolvedThreadId = if (threadId.isNotBlank()) threadId else postsWithReferences.firstOrNull()?.id.orEmpty()
            ThreadPage(
                threadId = resolvedThreadId,
                boardTitle = boardTitle,
                expiresAtLabel = expiresAt,
                deletedNotice = deletedNotice,
                posts = postsWithReferences
            )
        } catch (e: Exception) {
            println("ThreadHtmlParserCore: Failed to parse thread HTML: ${e.message}")
            throw ParserException("Failed to parse thread HTML", e)
        }
    }

    private fun extractBetween(text: String, startRegex: Regex, endRegex: Regex): String? {
        val start = startRegex.find(text) ?: return null
        val end = endRegex.find(text, start.range.last) ?: return null
        return text.substring(start.range.last + 1, end.range.first)
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

    private fun parsePostBlock(
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
        val messageHtml = extractBetween(block, blockquoteRegex, blockquoteEndRegex)
            ?.let(::cleanMessageHtml)
            .orEmpty()
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
        val isDeleted = Regex("class\\s*=\\s*\"?deleted\"?", RegexOption.IGNORE_CASE).containsMatchIn(block)

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
        val regex = spanRegex(className)
        val raw = regex.find(block)?.groupValues?.getOrNull(1) ?: return null
        val cleaned = decodeHtmlEntities(stripTags(raw)).trim()
        return cleaned.ifBlank { null }
    }

    private fun cleanMessageHtml(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .trim()
    }

    private fun spanRegex(className: String): Regex = Regex(
        pattern = "<span(?=[^>]*class=(?:['\"])?$className(?:['\"])?)" +
            "[^>]*>([\\s\\S]*?)</span>",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private fun stripTags(value: String): String = htmlTagRegex.replace(
        value.replace(Regex("(?i)<br\\s*/?>"), "\n"),
        ""
    )

    private fun decodeHtmlEntities(value: String): String {
        var result = value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")

        result = hexEntityRegex.replace(result) { match ->
            val value = match.groupValues.getOrNull(1) ?: return@replace ""
            val codePoint = runCatching { value.toInt(16) }.getOrNull()
            if (codePoint != null && codePoint in 0x20..0x10FFFF) {
                codePointToString(codePoint)
            } else {
                match.value
            }
        }
        result = numericEntityRegex.replace(result) { match ->
            val value = match.groupValues.getOrNull(1) ?: return@replace ""
            val codePoint = runCatching { value.toInt() }.getOrNull()
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
            val cpPrime = codePoint - 0x10000
            val highSurrogate = ((cpPrime shr 10) + 0xD800).toChar()
            val lowSurrogate = ((cpPrime and 0x3FF) + 0xDC00).toChar()
            charArrayOf(highSurrogate, lowSurrogate).concatToString()
        }
    }

    private fun computeReferencedCounts(posts: List<Post>): Map<String, Int> {
        if (posts.isEmpty()) return emptyMap()
        val posterIdIndex = buildPosterIdIndex(posts)
        val messageLineIndex = buildMessageLineIndex(posts)
        val counts = mutableMapOf<String, Int>()
        posts.forEach { post: Post ->
            if (post.messageHtml.isBlank()) return@forEach
            extractQuoteLines(post.messageHtml).forEach { quoteLine: String ->
                resolveQuoteTargets(quoteLine, posterIdIndex, messageLineIndex).forEach { targetId: String ->
                    counts[targetId] = counts[targetId]?.plus(1) ?: 1
                }
            }
        }
        return counts
    }

    private fun buildQuoteReferenceMap(posts: List<Post>): Map<String, List<QuoteReference>> {
        if (posts.isEmpty()) return emptyMap()
        val posterIdIndex = buildPosterIdIndex(posts)
        val messageLineIndex = buildMessageLineIndex(posts)
        val map = mutableMapOf<String, MutableList<QuoteReference>>()
        posts.forEach { post: Post ->
            val quoteLines = extractQuoteLines(post.messageHtml)
            if (quoteLines.isEmpty()) return@forEach
            val references = quoteLines.map { quoteLine: String ->
                val targets: List<String> = resolveQuoteTargets(quoteLine, posterIdIndex, messageLineIndex)
                    .toList()
                QuoteReference(
                    text = quoteLine.trim(),
                    targetPostIds = targets
                )
            }
            map[post.id] = references.toMutableList()
        }
        return map
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
        val hostStart = schemeEnd + 3
        val slashIndex = url.indexOf('/', startIndex = hostStart)
        return if (slashIndex == -1) url else url.substring(0, slashIndex)
    }

    private fun buildPosterIdIndex(posts: List<Post>): Map<String, Set<String>> {
        return posts
            .mapNotNull { post: Post ->
                val id = post.posterId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to post.id
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { entry -> entry.value.toSet() }
    }

    private fun buildMessageLineIndex(posts: List<Post>): Map<String, Set<String>> {
        val index = mutableMapOf<String, MutableSet<String>>()
        posts.forEach { post: Post ->
            if (post.messageHtml.isBlank()) return@forEach
            decodeHtmlEntities(stripTags(post.messageHtml))
                .lines()
                .map { normalizeQuoteText(it) }
                .filter { it.isNotBlank() && !it.startsWith(">") && !it.startsWith("＞") }
                .forEach { line: String ->
                    index.getOrPut(line) { mutableSetOf() }.add(post.id)
                }
        }
        return index
    }

    private fun extractQuoteLines(messageHtml: String): List<String> {
        if (messageHtml.isBlank()) return emptyList()
        return decodeHtmlEntities(stripTags(messageHtml))
            .lines()
            .map { it.trimStart() }
            .filter { it.startsWith(">") || it.startsWith("＞") }
    }

    private fun resolveQuoteTargets(
        quoteLine: String,
        posterIdIndex: Map<String, Set<String>>,
        messageLineIndex: Map<String, Set<String>>
    ): Set<String> {
        val trimmed = quoteLine.trim()
        if (trimmed.isBlank()) return emptySet()
        val content = trimmed.trimStart { it == '>' || it == '＞' }.trim()
        if (content.isBlank()) return emptySet()
        val explicitNumber = noReferenceRegex.find(content)?.groupValues?.getOrNull(1)
            ?: leadingNumberRegex.find(content)?.groupValues?.getOrNull(1)
        if (explicitNumber != null) {
            return setOf(explicitNumber)
        }
        val idMatch = idReferenceRegex.find(content)?.value
        idMatch?.let { id ->
            posterIdIndex[id]?.let { return it }
        }
        val normalized = normalizeQuoteText(content)
        if (normalized.isBlank()) return emptySet()
        return messageLineIndex[normalized] ?: emptySet()
    }

    private fun normalizeQuoteText(value: String): String {
        return value.replace("\\s+".toRegex(), " ").trim()
    }
}
