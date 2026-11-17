package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.lazy.LazyListState
import com.valoser.futacha.shared.model.Post

/** Which kind of related posts a bubble shows. */
internal enum class FutaberQuoteKind(val label: String) {
    /** The posts a quoted line points at. */
    Source("引用元"),

    /** The posts that quote the tapped post. */
    Replies("返信先"),

    /** The long-pressed post, the posts it quotes and the posts quoting it (the post detail extension). */
    Related("関連レス"),

    /** The posts whose header shows the same ID / IP (the post detail extension). */
    SameId("同じIDのレス"),
    SameIp("同じIPのレス"),

    /** The posts the operation menu's search word finds (M08; opened from L-B6). */
    Search("検索結果"),

    /** The posts with many replies (M11; opened from L-B6). */
    ManyReplies("返信が多いレス"),

    /** A post opened from the picture viewer's description (opened from L-B11). */
    FromViewer("ビューアのレス"),

    /** The whole conversation the long-pressed post is in: every post tied to it by quotes (F-B3 E11). */
    Conversation("会話")
}

/**
 * The darkness laid over a bubble that has [levelsAbove] bubbles over it (F01): the original keeps the lower bubbles in
 * sight, each one 20% darker per bubble over it (63-02: white reads #CCCCCC under one), while the thread's veil stays
 * one. 0 for the bubble on top.
 */
internal fun futaberBubbleVeilAlpha(levelsAbove: Int): Float {
    if (levelsAbove <= 0) return 0f
    var light = 1f
    repeat(levelsAbove) { light *= 1f - FUTABER_BUBBLE_VEIL_ALPHA }
    return 1f - light
}

/** One level of the veil: the thread under the first bubble, and each bubble under another one (20%). */
internal const val FUTABER_BUBBLE_VEIL_ALPHA = 0.2f

/** Root-coordinate bounds of the line or counter that was tapped (px). */
internal data class FutaberAnchor(val x: Float, val top: Float, val bottom: Float)

/**
 * One bubble, placed beside [anchor] and pointing at it. A bubble the operation menu opens (M08・M11) has no line to
 * point at: it stands at an [edge] of the thread instead (the anchor is then not read), and an empty one says
 * [FUTABER_NOTHING_FOUND].
 */
internal data class FutaberQuoteLayer(
    val kind: FutaberQuoteKind,
    val posts: List<Post>,
    val anchor: FutaberAnchor,
    // Each layer owns its viewport: a nested quote starts at its first result and Back restores
    // the parent bubble's position, rather than reusing the last visible bubble's LazyColumn.
    val listState: LazyListState = LazyListState(),
    val edge: FutaberBubbleEdge? = null
)

/**
 * Where a bubble of the operation menu stands: the search's found posts over the bottom bar, pointing down at it
 * (futaber's 40-03), 「返信が多いレスを表示」 under the title, pointing up (43-01).
 */
internal enum class FutaberBubbleEdge { Top, Bottom }

/** The bubble of the menu's search word (M08) or of 「返信が多いレスを表示」 (M11); empty when nothing was found. */
internal fun futaberMenuBubble(kind: FutaberQuoteKind, posts: List<Post>): FutaberQuoteLayer = FutaberQuoteLayer(
    kind = kind,
    posts = posts,
    anchor = FutaberAnchor(0f, 0f, 0f),
    edge = if (kind == FutaberQuoteKind.ManyReplies) FutaberBubbleEdge.Top else FutaberBubbleEdge.Bottom
)

/** The x of a menu bubble's pointer, as a fraction of the thread's width (futaber's point near the left, 40-03・43-01). */
internal const val FUTABER_MENU_BUBBLE_POINTER_X = 0.07f

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
        source.quoteReferences.flatMap { it.replyTargetPostIds }.distinct().forEach { targetId ->
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
