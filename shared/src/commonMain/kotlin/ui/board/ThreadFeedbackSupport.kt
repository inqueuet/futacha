package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.parseCompatThreadStatusFlags
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadStatus

internal const val THREAD_OPEN_SUMMARY_KEY = "compat.control.controlThreadOpenSummary"

/** Board-local opt-in, shared by the modes. */
internal fun viewedThreadFirstKey(boardUrl: String): String =
    "compat.catalog.viewedFirst." + compatBoardKey(canonicalizeBoardUrl(boardUrl) ?: boardUrl)

/** The chosen thread must already have survived search, NG and the display limit. */
internal fun catalogWithViewedThreadFirst(items: List<CatalogItem>, threadId: String?): List<CatalogItem> {
    if (threadId == null) return items
    val index = items.indexOfFirst { it.id == threadId }
    if (index <= 0) return items
    return buildList(items.size) {
        add(items[index])
        items.forEachIndexed { position, item -> if (position != index) add(item) }
    }
}

/** Only call after a successful live response (or a confirmed 404/410 for [gone]). */
internal fun confirmedThreadStatus(page: ThreadPage?, gone: Boolean = false): ThreadStatus? {
    if (page == null) return if (gone) ThreadStatus(isDead = true) else null
    val flags = parseCompatThreadStatusFlags(page.deletedNotice)
    return ThreadStatus(gone, flags.isIsolated, flags.isDeleted, flags.isAdminDeleted)
}

internal fun CompatTab.confirmedStatus(): ThreadStatus =
    ThreadStatus(isDead, isIsolated, isDeleted, isExploded)

internal fun threadStatusText(status: ThreadStatus?): String = buildList {
    if (status?.isIsolated == true) add("隔離")
    if (status?.isAdminDeleted == true) add("管理者削除")
    else if (status?.isDeleted == true) add("削除")
    if (status?.isDead == true) add("落ち")
}.joinToString("・")

/** Compact thumbnail tabs have a fixed slot; the list and accessibility retain every confirmed flag. */
internal fun threadStatusBadgeText(status: ThreadStatus?): String = when {
    status?.isAdminDeleted == true -> "管削"
    status?.isDeleted == true -> "削除"
    status?.isIsolated == true -> "隔離"
    status?.isDead == true -> "落ち"
    else -> ""
}

/** Only a foreground view establishes this baseline; network metadata is not a reading record. */
internal fun knownViewedPostCount(entry: com.valoser.futacha.shared.model.ThreadHistoryEntry?): Int? = entry
    ?.lastViewedSnapshot?.postCount?.takeIf { it > 0 }

/** A stable post boundary survives older posts being removed from a later response. */
internal fun threadPostsAfterBaseline(posts: List<Post>, count: Int, lastPostId: String?): List<Post> {
    val numericBoundary = lastPostId?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.trimStart('0').orEmpty()
    if (numericBoundary.isNotEmpty()) return posts.filter { post ->
        val id = post.id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.trimStart('0') ?: return@filter false
        id.length > numericBoundary.length || (id.length == numericBoundary.length && id > numericBoundary)
    }
    if (lastPostId != null) {
        val index = posts.indexOfFirst { it.id == lastPostId }
        if (index >= 0) return posts.drop(index + 1)
    }
    return posts.drop(count.coerceAtLeast(0))
}

internal data class ThreadOpeningSummary(val newPosts: Int, val repliesToSelf: Int?) {
    val text: String get() = "新着${newPosts}件" +
        (repliesToSelf?.let { "・自分への返信${it}件" } ?: "（自分の投稿記録なし）")
}

/** Captures the pre-open baseline; an archive/preview and an incomplete response cannot consume the notice. */
internal class ThreadOpeningSummaryTracker(
    private val knownPostCount: Int?,
    private val knownLastPostId: String? = null
) {
    private var completed = false
    val isCompleted: Boolean get() = completed

    fun accept(
        posts: List<Post>, selfIds: Set<String>, hiddenIds: Set<String> = emptySet(),
        live: Boolean = true, truncated: Boolean = false
    ): ThreadOpeningSummary? {
        if (completed || !live || truncated || posts.isEmpty()) return null
        val count = knownPostCount?.takeIf { it > 0 } ?: run { completed = true; return null }
        completed = true
        val added = threadPostsAfterBaseline(posts, count, knownLastPostId).filter { it.id !in hiddenIds }
        if (added.isEmpty()) return null
        val replies = if (selfIds.isEmpty()) null else added.count { post ->
            post.quoteReferences.any { quote -> quote.replyTargetPostIds.any { it in selfIds } }
        }
        return ThreadOpeningSummary(added.size, replies)
    }
}
