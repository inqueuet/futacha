package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.COMPAT_CLOSED_BATCH_MAX
import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.COMPAT_TAB_VIEW_SHOWN_ONLY
import com.valoser.futacha.shared.compat.COMPAT_UNTITLED_TAB_TITLE
import com.valoser.futacha.shared.compat.compatRaisedTabLimit
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatSharedTabLimit
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.futaber.FutaberSharedTabsPlan
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.FutaberPreferenceKeys
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberNgScopeKey
import com.valoser.futacha.shared.ui.futaber.futaberSharedTabsPlan
import com.valoser.futacha.shared.ui.futaber.futaberTabsFromStore
import kotlinx.coroutines.flow.first

/** D-B5: the name this mode's marks go under on the shared tabs (`compat_tab_view.profile`). */
internal const val FTUTTER_TAB_PROFILE = "ftutter"

/**
 * This mode's tab strip (thread URLs, in order) as shared tabs, by the same rules as the futaber mode's. A URL whose board
 * is not registered, or not an official one, stays only in this mode. Protection stays with this mode (it is not a star).
 */
internal fun ftutterSharedTabsPlan(
    urls: List<String>,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long
): FutaberSharedTabsPlan {
    val keys = urls.mapNotNull { url ->
        val board = ftutterBoardForUrl(boards, url) ?: return@mapNotNull null
        val threadId = ftutterThreadIdFromUrl(url) ?: return@mapNotNull null
        FutaberTabKey(board.id, threadId)
    }
    return futaberSharedTabsPlan(keys, boards, history, nowEpochMillis, FTUTTER_TAB_PROFILE)
}

/*
 * Plan B7a (docs/shared-history-tabs-plan-2026-11-09.md §1-2, 1-7): this mode's tab bar is a view of the shared tabs.
 * The tabs, their order (`compat_tab_view` profile "ftutter") and the protection (= the shared ★) live in the shared
 * store; the old rows `compat.ftutter.tabs` / `protected` keep the threads that have no shared place
 * (a board that is not an official one) and, until the migration stage `tabs-ftutter-v1` took them in, the old tabs.
 */

/** Migration stage id; its record of the URLs already taken in, and the protections it could not turn into ★. */
internal const val FTUTTER_TABS_STAGE_ID = "tabs-ftutter-v1"
internal const val FTUTTER_TABS_CONSUMED_KEY = "shared.tabs-ftutter-v1.consumed"
internal const val FTUTTER_TABS_UNSTARRED_KEY = "shared.tabs-ftutter-v1.unstarred"

/** The ★ cap (plan 1-3): the shared tab limit less 10. */
internal fun ftutterStarCap(limit: Int): Int = (limit - 10).coerceAtLeast(0)

internal fun ftutterStarLimitNotice(cap: Int): String = "保護できるのは${cap}件までです（タブの上限−10件）"

internal fun ftutterUnstarredNotice(count: Int): String =
    "保護していたタブのうち${count}件は、保護の上限を超えるため保護せずに残しました"

/** What the stores say this mode's tab bar holds. */
internal data class FtutterTabSnapshot(
    /** Shared tabs in this mode's order: tabs without this mode's mark newest first, then its marks in order. */
    val shared: List<String>,
    /** Threads only in this mode's old rows (no shared place, or not taken in yet), in their old order. */
    val legacy: List<String>,
    /** ★ of the shared tabs, and the old protections not taken in yet. */
    val protected: Set<String>
) {
    val all: List<String> get() = shared + legacy
}

internal fun ftutterTabSnapshot(
    sharedTabs: List<CompatTab>,
    views: List<CompatSharedTabView>,
    legacyTabs: List<String>,
    legacyProtected: Set<String>,
    /** The migration's record; null until it was read, when no old row can be told from a tab already taken in (audit L7). */
    consumed: Set<String>?
): FtutterTabSnapshot {
    val byKey = sharedTabs.associateBy { it.key }
    val own = views.filter { it.profile == FTUTTER_TAB_PROFILE }.sortedBy { it.ordinal }
    val ownKeys = own.mapTo(HashSet()) { it.tabKey }
    val unmarked = sharedTabs.filter { it.key !in ownKeys }.sortedByDescending { it.insertedAtEpochMillis }.map { it.canonicalUrl }
    val shared = (unmarked + own.mapNotNull { byKey[it.tabKey]?.canonicalUrl }).distinct()
    val sharedSet = shared.toHashSet()
    val legacy = if (consumed == null) emptyList() else legacyTabs.filter { it !in consumed && it !in sharedSet }.distinct()
    val stars = sharedTabs.filter { it.favorite }.mapTo(HashSet()) { it.canonicalUrl }
    return FtutterTabSnapshot(shared, legacy, if (consumed == null) stars else stars + legacyProtected.filter { it !in consumed })
}

/** The URLs the migration already took in ([FTUTTER_TABS_CONSUMED_KEY]). */
internal fun ftutterConsumedTabs(sharedData: Map<String, String>): Set<String> =
    decodeFtutterList(sharedData[FTUTTER_TABS_CONSUMED_KEY]).orEmpty().toHashSet()

/** What [ftutterAddLegacyTabs] did. */
internal data class FtutterLegacyTabsResult(val added: List<String>, val unstarred: Int, val raisedLimitTo: Int?)

/**
 * Takes [urls] (this mode's old tab rows, or a backup's) into the shared tabs in one pass (plan 1-7): the shared tab limit
 * is raised once to hold them, the official boards are added, the tabs are added (an existing tab keeps its ★ and place),
 * the protected ones get the ★ up to the cap (the rest stay without it and are counted), and this mode's order keeps its
 * marks with the new ones appended in [urls]' order. A thread with no shared place is left out. Running it twice adds
 * nothing more.
 */
internal suspend fun ftutterAddLegacyTabs(
    store: CompatibilityStore,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    urls: List<String>,
    protected: Set<String>,
    nowEpochMillis: Long,
    savedHistoryLimit: Int? = null
): FtutterLegacyTabsResult {
    val plan = ftutterSharedTabsPlan(urls, boards, history, nowEpochMillis)
    if (plan.tabs.isEmpty()) return FtutterLegacyTabsResult(emptyList(), 0, null)
    val before = store.tabs.first()
    val beforeByKey = before.associateBy { it.key }
    val toStar = plan.tabs.filter { it.canonicalUrl in protected && beforeByKey[it.key]?.favorite != true }
    val futaberRows = futaberTabsFromStore(store.loadPreference(FutaberPreferenceKeys.TABS), boards).size
    val union = (beforeByKey.keys + plan.tabs.map { it.key }).size + futaberRows
    val starsAfter = before.count { it.favorite } + toStar.size
    val current = compatSharedTabLimit(store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY)?.let { mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to it) }.orEmpty())
    val target = compatRaisedTabLimit(current, union, starsAfter, savedHistoryLimit ?: 0)
    val raised = target.takeIf { it > current }
    if (raised != null) store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, raised.toString())
    val boardKeys = plan.tabs.mapTo(HashSet()) { it.boardKey }
    val stored = store.boards.first().mapTo(HashSet()) { it.key }
    val missing = boards.filter { futaberNgScopeKey(it) != null && futaberCompatBoardKey(it) in boardKeys && futaberCompatBoardKey(it) !in stored }
    if (missing.isNotEmpty()) store.importModernBoards(missing)
    store.addTabsIfAbsent(plan.tabs)
    var stars = store.tabs.first().count { it.favorite }
    val cap = ftutterStarCap(target)
    var unstarred = 0
    toStar.forEach { tab ->
        if (stars >= cap) unstarred++
        else if (store.updateTabIfPresent(tab.key) { it.takeIf { !it.favorite }?.copy(favorite = true) }) stars++
    }
    val present = store.tabs.first().mapTo(HashSet()) { it.key }
    val views = store.loadTabViews(FTUTTER_TAB_PROFILE).sortedBy { it.ordinal }
    val have = views.mapTo(HashSet()) { it.tabKey }
    // A tab that was shared before this is another mode's too: the mark only holds its place here (audit round 6, as L2).
    val appended = plan.views.sortedBy { it.ordinal }.filter { it.tabKey !in have && it.tabKey in present }
        .map { view -> if (view.tabKey in beforeByKey) view.copy(addedAtEpochMillis = COMPAT_TAB_VIEW_SHOWN_ONLY) else view }
    if (appended.isNotEmpty()) {
        store.replaceTabViews(FTUTTER_TAB_PROFILE, (views + appended).mapIndexed { index, view -> view.copy(ordinal = index) })
    }
    return FtutterLegacyTabsResult(plan.tabs.filter { it.key in present }.map { it.canonicalUrl }, unstarred, raised)
}

/**
 * The migration stage's work: this mode's old tab rows not taken in yet go to the shared tabs ([ftutterAddLegacyTabs]),
 * and the record of what was taken in grows. Threads with no shared place stay in the rows (and in the bar).
 */
internal suspend fun migrateFtutterLegacyTabs(
    store: CompatibilityStore,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long
): FtutterLegacyTabsResult {
    val rows = listOf(FtutterKeys.TABS, FtutterKeys.PROTECTED, FtutterKeys.HISTORY_LIMIT)
        .mapNotNull { key -> store.loadPreference(key)?.let { key to it } }.toMap()
    val shared = store.loadSharedData()
    val consumed = ftutterConsumedTabs(shared)
    val fresh = parseFtutterTabs(rows).urls.filter { it !in consumed }
    if (fresh.isEmpty()) return FtutterLegacyTabsResult(emptyList(), 0, null)
    val result = ftutterAddLegacyTabs(
        store, boards, history, fresh, parseFtutterProtected(rows), nowEpochMillis,
        rows[FtutterKeys.HISTORY_LIMIT]?.let { ftutterHistoryLimit(rows) }
    )
    if (result.added.isEmpty() && result.unstarred == 0) return result
    val previousUnstarred = shared[FTUTTER_TABS_UNSTARRED_KEY]?.toIntOrNull() ?: 0
    store.saveSharedData(buildMap {
        put(FTUTTER_TABS_CONSUMED_KEY, encodeFtutterList((consumed + result.added).toList()))
        if (result.unstarred > 0) put(FTUTTER_TABS_UNSTARRED_KEY, (previousUnstarred + result.unstarred).toString())
    })
    return result
}

/** How the tab bar's changes reach the stores. */
internal interface FtutterTabWriter {
    /**
     * Adds [url]'s tab (the shared one when it has a place, else the old row) and puts it first in this mode's order unless
     * it is a tab already. [seed] fills what the history cannot say yet: the tab is written before a first opening's row.
     */
    suspend fun open(url: String, seed: FtutterTabSeed? = null)
    /** This mode's order after a drag: [shown] (every tab shown, in order) first, the tabs not shown after them. */
    suspend fun move(shown: List<String>)
    suspend fun close(urls: List<String>)
    /** False when the ★ was refused (the cap); the reason has been told. */
    suspend fun protect(url: String, on: Boolean): Boolean
    /** Brings back the newest closed tab this mode may bring back, or null. */
    suspend fun restore(): FtutterRestoredTab?
    /** [url]'s page was read: [page] fills what its tab could not say when it was added (audit round 6: a link has no title). */
    suspend fun pageRead(url: String, page: FtutterTabSeed) {}
    /** The closed threads with no shared place this session can still bring back (they are not in the shared record). */
    val legacyClosedCount: Int get() = 0
}

/** What the screen knows of a thread when its tab is added, before the history has a row for it (audit round 5, N1). */
internal data class FtutterTabSeed(val title: String, val thumbnailUrl: String, val replyCount: Int)

internal fun CompatTab.withFtutterSeed(seed: FtutterTabSeed?): CompatTab {
    if (seed == null) return this
    val replies = if (replyCount > 0) replyCount else seed.replyCount.coerceAtLeast(0)
    return copy(
        title = if (title == COMPAT_UNTITLED_TAB_TITLE) seed.title.ifBlank { title } else title,
        thumbnailUrl = thumbnailUrl ?: seed.thumbnailUrl.ifBlank { null },
        replyCount = replies,
        checkedReplyCount = if (replyCount > 0) checkedReplyCount else replies
    )
}

/** What a read page says of its thread, for [FtutterTabWriter.pageRead]; null for a page with no posts. [storedTitle] wins, as in the history row. */
internal fun ftutterTabSeedFromPage(storedTitle: String, posts: List<Post>): FtutterTabSeed? =
    posts.firstOrNull()?.let { first ->
        FtutterTabSeed(ftutterThreadTitle(storedTitle, first), first.thumbnailUrl.orEmpty(), (posts.size - 1).coerceAtLeast(0))
    }

/** A tab brought back: [tab] is the shared tab (null for a thread only in this mode's old rows). */
internal data class FtutterRestoredTab(val url: String, val tab: CompatTab?)

/** [FtutterTabWriter] on the shared store; without one (previews) every tab stays in the old rows. */
internal class FtutterStoreTabWriter(
    private val store: CompatibilityStore?,
    private val boards: () -> List<BoardSummary>,
    private val history: () -> List<ThreadHistoryEntry>,
    /** Edits one of this mode's old rows (a URL list) as one change. */
    private val editRow: (String, (List<String>) -> List<String>) -> Unit,
    private val notice: (String) -> Unit,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) : FtutterTabWriter {
    private val profile = FTUTTER_TAB_PROFILE

    private suspend fun order(store: CompatibilityStore): Pair<List<CompatTab>, List<String>> {
        val tabs = store.tabs.first()
        return tabs to ftutterTabSnapshot(tabs, store.loadTabViews(profile), emptyList(), emptySet(), emptySet()).shared
    }

    /**
     * Every tab shown gets a mark, for its place. Only a tab this mode opened itself ([openedKey]) is dated: the trim's
     * first group is "only ふたったー風 opened it", and a tab another mode opened is merely shown here (audit L2).
     */
    private suspend fun writeOrder(store: CompatibilityStore, tabs: List<CompatTab>, urls: List<String>, openedKey: String? = null) {
        val byUrl = tabs.associateBy { it.canonicalUrl }
        val stamp = now()
        val marked = store.loadTabViews(profile).associateBy { it.tabKey }
        val keys = urls.mapNotNull { byUrl[it]?.key }.distinct()
        store.replaceTabViews(profile, keys.mapIndexed { index, key ->
            val addedAt = marked[key]?.addedAtEpochMillis ?: if (key == openedKey) stamp else COMPAT_TAB_VIEW_SHOWN_ONLY
            CompatSharedTabView(key, profile, index, addedAt)
        })
    }

    override suspend fun open(url: String, seed: FtutterTabSeed?) {
        val store = store
        val boards = boards()
        val tab = store?.let { ftutterSharedTabsPlan(listOf(url), boards, history(), now()).tabs.firstOrNull() }?.withFtutterSeed(seed)
        if (store == null || tab == null) {
            editRow(FtutterKeys.TABS) { ftutterPushTab(it, url) }
            return
        }
        if (store.boards.first().none { it.key == tab.boardKey }) {
            store.importModernBoards(boards.filter { futaberNgScopeKey(it) != null && futaberCompatBoardKey(it) == tab.boardKey })
        }
        val isNew = store.tabs.first().none { it.key == tab.key }
        if (!store.openSharedTab(tab, profile = null)) {
            editRow(FtutterKeys.TABS) { ftutterPushTab(it, url) }
            return
        }
        // The new tab is the newest without this mode's mark, so it is first; a known tab keeps its place. The whole
        // order is written so the tabs shown without a mark keep where they are from now on.
        val (tabs, shown) = order(store)
        writeOrder(store, tabs, ftutterPushTab(shown, tab.canonicalUrl), openedKey = tab.key.takeIf { isNew })
        // A page read while this write was on its way found no tab to fill.
        lastPageRead?.takeIf { it.first == url }?.let { (_, page) -> fillFromPage(store, url, page) }
    }

    /** The last page read; set before its tab is looked for, so either [pageRead] or the end of [open] fills the tab. */
    private var lastPageRead: Pair<String, FtutterTabSeed>? = null

    private suspend fun fillFromPage(store: CompatibilityStore, url: String, page: FtutterTabSeed) {
        // The key as [open] makes it: the screen's address and the tab's canonical one can be written differently.
        val key = ftutterSharedTabsPlan(listOf(url), boards(), history(), now()).tabs.firstOrNull()?.key ?: return
        store.updateTabIfPresent(key) { current -> current.withFtutterSeed(page).takeIf { it != current } }
    }

    override suspend fun pageRead(url: String, page: FtutterTabSeed) {
        lastPageRead = url to page
        store?.let { fillFromPage(it, url, page) }
    }

    override suspend fun move(shown: List<String>) {
        val store = store
        val current = if (store != null) order(store) else null
        val sharedSet = current?.second.orEmpty().toHashSet()
        if (store != null && current != null) {
            val shownSet = shown.toHashSet()
            writeOrder(store, current.first, shown.filter { it in sharedSet } + current.second.filter { it !in shownSet })
        }
        // Threads only in the old row keep their order there: the row's places they hold get them in the order shown
        // (no URL is added or removed, so no shared tab is closed by the reconcile).
        val legacy = shown.filter { it !in sharedSet }
        if (legacy.size > 1) {
            val legacySet = legacy.toHashSet()
            editRow(FtutterKeys.TABS) { row ->
                val inRow = legacy.filter { it in row }
                if (inRow.size < 2) return@editRow row
                val next = inRow.iterator()
                val inRowSet = inRow.toHashSet()
                row.map { if (it in legacySet && it in inRowSet) next.next() else it }
            }
        }
    }

    override suspend fun close(urls: List<String>) {
        if (urls.isEmpty()) return
        val store = store
        val tabs = store?.tabs?.first().orEmpty()
        val shared = tabs.filter { it.canonicalUrl in urls }
        if (store != null && shared.isNotEmpty()) {
            store.closeTabs(shared.mapTo(HashSet()) { it.key }, now(), closedBy = ExperienceProfile.FTUTTER.persistedValue)
        }
        // The rest are only in the old rows; taking them out there closes no shared tab.
        val sharedUrls = shared.mapTo(HashSet()) { it.canonicalUrl }
        // A shared tab's URL also leaves the old rows if it is there (a thread whose board was added after the migration),
        // so it does not come back from them once the shared tab is gone (audit L1).
        if (sharedUrls.isNotEmpty()) {
            editRow(FtutterKeys.TABS) { row -> if (row.none { it in sharedUrls }) row else row.filter { it !in sharedUrls } }
            editRow(FtutterKeys.PROTECTED) { row -> if (row.none { it in sharedUrls }) row else row.filter { it !in sharedUrls } }
        }
        if (shared.isNotEmpty()) lastSharedClose = now()
        val rest = urls.filter { it !in sharedUrls }.toSet()
        if (rest.isNotEmpty()) {
            val stamp = now()
            editRow(FtutterKeys.TABS) { row ->
                // The edit may run again (a store that retries); the same close is recorded once.
                row.forEachIndexed { index, url ->
                    if (url in rest && legacyClosed.none { it.url == url && it.closedAt == stamp }) legacyClosed.addLast(FtutterLegacyClosed(url, index, stamp))
                }
                while (legacyClosed.size > COMPAT_CLOSED_BATCH_MAX) legacyClosed.removeFirst()
                row - rest
            }
            editRow(FtutterKeys.PROTECTED) { it - rest }
        }
    }

    override suspend fun protect(url: String, on: Boolean): Boolean {
        val store = store
        val tab = store?.tabs?.first()?.firstOrNull { it.canonicalUrl == url }
        if (store == null || tab == null) {
            editRow(FtutterKeys.PROTECTED) { if (on) (it + url).distinct() else it - url }
            return true
        }
        if (on && !tab.favorite) {
            val cap = ftutterStarCap(compatSharedTabLimit(store.preferences.first()))
            if (store.tabs.first().count { it.favorite } >= cap) {
                notice(ftutterStarLimitNotice(cap))
                return false
            }
        }
        store.updateTabIfPresent(tab.key) { current -> current.takeIf { it.favorite != on }?.copy(favorite = on) }
        // A protection not taken in yet is in the old row too.
        if (!on) editRow(FtutterKeys.PROTECTED) { it - url }
        return true
    }

    override suspend fun restore(): FtutterRestoredTab? {
        val legacy = legacyClosed.lastOrNull()
        if (legacy != null && legacy.closedAt > lastSharedClose) return restoreLegacy()
        val tab = store?.popClosedTabBatch(profile, now())?.tabs?.firstOrNull()?.tab
        return tab?.let { FtutterRestoredTab(it.canonicalUrl, it) } ?: restoreLegacy()
    }

    private fun restoreLegacy(): FtutterRestoredTab? {
        val closed = legacyClosed.removeLastOrNull() ?: return null
        // Back at its place in the old row; the protection does not come back (the original).
        editRow(FtutterKeys.TABS) { row -> if (closed.url in row) row else row.toMutableList().apply { add(minOf(closed.index, size), closed.url) } }
        return FtutterRestoredTab(closed.url, null)
    }

    /** Closed threads with no shared place: memory only, as the original's undo (they have no shared record). */
    private val legacyClosed = ArrayDeque<FtutterLegacyClosed>()
    private var lastSharedClose = Long.MIN_VALUE
    override val legacyClosedCount: Int get() = legacyClosed.size
}

private data class FtutterLegacyClosed(val url: String, val index: Int, val closedAt: Long)

/** The tab keys of a `.ftt` import that are not written as settings (plan B7a). */
internal val FTUTTER_IMPORT_TAB_KEYS: Set<String> = setOf(FtutterKeys.TABS, FtutterKeys.PROTECTED, FtutterKeys.OPENING)

/**
 * A `.ftt` import's tabs (audit L1): the ones with a shared place go to the shared tabs as the migration does
 * ([ftutterAddLegacyTabs]); the rest are added to this mode's old rows. Returns how many protections could not get a ★.
 */
internal suspend fun ftutterImportTabs(
    store: CompatibilityStore?,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    imported: Map<String, String>,
    nowEpochMillis: Long,
    editRow: (String, (List<String>) -> List<String>) -> Unit
): Int {
    val urls = parseFtutterTabs(imported).urls
    if (urls.isEmpty()) return 0
    val protected = parseFtutterProtected(imported)
    val result = store?.let {
        ftutterAddLegacyTabs(it, boards, history, urls, protected, nowEpochMillis,
            imported[FtutterKeys.HISTORY_LIMIT]?.let { ftutterHistoryLimit(imported) })
    }
    val added = result?.added.orEmpty().toHashSet()
    val rest = urls.filter { it !in added }
    if (rest.isNotEmpty()) {
        editRow(FtutterKeys.TABS) { (it + rest).distinct() }
        rest.filter { it in protected }.takeIf { it.isNotEmpty() }?.let { kept -> editRow(FtutterKeys.PROTECTED) { (it + kept).distinct() } }
    }
    return result?.unstarred ?: 0
}
