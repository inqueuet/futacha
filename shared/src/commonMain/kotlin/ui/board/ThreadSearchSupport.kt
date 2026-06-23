package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private const val THREAD_SEARCH_MAX_HIGHLIGHT_RANGES_PER_POST = 64
private const val THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL = 32

internal fun nextThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex + 1 >= matchCount) 0 else currentIndex + 1
}

internal fun previousThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex - 1 < 0) matchCount - 1 else currentIndex - 1
}

internal fun normalizeThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex in 0 until matchCount) currentIndex else 0
}

internal data class ThreadSearchNavigationState(
    val nextIndex: Int,
    val targetPostIndex: Int?,
    val shouldScroll: Boolean
)

internal fun focusThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    val target = matches.getOrNull(currentIndex)
    return ThreadSearchNavigationState(
        nextIndex = normalizeThreadSearchResultIndex(currentIndex, matches.size),
        targetPostIndex = target?.postIndex,
        shouldScroll = target != null
    )
}

internal fun moveToNextThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    if (matches.isEmpty()) {
        return ThreadSearchNavigationState(
            nextIndex = 0,
            targetPostIndex = null,
            shouldScroll = false
        )
    }
    val nextIndex = nextThreadSearchResultIndex(currentIndex, matches.size)
    return ThreadSearchNavigationState(
        nextIndex = nextIndex,
        targetPostIndex = matches.getOrNull(nextIndex)?.postIndex,
        shouldScroll = true
    )
}

internal fun moveToPreviousThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    if (matches.isEmpty()) {
        return ThreadSearchNavigationState(
            nextIndex = 0,
            targetPostIndex = null,
            shouldScroll = false
        )
    }
    val previousIndex = previousThreadSearchResultIndex(currentIndex, matches.size)
    return ThreadSearchNavigationState(
        nextIndex = previousIndex,
        targetPostIndex = matches.getOrNull(previousIndex)?.postIndex,
        shouldScroll = true
    )
}

internal data class ThreadSearchMatch(
    val postId: String,
    val postIndex: Int,
    val highlightRanges: List<IntRange>
)

internal data class ThreadSearchTarget(
    val postId: String,
    val postIndex: Int,
    val searchableText: String,
    val messagePlainText: String
)

internal suspend fun buildThreadSearchTargets(
    posts: List<Post>,
    textCache: ThreadPostTextCache? = null
): List<ThreadSearchTarget> {
    if (posts.isEmpty()) return emptyList()
    val targets = ArrayList<ThreadSearchTarget>(posts.size)
    posts.forEachIndexed { index, post ->
        if (index % THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        val messagePlainText = textCache?.get(post)?.plainText
            ?: messageHtmlToPlainText(post.messageHtml)
        targets += ThreadSearchTarget(
            postId = post.id,
            postIndex = index,
            searchableText = buildSearchTextForPost(post, messagePlainText),
            messagePlainText = messagePlainText
        )
    }
    return targets
}

internal suspend fun buildThreadSearchMatches(
    searchTargets: List<ThreadSearchTarget>,
    query: String
): List<ThreadSearchMatch> {
    if (searchTargets.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    val matches = ArrayList<ThreadSearchMatch>()
    searchTargets.forEachIndexed { index, target ->
        if (index % THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        val haystack = target.searchableText
        if (haystack.contains(normalizedQuery)) {
            val ranges = computeHighlightRanges(target.messagePlainText, normalizedQuery)
            matches += ThreadSearchMatch(target.postId, target.postIndex, ranges)
        }
    }
    return matches
}

internal fun buildSearchTextForPost(post: Post, messagePlainText: String): String {
    val builder = StringBuilder()
    post.subject?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.author?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.posterId?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    builder.appendLine(post.id)
    builder.append(messagePlainText)
    return builder.toString().lowercase()
}

internal fun computeHighlightRanges(text: String, normalizedQuery: String): List<IntRange> {
    if (normalizedQuery.isEmpty()) return emptyList()
    val normalizedText = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    var startIndex = normalizedText.indexOf(normalizedQuery)
    while (startIndex >= 0 && ranges.size < THREAD_SEARCH_MAX_HIGHLIGHT_RANGES_PER_POST) {
        val endIndex = startIndex + normalizedQuery.length - 1
        ranges.add(startIndex..endIndex)
        startIndex = normalizedText.indexOf(normalizedQuery, startIndex + normalizedQuery.length)
    }
    return ranges
}
