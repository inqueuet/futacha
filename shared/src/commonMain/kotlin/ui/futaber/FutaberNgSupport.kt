package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.matchesCompatThreadNg
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.board.buildPostHeaderText
import com.valoser.futacha.shared.ui.board.canonicalThreadSearchText
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText

internal const val FUTABER_NG_MAX_ENTRIES = 200
internal const val FUTABER_NG_MAX_LENGTH = 100

/**
 * Ids of the posts to hide: the shared word and header lists (the ones ふたちゃ edits) and the
 * detailed thread rules other modes created for this thread. Rules are matched on this
 * thread's compatibility keys, so they hide the same posts in every mode.
 *
 * The thread screen works the same answer out apart from the main thread ([futaberNgPrepare] / [futaberNgResolve]);
 * this is the one-call form of it.
 */
internal fun futaberNgHiddenIds(
    posts: List<Post>,
    ngWords: List<String>,
    ngHeaders: List<String>,
    rules: List<CompatNgRule>,
    tabKey: String,
    boardKey: String
): Set<String> = futaberNgEvaluate(posts, futaberNgPrepare(ngWords, ngHeaders, rules, tabKey, boardKey))

/**
 * What matching posts against the NG lists needs, worked out once: the word and header lists folded the way the posts
 * are (so a post is folded once, not once per word), and the thread rules cut down to the ones that apply to this
 * thread and board ([ruleIndex] is null when none of them can hide anything, so no post is converted for them).
 */
internal class FutaberNgPrepared(
    val headers: List<String>,
    val words: List<String>,
    val ruleIndex: CompatThreadNgRuleIndex?,
    val tabKey: String
) {
    /** Nothing can be hidden: no post needs to be looked at. */
    val isEmpty: Boolean get() = headers.isEmpty() && words.isEmpty() && ruleIndex == null

    /** What looking at one post costs, in word comparisons (a rough weight, to tell a small list from a big one). */
    internal val costPerPost: Long
        get() = headers.size.toLong() + words.size +
            if (ruleIndex == null) 0L else FUTABER_NG_RULE_POST_COST + ruleIndex.bodyWords.size +
                ruleIndex.headerWords.size + ruleIndex.bodyAndHeaderWords.size
}

/** Converting one post for the thread rules costs about this many word comparisons. */
private const val FUTABER_NG_RULE_POST_COST = 50L

/** The same folding the shared NG filter applies to its word lists (trim, lower case, full width to half width). */
private fun futaberNgFold(values: List<String>): List<String> =
    values.mapNotNull { value ->
        value.trim().takeIf { it.isNotBlank() }?.lowercase()?.let(::canonicalThreadSearchText)
    }.distinct()

private fun CompatThreadNgRuleIndex.canHideAnything(): Boolean =
    // The picture-hash rules are matched by the hashing path, never here.
    postNos.isNotEmpty() || posterIds.isNotEmpty() || imageUrls.isNotEmpty() ||
        bodyWords.isNotEmpty() || headerWords.isNotEmpty() || bodyAndHeaderWords.isNotEmpty()

internal fun futaberNgPrepare(
    ngWords: List<String>,
    ngHeaders: List<String>,
    rules: List<CompatNgRule>,
    tabKey: String,
    boardKey: String
): FutaberNgPrepared {
    val index = if (rules.isEmpty()) null
    else buildCompatThreadNgRuleIndex(rules, tabKey, boardKey).takeIf { it.canHideAnything() }
    return FutaberNgPrepared(futaberNgFold(ngHeaders), futaberNgFold(ngWords), index, tabKey)
}

private fun futaberNgPostMatchesLists(post: Post, headers: List<String>, words: List<String>): Boolean {
    if (headers.isNotEmpty()) {
        val headerText = canonicalThreadSearchText(buildPostHeaderText(post))
        if (headers.any { headerText.contains(it) }) return true
    }
    if (words.isNotEmpty()) {
        val bodyText = canonicalThreadSearchText(messageHtmlToPlainText(post.messageHtml).lowercase())
        if (words.any { bodyText.contains(it) }) return true
    }
    return false
}

private const val FUTABER_NG_CANCEL_CHECK_INTERVAL = 32

/**
 * The ids of [posts] that [prepared] hides. [ensureActive] is called now and then so a long run can be cancelled.
 * (A post is hidden by the word / header lists when every post with its id matches them, as the shared filter has
 * it; by a rule when its snapshot matches.)
 */
internal fun futaberNgEvaluate(
    posts: List<Post>,
    prepared: FutaberNgPrepared,
    ensureActive: () -> Unit = {}
): Set<String> {
    if (posts.isEmpty() || prepared.isEmpty) return emptySet()
    val hidden = HashSet<String>()
    if (prepared.headers.isNotEmpty() || prepared.words.isNotEmpty()) {
        val kept = HashSet<String>()
        posts.forEachIndexed { index, post ->
            if (index % FUTABER_NG_CANCEL_CHECK_INTERVAL == 0) ensureActive()
            if (!futaberNgPostMatchesLists(post, prepared.headers, prepared.words)) kept += post.id
        }
        posts.forEach { if (it.id !in kept) hidden += it.id }
    }
    val index = prepared.ruleIndex
    if (index != null) {
        ensureActive()
        val snapshots = ThreadPage(threadId = "", boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = posts)
            .toCompatThreadSnapshot(prepared.tabKey, 0).posts
        snapshots.forEachIndexed { position, snapshot ->
            if (position % FUTABER_NG_CANCEL_CHECK_INTERVAL == 0) ensureActive()
            if (snapshot.matchesCompatThreadNg(index)) hidden += snapshot.postNo
        }
    }
    return hidden
}

/** What was worked out for a thread: the posts looked at, the lists used and the ids they hid. */
internal class FutaberNgSnapshot(
    val posts: List<Post>,
    val prepared: FutaberNgPrepared,
    val hidden: Set<String>
)

/**
 * The tail of [posts] when it is [previous] with posts added at the end (nothing before changed, and the new posts
 * have ids of their own), so only the new posts need looking at; null for any other change.
 */
private fun futaberNgAppendedTail(previous: List<Post>, posts: List<Post>): List<Post>? {
    if (posts.size < previous.size) return null
    for (i in previous.indices) {
        val before = previous[i]
        val now = posts[i]
        if (before !== now && before != now) return null
    }
    val known = HashSet<String>(previous.size * 2)
    previous.forEach { known += it.id }
    val tail = posts.subList(previous.size, posts.size)
    tail.forEach { if (!known.add(it.id)) return null }
    return tail
}

/**
 * The hidden ids of [posts], reusing [previous] when it was worked out with the same [prepared] lists for the posts
 * this list only added to. The same answer as [futaberNgEvaluate] over all of [posts].
 */
internal fun futaberNgResolve(
    previous: FutaberNgSnapshot?,
    posts: List<Post>,
    prepared: FutaberNgPrepared,
    ensureActive: () -> Unit = {}
): Set<String> {
    if (previous != null && previous.prepared === prepared) {
        val tail = futaberNgAppendedTail(previous.posts, posts)
        if (tail != null) {
            return if (tail.isEmpty()) previous.hidden else previous.hidden + futaberNgEvaluate(tail, prepared, ensureActive)
        }
    }
    return futaberNgEvaluate(posts, prepared, ensureActive)
}

/**
 * What the screen can show at once. [hidden] is complete when [complete]; otherwise it is the best stand-in until
 * [futaberNgResolve] is done: what the last result hid, plus the posts that last result did not know (they wait, so
 * a post that should be hidden is never shown for a moment). [hold] is set when there is no earlier result at all: the
 * list then waits for the first one instead of showing every post.
 */
internal class FutaberNgQuick(val hidden: Set<String>, val complete: Boolean, val hold: Boolean)

/** Work (in word comparisons) the screen does on the main thread at most; a bigger job goes to the worker. */
internal const val FUTABER_NG_SYNC_BUDGET = 300_000L

internal fun futaberNgQuick(earlier: FutaberNgSnapshot?, posts: List<Post>, prepared: FutaberNgPrepared): FutaberNgQuick {
    if (posts.isEmpty() || prepared.isEmpty) return FutaberNgQuick(emptySet(), complete = true, hold = false)
    // The result for a thread with no posts knows nothing worth keeping (the first page is "all new").
    val previous = earlier?.takeIf { it.posts.isNotEmpty() }
    val tail = if (previous != null && previous.prepared === prepared) futaberNgAppendedTail(previous.posts, posts) else null
    if (previous != null && tail != null) {
        if (tail.size * prepared.costPerPost <= FUTABER_NG_SYNC_BUDGET) {
            val hidden = if (tail.isEmpty()) previous.hidden else previous.hidden + futaberNgEvaluate(tail, prepared)
            return FutaberNgQuick(hidden, complete = true, hold = false)
        }
    } else if (posts.size * prepared.costPerPost <= FUTABER_NG_SYNC_BUDGET) {
        return FutaberNgQuick(futaberNgEvaluate(posts, prepared), complete = true, hold = false)
    }
    if (previous == null) return FutaberNgQuick(emptySet(), complete = false, hold = true)
    val known = HashSet<String>(previous.posts.size * 2)
    previous.posts.forEach { known += it.id }
    val waiting = HashSet(previous.hidden)
    posts.forEach { if (it.id !in known) waiting += it.id }
    return FutaberNgQuick(waiting, complete = false, hold = false)
}

/** The typed word added to [list]: trimmed, case-folded duplicates refused, size and count bounded. */
internal fun futaberAddNgEntry(list: List<String>, input: String): List<String> {
    val value = input.trim().futaberTakeChars(FUTABER_NG_MAX_LENGTH)
    if (value.isEmpty() || list.size >= FUTABER_NG_MAX_ENTRIES) return list
    if (list.any { it.trim().equals(value, ignoreCase = true) }) return list
    return list + value
}

/** Why nothing more can be added to [list] (it holds the most entries), or null while there is room; shown instead of refusing silently. */
internal fun futaberNgFullMessage(list: List<String>): String? =
    if (list.size >= FUTABER_NG_MAX_ENTRIES) "登録できるのは${FUTABER_NG_MAX_ENTRIES}件までです。増やすには、使わない語句を削除してください。" else null

internal fun futaberRemoveNgEntry(list: List<String>, entry: String): List<String> = list.filterNot { it == entry }


/**
 * What "NG登録" of a long-pressed post offers to hide: its poster ID, else its name. Blank when the post has neither
 * (the dialog then opens on the word list). The default names are not offered, since they would hide most posts.
 */
internal fun futaberNgHeaderOf(post: com.valoser.futacha.shared.model.Post): String {
    post.posterId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val name = post.author?.trim().orEmpty()
    return if (name.isEmpty() || name in FUTABER_DEFAULT_NAMES) "" else name
}

private val FUTABER_DEFAULT_NAMES = setOf("としあき", "無念", "名無し", "名無しさん")


/** One line for an NG thread rule in the settings: the first letters of its title, then its address. */
internal fun futaberCatalogThreadRuleLabel(rule: com.valoser.futacha.shared.compat.CompatNgRule): String =
    listOf(rule.memo.trim(), rule.normalizedValue.trim()).filter { it.isNotEmpty() }.joinToString("　")
