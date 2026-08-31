package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val THREAD_POST_TEXT_CACHE_MAX_ENTRIES = 2048
private const val THREAD_POST_TEXT_CACHE_MAX_BYTES = 8 * 1024 * 1024

internal fun messageHtmlToLines(html: String): List<String> {
    val normalized = replaceHtmlBreakTags(html)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded.lines()
}

internal fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .joinToString("\n") { it.trimEnd() }
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
    private val maxEntries: Int = THREAD_POST_TEXT_CACHE_MAX_ENTRIES,
    private val maxBytes: Int = THREAD_POST_TEXT_CACHE_MAX_BYTES
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<ThreadPostTextCacheKey, ThreadPostTextData>()
    private val entrySizes = mutableMapOf<ThreadPostTextCacheKey, Int>()
    private var totalBytes = 0L

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
                val entryBytes = estimateThreadPostTextCacheBytes(key, value)
                while (entries.isNotEmpty() && (entries.size >= maxEntries || totalBytes + entryBytes > maxBytes)) {
                    val iterator = entries.entries.iterator()
                    if (iterator.hasNext()) {
                        val removedKey = iterator.next().key
                        iterator.remove()
                        totalBytes = (totalBytes - (entrySizes.remove(removedKey) ?: 0)).coerceAtLeast(0L)
                    }
                }
                // Oversized posts remain usable but are not retained as four
                // separate String/List representations in the thread cache.
                if (entryBytes <= maxBytes && maxEntries > 0) {
                    entries[key] = it
                    entrySizes[key] = entryBytes
                    totalBytes += entryBytes
                }
            }
        }
    }
}

private fun estimateThreadPostTextCacheBytes(
    key: ThreadPostTextCacheKey,
    value: ThreadPostTextData
): Int {
    val chars = key.postId.length.toLong() + key.messageHtml.length +
        value.plainText.length + value.lowerText.length +
        value.lines.sumOf(String::length) + (value.firstLine?.length ?: 0)
    return (chars * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun buildThreadPostTextData(messageHtml: String): ThreadPostTextData {
    val lines = messageHtmlToLines(messageHtml)
    val plainText = lines
        .joinToString("\n") { it.trimEnd() }
    return ThreadPostTextData(
        lines = lines,
        plainText = plainText,
        lowerText = plainText.lowercase(),
        firstLine = lines.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    )
}
