package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.createHtmlParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A thread has a thousand posts or so; a file that claims far more is not one this reads. */
private const val MAX_POSTS = 10_000

/** What a page written here says about the thread as a whole (the posts are read apart). */
internal data class FutaberMhtPageInfo(
    val expiresAtLabel: String? = null,
    val deletedNotice: String? = null,
    val isTruncated: Boolean = false,
    val truncationReason: String? = null
)

/**
 * The page inside an MHT file written here: one `<article>` per post, with the post's facts as
 * attributes, so that reading it back gives the same posts. Browsers show it as a plain page.
 * The pictures are the file's other parts, found by the address in `data-thumb` / `data-full`.
 */
internal object FutaberMhtThreadHtml {
    private const val MARKER_ATTR = "data-futaber-mht"
    private const val ARTICLE_OPEN = "<article class=\"res\""
    private const val ARTICLE_CLOSE = "</article>"
    private const val MESSAGE_OPEN = "<!--msg-->"
    private const val MESSAGE_CLOSE = "<!--/msg-->"

    private val quotesJson = Json { ignoreUnknownKeys = true }
    private val quotesSerializer = ListSerializer(QuoteReference.serializer())

    fun build(
        title: String,
        threadUrl: String,
        boardName: String,
        posts: List<Post>,
        info: FutaberMhtPageInfo = FutaberMhtPageInfo()
    ): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">\n")
        append("<title>").append(escape(title)).append("</title>\n")
        append("<style>body{font-family:sans-serif;max-width:46em;margin:0 auto;padding:1em;color:#222;background:#fbfaf7}")
        append("article{border-bottom:1px solid #ddd;padding:.6em 0}header{color:#666;font-size:.85em}")
        append(".msg{margin:.4em 0;line-height:1.55}img{max-width:100%;border-radius:8px}</style>\n")
        append("</head><body ").append(MARKER_ATTR).append("=\"1\"")
        info.expiresAtLabel?.let { attr("data-expires", it) }
        info.deletedNotice?.let { attr("data-deleted-notice", it) }
        if (info.isTruncated) attr("data-truncated", "1")
        info.truncationReason?.let { attr("data-truncation-reason", it) }
        append(">\n")
        append("<h1>").append(escape(title)).append("</h1>\n")
        append("<p><a href=\"").append(escape(threadUrl)).append("\">").append(escape(boardName)).append("</a></p>\n")
        posts.forEachIndexed { index, post ->
            append(ARTICLE_OPEN)
            attr("data-no", post.id)
            attr("data-order", (post.order ?: index).toString())
            attr("data-ts", post.timestamp)
            post.posterId?.let { attr("data-pid", it) }
            post.author?.let { attr("data-author", it) }
            post.subject?.let { attr("data-subject", it) }
            post.mail?.let { attr("data-mail", it) }
            post.saidaneLabel?.let { attr("data-saidane", it) }
            if (post.isDeleted) attr("data-deleted", "1")
            if (post.isIsolated) attr("data-isolated", "1")
            post.thumbnailUrl?.let { attr("data-thumb", it) }
            post.imageUrl?.let { attr("data-full", it) }
            post.thumbnailWidth?.let { attr("data-w", it.toString()) }
            post.thumbnailHeight?.let { attr("data-h", it.toString()) }
            post.imageFileSizeBytes?.let { attr("data-size", it.toString()) }
            if (post.referencedCount > 0) attr("data-refs", post.referencedCount.toString())
            if (post.quoteReferences.isNotEmpty()) attr("data-quotes", quotesJson.encodeToString(quotesSerializer, post.quoteReferences))
            append(">\n<header>").append(index).append(' ').append(escape(post.timestamp))
            post.posterId?.let { append(' ').append(escape(it)) }
            append(" No.").append(escape(post.id)).append("</header>\n")
            post.subject?.takeIf { it.isNotBlank() }?.let { append("<b>").append(escape(it)).append("</b> ") }
            post.author?.takeIf { it.isNotBlank() }?.let { append("<span>").append(escape(it)).append("</span>") }
            // The message is HTML already; it is kept between markers so reading takes it back exactly.
            append(MESSAGE_OPEN).append("<div class=\"msg\">").append(post.messageHtml).append("</div>").append(MESSAGE_CLOSE).append("\n")
            val thumb = post.thumbnailUrl
            val full = post.imageUrl
            if (thumb != null || full != null) {
                append("<figure>")
                if (full != null) append("<a href=\"").append(escape(full)).append("\">")
                append("<img src=\"").append(escape(thumb ?: full.orEmpty())).append("\" alt=\"\">")
                if (full != null) append("</a>")
                append("</figure>\n")
            }
            append(ARTICLE_CLOSE).append("\n")
        }
        append("</body></html>\n")
    }

    private fun StringBuilder.attr(name: String, value: String) {
        append(' ').append(name).append("=\"").append(escape(value)).append('"')
    }

    fun escape(text: String): String = buildString(text.length + 8) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private fun unescape(text: String): String {
        if (!text.contains('&')) return text
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
    }

    /** True for a page written by [build]. */
    fun isOurs(html: String): Boolean = html.contains(MARKER_ATTR)

    private val attributeRegex = Regex("([a-z-]+)=\"([^\"]*)\"")

    private fun attributesOf(text: String): Map<String, String> =
        attributeRegex.findAll(text).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    fun title(html: String): String? {
        val open = html.indexOf("<title>")
        if (open < 0) return null
        val from = open + "<title>".length
        val close = html.indexOf("</title>", from)
        if (close < 0) return null
        return unescape(html.substring(from, close)).trim().takeIf { it.isNotEmpty() }
    }

    /** What the page says about the thread as a whole (a file from before these were kept has none of it). */
    fun pageInfo(html: String): FutaberMhtPageInfo {
        val open = html.indexOf("<body $MARKER_ATTR")
        if (open < 0) return FutaberMhtPageInfo()
        val close = html.indexOf('>', open)
        if (close < 0) return FutaberMhtPageInfo()
        val attributes = attributesOf(html.substring(open, close))
        return FutaberMhtPageInfo(
            expiresAtLabel = attributes["data-expires"],
            deletedNotice = attributes["data-deleted-notice"],
            isTruncated = attributes["data-truncated"] == "1",
            truncationReason = attributes["data-truncation-reason"]
        )
    }

    /**
     * The posts of a page written by [build], in order. The page is walked once by position (no pattern that can
     * run through the whole page from every start), and more than a thread can have is refused.
     */
    fun parse(html: String): List<Post> {
        val posts = ArrayList<Post>()
        var from = 0
        while (true) {
            val open = html.indexOf(ARTICLE_OPEN, from)
            if (open < 0) break
            val attributesStart = open + ARTICLE_OPEN.length
            val tagEnd = html.indexOf('>', attributesStart)
            if (tagEnd < 0) break
            val close = html.indexOf(ARTICLE_CLOSE, tagEnd)
            if (close < 0) break
            from = close + ARTICLE_CLOSE.length
            val attributes = attributesOf(html.substring(attributesStart, tagEnd))
            val id = attributes["data-no"] ?: continue
            if (posts.size >= MAX_POSTS) throw FutaberMhtFormatException("レスが多すぎて読めません")
            val messageStart = html.indexOf(MESSAGE_OPEN, tagEnd).takeIf { it in 0 until close }
            val messageEnd = messageStart?.let { html.indexOf(MESSAGE_CLOSE, it + MESSAGE_OPEN.length).takeIf { end -> end in 0 until close } }
            val messageHtml = if (messageStart != null && messageEnd != null) {
                html.substring(messageStart + MESSAGE_OPEN.length, messageEnd).removePrefix("<div class=\"msg\">").removeSuffix("</div>")
            } else ""
            posts += Post(
                id = id,
                order = attributes["data-order"]?.toIntOrNull(),
                author = attributes["data-author"],
                subject = attributes["data-subject"],
                timestamp = attributes["data-ts"].orEmpty(),
                posterId = attributes["data-pid"],
                messageHtml = messageHtml,
                imageUrl = attributes["data-full"],
                thumbnailUrl = attributes["data-thumb"],
                saidaneLabel = attributes["data-saidane"],
                isDeleted = attributes["data-deleted"] == "1",
                isIsolated = attributes["data-isolated"] == "1",
                referencedCount = attributes["data-refs"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                quoteReferences = attributes["data-quotes"]
                    ?.let { runCatching { quotesJson.decodeFromString(quotesSerializer, it) }.getOrNull() }
                    .orEmpty(),
                mail = attributes["data-mail"],
                thumbnailWidth = attributes["data-w"]?.toIntOrNull(),
                thumbnailHeight = attributes["data-h"]?.toIntOrNull(),
                imageFileSizeBytes = attributes["data-size"]?.toLongOrNull()
            )
        }
        return posts
    }
}

/** What reading an MHT file gives: the thread, and where each picture of it is (by its original address). */
internal class FutaberMhtThread(
    val title: String,
    val boardName: String?,
    val boardKey: String?,
    val boardUrl: String?,
    val threadId: String,
    val threadUrl: String?,
    val page: ThreadPage,
    /** Address on the web → the picture part of the file. */
    val pictures: Map<String, FutaberMhtPart>,
    /** When the file was saved, as the file says it (its own header, or its Date header); null when it does not. */
    val savedAtMillis: Long? = null
) {
    /**
     * The same thread without the picture parts. Those parts point into the whole file's bytes, so a thread that
     * is kept while it is shown must not carry them: the pictures are written out when the file is opened.
     */
    fun withoutPictures(page: ThreadPage = this.page): FutaberMhtThread = FutaberMhtThread(
        title = title, boardName = boardName, boardKey = boardKey, boardUrl = boardUrl, threadId = threadId,
        threadUrl = threadUrl, page = page, pictures = emptyMap(), savedAtMillis = savedAtMillis
    )
}

internal object FutaberMhtThreadReader {
    /**
     * Reads the thread of a parsed file. A file written here is read exactly; a file from another tool is
     * read as a Futaba thread page (the usual MHT of a thread), which is what the board itself serves.
     */
    suspend fun read(document: FutaberMhtDocument): FutaberMhtThread {
        val htmlPart = document.htmlPart ?: throw FutaberMhtFormatException("ページ（HTML）が入っていません")
        val html = htmlPart.text()
        val threadUrl = document.headers["snapshot-content-location"]?.trim() ?: htmlPart.location
        val pictures = LinkedHashMap<String, FutaberMhtPart>()
        document.parts.filter { it.isImage }.forEach { part -> part.location?.let { pictures[it] = part } }

        val ours = FutaberMhtThreadHtml.isOurs(html)
        val posts: List<Post>
        val threadId: String
        val title: String
        val pageTemplate: ThreadPage
        if (ours) {
            posts = FutaberMhtThreadHtml.parse(html)
            threadId = document.custom("thread") ?: posts.firstOrNull()?.id.orEmpty()
            title = document.subject ?: FutaberMhtThreadHtml.title(html) ?: "(無題)"
            val info = FutaberMhtThreadHtml.pageInfo(html)
            pageTemplate = ThreadPage(
                threadId = threadId, boardTitle = title, expiresAtLabel = info.expiresAtLabel, deletedNotice = info.deletedNotice,
                posts = posts, isTruncated = info.isTruncated, truncationReason = info.truncationReason
            )
        } else {
            val parsed = createHtmlParser().parseThread(html, threadUrl)
            if (parsed.posts.size > MAX_POSTS) throw FutaberMhtFormatException("レスが多すぎて読めません")
            posts = parsed.posts.map { post ->
                post.copy(
                    imageUrl = resolvePicture(post.imageUrl, pictures, threadUrl),
                    thumbnailUrl = resolvePicture(post.thumbnailUrl, pictures, threadUrl)
                )
            }
            threadId = parsed.threadId.takeIf { it.isNotBlank() } ?: threadIdFromUrl(threadUrl) ?: posts.firstOrNull()?.id.orEmpty()
            title = document.subject ?: FutaberMhtThreadHtml.title(html) ?: "(無題)"
            pageTemplate = parsed.copy(threadId = threadId, boardTitle = title, posts = posts)
        }
        if (posts.isEmpty()) throw FutaberMhtFormatException("スレッドのレスが見つかりません")
        // The thread's number names files and tabs: it is digits and nothing else, and it is the one the address says.
        if (!isThreadNumber(threadId)) throw FutaberMhtFormatException("スレッド番号を読み取れません")
        val numberInUrl = threadIdFromUrl(threadUrl)
        if (numberInUrl != null && numberInUrl != threadId) throw FutaberMhtFormatException("スレッド番号がURLと一致しません")
        return FutaberMhtThread(
            title = title,
            boardName = document.custom("board-name"),
            boardKey = document.custom("board"),
            boardUrl = document.custom("board-url") ?: threadUrl?.let(::boardUrlFromThreadUrl),
            threadId = threadId,
            threadUrl = threadUrl,
            page = pageTemplate,
            pictures = pictures,
            savedAtMillis = document.custom("saved")?.toLongOrNull() ?: parseRfc1123Millis(document.headers["date"])
        )
    }

    /** Digits only (a thread number is the post number of its first post). */
    internal fun isThreadNumber(value: String): Boolean = value.isNotEmpty() && value.length <= 20 && value.all { it in '0'..'9' }

    /** The picture part for an address as it is written in the page: absolute, or relative to the thread's address. */
    internal fun resolvePicture(url: String?, pictures: Map<String, FutaberMhtPart>, base: String?): String? {
        if (url == null) return null
        if (pictures.containsKey(url)) return url
        val resolved = base?.let { resolveAgainst(it, url) }
        return resolved?.takeIf { pictures.containsKey(it) } ?: url
    }

    private fun resolveAgainst(base: String, relative: String): String {
        if (relative.contains("://")) return relative
        val scheme = base.substringBefore("://", "")
        val rest = base.substringAfter("://", base)
        val host = rest.substringBefore('/')
        if (relative.startsWith("//")) return "$scheme:$relative"
        if (relative.startsWith("/")) return "$scheme://$host$relative"
        val dir = rest.substringAfter('/', "").substringBeforeLast('/', "")
        val joined = if (dir.isEmpty()) relative else "$dir/$relative"
        val parts = ArrayList<String>()
        joined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return "$scheme://$host/" + parts.joinToString("/")
    }

    internal fun threadIdFromUrl(url: String?): String? =
        url?.let { Regex("/res/(\\d+)\\.html?").find(it)?.groupValues?.get(1) }

    /** `https://may.2chan.net/27/res/324989.htm` → `https://may.2chan.net/27/`. */
    internal fun boardUrlFromThreadUrl(url: String): String? {
        val at = url.indexOf("/res/")
        return if (at > 0) url.substring(0, at + 1) else null
    }
}
