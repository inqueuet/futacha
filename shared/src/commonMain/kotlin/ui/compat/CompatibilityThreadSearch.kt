package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText

internal data class CompatSearchTextRange(
    val start: Int,
    val endExclusive: Int
)

internal data class CompatThreadSearchHit(
    val postIndex: Int,
    val textRanges: List<CompatSearchTextRange>
)

private const val COMPAT_THREAD_SEARCH_MAX_RANGES_PER_POST = 64

/**
 * Mirrors the target's post-level search contract. Text ranges may overlap because
 * Android-PullToRefresh's companion utility advances one UTF-16 position per match.
 * A mail-only match still contributes one post hit but has no body highlight range.
 */
internal fun findCompatThreadSearchHits(
    posts: List<CompatPostSnapshot>,
    query: String
): List<CompatThreadSearchHit> {
    if (query.isEmpty()) return emptyList()
    return posts.mapIndexedNotNull { index, post ->
        val ranges = findCompatOverlappingRanges(post.messageHtml.toCompatPlainText(), query)
        val mailMatches = post.mail.orEmpty().indexOf(query) >= 0
        if (ranges.isEmpty() && !mailMatches) null else CompatThreadSearchHit(index, ranges)
    }
}

internal fun findCompatOverlappingRanges(
    text: String,
    query: String
): List<CompatSearchTextRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val ranges = mutableListOf<CompatSearchTextRange>()
    var fromIndex = 0
    while (fromIndex < text.length && ranges.size < COMPAT_THREAD_SEARCH_MAX_RANGES_PER_POST) {
        val start = text.indexOf(query, startIndex = fromIndex)
        if (start < 0) break
        ranges += CompatSearchTextRange(start, start + query.length)
        fromIndex = start + 1
    }
    return ranges
}
