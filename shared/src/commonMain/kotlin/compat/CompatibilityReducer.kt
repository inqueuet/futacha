package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.saturatingEpochAdd

sealed interface CompatHost {
    data object Main : CompatHost
    data class Catalog(val boardKey: String) : CompatHost
    data class ThreadWorkspace(val origin: CompatThreadOrigin) : CompatHost
    data class Post(val tabKey: String) : CompatHost
    data class PostBuild(val boardKey: String) : CompatHost
    data class PostDrawing(val origin: CompatHost) : CompatHost
    data class Gallery(
        val tabKey: String,
        /** Gallery item to restore when returning from the viewer. */
        val index: Int = 0,
        /** Stable media identity used before the index when the snapshot changed. */
        val postNo: String? = null
    ) : CompatHost
    data class Viewer(
        val tabKey: String,
        val index: Int,
        val caller: CompatViewerCaller,
        val chromeVisible: Boolean = true,
        /** Stable identity of the post that opened the viewer. Indexes can move after a refresh/NG filter. */
        val postNo: String? = null,
        /** Media opened directly from a filename while uploader gallery display is disabled. */
        val directMediaUrl: String? = null,
        /** Original thread position used by the viewer's source-post action. */
        val directSourcePosition: Int? = null
    ) : CompatHost
    data class ToolbarEditor(
        val surface: CompatToolbarSurface,
        val origin: CompatHost
    ) : CompatHost
    data class SavedThreads(val origin: CompatHost = Main) : CompatHost
    data class ChangeLog(val origin: CompatHost = Main) : CompatHost
    data class License(val origin: CompatHost = Main) : CompatHost
    data class Help(val origin: CompatHost = Main) : CompatHost
    data class Settings(
        val path: String = "root",
        val origin: CompatHost = Main,
        /**
         * A child opened from AppSettingActivity returns to its settings root.
         * The same child Activity opened directly from Catalog/Thread/Viewer
         * finishes back to that caller instead.  Preserve that distinction in
         * the single-Activity Compose host.
         */
        val returnToRoot: Boolean = path == "root"
    ) : CompatHost
}

enum class CompatThreadOrigin { MAIN, CATALOG, DEEP_LINK }
enum class CompatViewerCaller { THREAD, GALLERY }
enum class CompatDrawerPage { TABS, HISTORY, WATCHER }

sealed interface CompatOverlay {
    data class Dialog(val key: String) : CompatOverlay
    data class ContextMenu(val key: String) : CompatOverlay
    data class Quote(val postNo: String) : CompatOverlay
    data class Extraction(val key: String) : CompatOverlay
}

data class CompatSearchState(
    val query: String = "",
    val matches: List<Int> = emptyList(),
    val currentMatch: Int = 0,
    val imeVisible: Boolean = false,
    val focused: Boolean = false
)

data class CompatibilityWorkspaceState(
    val host: CompatHost = CompatHost.Main,
    /** Catalog that launched the current thread workspace. */
    val catalogHostBoardKey: String? = null,
    val activeTabKey: String? = null,
    val tabs: List<CompatTab> = emptyList(),
    val selectorOpen: Boolean = false,
    val selectorPresentation: SelectorPresentation = SelectorPresentation.ABOVE,
    val search: CompatSearchState? = null,
    val drawerPage: CompatDrawerPage? = null,
    /** Last drawer tab selected by the user; reopening must not reset it. */
    val lastDrawerPage: CompatDrawerPage? = null,
    val overlays: List<CompatOverlay> = emptyList(),
    val pendingClose: ClosedTabBatch? = null
)

sealed interface CompatibilityEvent {
    data class ReplaceTabs(val tabs: List<CompatTab>, val activeTabKey: String?) : CompatibilityEvent
    data class OpenCatalog(val boardKey: String) : CompatibilityEvent
    data class OpenThread(val tabKey: String, val origin: CompatThreadOrigin) : CompatibilityEvent
    data class OpenDrawer(val page: CompatDrawerPage) : CompatibilityEvent
    data object CloseDrawer : CompatibilityEvent
    data class SetSelector(val open: Boolean, val presentation: SelectorPresentation) : CompatibilityEvent
    data class SelectTab(val tabKey: String) : CompatibilityEvent
    data class CloseTab(val tabKey: String, val nowEpochMillis: Long) : CompatibilityEvent
    data class CloseTabs(val tabKeys: Set<String>, val nowEpochMillis: Long) : CompatibilityEvent
    data object UndoClose : CompatibilityEvent
    data class OpenSearch(val imeVisible: Boolean = true) : CompatibilityEvent
    data class UpdateSearch(val query: String, val matches: List<Int>) : CompatibilityEvent
    data object ClearSearchFocus : CompatibilityEvent
    data class PushOverlay(val overlay: CompatOverlay) : CompatibilityEvent
    data object PopOverlay : CompatibilityEvent
    data class OpenHost(val host: CompatHost) : CompatibilityEvent
    data object Back : CompatibilityEvent
}

sealed interface CompatibilityEffect {
    data class PersistActiveTab(val tabKey: String?) : CompatibilityEffect
    data class PersistCatalogHost(val boardKey: String?) : CompatibilityEffect
    data class PersistClosedTabs(
        val tabKeys: Set<String>,
        val nowEpochMillis: Long,
        val finalScrollAnchors: Map<String, ScrollAnchor>
    ) : CompatibilityEffect
    data class RestoreClosedTabs(val batch: ClosedTabBatch) : CompatibilityEffect
    data class ScrollTabToBottom(val tabKey: String) : CompatibilityEffect
    data object HideIme : CompatibilityEffect
    data object FinishApplication : CompatibilityEffect
}

data class CompatibilityReduction(
    val state: CompatibilityWorkspaceState,
    val effects: List<CompatibilityEffect> = emptyList()
)

/**
 * A board is represented by one stable key throughout compatibility mode.
 * Persisted stores enforce this invariant, but UI-facing Flow implementations
 * and an in-flight reorder can still briefly hand Compose a repeated key.
 */
internal fun distinctCompatBoards(boards: List<CompatBoard>): List<CompatBoard> =
    boards.distinctBy(CompatBoard::key)

/**
 * A thread is represented by one stable tab key throughout compatibility mode.
 * Keep this invariant at every state/UI boundary: a duplicate key is not just
 * redundant, it is an unrecoverable Compose LazyLayout exception.
 */
internal fun distinctCompatTabs(tabs: List<CompatTab>): List<CompatTab> =
    tabs.distinctBy(CompatTab::key)

internal fun List<CompatTab>.prependCompatTab(tab: CompatTab): List<CompatTab> =
    distinctCompatTabs(listOf(tab) + filterNot { it.key == tab.key })

/**
 * Merge the latest catalog row into an existing tab without discarding the
 * thread-local read baseline and restoration state. Opening a row for viewing
 * marks the count already shown by that catalog as read; adding it in the
 * background keeps the previous unread baseline.
 */
internal fun mergeCompatCatalogTab(
    existing: CompatTab?,
    candidate: CompatTab,
    markCatalogCountRead: Boolean
): CompatTab {
    val checkedReplyCount = when {
        markCatalogCountRead -> maxOf(existing?.checkedReplyCount ?: 0, candidate.replyCount)
        existing != null -> existing.checkedReplyCount
        else -> candidate.checkedReplyCount
    }
    if (existing == null) {
        return candidate.copy(checkedReplyCount = checkedReplyCount)
    }
    return candidate.copy(
        thumbnailUrl = candidate.thumbnailUrl ?: existing.thumbnailUrl,
        replyCount = maxOf(existing.replyCount, candidate.replyCount),
        checkedReplyCount = checkedReplyCount,
        favorite = existing.favorite,
        insertedAtEpochMillis = existing.insertedAtEpochMillis,
        scrollAnchor = existing.scrollAnchor,
        snapshotRevision = existing.snapshotRevision
    )
}

/** Keep history keys unique at the UI boundary as well as in SQLite. */
internal fun distinctCompatHistory(history: List<CompatHistoryEntry>): List<CompatHistoryEntry> =
    history.distinctBy(CompatHistoryEntry::canonicalUrl)

private fun CompatibilityWorkspaceState.hostAfterClosingLastTab(
    remaining: List<CompatTab>,
    closed: List<CompatTab>
): CompatHost {
    if (remaining.isNotEmpty()) return host
    val threadHost = host as? CompatHost.ThreadWorkspace ?: return host
    if (threadHost.origin != CompatThreadOrigin.CATALOG) return CompatHost.Main
    val launchingBoardKey = catalogHostBoardKey
        ?: closed.firstOrNull { it.key == activeTabKey }?.boardKey
        ?: closed.firstOrNull()?.boardKey
    return launchingBoardKey?.let(CompatHost::Catalog) ?: CompatHost.Main
}

fun reduceCompatibilityWorkspace(
    state: CompatibilityWorkspaceState,
    event: CompatibilityEvent
): CompatibilityReduction = when (event) {
    is CompatibilityEvent.ReplaceTabs -> {
        // Android's store refreshes tabs/workspace state fields one after the
        // other inside one SQLite transaction. A Flow combine can therefore
        // briefly deliver the new tab list with the previous active key. Do
        // not turn that short-lived mismatch into Main (or an empty thread):
        // retain the current key when possible, then fall back to the first
        // durable tab. Distinct keys also keep a stale/duplicated migration
        // row from producing duplicate selector items (#30/#33).
        val tabs = distinctCompatTabs(event.tabs)
        val keys = tabs.mapTo(mutableSetOf()) { it.key }
        val active = event.activeTabKey?.takeIf(keys::contains)
            ?: state.activeTabKey?.takeIf(keys::contains)
            ?: tabs.firstOrNull()?.key
        CompatibilityReduction(state.copy(tabs = tabs, activeTabKey = active))
    }
    is CompatibilityEvent.OpenCatalog -> CompatibilityReduction(
        state.copy(
            host = CompatHost.Catalog(event.boardKey),
            catalogHostBoardKey = event.boardKey,
            drawerPage = null,
            overlays = emptyList(),
            search = null
        ),
        listOf(CompatibilityEffect.PersistCatalogHost(event.boardKey))
    )
    is CompatibilityEvent.OpenThread -> {
        val catalogHostBoardKey = if (event.origin == CompatThreadOrigin.CATALOG) {
            (state.host as? CompatHost.Catalog)?.boardKey ?: state.catalogHostBoardKey
        } else {
            state.catalogHostBoardKey
        }
        CompatibilityReduction(
            state.copy(
                host = CompatHost.ThreadWorkspace(event.origin),
                catalogHostBoardKey = catalogHostBoardKey,
                activeTabKey = event.tabKey,
                drawerPage = null,
                overlays = emptyList(),
                search = null
            ),
            buildList {
                add(CompatibilityEffect.PersistActiveTab(event.tabKey))
                if (event.origin == CompatThreadOrigin.CATALOG) {
                    add(CompatibilityEffect.PersistCatalogHost(catalogHostBoardKey))
                }
            }
        )
    }
    is CompatibilityEvent.OpenDrawer -> CompatibilityReduction(
        state.copy(drawerPage = event.page, lastDrawerPage = event.page)
    )
    CompatibilityEvent.CloseDrawer -> CompatibilityReduction(state.copy(drawerPage = null))
    is CompatibilityEvent.SetSelector -> CompatibilityReduction(
        state.copy(selectorOpen = event.open, selectorPresentation = event.presentation)
    )
    is CompatibilityEvent.SelectTab -> {
        if (state.activeTabKey == event.tabKey) {
            CompatibilityReduction(state, listOf(CompatibilityEffect.ScrollTabToBottom(event.tabKey)))
        } else {
            CompatibilityReduction(
                state.copy(activeTabKey = event.tabKey, search = null),
                listOf(CompatibilityEffect.PersistActiveTab(event.tabKey))
            )
        }
    }
    is CompatibilityEvent.CloseTab -> {
        val tab = state.tabs.firstOrNull { it.key == event.tabKey }
            ?: return CompatibilityReduction(state)
        val index = state.tabs.indexOf(tab)
        val remaining = state.tabs.filterNot { it.key == tab.key }
        val newActive = if (state.activeTabKey == tab.key) {
            remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key
                ?: remaining.lastOrNull()?.key
        } else {
            state.activeTabKey
        }
        val batch = ClosedTabBatch(
            id = "close-${event.nowEpochMillis}-${tab.key}",
            tabs = listOf(ClosedCompatTab(tab, index)),
            selectedTabKey = state.activeTabKey,
            expiresAtEpochMillis = saturatingEpochAdd(event.nowEpochMillis, 7_000L)
        )
        CompatibilityReduction(
            state.copy(
                host = state.hostAfterClosingLastTab(remaining, listOf(tab)),
                tabs = remaining,
                activeTabKey = newActive,
                pendingClose = batch
            ),
            listOf(
                CompatibilityEffect.PersistClosedTabs(
                    setOf(tab.key),
                    event.nowEpochMillis,
                    mapOf(tab.key to tab.scrollAnchor)
                ),
                CompatibilityEffect.PersistActiveTab(newActive)
            )
        )
    }
    is CompatibilityEvent.CloseTabs -> {
        val closed = state.tabs.mapIndexedNotNull { index, tab ->
            ClosedCompatTab(tab, index).takeIf { tab.key in event.tabKeys }
        }
        if (closed.isEmpty()) return CompatibilityReduction(state)
        val remaining = state.tabs.filterNot { it.key in event.tabKeys }
        val activeIndex = state.tabs.indexOfFirst { it.key == state.activeTabKey }.coerceAtLeast(0)
        val newActive = state.activeTabKey?.takeIf { it !in event.tabKeys }
            ?: remaining.getOrNull(activeIndex.coerceAtMost(remaining.lastIndex))?.key
            ?: remaining.lastOrNull()?.key
        val batch = ClosedTabBatch(
            id = "close-${event.nowEpochMillis}-${closed.joinToString("-") { it.tab.key }}",
            tabs = closed,
            selectedTabKey = state.activeTabKey,
            expiresAtEpochMillis = saturatingEpochAdd(event.nowEpochMillis, 7_000L)
        )
        CompatibilityReduction(
            state.copy(
                host = state.hostAfterClosingLastTab(remaining, closed.map(ClosedCompatTab::tab)),
                tabs = remaining,
                activeTabKey = newActive,
                pendingClose = batch
            ),
            listOf(
                CompatibilityEffect.PersistClosedTabs(
                    event.tabKeys,
                    event.nowEpochMillis,
                    closed.associate { it.tab.key to it.tab.scrollAnchor }
                ),
                CompatibilityEffect.PersistActiveTab(newActive)
            )
        )
    }
    CompatibilityEvent.UndoClose -> {
        val batch = state.pendingClose ?: return CompatibilityReduction(state)
        val restored = state.tabs.toMutableList()
        batch.tabs.sortedBy { it.originalIndex }.forEach { closed ->
            restored.add(closed.originalIndex.coerceIn(0, restored.size), closed.tab)
        }
        val selected = batch.selectedTabKey?.takeIf { key -> restored.any { it.key == key } }
            ?: state.activeTabKey
        CompatibilityReduction(
            state.copy(tabs = distinctCompatTabs(restored), activeTabKey = selected, pendingClose = null),
            listOf(CompatibilityEffect.RestoreClosedTabs(batch), CompatibilityEffect.PersistActiveTab(selected))
        )
    }
    is CompatibilityEvent.OpenSearch -> CompatibilityReduction(
        state.copy(search = CompatSearchState(imeVisible = event.imeVisible, focused = true))
    )
    is CompatibilityEvent.UpdateSearch -> {
        val current = state.search ?: CompatSearchState()
        CompatibilityReduction(
            state.copy(search = current.copy(query = event.query, matches = event.matches, currentMatch = 0))
        )
    }
    CompatibilityEvent.ClearSearchFocus -> CompatibilityReduction(
        state.copy(search = state.search?.copy(imeVisible = false, focused = false))
    )
    is CompatibilityEvent.PushOverlay -> CompatibilityReduction(state.copy(overlays = state.overlays + event.overlay))
    CompatibilityEvent.PopOverlay -> CompatibilityReduction(state.copy(overlays = state.overlays.dropLast(1)))
    is CompatibilityEvent.OpenHost -> CompatibilityReduction(
        // A host transition must never leave the modal drawer logically open.
        // The UI closes the drawer animation before navigating when possible;
        // keeping this invariant here also covers toolbar/deep-link callers.
        state.copy(host = event.host, drawerPage = null)
    )
    CompatibilityEvent.Back -> reduceCompatibilityBack(state)
}

private fun reduceCompatibilityBack(state: CompatibilityWorkspaceState): CompatibilityReduction {
    val search = state.search
    // The APK Activity dispatches Back to the open drawer before the event can
    // reach SearchView or the current screen. Keep this boundary explicit so
    // a simultaneous search/drawer state closes the drawer first.
    if (state.drawerPage != null) {
        return CompatibilityReduction(state.copy(drawerPage = null))
    }
    if (state.selectorOpen && state.selectorPresentation == SelectorPresentation.OVER) {
        return CompatibilityReduction(state.copy(selectorOpen = false))
    }
    if (state.overlays.lastOrNull() is CompatOverlay.Dialog || state.overlays.lastOrNull() is CompatOverlay.ContextMenu) {
        return CompatibilityReduction(state.copy(overlays = state.overlays.dropLast(1)))
    }
    if (state.overlays.lastOrNull() is CompatOverlay.Quote) {
        return CompatibilityReduction(state.copy(overlays = state.overlays.dropLast(1)))
    }
    val extractionIndex = state.overlays.indexOfLast { it is CompatOverlay.Extraction }
    if (extractionIndex >= 0) {
        return CompatibilityReduction(state.copy(overlays = state.overlays.take(extractionIndex)))
    }
    if (search?.imeVisible == true) {
        return CompatibilityReduction(
            state.copy(search = search.copy(imeVisible = false)),
            listOf(CompatibilityEffect.HideIme)
        )
    }
    if (search?.focused == true) {
        return CompatibilityReduction(state.copy(search = search.copy(focused = false)))
    }
    if (search != null) {
        return CompatibilityReduction(state.copy(search = null))
    }
    return when (val host = state.host) {
        is CompatHost.Viewer -> CompatibilityReduction(
            state.copy(
                host = when (host.caller) {
                    CompatViewerCaller.THREAD -> CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG)
                    CompatViewerCaller.GALLERY -> CompatHost.Gallery(host.tabKey, host.index, host.postNo)
                }
            )
        )
        is CompatHost.Gallery -> CompatibilityReduction(
            state.copy(host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG))
        )
        is CompatHost.Post -> CompatibilityReduction(
            state.copy(host = CompatHost.ThreadWorkspace(CompatThreadOrigin.CATALOG))
        )
        is CompatHost.PostBuild -> CompatibilityReduction(state.copy(host = CompatHost.Catalog(host.boardKey)))
        is CompatHost.PostDrawing -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.ToolbarEditor -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.SavedThreads -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.ChangeLog -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.License -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.Help -> CompatibilityReduction(state.copy(host = host.origin))
        is CompatHost.Settings -> {
            if (host.path == "root") {
                CompatibilityReduction(state.copy(host = host.origin))
            } else if (host.returnToRoot) {
                CompatibilityReduction(state.copy(host = host.copy(path = "root")))
            } else {
                CompatibilityReduction(state.copy(host = host.origin))
            }
        }
        is CompatHost.ThreadWorkspace -> when (host.origin) {
            CompatThreadOrigin.MAIN -> CompatibilityReduction(state.copy(host = CompatHost.Main))
            CompatThreadOrigin.CATALOG -> {
                val boardKey = state.catalogHostBoardKey
                    ?: state.tabs.firstOrNull { it.key == state.activeTabKey }?.boardKey
                if (boardKey != null) CompatibilityReduction(state.copy(host = CompatHost.Catalog(boardKey)))
                else CompatibilityReduction(state.copy(host = CompatHost.Main))
            }
            CompatThreadOrigin.DEEP_LINK -> CompatibilityReduction(state.copy(host = CompatHost.Main))
        }
        is CompatHost.Catalog -> CompatibilityReduction(state.copy(host = CompatHost.Main))
        CompatHost.Main -> CompatibilityReduction(state, listOf(CompatibilityEffect.FinishApplication))
    }
}
