package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.normalizeCatalogSearchText
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText

/** A post replied to at least this many times counts as one with many replies. */
internal const val FUTABER_MANY_REPLIES_THRESHOLD = 3

/** A post with its number in the full thread (kept when other posts are filtered out). [depth] is the indent of the tree view. */
internal data class FutaberRow(val ordinal: Int, val post: Post, val depth: Int = 0)

/** "レスを抽出" (an extension, off by default): which posts the list is cut down to. */
internal enum class FutaberExtract(val id: String, val label: String) {
    SelfPosts("self", "自分の書き込み"),
    Saidane("saidane", "そうだねが多いレス"),
    Deleted("deleted", "削除されたレス"),
    Url("url", "URLを含むレス"),
    Image("image", "画像のあるレス")
}

/** A post with this many "そうだね" or more counts as one with many. */
internal const val FUTABER_MANY_SAIDANE_THRESHOLD = 3

/**
 * What narrows the thread list: a search word, "many replies only", and the extensions' own cuts
 * (an [extract] kind, the posts of one poster [sameId]).
 */
internal data class FutaberViewFilter(
    val query: String = "",
    val repliesOnly: Boolean = false,
    val extract: FutaberExtract? = null,
    val sameId: String = ""
) {
    val isActive: Boolean get() = query.isNotBlank() || repliesOnly || extract != null || sameId.isNotBlank()
}

private val FUTABER_URL_PATTERN = Regex("t?tps?://|https?://", RegexOption.IGNORE_CASE)

/**
 * Whether [post] belongs to the [kind] of posts "レスを抽出" lists. [hasUrl] is the already known answer to "does the text
 * hold a link" ([FutaberPostSearchEntry.hasUrl]); null works it out from the post.
 */
internal fun futaberMatchesExtract(
    post: Post,
    kind: FutaberExtract,
    selfIds: Set<String>,
    hasUrl: Boolean? = null
): Boolean = when (kind) {
    FutaberExtract.SelfPosts -> post.id in selfIds
    FutaberExtract.Saidane -> (futaberSaidaneCount(post.saidaneLabel) ?: 0) >= FUTABER_MANY_SAIDANE_THRESHOLD
    FutaberExtract.Deleted -> post.isDeleted || post.isIsolated
    FutaberExtract.Url -> hasUrl ?: FUTABER_URL_PATTERN.containsMatchIn(messageHtmlToPlainText(post.messageHtml))
    FutaberExtract.Image -> !post.imageUrl.isNullOrBlank() || !post.thumbnailUrl.isNullOrBlank()
}

/** Everything a person could search a post by: body text, subject, name, ID and number. */
internal fun futaberPostSearchText(post: Post): String = futaberPostSearchText(post, messageHtmlToPlainText(post.messageHtml))

/** [futaberPostSearchText] with the body text already taken out of the post. */
private fun futaberPostSearchText(post: Post, plainBody: String): String = buildString {
    append(plainBody).append('\n')
    post.subject?.let { append(it).append('\n') }
    post.author?.let { append(it).append('\n') }
    post.posterId?.let { append(it).append('\n') }
    append(post.id)
}

/** What one post's text yields for searching, worked out once: its search text folded for comparison, and whether it holds a link. */
internal class FutaberPostSearchEntry(val normalized: String, val hasUrl: Boolean)

internal fun futaberPostSearchEntry(post: Post): FutaberPostSearchEntry {
    val plainBody = messageHtmlToPlainText(post.messageHtml)
    return FutaberPostSearchEntry(
        normalized = normalizeCatalogSearchText(futaberPostSearchText(post, plainBody)),
        hasUrl = FUTABER_URL_PATTERN.containsMatchIn(plainBody)
    )
}

/** The search entries of [posts], one per post in the same order. */
internal class FutaberPostTexts(val posts: List<Post>, val entries: List<FutaberPostSearchEntry>)

private const val FUTABER_POST_TEXTS_CANCEL_CHECK_INTERVAL = 32

/**
 * The entries of [posts], taking those of [previous] for the posts that did not change (a thread that only grew, or
 * a reload that changed a few posts, works out the new ones only). [ensureActive] is called now and then so a long run
 * can be cancelled.
 */
internal fun futaberBuildPostTexts(
    previous: FutaberPostTexts?,
    posts: List<Post>,
    ensureActive: () -> Unit = {}
): FutaberPostTexts {
    val entries = ArrayList<FutaberPostSearchEntry>(posts.size)
    posts.forEachIndexed { index, post ->
        if (index % FUTABER_POST_TEXTS_CANCEL_CHECK_INTERVAL == 0) ensureActive()
        val before = previous?.posts?.getOrNull(index)
        entries += if (previous != null && before != null && (before === post || before == post)) {
            previous.entries[index]
        } else {
            futaberPostSearchEntry(post)
        }
    }
    return FutaberPostTexts(posts, entries)
}

/**
 * The rows to list: posts hidden by NG are dropped first, then the filter applies. Rows keep the
 * number they have in the whole thread, so a filtered list still shows 12, 40, 41. [texts] (of these [posts]) saves
 * working out each post's text again; without it, or with the texts of other posts, they are worked out here.
 */
internal fun futaberVisibleRows(
    posts: List<Post>,
    filter: FutaberViewFilter,
    replyIndex: Map<String, List<Post>>,
    hidden: Set<String> = emptySet(),
    manyRepliesThreshold: Int = FUTABER_MANY_REPLIES_THRESHOLD,
    selfIds: Set<String> = emptySet(),
    texts: FutaberPostTexts? = null
): List<FutaberRow> {
    val needle = normalizeCatalogSearchText(filter.query)
    val entries = texts?.takeIf { it.posts === posts }?.entries
    return posts.mapIndexedNotNull { ordinal, post ->
        when {
            post.id in hidden -> null
            filter.repliesOnly && (replyIndex[post.id]?.size ?: 0) < manyRepliesThreshold -> null
            filter.extract != null && !futaberMatchesExtract(post, filter.extract, selfIds, entries?.get(ordinal)?.hasUrl) -> null
            filter.sameId.isNotBlank() && post.posterId != filter.sameId -> null
            needle.isNotEmpty() &&
                !(entries?.get(ordinal)?.normalized ?: normalizeCatalogSearchText(futaberPostSearchText(post))).contains(needle) -> null
            else -> FutaberRow(ordinal, post)
        }
    }
}

/**
 * Position in [rows] of the row for post [ordinal], else of the first row after it, for scrolling to a post number.
 * (The tree view lists the rows out of order, so an exact match is looked for first.)
 */
internal fun futaberRowPositionAtOrAfter(rows: List<FutaberRow>, ordinal: Int): Int? =
    rows.indexOfFirst { it.ordinal == ordinal }.takeIf { it >= 0 }
        ?: rows.indexOfFirst { it.ordinal >= ordinal }.takeIf { it >= 0 }

/** Position in [rows] of the row that shows post [postId]; null when it is not listed (hidden, or filtered out). */
internal fun futaberRowPositionOfPost(rows: List<FutaberRow>, postId: String?): Int? =
    if (postId == null) null else rows.indexOfFirst { it.post.id == postId }.takeIf { it >= 0 }

/** The number each post has in the whole thread, by post id; a repeated id keeps its first number (as `indexOfFirst` gave). */
internal fun futaberOrdinalsById(posts: List<Post>): Map<String, Int> {
    val ordinals = HashMap<String, Int>(posts.size * 2)
    posts.forEachIndexed { index, post -> if (!ordinals.containsKey(post.id)) ordinals[post.id] = index }
    return ordinals
}

/** Where reading stood: the post's own number (kept when NG hides posts), the pixel offset in its row, its id and the thread size. */
internal data class FutaberScrollPosition(val ordinal: Int, val offset: Int, val postId: String, val total: Int)

/**
 * The reading position to keep for the list scrolled to row [index]; null when none should be kept: a search or
 * "many replies" list shows other rows than the thread, and an empty thread has no position.
 */
internal fun futaberScrollPositionToSave(
    rows: List<FutaberRow>,
    index: Int,
    offset: Int,
    total: Int,
    narrowed: Boolean
): FutaberScrollPosition? {
    if (total <= 0 || narrowed) return null
    val row = rows.getOrNull(index) ?: return null
    return FutaberScrollPosition(row.ordinal, offset, row.post.id, total)
}

/**
 * The newest reading position that has not been written yet. Scrolling records it at once; the debounced save and
 * leaving the screen take it, so each position is written at most once and the last half second is not lost.
 */
internal class FutaberPendingScrollSave {
    private var pending: FutaberScrollPosition? = null

    fun record(position: FutaberScrollPosition?) {
        pending = position
    }

    fun take(): FutaberScrollPosition? {
        val position = pending
        pending = null
        return position
    }
}

/** The most a tree level indents a row (deeper levels stay at this indent so a long chain keeps its width). */
internal const val FUTABER_TREE_MAX_INDENT_LEVELS = 6

/**
 * The tree view of the other modes: [rows] re-ordered so replies follow the post they quote, each with
 * its depth. [tree] is `buildThreadTreeNodes` of the whole thread; posts it lacks keep their place at the end.
 */
internal fun futaberApplyTree(rows: List<FutaberRow>, tree: List<com.valoser.futacha.shared.ui.board.ThreadTreeNode>): List<FutaberRow> {
    if (tree.isEmpty() || rows.isEmpty()) return rows
    val slot = HashMap<String, Pair<Int, Int>>(tree.size)
    tree.forEachIndexed { index, node -> slot[node.post.id] = index to node.depth }
    return rows
        .map { row -> slot[row.post.id]?.let { (_, depth) -> row.copy(depth = depth) } ?: row }
        .sortedBy { row -> slot[row.post.id]?.first ?: Int.MAX_VALUE }
}
