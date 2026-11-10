package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.COMPAT_UNTITLED_TAB_TITLE
import com.valoser.futacha.shared.compat.compatRaisedTabLimit
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatSharedTabLimit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.serializer
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

/** D-B4: the name this mode's marks go under on the shared tabs (`compat_tab_view.profile`). */
internal const val FUTABER_TAB_PROFILE = "futaber"

/** What this mode's tab strip is, in the shared store's terms. */
internal data class FutaberSharedTabsPlan(
    val tabs: List<CompatTab>,
    /** The strip, in its order: only these tabs show in this mode's strip. */
    val views: List<CompatSharedTabView>
)

/**
 * The shared tabs for this mode's strip ([tabKeys]). Only official boards have a place in the shared store, so a thread of
 * any other board stays in this mode's own lists. Titles come from the history; counts are replies (the others' unit), the
 * stored ones being posts.
 *
 * This mode's favourites are not written here (plan B5, decision Q5): they are bookmarks of their own, apart from the
 * shared ★, so closing a tab never takes a favourite (audit H1) and a ★ taken off elsewhere never takes one either.
 */
internal fun futaberSharedTabsPlan(
    tabKeys: List<FutaberTabKey>,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long,
    profile: String = FUTABER_TAB_PROFILE
): FutaberSharedTabsPlan {
    val byId = boards.associateBy { it.id }
    val rows = futaberHistoryRowsFor(tabKeys.toHashSet(), history)
    fun tabFor(key: FutaberTabKey): CompatTab? {
        val board = byId[key.boardId]?.takeIf { futaberNgScopeKey(it) != null } ?: return null
        val row = rows[key]
        val url = futaberCompatThreadUrl(board, key.threadId)
        val replies = futaberShownReplyCount(row?.replyCount ?: 0)
        return CompatTab(
            key = futaberCompatTabKey(board, key.threadId),
            canonicalUrl = url,
            originalUrl = url,
            boardKey = futaberCompatBoardKey(board),
            boardName = row?.boardName?.ifBlank { null } ?: board.name,
            threadNo = key.threadId,
            title = row?.title?.ifBlank { null } ?: COMPAT_UNTITLED_TAB_TITLE,
            thumbnailUrl = row?.titleImageUrl?.ifBlank { null },
            replyCount = replies,
            checkedReplyCount = replies,
            insertedAtEpochMillis = nowEpochMillis,
            contentUpdatedAtEpochMillis = nowEpochMillis
        )
    }
    val tabs = LinkedHashMap<String, CompatTab>()
    val views = ArrayList<CompatSharedTabView>()
    tabKeys.forEach { key ->
        val tab = tabFor(key) ?: return@forEach
        tabs.getOrPut(tab.key) { tab }
        views += CompatSharedTabView(tab.key, profile, views.size, nowEpochMillis)
    }
    return FutaberSharedTabsPlan(tabs.values.toList(), views)
}

/** The shared tab keys with a ★ among [tabs]: ふたばー風's bulk close keeps them as well as its own favourites (plan B5, Q7). */
internal fun futaberStarredTabKeys(tabs: List<CompatTab>): Set<String> = tabs.filter { it.favorite }.mapTo(HashSet()) { it.key }

/*
 * Plan B8a (docs/shared-history-tabs-plan-2026-11-09.md §1-2, 1-7, Q1, Q3b): this mode's tab strip is a view of the
 * shared tabs. The tabs and this mode's order (`compat_tab_view` profile "futaber") live in the shared store; the old row
 * `compat.futaber.tabs` is only read for the threads with no shared place (a board that is not an official one) and, until
 * the migration stage `tabs-futaber-v1` took them in, the old tabs. Since B8b the reconcile does not read the row: writing
 * it closes nothing, and a shared tab's key leaves the row when this mode opens or closes that tab.
 */

/** Migration stage id and its record of the shared tab keys already taken in. */
internal const val FUTABER_TABS_STAGE_ID = "tabs-futaber-v1"
internal const val FUTABER_TABS_CONSUMED_KEY = "shared.tabs-futaber-v1.consumed"

/** Q3b: told only when the ★ and the open tabs alone fill the shared limit, so the tab added was trimmed at once. */
internal const val FUTABER_TAB_TRIMMED_NOTICE = "タブの上限を超えたため、このタブはふたばー風にだけ表示します"

/** What the stores say this mode's strip holds. */
internal data class FutaberTabSnapshot(
    /** The strip, in order: this mode's marks, then the shared tabs without its mark (oldest first), then the old row. */
    val keys: List<FutaberTabKey>,
    /** The shared tab behind each key that has one (its title and count when the history has no row, audit F1). */
    val shared: Map<FutaberTabKey, CompatTab>
)

/** The registered official board a shared tab's board key names, by [futaberCompatBoardKey]. */
private fun futaberOfficialBoardsByKey(boards: List<BoardSummary>): Map<String, BoardSummary> =
    boards.filter { futaberNgScopeKey(it) != null }.associateBy { futaberCompatBoardKey(it) }

internal fun futaberTabSnapshot(
    sharedTabs: List<CompatTab>,
    views: List<CompatSharedTabView>,
    rowKeys: List<FutaberTabKey>,
    /** The migration's record; null until it was read, when only the rows that can have no shared tab are shown (audit L7). */
    consumed: Set<String>?,
    boards: List<BoardSummary>
): FutaberTabSnapshot {
    val boardsByKey = futaberOfficialBoardsByKey(boards)
    val byId = boards.associateBy { it.id }
    val byTabKey = sharedTabs.associateBy { it.key }
    fun keyOf(tab: CompatTab): FutaberTabKey? {
        val board = boardsByKey[tab.boardKey] ?: return null
        return tab.threadNo.takeIf { it.isNotBlank() }?.let { FutaberTabKey(board.id, it) }
    }
    val shared = LinkedHashMap<FutaberTabKey, CompatTab>()
    val own = views.filter { it.profile == FUTABER_TAB_PROFILE }.sortedBy { it.ordinal }
    val ownKeys = own.mapTo(HashSet()) { it.tabKey }
    own.mapNotNull { byTabKey[it.tabKey] }.forEach { tab -> keyOf(tab)?.let { shared.getOrPut(it) { tab } } }
    sharedTabs.filter { it.key !in ownKeys }.sortedBy { it.insertedAtEpochMillis }
        .forEach { tab -> keyOf(tab)?.let { shared.getOrPut(it) { tab } } }
    val sharedCompatKeys = byTabKey.keys
    val legacy = rowKeys.filter { key ->
        if (key in shared) return@filter false
        val board = byId[key.boardId] ?: return@filter false
        if (futaberNgScopeKey(board) == null) return@filter true
        if (consumed == null) return@filter false
        val compatKey = futaberCompatTabKey(board, key.threadId)
        compatKey !in sharedCompatKeys && compatKey !in consumed
    }
    return FutaberTabSnapshot((shared.keys + legacy).distinct(), shared)
}

/** The shared tab keys the migration already took in ([FUTABER_TABS_CONSUMED_KEY]). */
internal fun futaberConsumedTabs(sharedData: Map<String, String>): Set<String> =
    decodeFutaberKeyList(sharedData[FUTABER_TABS_CONSUMED_KEY]).toHashSet()

private fun decodeFutaberKeyList(stored: String?): List<String> =
    if (stored.isNullOrBlank()) emptyList()
    else runCatching<List<String>> { kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), stored) }
        .getOrNull().orEmpty()

private fun encodeFutaberKeyList(keys: Collection<String>): String =
    kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), keys.toList())

/**
 * The migration stage's work: this mode's old row's tabs not taken in yet go to the shared tabs with this mode's marks in
 * the row's order (an existing tab keeps its ★ and place), the shared tab limit is raised once to hold them (plan 1-7),
 * and the record of what was taken in grows. Threads with no shared place stay in the row (and in the strip). Returns the
 * keys taken in.
 */
internal suspend fun migrateFutaberLegacyTabs(
    store: CompatibilityStore,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long
): List<String> {
    val rowKeys = futaberTabsFromStore(store.loadPreference(FutaberPreferenceKeys.TABS), boards)
    val shared = store.loadSharedData()
    val consumed = futaberConsumedTabs(shared)
    val plan = futaberSharedTabsPlan(rowKeys, boards, history, nowEpochMillis)
    val fresh = plan.tabs.filter { it.key !in consumed }
    if (fresh.isEmpty()) return emptyList()
    val before = store.tabs.first()
    val union = (before.map { it.key } + fresh.map { it.key }).toHashSet().size
    val stars = before.count { it.favorite }
    val current = compatSharedTabLimit(store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY)?.let { mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to it) }.orEmpty())
    val target = compatRaisedTabLimit(current, union, stars)
    if (target > current) store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, target.toString())
    val boardKeys = fresh.mapTo(HashSet()) { it.boardKey }
    val stored = store.boards.first().mapTo(HashSet()) { it.key }
    val missing = boards.filter { futaberNgScopeKey(it) != null && futaberCompatBoardKey(it) in boardKeys && futaberCompatBoardKey(it) !in stored }
    if (missing.isNotEmpty()) store.importModernBoards(missing)
    store.addTabsIfAbsent(fresh)
    val present = store.tabs.first().mapTo(HashSet()) { it.key }
    val views = store.loadTabViews(FUTABER_TAB_PROFILE).sortedBy { it.ordinal }
    val have = views.mapTo(HashSet()) { it.tabKey }
    val appended = plan.views.filter { it.tabKey !in have && it.tabKey in present && fresh.any { tab -> tab.key == it.tabKey } }
    if (appended.isNotEmpty()) {
        store.replaceTabViews(FUTABER_TAB_PROFILE, (views + appended).mapIndexed { index, view -> view.copy(ordinal = index) })
    }
    val added = fresh.filter { it.key in present }.map { it.key }
    store.saveSharedData(mapOf(FUTABER_TABS_CONSUMED_KEY to encodeFutaberKeyList(consumed + added)))
    return added
}

/** How the strip's changes reach the stores. */
internal interface FutaberTabWriter {
    /** Makes the strip [next] from [current]: the tabs gone are closed, the tabs new are added at the end. */
    suspend fun apply(current: List<FutaberTabKey>, next: List<FutaberTabKey>)
    /** Brings back the newest closed tab this mode may bring back, or null. */
    suspend fun restore(): FutaberTabKey?
    /** How many closes [restore] can still bring back. */
    suspend fun restorableCount(): Int
}

/** [FutaberTabWriter] on the shared store; without one (previews) every tab stays in the old row. */
internal class FutaberStoreTabWriter(
    private val store: CompatibilityStore?,
    private val boards: () -> List<BoardSummary>,
    private val history: () -> List<ThreadHistoryEntry>,
    /** Edits the old row (as keys) as one change. */
    private val editRow: ((List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit,
    private val notice: (String) -> Unit,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) : FutaberTabWriter {
    private val profile = FUTABER_TAB_PROFILE

    private fun compatKeyOf(key: FutaberTabKey): String? =
        boards().firstOrNull { it.id == key.boardId }?.takeIf { futaberNgScopeKey(it) != null }?.let { futaberCompatTabKey(it, key.threadId) }

    override suspend fun apply(current: List<FutaberTabKey>, next: List<FutaberTabKey>) {
        val removed = current.filter { it !in next }
        if (removed.isNotEmpty()) close(removed, current)
        next.filter { it !in current }.forEach { open(it) }
    }

    private suspend fun close(keys: List<FutaberTabKey>, strip: List<FutaberTabKey>) {
        val store = store
        val present = store?.tabs?.first()?.mapTo(HashSet()) { it.key }.orEmpty()
        val shared = keys.mapNotNull { key -> compatKeyOf(key)?.takeIf { it in present }?.let { key to it } }
        if (store != null && shared.isNotEmpty()) {
            store.closeTabs(shared.mapTo(HashSet()) { it.second }, now(), closedBy = ExperienceProfile.FUTABER.persistedValue)
            lastSharedClose = now()
        }
        val sharedKeys = shared.mapTo(HashSet()) { it.first }
        // A shared tab's key also leaves the old row if it is there (a thread added while it had no shared place), so it
        // does not come back from the row once the shared tab is gone (plan B8b: nothing else edits the row any more).
        if (sharedKeys.isNotEmpty()) editRow { row -> if (row.none { it in sharedKeys }) row else row.filter { it !in sharedKeys } }
        val rest = keys.filter { it !in sharedKeys }.toSet()
        if (rest.isEmpty()) return
        val stamp = now()
        rest.forEach { key -> legacyClosed.addLast(FutaberLegacyClosed(key, strip.indexOf(key).coerceAtLeast(0), stamp)) }
        while (legacyClosed.size > FUTABER_MAX_CLOSED_TABS) legacyClosed.removeFirst()
        editRow { row -> row.filter { it !in rest } }
    }

    private suspend fun open(key: FutaberTabKey) {
        val store = store
        val boards = boards()
        val tab = store?.let { futaberSharedTabsPlan(listOf(key), boards, history(), now()).tabs.firstOrNull() }
        if (store == null || tab == null) {
            editRow { row -> if (key in row) row else row + key }
            return
        }
        if (store.boards.first().none { it.key == tab.boardKey }) {
            store.importModernBoards(boards.filter { futaberNgScopeKey(it) != null && futaberCompatBoardKey(it) == tab.boardKey })
        }
        if (!store.openSharedTab(tab, profile = profile)) {
            editRow { row -> if (key in row) row else row + key }
            notice(FUTABER_TAB_TRIMMED_NOTICE)
            return
        }
        // The thread has a shared place now: a row entry from a time it had none is not a second copy (plan B8b).
        editRow { row -> if (key in row) row.filter { it != key } else row }
    }

    override suspend fun restore(): FutaberTabKey? {
        val legacy = legacyClosed.lastOrNull()
        if (legacy != null && legacy.closedAt > lastSharedClose) return restoreLegacy()
        val tab = store?.popClosedTabBatch(profile, now())?.tabs?.firstOrNull()?.tab
        val key = tab?.let { restored -> futaberOfficialBoardsByKey(boards())[restored.boardKey]?.let { FutaberTabKey(it.id, restored.threadNo) } }
        return key ?: restoreLegacy()
    }

    override suspend fun restorableCount(): Int = (store?.countClosedTabBatches(profile, now()) ?: 0) + legacyClosed.size

    private fun restoreLegacy(): FutaberTabKey? {
        val closed = legacyClosed.removeLastOrNull() ?: return null
        editRow { row -> if (closed.key in row) row else row.toMutableList().apply { add(minOf(closed.index, size), closed.key) } }
        return closed.key
    }

    /** Closed threads with no shared tab: memory only, this run (they have no shared record). */
    private val legacyClosed = ArrayDeque<FutaberLegacyClosed>()
    private var lastSharedClose = Long.MIN_VALUE
}

private data class FutaberLegacyClosed(val key: FutaberTabKey, val index: Int, val closedAt: Long)

/** The strip as the stores hold it now (read inside the screen's tab write lock, so a change applies to the latest strip). */
internal suspend fun futaberStoreTabSnapshot(store: CompatibilityStore, boards: List<BoardSummary>, consumed: Set<String>?): FutaberTabSnapshot =
    futaberTabSnapshot(
        store.tabs.first(), store.loadTabViews(FUTABER_TAB_PROFILE),
        futaberTabsFromStore(store.loadPreference(FutaberPreferenceKeys.TABS), boards), consumed, boards
    )
