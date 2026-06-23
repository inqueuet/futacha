package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val THREAD_POST_TEXT_CACHE_MAX_ENTRIES = 2048

internal fun messageHtmlToLines(html: String): List<String> {
    val normalized = replaceHtmlBreakTags(html)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded.lines()
}

internal fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}

internal data class ThreadPostTextData(
    val lines: List<String>,
    val plainText: String,
    val lowerText: String,
    val firstLine: String?
)

private data class ThreadPostTextCacheKey(
    val postId: String,
    val messageHtml: String
)

internal class ThreadPostTextCache(
    private val maxEntries: Int = THREAD_POST_TEXT_CACHE_MAX_ENTRIES
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<ThreadPostTextCacheKey, ThreadPostTextData>()

    suspend fun get(post: Post): ThreadPostTextData {
        val key = ThreadPostTextCacheKey(
            postId = post.id,
            messageHtml = post.messageHtml
        )
        mutex.withLock {
            entries[key]?.let { return it }
        }
        val value = buildThreadPostTextData(post.messageHtml)
        return mutex.withLock {
            entries[key]?.let { existing -> existing } ?: value.also {
                if (entries.size >= maxEntries) {
                    val iterator = entries.entries.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                entries[key] = it
            }
        }
    }
}

internal fun buildThreadPostTextData(messageHtml: String): ThreadPostTextData {
    val lines = messageHtmlToLines(messageHtml)
    val plainText = lines
        .map { it.trimEnd() }
        .joinToString("\n")
    return ThreadPostTextData(
        lines = lines,
        plainText = plainText,
        lowerText = plainText.lowercase(),
        firstLine = lines.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    )
}
