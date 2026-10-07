package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post

/** Which kind of related posts a bubble shows. */
internal enum class FutaberQuoteKind(val label: String) {
    /** The posts a quoted line points at. */
    Source("引用元"),

    /** The posts that quote the tapped post. */
    Replies("返信先")
}

/** Root-coordinate bounds of the line or counter that was tapped (px). */
internal data class FutaberAnchor(val x: Float, val top: Float, val bottom: Float)

/** One bubble, placed beside [anchor] and pointing at it. */
internal data class FutaberQuoteLayer(
    val kind: FutaberQuoteKind,
    val posts: List<Post>,
    val anchor: FutaberAnchor
)

/** Where a bubble of [cardHeight] goes relative to its anchor, and where its pointer sits. */
internal data class FutaberBubblePlacement(val top: Float, val pointerOnTop: Boolean)

/**
 * Above the anchor when it fits (as the original app puts it), else below, else wherever leaves
 * the most room inside [minTop]..[maxBottom]. [gap] leaves space for the pointer.
 */
internal fun futaberBubblePlacement(
    anchor: FutaberAnchor,
    cardHeight: Float,
    minTop: Float,
    maxBottom: Float,
    gap: Float
): FutaberBubblePlacement {
    val above = anchor.top - gap - cardHeight
    if (above >= minTop) return FutaberBubblePlacement(above, pointerOnTop = false)
    val below = anchor.bottom + gap
    if (below + cardHeight <= maxBottom) return FutaberBubblePlacement(below, pointerOnTop = true)
    val roomBelow = maxBottom - below
    val roomAbove = anchor.top - gap - minTop
    return if (roomBelow >= roomAbove) {
        FutaberBubblePlacement(below.coerceAtMost((maxBottom - cardHeight).coerceAtLeast(minTop)), pointerOnTop = true)
    } else {
        FutaberBubblePlacement(minTop, pointerOnTop = false)
    }
}

/** A quoted body line, trimmed the way the parser stored it in `QuoteReference.text`. */
private fun normalizeQuoteLine(line: String): String = line.trim()

/**
 * Posts the quoted [line] of [post] refers to: the targets of the reference whose text is
 * this line (a reference may span several lines when the quote copied a whole block).
 */
internal fun futaberQuoteTargetIds(post: Post, line: String): List<String> {
    val wanted = normalizeQuoteLine(line)
    if (wanted.isEmpty()) return emptyList()
    return post.quoteReferences
        .filter { reference ->
            normalizeQuoteLine(reference.text) == wanted ||
                reference.text.lineSequence().any { normalizeQuoteLine(it) == wanted }
        }
        .flatMap { it.targetPostIds }
        .distinct()
}

/** Target id -> the posts quoting it, in display order and without repeats. */
internal fun futaberReplyIndex(posts: List<Post>): Map<String, List<Post>> {
    val index = LinkedHashMap<String, MutableList<Post>>()
    posts.forEach { source ->
        source.quoteReferences.flatMap { it.targetPostIds }.distinct().forEach { targetId ->
            if (targetId != source.id) index.getOrPut(targetId) { mutableListOf() }.add(source)
        }
    }
    return index
}

/** The posts named by [ids], in thread order; ids that are not in the thread are skipped. */
internal fun futaberPostsById(posts: List<Post>, ids: Collection<String>): List<Post> {
    if (ids.isEmpty()) return emptyList()
    val wanted = ids.toSet()
    return posts.filter { it.id in wanted }.distinctBy { it.id }
}

/** Number of replies shown next to a post: what the thread really holds, not the parser's guess. */
internal fun futaberReplyCount(post: Post, replyIndex: Map<String, List<Post>>): Int =
    replyIndex[post.id]?.size ?: 0
