package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

/** D-B4: the name this mode's marks go under on the shared tabs (`compat_tab_view.profile`). */
internal const val FUTABER_TAB_PROFILE = "futaber"

/** What this mode's tab strip and starred threads are, in the shared store's terms. */
internal data class FutaberSharedTabsPlan(
    val tabs: List<CompatTab>,
    /** The strip, in its order: only these tabs show in this mode's strip. */
    val views: List<CompatSharedTabView>,
    /** The shared tab keys of the starred threads. */
    val favoriteKeys: Set<String>
)

/**
 * The shared tabs for this mode's strip ([tabKeys]) and starred threads ([favorites]). Only official boards have a place
 * in the shared store, so a thread of any other board stays in this mode's own lists. Titles come from the star when
 * there is one, else the history; counts are replies (the others' unit), the stored ones being posts.
 */
internal fun futaberSharedTabsPlan(
    tabKeys: List<FutaberTabKey>,
    favorites: List<FutaberFavorite>,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long,
    profile: String = FUTABER_TAB_PROFILE
): FutaberSharedTabsPlan {
    val byId = boards.associateBy { it.id }
    val favoriteByKey = favorites.associateBy { it.key }
    val rows = futaberHistoryRowsFor((tabKeys + favorites.map { it.key }).toHashSet(), history)
    fun tabFor(key: FutaberTabKey): CompatTab? {
        val board = byId[key.boardId]?.takeIf { futaberNgScopeKey(it) != null } ?: return null
        val star = favoriteByKey[key]
        val row = rows[key]
        val url = futaberCompatThreadUrl(board, key.threadId)
        val replies = futaberShownReplyCount(star?.replyCount ?: row?.replyCount ?: 0)
        return CompatTab(
            key = futaberCompatTabKey(board, key.threadId),
            canonicalUrl = url,
            originalUrl = url,
            boardKey = futaberCompatBoardKey(board),
            boardName = star?.boardName?.ifBlank { null } ?: row?.boardName?.ifBlank { null } ?: board.name,
            threadNo = key.threadId,
            title = star?.title?.ifBlank { null } ?: row?.title?.ifBlank { null } ?: "(無題)",
            thumbnailUrl = (star?.thumbnailUrl?.ifBlank { null } ?: row?.titleImageUrl?.ifBlank { null }),
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
    val favoriteKeys = LinkedHashSet<String>()
    favorites.forEach { star ->
        val tab = tabFor(star.key) ?: return@forEach
        tabs.getOrPut(tab.key) { tab }
        favoriteKeys += tab.key
    }
    return FutaberSharedTabsPlan(tabs.values.toList(), views, favoriteKeys)
}

/**
 * Writes [plan] to the shared store without selecting or closing anything of the other modes: absent tabs are added,
 * this mode's strip is replaced, the stars are set, and the stars [previousFavorites] had that are gone are taken off.
 * Returns the star keys now set, to hand back as [previousFavorites] next time.
 */
internal suspend fun futaberApplySharedTabs(
    store: CompatibilityStore,
    plan: FutaberSharedTabsPlan,
    officialBoards: List<BoardSummary>,
    previousFavorites: Set<String>,
    profile: String = FUTABER_TAB_PROFILE
): Set<String> {
    store.importModernBoards(officialBoards)
    store.addTabsIfAbsent(plan.tabs)
    store.replaceTabViews(profile, plan.views)
    plan.favoriteKeys.forEach { key -> store.updateTabIfPresent(key) { tab -> if (tab.favorite) null else tab.copy(favorite = true) } }
    (previousFavorites - plan.favoriteKeys).forEach { key ->
        store.updateTabIfPresent(key) { tab -> if (tab.favorite) tab.copy(favorite = false) else null }
    }
    return plan.favoriteKeys
}

/** What a look at the shared tabs says about the stars this mode wrote there (D-B4, J2). */
internal data class FutaberSharedStarChanges(
    /** Shared tab keys that were starred and no longer are (un-starred or closed in another mode). */
    val unstarred: Set<String>,
    /** The starred ones seen so far, to hand back next time. */
    val seen: Set<String>
)

/**
 * A star another mode takes off (or whose tab it closes) is taken off here too: star and protection are one thing across
 * the modes. Only a star seen set on the shared tab counts, so a store not loaded yet (no tabs) or a star not written
 * there yet never removes anything; [mirrored] are the stars this mode wrote, so one it took off itself is left alone.
 */
internal fun futaberSharedStarChanges(seen: Set<String>, tabs: List<CompatTab>, mirrored: Set<String>): FutaberSharedStarChanges {
    val starredNow = tabs.filter { it.favorite }.mapTo(HashSet()) { it.key }
    val unstarred = (seen intersect mirrored) - starredNow
    return FutaberSharedStarChanges(unstarred, (seen + (starredNow intersect mirrored)) - unstarred)
}

/** The stars still this mode's while it writes [planFavoriteKeys]: the ones it is about to take off are dropped first (audit A6). */
internal fun futaberMirroredBeforeApply(previous: Set<String>, planFavoriteKeys: Set<String>): Set<String> =
    previous intersect planFavoriteKeys
