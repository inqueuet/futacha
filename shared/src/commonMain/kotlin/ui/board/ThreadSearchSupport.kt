package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

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

internal fun buildThreadSearchTargets(posts: List<Post>): List<ThreadSearchTarget> {
    if (posts.isEmpty()) return emptyList()
    return posts.mapIndexed { index, post ->
        val messagePlainText = messageHtmlToPlainText(post.messageHtml)
        ThreadSearchTarget(
            postId = post.id,
            postIndex = index,
            searchableText = buildSearchTextForPost(post, messagePlainText),
            messagePlainText = messagePlainText
        )
    }
}

internal fun buildThreadSearchMatches(
    searchTargets: List<ThreadSearchTarget>,
    query: String
): List<ThreadSearchMatch> {
    if (searchTargets.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    return searchTargets.mapNotNull { target ->
        val haystack = target.searchableText
        if (haystack.contains(normalizedQuery)) {
            val ranges = computeHighlightRanges(target.messagePlainText, normalizedQuery)
            ThreadSearchMatch(target.postId, target.postIndex, ranges)
        } else {
            null
        }
    }
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
    while (startIndex >= 0) {
        val endIndex = startIndex + normalizedQuery.length - 1
        ranges.add(startIndex..endIndex)
        startIndex = normalizedText.indexOf(normalizedQuery, startIndex + normalizedQuery.length)
    }
    return ranges
}
