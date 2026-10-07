package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.saturatingEpochAdd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val MAX_COMPAT_LEGACY_STATE_BYTES = 32L * 1024L * 1024L

/**
 * Desktop persistence for the compatibility profile.
 *
 * The store deliberately has no UI dependency.  Every public mutation is
 * serialized, persisted before its Flow is published, and uses the same
 * identity/revision rules as the Android compatibility store.  Keeping this
 * boundary independent from the host lets the on-disk implementation remain
 * native SQLite without changing CompatibilityApp or its callers.
 */
internal class DesktopCompatibilityStore(
    private val fileSystem: FileSystem,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : CompatibilityStore {
    private val mutex = Mutex()
    // Lenient so a downgrade (unknown fields or enum values written by a newer
    // build) still reads the profile instead of discarding it.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val database = DesktopCompatibilityDatabase(fileSystem)
    // Only read during the one-time migration from builds which stored this
    // profile as JSON.  New writes are committed exclusively through SQLite.
    private val legacyStatePath = "compatibility/ios_compatibility_state.json"
    private val legacyBackupPath = "compatibility/ios_compatibility_state.backup.json"

    private var initialized = false
    private var state = PersistedCompatibilityState()
    // The cache rows as last written (or read): compared by identity, so a change that touches
    // no snapshot/catalog costs no serialization and no write of them.
    private var persistedCaches: Map<String, Any> = emptyMap()
    private var persistedCacheBytes: Map<String, Long> = emptyMap()
    // False until the rows on disk are known to match memory (a fresh, legacy or non-partitioned profile).
    private var cacheRowsInSync = false

    private val boardsState = MutableStateFlow<List<CompatBoard>>(emptyList())
    private val tabsState = MutableStateFlow<List<CompatTab>>(emptyList())
    private val historyState = MutableStateFlow<List<CompatHistoryEntry>>(emptyList())
    private val workspaceState = MutableStateFlow(CompatWorkspaceRecord())
    private val preferencesState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val ngRulesState = MutableStateFlow<List<CompatNgRule>>(emptyList())

    override val boards: Flow<List<CompatBoard>> = boardsState
    override val tabs: Flow<List<CompatTab>> = tabsState
    override val history: Flow<List<CompatHistoryEntry>> = historyState
    override val workspace: Flow<CompatWorkspaceRecord> = workspaceState
    override val preferences: Flow<Map<String, String>> = preferencesState
    override val ngRules: Flow<List<CompatNgRule>> = ngRulesState

    fun close() = database.close()

    suspend fun initialize() = withContext(AppDispatchers.io) {
        mutex.withLock {
            ensureInitializedLocked()
            var changed = false
            val repairedBoards = repairBoards(state.boards)
            val validBoardKeys = repairedBoards.mapTo(mutableSetOf(), CompatBoard::key)
            val validTabs = state.tabs.filter { it.boardKey in validBoardKeys }
            val repairedTabs = if (validTabs.size > MAX_TABS) trimTabs(validTabs, state.workspace.activeTabKey) else validTabs
            val validHistory = state.history.filter { it.boardKey in validBoardKeys }
            val repairedHistory = if (validHistory.size > MAX_HISTORY) trimHistory(validHistory) else validHistory
            // Move image hashes stored as preferences by older versions into
            // their cache table before the preference cap drops real settings.
            // The table is written first; a crash before the state is saved
            // just repeats this idempotent copy.
            val legacyImagePhashes = state.preferences.filterKeys(::isCompatImagePhashCacheKey)
            if (legacyImagePhashes.isNotEmpty()) {
                database.writeImagePhashes(legacyImagePhashes.filterValues(::isValidCompatImagePhash), nowMillis())
            }
            val repairedPreferences = state.preferences.asSequence()
                .filterNot { (key, _) -> isCompatImagePhashCacheKey(key) }
                .filter { (key, value) -> isValidCompatPreference(key, value) }
                .take(MAX_PREFERENCES)
                .associate { it.toPair() }
            val validTabKeys = repairedTabs.mapTo(mutableSetOf(), CompatTab::key)
            val repairedRules = state.ngRules.asSequence()
                .filter {
                    isValidCompatNgRule(it) &&
                        isCompatNgScopeValid(it.kind, it.scopeKey, validBoardKeys, validTabKeys)
                }
                .take(MAX_COMPAT_NG_RULES)
                .toList()
            val pending = state.closedBatch
                ?.takeIf { it.expiresAtEpochMillis > nowMillis() }
                ?.let { batch ->
                    val tabs = batch.tabs.asSequence()
                        .filter { closed ->
                            closed.tab.boardKey in validBoardKeys &&
                                (closed.snapshot?.posts?.size ?: 0) <= MAX_COMPAT_THREAD_SNAPSHOT_POSTS
                        }
                        .distinctBy { closed -> closed.tab.key }
                        .take(MAX_TABS)
                        .toList()
                    batch.takeIf { tabs.isNotEmpty() }?.copy(
                        id = batch.id.take(MAX_COMPAT_BOARD_FIELD_CHARS),
                        tabs = tabs,
                        selectedTabKey = batch.selectedTabKey?.takeIf { selected ->
                            tabs.any { closed -> closed.tab.key == selected }
                        }
                    )
                }
            val repairedReplyDrafts = state.replyDrafts
                .asSequence()
                .filter { it.tabKey in validTabKeys }
                .distinctBy(CompatReplyDraft::tabKey)
                .take(MAX_TABS)
                .toList()
            val repairedBuildDrafts = state.buildDrafts
                .asSequence()
                .filter { it.boardKey in validBoardKeys }
                .distinctBy(CompatBuildDraft::boardKey)
                .take(MAX_BOARDS)
                .toList()
            val repairedSnapshots = state.snapshots
                .asSequence()
                .filter { snapshot ->
                    (snapshot.tabKey in validTabKeys || COMPAT_SHARED_SNAPSHOT_KEY_REGEX.matches(snapshot.tabKey)) &&
                        snapshot.posts.size <= MAX_COMPAT_THREAD_SNAPSHOT_POSTS
                }
                .distinctBy(CompatThreadSnapshot::tabKey)
                .take(MAX_THREAD_SNAPSHOTS)
                .toList()
            val repairedSnapshotKeys = repairedSnapshots.mapTo(mutableSetOf(), CompatThreadSnapshot::tabKey)
            val repairedSnapshotAccess = state.snapshotAccess.filterKeys { it in repairedSnapshotKeys }
            val repairedCatalogPreferences = state.catalogPreferences
                .asSequence()
                .filter { it.boardKey in validBoardKeys }
                .distinctBy(CompatCatalogPreference::boardKey)
                .take(MAX_BOARDS)
                .toList()
            val repairedCatalogSnapshots = state.catalogSnapshots
                .asSequence()
                .filter { record ->
                    record.boardKey in validBoardKeys &&
                        record.items.size <= MAX_CATALOG_ITEMS &&
                        enumValueOrDefault(record.sort, CompatCatalogSort.CATALOG).name == record.sort
                }
                .groupBy { record -> record.boardKey to record.sort }
                .values
                .flatMap { records ->
                    records.sortedByDescending(CatalogSnapshotRecord::revision).take(MAX_CATALOG_GENERATIONS)
                }
            val repairedDroppedItems = state.droppedItems
                .asSequence()
                .filter { it.boardKey in validBoardKeys }
                .groupBy(DroppedCatalogRecord::boardKey)
                .values
                .flatMap { rows ->
                    rows.sortedByDescending(DroppedCatalogRecord::lastSeenAtEpochMillis).take(MAX_DROPPED_ITEMS)
                }
            val repairedToolbars = state.toolbars
                .asSequence()
                .filter { record ->
                    record.items.size <= MAX_TOOLBAR_ITEMS &&
                        CompatToolbarSurface.entries.any { it.name == record.surface }
                }
                .distinctBy(ToolbarRecord::surface)
                .take(CompatToolbarSurface.entries.size)
                .toList()
            val repairedTombstones = trimHistoryTombstones(state.historyTombstones)
            val repairedArchiveRows = state.archiveRows
                .asSequence()
                .take(ARCHIVE_REPORT_MAX_ROWS)
                .map { row -> row.copy(attemptCount = row.attemptCount.coerceAtLeast(0)) }
                .toList()
            if (
                repairedBoards != state.boards || repairedTabs != state.tabs || repairedHistory != state.history ||
                repairedRules.size != state.ngRules.size ||
                repairedPreferences.size != state.preferences.size || pending != state.closedBatch ||
                repairedReplyDrafts != state.replyDrafts || repairedBuildDrafts != state.buildDrafts ||
                repairedSnapshots != state.snapshots || repairedSnapshotAccess != state.snapshotAccess ||
                repairedCatalogPreferences != state.catalogPreferences ||
                repairedCatalogSnapshots != state.catalogSnapshots || repairedDroppedItems != state.droppedItems ||
                repairedToolbars != state.toolbars || repairedTombstones != state.historyTombstones ||
                repairedArchiveRows != state.archiveRows
            ) {
                state = state.copy(
                    boards = repairedBoards,
                    tabs = repairedTabs,
                    history = repairedHistory,
                    preferences = repairedPreferences,
                    ngRules = repairedRules,
                    replyDrafts = repairedReplyDrafts,
                    buildDrafts = repairedBuildDrafts,
                    snapshots = repairedSnapshots,
                    snapshotAccess = repairedSnapshotAccess,
                    catalogPreferences = repairedCatalogPreferences,
                    catalogSnapshots = repairedCatalogSnapshots,
                    droppedItems = repairedDroppedItems,
                    toolbars = repairedToolbars,
                    closedBatch = pending,
                    historyTombstones = repairedTombstones,
                    archiveRows = repairedArchiveRows
                )
                changed = true
            }
            changed = enforceSnapshotQuotaLocked() || changed
            if (changed) persistLocked()
            publishLocked()
        }
    }

    override suspend fun bootstrapBoardsIfNeeded(modernBoards: List<BoardSummary>): Boolean = mutate {
        if (it.boardBootstrapComplete) return@mutate false
        val imported = mergeBoards(it.boards, validatedModernBoards(modernBoards))
        state = it.copy(boards = imported, boardBootstrapComplete = true)
        true
    }

    override suspend fun importModernBoards(modernBoards: List<BoardSummary>): Int = mutate {
        val candidates = validatedModernBoards(modernBoards)
        val before = it.boards.mapTo(mutableSetOf(), CompatBoard::canonicalUrl)
        state = it.copy(boards = mergeBoards(it.boards, candidates))
        candidates.count { candidate -> candidate.canonicalUrl !in before }
    }

    override suspend fun importModernHistory(
        modernHistory: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>
    ): Int = mutate {
        // Only rows which change and survive trimming are written; the
        // compatibility update time is kept (see planModernHistoryImport).
        val writes = planModernHistoryImport(
            modernHistory = modernHistory,
            current = it.history,
            knownBoardKeys = it.boards.mapTo(mutableSetOf(), CompatBoard::key),
            tombstoneAt = { url -> it.historyTombstones[url] },
            historyLimit = HISTORY_LIMIT_TRIGGER,
            retain = { entries -> trimHistory(entries) }
        )
        if (writes.isEmpty()) return@mutate 0
        val next = LinkedHashMap<String, CompatHistoryEntry>()
        it.history.forEach { entry -> next[entry.canonicalUrl] = entry }
        writes.forEach { entry -> next[entry.canonicalUrl] = entry }
        state = it.copy(
            history = trimHistory(next.values.toList()),
            historyTombstones = it.historyTombstones - writes.mapTo(mutableSetOf(), CompatHistoryEntry::canonicalUrl)
        )
        writes.size
    }

    override suspend fun upsertBoard(board: CompatBoard) = mutate {
        require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) { "Invalid Futaba board URL" }
        state = it.copy(boards = replaceBoard(it.boards, board))
    }

    override suspend fun upsertBoards(boards: List<CompatBoard>) = mutate {
        boards.forEach { board ->
            require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) {
                "Invalid Futaba board URL"
            }
        }
        state = it.copy(
            boards = boards.fold(it.boards) { current, board -> replaceBoard(current, board) }
        )
    }

    override suspend fun reorderBoards(orderedKeys: List<String>) = mutate {
        val existing = it.boards.map(CompatBoard::key)
        require(existing.size == orderedKeys.size && existing.toSet() == orderedKeys.toSet()) {
            "Board reorder must contain every existing board exactly once"
        }
        state = it.copy(boards = orderedKeys.mapIndexed { index, key ->
            it.boards.first { board -> board.key == key }.copy(sortOrder = index)
        })
    }

    override suspend fun deleteBoard(boardKey: String) = mutate {
        val remainingBoards = it.boards.filterNot { board -> board.key == boardKey }
        val remainingTabs = it.tabs.filterNot { tab -> tab.boardKey == boardKey }
        val remainingTabKeys = remainingTabs.mapTo(mutableSetOf(), CompatTab::key)
        val nextWorkspace = it.workspace.copy(
            activeTabKey = it.workspace.activeTabKey?.takeIf { key -> key in remainingTabKeys }
                ?: remainingTabs.firstOrNull()?.key,
            catalogHostBoardKey = it.workspace.catalogHostBoardKey?.takeIf { key ->
                remainingBoards.any { board -> board.key == key }
            }
        )
        state = it.copy(
            boards = remainingBoards,
            tabs = remainingTabs,
            history = it.history.filterNot { entry -> entry.boardKey == boardKey },
            replyDrafts = it.replyDrafts.filter { draft -> draft.tabKey in remainingTabKeys },
            buildDrafts = it.buildDrafts.filterNot { draft -> draft.boardKey == boardKey },
            snapshots = it.snapshots.filter { snapshot -> snapshot.tabKey in remainingTabKeys || snapshot.tabKey.startsWith("compat_tab_") },
            catalogPreferences = it.catalogPreferences.filterNot { pref -> pref.boardKey == boardKey },
            catalogSnapshots = it.catalogSnapshots.filterNot { snapshot -> snapshot.boardKey == boardKey },
            droppedItems = it.droppedItems.filterNot { dropped -> dropped.boardKey == boardKey },
            ngRules = it.ngRules.filterNot { rule -> rule.scopeKey == boardKey },
            closedBatch = it.closedBatch?.let { batch ->
                val tabs = batch.tabs.filterNot { closed -> closed.tab.boardKey == boardKey }
                if (tabs.isEmpty()) null else batch.copy(
                    tabs = tabs,
                    selectedTabKey = batch.selectedTabKey?.takeIf { selected -> tabs.any { closed -> closed.tab.key == selected } }
                )
            },
            workspace = nextWorkspace
        )
    }

    override suspend fun openTab(tab: CompatTab, historyEntry: CompatHistoryEntry?) = mutate {
        val currentAnchor = it.tabs.firstOrNull { current -> current.key == tab.key }?.scrollAnchor
        val durableTab = currentAnchor?.let { anchor -> tab.copy(scrollAnchor = anchor) } ?: tab
        val nextTabs = replaceTab(it.tabs, durableTab)
        val nextHistory = historyEntry?.let { entry ->
            val current = it.history.firstOrNull { current -> current.canonicalUrl == entry.canonicalUrl }
            replaceHistory(it.history, mergeCompatHistoryEntry(entry, current, recordVisit = true))
        } ?: it.history
        state = it.copy(
            tabs = trimTabs(nextTabs, tab.key),
            history = trimHistory(nextHistory),
            historyTombstones = historyEntry?.let { entry -> it.historyTombstones - entry.canonicalUrl } ?: it.historyTombstones,
            workspace = it.workspace.copy(activeTabKey = tab.key, generation = it.workspace.generation + 1)
        )
    }

    override suspend fun updateTab(tab: CompatTab) = mutate {
        val anchor = it.tabs.firstOrNull { current -> current.key == tab.key }?.scrollAnchor
        state = it.copy(tabs = replaceTab(it.tabs, anchor?.let { tab.copy(scrollAnchor = it) } ?: tab))
    }

    override suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean = mutate {
        val current = it.tabs.firstOrNull { tab -> tab.key == tabKey } ?: return@mutate false
        val next = transform(current)?.pinnedTo(current) ?: return@mutate false
        // Replace in place: re-appending would reorder the tab strip.
        state = it.copy(tabs = it.tabs.map { tab -> if (tab.key == tabKey) next else tab })
        true
    }

    override suspend fun selectTab(tabKey: String?) = mutate {
        require(tabKey == null || it.tabs.any { tab -> tab.key == tabKey }) { "Unknown compatibility tab: $tabKey" }
        state = it.copy(workspace = it.workspace.copy(activeTabKey = tabKey, generation = it.workspace.generation + 1))
    }

    override suspend fun closeTabs(
        tabKeys: Set<String>,
        nowEpochMillis: Long,
        finalScrollAnchors: Map<String, ScrollAnchor>
    ): ClosedTabBatch? = mutate {
        val anchored = it.tabs.map { tab ->
            finalScrollAnchors[tab.key]?.let { anchor -> tab.copy(scrollAnchor = anchor) } ?: tab
        }
        val closed = anchored.mapIndexedNotNull { index, tab ->
            tab.takeIf { current -> current.key in tabKeys }?.let { ClosedCompatTab(it, index) }
        }
        if (closed.isEmpty()) return@mutate null
        val remaining = anchored.filterNot { tab -> tab.key in tabKeys }
        val nextHistory = it.history.map { history ->
            anchored.firstOrNull { tab -> tab.canonicalUrl == history.canonicalUrl }
                ?.let { tab -> history.copy(scrollAnchor = tab.scrollAnchor) } ?: history
        }
        val batch = ClosedTabBatch(
            id = "close-$nowEpochMillis-${closed.joinToString("-") { closedTab -> closedTab.tab.key }}",
            tabs = closed,
            selectedTabKey = it.workspace.activeTabKey,
            expiresAtEpochMillis = saturatingEpochAdd(nowEpochMillis, UNDO_WINDOW_MILLIS)
        )
        state = it.copy(
            tabs = remaining,
            replyDrafts = it.replyDrafts.filterNot { draft -> draft.tabKey in tabKeys },
            history = nextHistory,
            workspace = it.workspace.copy(
                activeTabKey = it.workspace.activeTabKey?.takeIf { key -> remaining.any { tab -> tab.key == key } }
                    ?: remaining.firstOrNull()?.key,
                generation = it.workspace.generation + 1
            ),
            closedBatch = batch
        )
        batch
    }

    override suspend fun restoreClosedTabs(batch: ClosedTabBatch) = mutate {
        val durable = it.closedBatch?.takeIf { current -> current.id == batch.id } ?: batch
        val boardKeys = it.boards.mapTo(mutableSetOf(), CompatBoard::key)
        val restored = durable.tabs.filter { closed -> closed.tab.boardKey in boardKeys }
        val tabs = it.tabs.toMutableList()
        restored.forEach { closed ->
            tabs.removeAll { tab -> tab.key == closed.tab.key }
            tabs.add(closed.originalIndex.coerceIn(0, tabs.size), closed.tab)
        }
        state = it.copy(
            tabs = trimTabs(
                tabs,
                durable.selectedTabKey?.takeIf { key -> tabs.any { tab -> tab.key == key } } ?: it.workspace.activeTabKey
            ),
            workspace = it.workspace.copy(
                activeTabKey = durable.selectedTabKey?.takeIf { key -> tabs.any { tab -> tab.key == key } }
                    ?: it.workspace.activeTabKey,
                generation = it.workspace.generation + 1
            ),
            closedBatch = null
        )
    }

    override suspend fun loadPendingClosedTabs(nowEpochMillis: Long): ClosedTabBatch? = mutate {
        val pending = it.closedBatch?.takeIf { batch -> batch.expiresAtEpochMillis > nowEpochMillis }
        if (pending != it.closedBatch) state = it.copy(closedBatch = pending)
        pending
    }

    override suspend fun upsertHistory(entry: CompatHistoryEntry) = mutate {
        if (entry.canonicalUrl in it.historyTombstones) return@mutate
        val current = it.history.firstOrNull { current -> current.canonicalUrl == entry.canonicalUrl }
        state = it.copy(history = trimHistory(replaceHistory(it.history, mergeCompatHistoryEntry(entry, current))))
    }

    override suspend fun updateHistoryIfPresent(
        canonicalUrl: String,
        transform: (CompatHistoryEntry) -> CompatHistoryEntry?
    ): Boolean = mutate {
        if (canonicalUrl in it.historyTombstones) return@mutate false
        val current = it.history.firstOrNull { entry -> entry.canonicalUrl == canonicalUrl } ?: return@mutate false
        val next = transform(current)?.copy(canonicalUrl = current.canonicalUrl) ?: return@mutate false
        state = it.copy(history = it.history.map { entry -> if (entry.canonicalUrl == canonicalUrl) next else entry })
        true
    }

    /**
     * One-transaction form of [applyCatalogReplyCountsOneByOne]: the same per-row rules
     * (counts only grow, missing rows and tombstoned history are never created, key/URL/anchor
     * stay as stored, rows keep their position), but the whole state is persisted once.
     */
    override suspend fun applyCatalogReplyCounts(updates: List<CompatCatalogReplyCountUpdate>): Int = mutate {
        if (updates.isEmpty()) return@mutate 0
        val tabs = it.tabs.toMutableList()
        val history = it.history.toMutableList()
        var changedTabs = 0
        var historyChanged = false
        for (update in updates) {
            val tabIndex = tabs.indexOfFirst { tab -> tab.key == update.tabKey }
            if (tabIndex >= 0 && tabs[tabIndex].replyCount < update.replyCount) {
                val current = tabs[tabIndex]
                tabs[tabIndex] = current.copy(replyCount = update.replyCount).pinnedTo(current)
                changedTabs++
            }
            if (update.canonicalUrl in it.historyTombstones) continue
            val historyIndex = history.indexOfFirst { entry -> entry.canonicalUrl == update.canonicalUrl }
            if (historyIndex >= 0 && history[historyIndex].replyCount < update.replyCount) {
                val current = history[historyIndex]
                history[historyIndex] = current.copy(replyCount = update.replyCount, canonicalUrl = current.canonicalUrl)
                historyChanged = true
            }
        }
        if (changedTabs > 0 || historyChanged) {
            state = it.copy(
                tabs = if (changedTabs > 0) tabs else it.tabs,
                history = if (historyChanged) history else it.history
            )
        }
        changedTabs
    }

    override suspend fun recordHistoryVisit(entry: CompatHistoryEntry) = mutate {
        val current = it.history.firstOrNull { current -> current.canonicalUrl == entry.canonicalUrl }
        state = it.copy(
            history = trimHistory(replaceHistory(it.history, mergeCompatHistoryEntry(entry, current, recordVisit = true))),
            historyTombstones = it.historyTombstones - entry.canonicalUrl
        )
    }

    override suspend fun deleteHistory(canonicalUrl: String) = mutate {
        state = it.copy(
            history = it.history.filterNot { entry -> entry.canonicalUrl == canonicalUrl },
            historyTombstones = trimHistoryTombstones(
                it.historyTombstones + (canonicalUrl.take(MAX_COMPAT_URL_CHARS) to nowMillis())
            )
        )
    }

    override suspend fun clearHistory() = mutate {
        val tombstones = it.historyTombstones.toMutableMap()
        (it.history.map(CompatHistoryEntry::canonicalUrl) + it.tabs.map(CompatTab::canonicalUrl)).forEach { url ->
            tombstones[url] = nowMillis()
        }
        state = it.copy(
            history = emptyList(),
            historyTombstones = trimHistoryTombstones(tombstones)
        )
    }

    override suspend fun saveDraft(draft: CompatReplyDraft) = mutate {
        state = it.copy(replyDrafts = replaceBy(it.replyDrafts, draft, CompatReplyDraft::tabKey))
    }

    override suspend fun loadDraft(tabKey: String): CompatReplyDraft? = read { current ->
        current.replyDrafts.firstOrNull { draft -> draft.tabKey == tabKey }
    }

    override suspend fun deleteDraft(tabKey: String) = mutate {
        state = it.copy(replyDrafts = it.replyDrafts.filterNot { draft -> draft.tabKey == tabKey })
    }

    override suspend fun saveBuildDraft(draft: CompatBuildDraft) = mutate {
        state = it.copy(buildDrafts = replaceBy(it.buildDrafts, draft, CompatBuildDraft::boardKey))
    }

    override suspend fun loadBuildDraft(boardKey: String): CompatBuildDraft? = read { current ->
        current.buildDrafts.firstOrNull { draft -> draft.boardKey == boardKey }
    }

    override suspend fun deleteBuildDraft(boardKey: String) = mutate {
        state = it.copy(buildDrafts = it.buildDrafts.filterNot { draft -> draft.boardKey == boardKey })
    }

    override suspend fun saveThreadSnapshot(snapshot: CompatThreadSnapshot): Boolean = mutate {
        validateSnapshot(snapshot)
        val current = it.snapshots.firstOrNull { row -> row.tabKey == snapshot.tabKey }
        if (current != null && current.revision >= snapshot.revision) return@mutate false
        state = it.copy(
            snapshots = replaceBy(it.snapshots, snapshot, CompatThreadSnapshot::tabKey),
            snapshotAccess = it.snapshotAccess + (snapshot.tabKey to nowMillis())
        )
        enforceSnapshotQuotaLocked()
        true
    }

    override suspend fun loadThreadSnapshot(tabKey: String): CompatThreadSnapshot? =
        loadThreadSnapshotWithoutFullRewrite { current ->
            current.snapshots.firstOrNull { row -> row.tabKey == tabKey }
        }

    override suspend fun loadThreadSnapshotByCanonicalUrl(canonicalUrl: String): CompatThreadSnapshot? {
        val parsed = canonicalizeThreadUrl(canonicalUrl) ?: return null
        return loadThreadSnapshotWithoutFullRewrite { current ->
            val key = compatTabKey(parsed.canonicalUrl)
            val actualKey = if (current.snapshots.any { snapshot -> snapshot.tabKey == key }) key else {
                current.tabs.firstOrNull { tab -> tab.canonicalUrl == parsed.canonicalUrl }?.key
            }
            actualKey?.let { keyValue -> current.snapshots.firstOrNull { row -> row.tabKey == keyValue } }
        }
    }

    override suspend fun saveSharedThreadSnapshot(
        canonicalUrl: String,
        originalUrl: String,
        boardName: String,
        title: String,
        thumbnailUrl: String?,
        snapshot: CompatThreadSnapshot
    ): Boolean = mutate {
        val parsed = canonicalizeThreadUrl(canonicalUrl) ?: return@mutate false
        val shared = snapshot.copy(tabKey = compatTabKey(parsed.canonicalUrl))
        validateSnapshot(shared)
        val current = it.snapshots.firstOrNull { row -> row.tabKey == shared.tabKey }
        if (current != null && current.revision >= shared.revision) return@mutate false
        state = it.copy(
            snapshots = replaceBy(it.snapshots, shared, CompatThreadSnapshot::tabKey),
            snapshotAccess = it.snapshotAccess + (shared.tabKey to nowMillis())
        )
        enforceSnapshotQuotaLocked()
        true
    }

    override suspend fun threadSnapshotCacheUsageBytes(): Long = read { current ->
        sumEncodedSnapshotBytes(current.snapshots)
    }

    override suspend fun clearThreadSnapshotCache(): Long = mutate {
        val removed = sumEncodedSnapshotBytes(it.snapshots)
        state = it.copy(
            snapshots = emptyList(),
            snapshotAccess = emptyMap(),
            tabs = it.tabs.map { tab -> tab.copy(snapshotRevision = 0L) }
        )
        removed
    }

    override suspend fun updateScrollAnchor(tabKey: String, anchor: ScrollAnchor) =
        withContext(AppDispatchers.io) {
            mutex.withLock {
                ensureInitializedLocked()
                val tab = state.tabs.firstOrNull { current -> current.key == tabKey }
                    ?: return@withLock
                val nextTabs = replaceTab(state.tabs, tab.copy(scrollAnchor = anchor))
                val nextHistory = state.history.map { entry ->
                    if (entry.canonicalUrl == tab.canonicalUrl) entry.copy(scrollAnchor = anchor) else entry
                }
                if (nextTabs == state.tabs && nextHistory == state.history) return@withLock
                // A scroll event must not serialize snapshots/catalogs/history
                // into one huge JSON blob. Persist just this small anchor; the
                // next ordinary full commit absorbs and clears the overlay.
                database.writeScrollAnchor(
                    tabKey = tabKey,
                    anchorPayload = json.encodeToString(ScrollAnchor.serializer(), anchor),
                    updatedAtMillis = nowMillis()
                )
                state = state.copy(tabs = nextTabs, history = nextHistory)
                tabsState.value = state.tabs.sortedByDescending(CompatTab::insertedAtEpochMillis)
                historyState.value = state.history.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis)
            }
        }

    override suspend fun updateWorkspace(record: CompatWorkspaceRecord) = mutate {
        state = it.copy(workspace = record)
    }

    override suspend fun loadCatalogPreference(boardKey: String): CompatCatalogPreference = read { current ->
        current.catalogPreferences.firstOrNull { pref -> pref.boardKey == boardKey }
            ?: CompatCatalogPreference(boardKey)
    }

    override suspend fun saveCatalogPreference(preference: CompatCatalogPreference) = mutate {
        require(it.boards.any { board -> board.key == preference.boardKey }) { "Unknown compatibility board" }
        state = it.copy(catalogPreferences = replaceBy(it.catalogPreferences, preference, CompatCatalogPreference::boardKey))
    }

    override suspend fun saveCatalogSnapshot(
        snapshot: CompatCatalogSnapshot,
        trackDropped: Boolean,
        requestedThreadCount: Int,
        activeDroppedThreadIds: Set<String>
    ): Boolean = mutate {
        require(it.boards.any { board -> board.key == snapshot.boardKey }) { "Unknown compatibility board" }
        require(snapshot.revision >= 0L && snapshot.items.size <= MAX_CATALOG_ITEMS) { "Invalid compatibility catalog snapshot" }
        require(snapshot.items.map { item -> item.id }.distinct().size == snapshot.items.size) { "Compatibility catalog item IDs must be unique" }
        val previous = it.catalogSnapshots
            .filter { record -> record.boardKey == snapshot.boardKey && record.sort == snapshot.sort.name }
            .maxByOrNull(CatalogSnapshotRecord::revision)
        if (previous != null && previous.revision >= snapshot.revision) return@mutate false
        val previousItems = previous?.items.orEmpty()
        val states = buildCompatCatalogItemStates(
            items = snapshot.items,
            previousStates = previousItems.associate { item -> item.id to item.toState() } + snapshot.itemStates,
            fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis,
            sort = snapshot.sort,
            requestedThreadCount = requestedThreadCount
        )
        val record = CatalogSnapshotRecord(
            boardKey = snapshot.boardKey,
            sort = snapshot.sort.name,
            revision = snapshot.revision,
            fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis,
            items = snapshot.items.map { item -> CompatCatalogSnapshotItem.from(item, states[item.id]) }
        )
        val currentThreadIds = snapshot.items.mapTo(mutableSetOf()) { item -> item.id }
        var dropped = it.droppedItems.filterNot { row ->
            row.boardKey == snapshot.boardKey && row.threadId in currentThreadIds
        }
        if (trackDropped && previous != null) {
            val diff = diffCompatCatalogGenerations(
                current = snapshot.items,
                previous = previousItems.map(CompatCatalogSnapshotItem::toCatalogItem),
                requestedThreadCount = requestedThreadCount,
                enabled = true
            )
            val previousById = previousItems.associateBy(CompatCatalogSnapshotItem::id)
            val classified = buildList {
                diff.vanishedWithin.forEach { item ->
                    add(item to if (item.id in activeDroppedThreadIds) CompatCatalogDroppedClass.ISOLATED else CompatCatalogDroppedClass.DELETED)
                }
                diff.vanishedBottom.forEach { item -> add(item to CompatCatalogDroppedClass.DIE) }
            }
            classified.forEach { (item, classification) ->
                dropped = dropped.filterNot { row -> row.boardKey == snapshot.boardKey && row.threadId == item.id } + DroppedCatalogRecord(
                    boardKey = snapshot.boardKey,
                    threadId = item.id,
                    item = previousById[item.id] ?: CompatCatalogSnapshotItem.from(item),
                    droppedAtEpochMillis = snapshot.fetchedAtEpochMillis,
                    lastSeenAtEpochMillis = previous.fetchedAtEpochMillis,
                    classification = classification.name
                )
            }
        }
        val trimmedSnapshots = (it.catalogSnapshots.filterNot { existing ->
            existing.boardKey == record.boardKey && existing.sort == record.sort && existing.revision == record.revision
        } + record).groupBy { existing -> existing.boardKey to existing.sort }
            .values.flatMap { records -> records.sortedByDescending(CatalogSnapshotRecord::revision).take(MAX_CATALOG_GENERATIONS) }
        val replyCounts = catalogReplyCountsByUrl(snapshot.items)
        state = it.copy(
            tabs = it.tabs.map { tab -> tab.withCatalogReplyCount(replyCounts, snapshot.fetchedAtEpochMillis) },
            catalogSnapshots = trimmedSnapshots,
            droppedItems = dropped.groupBy { row -> row.boardKey }
                .values.flatMap { rows -> rows.sortedByDescending(DroppedCatalogRecord::lastSeenAtEpochMillis).take(MAX_DROPPED_ITEMS) }
        )
        true
    }

    override suspend fun loadCatalogSnapshot(
        boardKey: String,
        sort: CompatCatalogSort,
        generation: Int
    ): CompatCatalogSnapshot? = read { current ->
        if (generation < 0 || generation >= MAX_CATALOG_GENERATIONS) return@read null
        current.catalogSnapshots.filter { record -> record.boardKey == boardKey && record.sort == sort.name }
            .sortedByDescending(CatalogSnapshotRecord::revision)
            .getOrNull(generation)
            ?.let { record ->
                CompatCatalogSnapshot(
                    boardKey = record.boardKey,
                    sort = sort,
                    revision = record.revision,
                    fetchedAtEpochMillis = record.fetchedAtEpochMillis,
                    items = record.items.map(CompatCatalogSnapshotItem::toCatalogItem),
                    itemStates = record.items.associate { item -> item.id to item.toState() }
                )
            }
    }

    override suspend fun loadDroppedCatalogItems(boardKey: String): List<CompatDroppedCatalogItem> = read { current ->
        current.droppedItems.filter { row -> row.boardKey == boardKey }
            .sortedByDescending(DroppedCatalogRecord::lastSeenAtEpochMillis)
            .map { row ->
                CompatDroppedCatalogItem(
                    boardKey = row.boardKey,
                    item = row.item.toCatalogItem(),
                    droppedAtEpochMillis = row.droppedAtEpochMillis,
                    lastSeenAtEpochMillis = row.lastSeenAtEpochMillis,
                    classification = enumValueOrDefault(row.classification, CompatCatalogDroppedClass.DIE)
                )
            }
    }

    override suspend fun deleteDroppedCatalogItems(boardKey: String, classification: CompatCatalogDroppedClass): Int = mutate {
        val count = it.droppedItems.count { row -> row.boardKey == boardKey && row.classification == classification.name }
        if (count > 0) state = it.copy(droppedItems = it.droppedItems.filterNot { row -> row.boardKey == boardKey && row.classification == classification.name })
        count
    }

    override suspend fun loadPreference(key: String): String? = read { current -> current.preferences[key] }

    override suspend fun savePreference(key: String, value: String) = mutate {
        requireValidCompatPreference(key, value)
        state = it.copy(preferences = it.preferences + (key to value))
        // Only the cache-size setting changes the snapshot quota. Checking it for
        // every preference encoded all cached snapshots on each save.
        if (key == COMPAT_THREAD_CACHE_PREFERENCE_KEY) enforceSnapshotQuotaLocked()
        Unit
    }

    override suspend fun loadImagePhashes(keys: Collection<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        return withContext(AppDispatchers.io) {
            mutex.withLock {
                ensureInitializedLocked()
                database.readImagePhashes(keys, nowMillis())
            }
        }
    }

    override suspend fun saveImagePhashes(entries: Map<String, String>) {
        val valid = entries.filter { (key, value) -> isCompatImagePhashCacheKey(key) && isValidCompatImagePhash(value) }
        if (valid.isEmpty()) return
        withContext(AppDispatchers.io) {
            mutex.withLock {
                ensureInitializedLocked()
                database.writeImagePhashes(valid, nowMillis())
            }
        }
    }

    override suspend fun savePreferences(values: Map<String, String?>) {
        if (values.isEmpty()) return
        mutate {
            values.forEach { (key, value) -> if (value != null) requireValidCompatPreference(key, value) }
            val updated = it.preferences.toMutableMap()
            values.forEach { (key, value) -> if (value == null) updated.remove(key) else updated[key] = value }
            state = it.copy(preferences = updated)
            if (COMPAT_THREAD_CACHE_PREFERENCE_KEY in values) enforceSnapshotQuotaLocked()
        }
    }

    override suspend fun exportSettingsBackup(): String = read { current ->
        val boardKeys = current.boards.mapTo(mutableSetOf(), CompatBoard::key)
        encodeCompatSettingsBackup(
            CompatSettingsBackup(
                exportedAtEpochMillis = nowMillis(),
                boards = current.boards,
                tabs = current.tabs.filter { tab -> tab.boardKey in boardKeys },
                history = current.history.filter { entry -> entry.boardKey in boardKeys },
                catalogPreferences = current.catalogPreferences.filter { pref -> pref.boardKey in boardKeys },
                preferences = compatBackupExportPreferences(current.preferences),
                ngRules = current.ngRules,
                workspace = current.workspace,
                toolbars = current.toolbars.map { toolbar ->
                    CompatToolbarBackup(
                        surface = toolbar.surface,
                        items = toolbar.items.map { item -> CompatToolbarBackupItem(item.key, item.position, item.active) }
                    )
                }
            )
        )
    }

    override suspend fun importSettingsBackup(
        payload: String,
        restoreUserSettings: Boolean,
        restoreNgRules: Boolean
    ): CompatSettingsBackupImportReport = mutate {
        val backup = decodeCompatSettingsBackup(payload)
        val nextBoards = if (restoreUserSettings) backup.boards.fold(it.boards) { all, board ->
            require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) { "バックアップの板URLが不正です" }
            replaceBoard(all, board)
        } else it.boards
        val boardKeys = nextBoards.mapTo(mutableSetOf(), CompatBoard::key)
        val nextTabs = if (restoreUserSettings) backup.tabs.filter { tab -> tab.boardKey in boardKeys }
            .fold(it.tabs) { all, tab -> replaceTab(all, tab) } else it.tabs
        val untrimmedTabKeys = nextTabs.mapTo(mutableSetOf(), CompatTab::key)
        // Same order as Android: settle the restored active tab first so the
        // normal tab trim keeps it and favourites; NG scopes see the kept tabs.
        val nextWorkspace = backup.workspace?.takeIf { restoreUserSettings }?.let { restored ->
            restored.copy(
                activeTabKey = restored.activeTabKey?.takeIf { key -> untrimmedTabKeys.contains(key) },
                catalogHostBoardKey = restored.catalogHostBoardKey?.takeIf { key -> boardKeys.contains(key) }
            )
        } ?: it.workspace
        val trimmedTabs = trimCompatTabs(nextTabs, nextWorkspace.activeTabKey)
        val tabKeys = trimmedTabs.mapTo(mutableSetOf(), CompatTab::key)
        val nextHistory = if (restoreUserSettings) backup.history.filter { entry -> entry.boardKey in boardKeys }
            .fold(it.history) { all, entry -> replaceHistory(all, entry) } else it.history
        val validRules = if (restoreNgRules) backup.ngRules.filter { rule ->
            isValidCompatNgRule(rule) &&
            isCompatNgScopeValid(rule.kind, rule.scopeKey, boardKeys, tabKeys)
        } else emptyList()
        val nextRules = if (restoreNgRules) validRules.fold(it.ngRules) { all, rule ->
            replaceBy(all, rule, CompatNgRule::id)
        }.sortedByDescending(CompatNgRule::createdAtEpochMillis).take(MAX_COMPAT_NG_RULES) else it.ngRules
        val retainedPreferences = if (COMPAT_WATCH_WORDS_PREFERENCE_KEY in backup.preferences && COMPAT_WATCH_RULES_KEY !in backup.preferences) {
            it.preferences - COMPAT_WATCH_RULES_KEY
        } else it.preferences
        val nextPreferences = if (restoreUserSettings) retainedPreferences + backup.preferences.also { values ->
            values.forEach { (key, value) -> requireValidCompatPreference(key, value) }
        } else it.preferences
        val nextCatalogPrefs = if (restoreUserSettings) backup.catalogPreferences.filter { pref -> pref.boardKey in boardKeys }
            .fold(it.catalogPreferences) { all, pref -> replaceBy(all, pref, CompatCatalogPreference::boardKey) } else it.catalogPreferences
        val nextToolbars = if (restoreUserSettings) backup.toolbars.fold(it.toolbars) { all, toolbar ->
            val surface = runCatching { CompatToolbarSurface.valueOf(toolbar.surface) }.getOrNull()
            val items = toolbar.items.map { item -> CompatToolbarItem(item.key, item.position, item.active) }
            if (surface == null || !validateCompatToolbar(surface, items)) all else replaceBy(
                all,
                ToolbarRecord(
                    toolbar.surface,
                    toolbar.items.map { item -> ToolbarItemRecord(item.key, item.position, item.active) }
                ),
                ToolbarRecord::surface
            )
        } else it.toolbars
        val keptHistory = trimHistory(nextHistory)
        state = it.copy(
            boards = nextBoards,
            tabs = trimmedTabs,
            history = keptHistory,
            preferences = nextPreferences,
            catalogPreferences = nextCatalogPrefs,
            ngRules = nextRules,
            toolbars = nextToolbars,
            workspace = nextWorkspace,
            historyTombstones = if (restoreUserSettings) it.historyTombstones - backup.history.map { entry -> entry.canonicalUrl }.toSet() else it.historyTombstones
        )
        enforceSnapshotQuotaLocked()
        CompatSettingsBackupImportReport(
            boardsImported = if (restoreUserSettings) backup.boards.size else 0,
            // The restored records still kept after the normal limits (P4-4).
            tabsImported = if (restoreUserSettings) countCompatRestoredKept(
                backup.tabs.filter { tab -> tab.boardKey in boardKeys }.map(CompatTab::key),
                tabKeys
            ) else 0,
            historyImported = if (restoreUserSettings) countCompatRestoredKept(
                backup.history.filter { entry -> entry.boardKey in boardKeys }.map(CompatHistoryEntry::canonicalUrl),
                keptHistory.mapTo(mutableSetOf(), CompatHistoryEntry::canonicalUrl)
            ) else 0,
            preferencesImported = if (restoreUserSettings) backup.preferences.size else 0,
            ngRulesImported = validRules.size,
            toolbarsImported = if (restoreUserSettings) backup.toolbars.size else 0
        )
    }

    override suspend fun enqueueArchiveReport(rawThreadUrl: String, nowEpochMillis: Long): ArchiveReportEnqueueResult = mutate {
        val normalized = normalizeArchiveReportThreadUrl(rawThreadUrl)
            ?: return@mutate ArchiveReportEnqueueResult(false, 0)
        var rows = purgeExpiredOutbox(it.archiveRows, nowEpochMillis)
        val exists = rows.any { row -> row.threadId == normalized.threadId }
        // A full outbox must not make new reports silently impossible forever.
        // Preserve in-flight rows, then discard the oldest non-sending record
        // exactly as the maintenance pass does.  This keeps the bounded queue
        // useful on iOS even if BGTask has not had an opportunity to run yet.
        var dropped = false
        if (!exists && rows.size >= ARCHIVE_REPORT_MAX_ROWS) {
            val removable = rows
                .filter { row -> row.state != "sending" }
                .minWithOrNull(compareBy<ArchiveRow> { row -> row.firstSeenAt }.thenBy { row -> row.threadId })
            if (removable == null) {
                dropped = true
            } else {
                rows = rows.filterNot { row -> row.threadId == removable.threadId }
            }
        }
        if (!exists && !dropped) {
            rows = rows + ArchiveRow(
                threadId = normalized.threadId,
                threadUrl = normalized.url,
                state = "pending",
                firstSeenAt = nowEpochMillis,
                nextAttemptAt = saturatingEpochAdd(nowEpochMillis, ARCHIVE_REPORT_SEND_DELAY_MILLIS)
            )
        }
        state = it.copy(archiveRows = rows)
        ArchiveReportEnqueueResult(
            inserted = !exists && !dropped,
            sendableCount = rows.count { row -> row.state == "pending" || row.state == "retry" },
            droppedForCapacity = dropped
        )
    }

    override suspend fun maintainArchiveReportOutbox(nowEpochMillis: Long): Int = mutate {
        val before = it.archiveRows.size
        var rows = purgeExpiredOutbox(it.archiveRows, nowEpochMillis)
        if (rows.size >= ARCHIVE_REPORT_MAINTENANCE_START_ROWS) {
            val removable = rows.filter { row -> row.state != "sending" }
                .sortedWith(compareBy<ArchiveRow> { row -> row.firstSeenAt }.thenBy { row -> row.threadId })
                .take((rows.size - ARCHIVE_REPORT_MAINTENANCE_TARGET_ROWS).coerceIn(0, ARCHIVE_REPORT_MAINTENANCE_BATCH_ROWS))
                .mapTo(mutableSetOf(), ArchiveRow::threadId)
            rows = rows.filterNot { row -> row.threadId in removable }
        }
        state = it.copy(archiveRows = rows)
        before - rows.size
    }

    override suspend fun recoverStaleArchiveReports(nowEpochMillis: Long): Int = mutate {
        val staleBefore = archiveReportStaleCutoffEpochMillis(nowEpochMillis)
        var changed = 0
        val rows = it.archiveRows.map { row ->
            if (row.state == "sending" && row.sendingStartedAt != null && row.sendingStartedAt <= staleBefore) {
                changed++
                row.copy(state = "retry", nextAttemptAt = nowEpochMillis, sendingStartedAt = null, lastError = "stale_sending")
            } else row
        }
        if (changed > 0) state = it.copy(archiveRows = rows)
        changed
    }

    override suspend fun claimArchiveReportBatch(nowEpochMillis: Long, newRequestId: String): ArchiveReportOutboxBatch? = mutate {
        val retainedRequestId = it.archiveRows.firstOrNull { row ->
            row.state == "retry" && row.batchRequestId != null && row.nextAttemptAt <= nowEpochMillis
        }?.batchRequestId
        val candidates = if (retainedRequestId != null) {
            it.archiveRows.filter { row -> row.state == "retry" && row.batchRequestId == retainedRequestId && row.nextAttemptAt <= nowEpochMillis }
        } else {
            it.archiveRows.filter { row -> (row.state == "pending" || row.state == "retry") && row.batchRequestId == null && row.nextAttemptAt <= nowEpochMillis }
                .sortedWith(compareBy<ArchiveRow> { row -> row.nextAttemptAt }.thenBy { row -> row.threadId })
                .take(ARCHIVE_REPORT_MAX_BATCH_SIZE)
        }
        if (candidates.isEmpty()) return@mutate null
        val requestId = retainedRequestId ?: newRequestId
        val payload = buildArchiveReportPayload(requestId, candidates.map { row -> NormalizedArchiveThread(row.threadId, row.threadUrl) })
            ?: return@mutate null
        val selected = payload.threadIds.toSet()
        val rows = it.archiveRows.map { row ->
            if (row.threadId in selected && (row.state == "pending" || row.state == "retry")) {
                row.copy(state = "sending", batchRequestId = payload.requestId, batchPayloadHash = payload.sha256, sendingStartedAt = nowEpochMillis, lastError = null)
            } else row
        }
        state = it.copy(archiveRows = rows)
        ArchiveReportOutboxBatch(payload, candidates.maxOfOrNull(ArchiveRow::attemptCount) ?: 0)
    }

    override suspend fun reassignSendingArchiveReportBatch(oldRequestId: String, payload: ArchiveReportPayload, nowEpochMillis: Long): Boolean = mutate {
        if (!payload.isValid()) return@mutate false
        val selected = payload.threadIds.toSet()
        val source = it.archiveRows.filter { row -> row.state == "sending" && row.batchRequestId == oldRequestId }
        if (!source.mapTo(mutableSetOf(), ArchiveRow::threadId).containsAll(selected)) return@mutate false
        state = it.copy(archiveRows = it.archiveRows.map { row ->
            if (row.threadId in selected && row.state == "sending" && row.batchRequestId == oldRequestId) row.copy(
                batchRequestId = payload.requestId, batchPayloadHash = payload.sha256, sendingStartedAt = nowEpochMillis
            ) else row
        })
        true
    }

    override suspend fun splitSendingArchiveReportBatch(oldRequestId: String, first: ArchiveReportPayload, second: ArchiveReportPayload, nowEpochMillis: Long): Boolean = mutate {
        if (!first.isValid() || !second.isValid() || first.requestId == second.requestId || first.threadIds.toSet().intersect(second.threadIds.toSet()).isNotEmpty()) return@mutate false
        val source = it.archiveRows.filter { row -> row.state == "sending" && row.batchRequestId == oldRequestId }.mapTo(mutableSetOf(), ArchiveRow::threadId)
        if (source != (first.threadIds + second.threadIds).toSet()) return@mutate false
        state = it.copy(archiveRows = it.archiveRows.map { row -> when (row.threadId) {
            in first.threadIds -> row.copy(batchRequestId = first.requestId, batchPayloadHash = first.sha256, sendingStartedAt = nowEpochMillis)
            in second.threadIds -> row.copy(batchRequestId = second.requestId, batchPayloadHash = second.sha256, sendingStartedAt = nowEpochMillis)
            else -> row
        } })
        true
    }

    override suspend fun markArchiveReportAccepted(requestId: String, nowEpochMillis: Long): Int = markArchiveRows(requestId) { row ->
        row.copy(state = "accepted", acceptedAt = nowEpochMillis, expiresAt = saturatingEpochAdd(nowEpochMillis, ARCHIVE_REPORT_RETENTION_MILLIS), batchRequestId = null, batchPayloadHash = null, sendingStartedAt = null, lastError = null)
    }

    override suspend fun markArchiveReportRetry(requestId: String, nextAttemptAt: Long, errorCode: String): Int = markArchiveRows(requestId) { row ->
        row.copy(
            state = "retry",
            nextAttemptAt = nextAttemptAt,
            attemptCount = if (row.attemptCount == Int.MAX_VALUE) Int.MAX_VALUE else row.attemptCount + 1,
            sendingStartedAt = null,
            lastError = errorCode.take(256)
        )
    }

    override suspend fun markArchiveReportBatchForSplit(requestId: String, nowEpochMillis: Long): Int = markArchiveRows(requestId) { row ->
        row.copy(state = "retry", nextAttemptAt = nowEpochMillis, batchRequestId = null, batchPayloadHash = null, sendingStartedAt = null, lastError = "batch_split")
    }

    override suspend fun markArchiveReportAbandoned(requestId: String, nowEpochMillis: Long, errorCode: String): Int = markArchiveRows(requestId) { row ->
        row.copy(state = "abandoned", expiresAt = saturatingEpochAdd(nowEpochMillis, ARCHIVE_REPORT_RETENTION_MILLIS), batchRequestId = null, batchPayloadHash = null, sendingStartedAt = null, lastError = errorCode.take(256))
    }

    override suspend fun archiveReportOutboxStats(): ArchiveReportOutboxStats = read { current ->
        ArchiveReportOutboxStats(current.archiveRows.size, current.archiveRows.count { row -> row.state == "pending" || row.state == "retry" })
    }

    override suspend fun archiveReportNextAttemptAt(): Long? = read { current ->
        current.archiveRows.filter { row -> row.state == "pending" || row.state == "retry" || row.state == "sending" }
            .minOfOrNull { row ->
                if (row.state == "sending") {
                    saturatingEpochAdd(
                        row.sendingStartedAt ?: row.nextAttemptAt,
                        ARCHIVE_REPORT_SENDING_STALE_MILLIS
                    )
                } else {
                    row.nextAttemptAt
                }
            }
    }

    override suspend fun clearArchiveReportOutbox(): Int = mutate {
        val count = it.archiveRows.size
        state = it.copy(archiveRows = emptyList())
        count
    }

    override suspend fun loadToolbar(surface: CompatToolbarSurface): List<CompatToolbarItem> = mutate {
        val current = it.toolbars.firstOrNull { record -> record.surface == surface.name }
            ?.items?.map { item -> CompatToolbarItem(item.key, item.position, item.active) }.orEmpty()
        val reconciled = reconcileCompatToolbar(surface, current)
        if (reconciled != current) state = it.copy(toolbars = replaceBy(it.toolbars, ToolbarRecord(surface.name, reconciled.map { item -> ToolbarItemRecord(item.key, item.position, item.active) }), ToolbarRecord::surface))
        reconciled
    }

    override suspend fun saveToolbar(surface: CompatToolbarSurface, items: List<CompatToolbarItem>) = mutate {
        require(validateCompatToolbar(surface, items)) { "Invalid compatibility toolbar" }
        state = it.copy(toolbars = replaceBy(it.toolbars, ToolbarRecord(surface.name, items.map { item -> ToolbarItemRecord(item.key, item.position, item.active) }), ToolbarRecord::surface))
    }

    override suspend fun upsertNgRule(rule: CompatNgRule): Boolean = mutate {
        if (!isValidCompatNgRule(rule)) return@mutate false
        val boards = it.boards.mapTo(mutableSetOf(), CompatBoard::key)
        val tabs = it.tabs.mapTo(mutableSetOf(), CompatTab::key)
        if (!isCompatNgScopeValid(rule.kind, rule.scopeKey, boards, tabs)) return@mutate false
        if (it.ngRules.size >= MAX_COMPAT_NG_RULES && it.ngRules.none { existing -> existing.id == rule.id }) {
            return@mutate false
        }
        state = it.copy(ngRules = replaceBy(it.ngRules, rule, CompatNgRule::id))
        true
    }

    override suspend fun deleteNgRule(ruleId: String) = mutate {
        state = it.copy(ngRules = it.ngRules.filterNot { rule -> rule.id == ruleId })
    }

    override suspend fun deleteNgRules(ruleIds: Collection<String>) = mutate {
        val ids = ruleIds.filter(String::isNotBlank).toSet()
        state = it.copy(ngRules = it.ngRules.filterNot { rule -> rule.id in ids })
    }

    private suspend fun markArchiveRows(requestId: String, transform: (ArchiveRow) -> ArchiveRow): Int = mutate {
        val count = it.archiveRows.count { row -> row.state == "sending" && row.batchRequestId == requestId }
        if (count > 0) state = it.copy(archiveRows = it.archiveRows.map { row ->
            if (row.state == "sending" && row.batchRequestId == requestId) transform(row) else row
        })
        count
    }

    private suspend fun <T> read(block: (PersistedCompatibilityState) -> T): T = withContext(AppDispatchers.io) {
        mutex.withLock {
            ensureInitializedLocked()
            block(state)
        }
    }

    private suspend fun loadThreadSnapshotWithoutFullRewrite(
        findSnapshot: (PersistedCompatibilityState) -> CompatThreadSnapshot?
    ): CompatThreadSnapshot? = withContext(AppDispatchers.io) {
        mutex.withLock {
            ensureInitializedLocked()
            val snapshot = findSnapshot(state) ?: return@withLock null
            val accessedAt = nowMillis()
            state = state.copy(snapshotAccess = state.snapshotAccess + (snapshot.tabKey to accessedAt))
            // A cache hit only changes LRU metadata. Persist that tiny update in
            // its own table instead of serializing every cached post again.
            database.writeSnapshotAccess(snapshot.tabKey, accessedAt)
            snapshot
        }
    }

    private suspend fun <T> mutate(block: (PersistedCompatibilityState) -> T): T = withContext(AppDispatchers.io) {
        mutex.withLock {
            ensureInitializedLocked()
            val previousState = state
            val result = block(state)
            if (state != previousState) {
                try {
                    persistLocked()
                    publishLocked()
                } catch (error: Throwable) {
                    state = previousState
                    throw error
                }
            }
            result
        }
    }

    private suspend fun ensureInitializedLocked() {
        if (initialized) return
        fileSystem.createDirectory("compatibility").getOrThrow()
        val databaseRead = runSuspendCatchingPreservingCancellation { database.readPayload() }
        val databasePayload = databaseRead.getOrElse { error ->
            if (error.isRecoverableDesktopCompatibilityDatabaseCorruption() || error is IllegalArgumentException) {
                // Keep a copy first (a payload over the size limit or a damaged file is still
                // the user's data); when the copy cannot be written, initialization fails and
                // the database is left as it is.
                val backupPath = withContext(AppDispatchers.io) { database.backupStorage(nowMillis()) }
                Logger.e(
                    "DesktopCompatibilityStore",
                    "Resetting unreadable compatibility database; a copy was kept at $backupPath",
                    error
                )
                database.deleteStorage()
                if (backupPath != null) {
                    val folder = java.io.File(fileSystem.resolveAbsolutePath(backupPath)).absoluteFile.parent
                    postUnreadableCompatibilityPayloadNotice(fileSystem, backupPath, location = "フォルダ「$folder」")
                }
                null
            } else {
                // A transient I/O or permission failure must not be presented as
                // an empty profile and subsequently overwrite durable state.
                throw error
            }
        }
        var databaseState = databasePayload?.let { raw ->
            runCatching { json.decodeFromString(PersistedCompatibilityState.serializer(), raw) }
                .onFailure { error ->
                    Logger.e("DesktopCompatibilityStore", "Compatibility database payload is corrupted", error)
                }
                .getOrNull()
        }
        if (databasePayload != null && databaseState == null) {
            // Never delete the database for an undecodable payload: keep a copy
            // first (or fail initialization when that is impossible) and leave
            // the stored row untouched until the next real write replaces it.
            val backupPath = preserveUnreadableCompatibilityPayload(fileSystem, databasePayload)
            Logger.e("DesktopCompatibilityStore", "Starting from an empty profile; unreadable payload kept at $backupPath")
            // Tell the user instead of silently starting empty (P4-1); the
            // data folder is reachable on desktop, so name the full path.
            val folder = java.io.File(fileSystem.resolveAbsolutePath(backupPath)).absoluteFile.parent
            postUnreadableCompatibilityPayloadNotice(fileSystem, backupPath, location = "フォルダ「$folder」")
        } else {
            restorePendingUnreadableCompatibilityPayloadNotice(fileSystem)
        }
        suspend fun readLegacyState(path: String): PersistedCompatibilityState? {
            if (!fileSystem.exists(path)) return null
            val size = fileSystem.getFileSize(path)
            if (size !in 0L..MAX_COMPAT_LEGACY_STATE_BYTES) return null
            val raw = fileSystem.readString(path).getOrNull() ?: return null
            if (raw.encodeToByteArray().size.toLong() > MAX_COMPAT_LEGACY_STATE_BYTES) return null
            return withContext(AppDispatchers.parsing) {
                runCatching {
                    json.decodeFromString(PersistedCompatibilityState.serializer(), raw)
                }.getOrNull()
            }
        }
        val legacyDocumentPaths = emptyList<String>()
        val legacyCandidates = listOf(legacyStatePath, legacyBackupPath) + legacyDocumentPaths
        val legacyState = legacyCandidates.firstNotNullOfOrNull { path ->
            readLegacyState(path)
        }
        state = databaseState ?: legacyState ?: PersistedCompatibilityState()
        if (databaseState?.partitionedCaches == true) loadPartitionedCachesLocked()
        val pendingAnchors = runSuspendCatchingPreservingCancellation {
            database.readPendingScrollAnchors()
        }.getOrDefault(emptyMap())
            .mapValues { (_, raw) ->
                runCatching { json.decodeFromString(ScrollAnchor.serializer(), raw) }.getOrNull()
            }
            .filterValues { it != null }
            .mapValues { (_, anchor) -> anchor!! }
        if (pendingAnchors.isNotEmpty()) {
            val tabKeysByCanonicalUrl = state.tabs.associate { tab -> tab.canonicalUrl to tab.key }
            state = state.copy(
                tabs = state.tabs.map { tab ->
                    pendingAnchors[tab.key]?.let { anchor -> tab.copy(scrollAnchor = anchor) } ?: tab
                },
                history = state.history.map { entry ->
                    val anchor = tabKeysByCanonicalUrl[entry.canonicalUrl]
                        ?.let(pendingAnchors::get)
                    anchor?.let { entry.copy(scrollAnchor = it) } ?: entry
                }
            )
        }
        val snapshotKeys = state.snapshots.mapTo(mutableSetOf(), CompatThreadSnapshot::tabKey)
        val pendingSnapshotAccess = runSuspendCatchingPreservingCancellation {
            database.readPendingSnapshotAccess()
        }
            .getOrDefault(emptyMap())
            .filterKeys { it in snapshotKeys }
        if (pendingSnapshotAccess.isNotEmpty()) {
            state = state.copy(snapshotAccess = state.snapshotAccess + pendingSnapshotAccess)
        }
        if (databaseState == null && legacyState != null) {
            database.writePayload(
                json.encodeToString(PersistedCompatibilityState.serializer(), state),
                nowMillis()
            )
            // Migration is complete only after the SQLite transaction commits.
            (listOf(legacyStatePath, legacyBackupPath) + legacyDocumentPaths)
                .distinct()
                .forEach { path -> fileSystem.delete(path) }
            databaseState = state
        }
        if (databaseState != null) {
            }
        initialized = true
        publishLocked()
    }

    private fun threadCacheKey(snapshot: CompatThreadSnapshot): String = "thread:${snapshot.tabKey}"

    private fun catalogCacheKey(record: CatalogSnapshotRecord): String =
        "catalog:${record.boardKey}:${record.sort}:${record.revision}"

    private fun cacheRecords(): Map<String, Any> = buildMap {
        state.closedBatch?.let { put(CLOSED_BATCH_CACHE_KEY, it) }
        state.snapshots.forEach { put(threadCacheKey(it), it) }
        state.catalogSnapshots.forEach { put(catalogCacheKey(it), it) }
    }

    private fun encodeCacheRecord(record: Any): String = when (record) {
        is CompatThreadSnapshot -> json.encodeToString(CompatThreadSnapshot.serializer(), record)
        is CatalogSnapshotRecord -> json.encodeToString(CatalogSnapshotRecord.serializer(), record)
        is ClosedTabBatch -> json.encodeToString(ClosedTabBatch.serializer(), record)
        else -> error("Unknown compatibility cache record")
    }

    /** The profile row without the caches, which live in their own rows. */
    private fun metadataOf(value: PersistedCompatibilityState) = value.copy(
        partitionedCaches = true,
        snapshots = emptyList(),
        catalogSnapshots = emptyList(),
        closedBatch = null
    )

    /**
     * Loads the partitioned caches. The rows are refetchable, so an undecodable or
     * misfiled one is logged and deleted instead of failing initialization on every launch.
     */
    private suspend fun loadPartitionedCachesLocked() {
        val read = runSuspendCatchingPreservingCancellation { database.readCacheRecords() }
            .getOrElse { error ->
                if (!error.isRecoverableDesktopCompatibilityDatabaseCorruption()) throw error
                Logger.e("DesktopCompatibilityStore", "Dropping unreadable compatibility cache records", error)
                database.clearCacheRecords()
                DesktopCompatibilityDatabase.CacheRecordsRead(emptyMap(), emptyList())
            }
        val snapshots = mutableListOf<CompatThreadSnapshot>()
        val catalogs = mutableListOf<CatalogSnapshotRecord>()
        var closedBatch: ClosedTabBatch? = null
        val acceptedBytes = mutableMapOf<String, Long>()
        val rejectedKeys = read.rejectedKeys.toMutableList()
        read.records.forEach { (key, payload) ->
            val accepted = runCatching {
                when {
                    key.startsWith("thread:") ->
                        json.decodeFromString(CompatThreadSnapshot.serializer(), payload)
                            .takeIf { threadCacheKey(it) == key }
                            ?.also(snapshots::add)
                    key.startsWith("catalog:") ->
                        json.decodeFromString(CatalogSnapshotRecord.serializer(), payload)
                            .takeIf { catalogCacheKey(it) == key }
                            ?.also(catalogs::add)
                    key == CLOSED_BATCH_CACHE_KEY ->
                        json.decodeFromString(ClosedTabBatch.serializer(), payload)
                            .also { closedBatch = it }
                    // Rows of an unknown kind (e.g. written by a newer build) are left untouched.
                    else -> return@forEach
                }
            }.onFailure { error ->
                Logger.w("DesktopCompatibilityStore", "Dropping undecodable compatibility cache record: ${error.message}")
            }.getOrNull()
            if (accepted == null) rejectedKeys += key else acceptedBytes[key] = payload.encodeToByteArray().size.toLong()
        }
        if (rejectedKeys.isNotEmpty()) {
            runCatching { database.deleteCacheRecords(rejectedKeys) }.onFailure { error ->
                Logger.e("DesktopCompatibilityStore", "Failed to delete invalid compatibility cache records", error)
            }
        }
        state = state.copy(snapshots = snapshots, catalogSnapshots = catalogs, closedBatch = closedBatch)
        persistedCaches = cacheRecords()
        persistedCacheBytes = acceptedBytes
        cacheRowsInSync = true
    }

    private suspend fun persistLocked() {
        state = state.copy(partitionedCaches = true)
        var records = cacheRecords()
        val inSync = cacheRowsInSync
        val updates = LinkedHashMap<String, String?>()
        val sizes = if (inSync) persistedCacheBytes.toMutableMap() else mutableMapOf()
        records.forEach { (key, record) ->
            if (!inSync || persistedCaches[key] !== record) {
                val encoded = encodeCacheRecord(record)
                updates[key] = encoded
                sizes[key] = encoded.encodeToByteArray().size.toLong()
            }
        }
        var encoded = json.encodeToString(PersistedCompatibilityState.serializer(), metadataOf(state))
        fun totalBytes(): Long = encoded.encodeToByteArray().size.toLong() + records.keys.sumOf { sizes[it] ?: 0L }
        if (totalBytes() > MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES) {
            // The user's cache quota may exceed the SQLite envelope limit (or
            // be unlimited). Evict refetchable bodies before they prevent even
            // a small setting/draft mutation from committing. Keep headroom for
            // subsequent writes and preserve all user-owned records.
            trimRefetchableCachesLocked(
                totalBytes() - MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES + 1024L * 1024L
            )
            state = state.copy(partitionedCaches = true)
            records = cacheRecords()
            encoded = json.encodeToString(PersistedCompatibilityState.serializer(), metadataOf(state))
        }
        require(totalBytes() <= MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES) {
            "Compatibility state exceeds its permitted size"
        }
        if (inSync) {
            (persistedCaches.keys + updates.keys).filter { it !in records }.forEach {
                updates[it] = null
                sizes.remove(it)
            }
        } else {
            // The whole table is replaced below, so only the rows still wanted are written.
            updates.keys.retainAll(records.keys)
        }
        database.writePayload(
            payload = encoded,
            updatedAtMillis = nowMillis(),
            cacheUpdates = updates,
            replaceCacheRecords = !inSync
        )
        persistedCaches = records
        persistedCacheBytes = sizes.filterKeys { it in records }
        cacheRowsInSync = true
    }

    private fun trimRefetchableCachesLocked(bytesToRelease: Long) {
        var remaining = bytesToRelease
        val removedSnapshots = mutableSetOf<String>()
        state.snapshots.sortedBy { state.snapshotAccess[it.tabKey] ?: it.fetchedAtEpochMillis }
            .forEach { snapshot ->
                if (remaining > 0L) {
                    remaining -= encodedSnapshotBytes(snapshot)
                    removedSnapshots += snapshot.tabKey
                }
            }
        val removedCatalogs = mutableSetOf<Int>()
        state.catalogSnapshots.withIndex().sortedBy { it.value.fetchedAtEpochMillis }
            .forEach { (index, snapshot) ->
                if (remaining > 0L) {
                    remaining -= json.encodeToString(CatalogSnapshotRecord.serializer(), snapshot)
                        .encodeToByteArray().size
                    removedCatalogs += index
                }
            }
        state = state.copy(
            snapshots = state.snapshots.filterNot { it.tabKey in removedSnapshots },
            snapshotAccess = state.snapshotAccess - removedSnapshots,
            tabs = state.tabs.map { tab ->
                if (tab.key in removedSnapshots) tab.copy(snapshotRevision = 0L) else tab
            },
            catalogSnapshots = state.catalogSnapshots.filterIndexed { index, _ -> index !in removedCatalogs }
        )
    }

    private fun publishLocked() {
        boardsState.value = state.boards.sortedBy(CompatBoard::sortOrder)
        tabsState.value = state.tabs.sortedByDescending(CompatTab::insertedAtEpochMillis)
        historyState.value = state.history.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis)
        workspaceState.value = state.workspace
        preferencesState.value = state.preferences
        ngRulesState.value = state.ngRules.sortedByDescending(CompatNgRule::createdAtEpochMillis)
    }

    private fun enforceSnapshotQuotaLocked(): Boolean {
        val quota = parseCompatThreadCacheQuotaBytes(state.preferences[COMPAT_THREAD_CACHE_PREFERENCE_KEY]) ?: return false
        val snapshotsWithSizes = state.snapshots.map { snapshot ->
            snapshot to encodedSnapshotBytes(snapshot)
        }
        var usage = snapshotsWithSizes.fold(0L) { total, (_, size) ->
            if (size > Long.MAX_VALUE - total) Long.MAX_VALUE else total + size
        }
        if (usage <= quota) return false
        val ordered = snapshotsWithSizes.sortedBy { (snapshot, _) ->
            state.snapshotAccess[snapshot.tabKey] ?: snapshot.fetchedAtEpochMillis
        }
        val remove = mutableSetOf<String>()
        ordered.forEach { (snapshot, size) ->
            if (usage > quota) {
                usage = (usage - size).coerceAtLeast(0L)
                remove += snapshot.tabKey
            }
        }
        if (remove.isEmpty()) return false
        state = state.copy(
            snapshots = state.snapshots.filterNot { snapshot -> snapshot.tabKey in remove },
            snapshotAccess = state.snapshotAccess - remove,
            tabs = state.tabs.map { tab -> if (tab.key in remove) tab.copy(snapshotRevision = 0L) else tab }
        )
        return true
    }

    private fun encodedSnapshotBytes(snapshot: CompatThreadSnapshot): Long =
        json.encodeToString(CompatThreadSnapshot.serializer(), snapshot).encodeToByteArray().size.toLong()

    private fun sumEncodedSnapshotBytes(snapshots: List<CompatThreadSnapshot>): Long =
        snapshots.fold(0L) { total, snapshot ->
            val size = encodedSnapshotBytes(snapshot)
            if (size > Long.MAX_VALUE - total) Long.MAX_VALUE else total + size
        }

    private fun validateSnapshot(snapshot: CompatThreadSnapshot) {
        require(snapshot.tabKey.isNotBlank()) { "Compatibility snapshot requires a tab key" }
        require(snapshot.revision >= 0L) { "Compatibility snapshot revision must be non-negative" }
        require(snapshot.posts.size <= MAX_COMPAT_THREAD_SNAPSHOT_POSTS) { "Compatibility snapshot exceeds $MAX_COMPAT_THREAD_SNAPSHOT_POSTS posts" }
        require(snapshot.posts.indices.all { index -> snapshot.posts[index].position == index }) { "Compatibility snapshot positions must be contiguous and zero-based" }
    }

    internal fun modernBoardKeys(boards: List<BoardSummary>): Set<String> =
        validatedModernBoards(boards).mapTo(mutableSetOf(), CompatBoard::key)

    private fun validatedModernBoards(boards: List<BoardSummary>): List<CompatBoard> {
        val seen = mutableSetOf<String>()
        return boards.asSequence().take(MAX_BOARDS).mapIndexedNotNull { index, board ->
            val canonical = resolvedCompatCanonicalBoardUrl(board.url)
                ?: return@mapIndexedNotNull null
            if (!seen.add(canonical)) return@mapIndexedNotNull null
            CompatBoard(
                key = compatBoardKey(canonical),
                name = board.name.ifBlank { canonical.substringAfter("//").substringBefore('/') },
                canonicalUrl = canonical,
                originalUrl = board.url,
                sortOrder = index
            )
        }.toList()
    }

    private fun repairBoards(boards: List<CompatBoard>): List<CompatBoard> {
        val keys = mutableSetOf<String>()
        val urls = mutableSetOf<String>()
        return boards.asSequence()
            .filter { board ->
                board.key.length in 1..MAX_COMPAT_BOARD_FIELD_CHARS &&
                    board.name.length <= MAX_COMPAT_BOARD_FIELD_CHARS &&
                    board.canonicalUrl.length <= MAX_COMPAT_URL_CHARS &&
                    board.originalUrl.length <= MAX_COMPAT_URL_CHARS &&
                    resolvedCompatCanonicalBoardUrl(board.originalUrl) == board.canonicalUrl &&
                    keys.add(board.key) && urls.add(board.canonicalUrl)
            }
            .take(MAX_BOARDS)
            .mapIndexed { index, board -> board.copy(sortOrder = index) }
            .toList()
    }

    private fun resolvedCompatCanonicalBoardUrl(originalUrl: String): String? {
        return canonicalizeBoardUrl(originalUrl)
            ?: originalUrl
                .takeIf { it.contains("example.com", ignoreCase = true) }
                ?.replace(Regex("^https?://[^/]+", RegexOption.IGNORE_CASE), "https://img.2chan.net")
                ?.let(::canonicalizeBoardUrl)
    }

    private fun trimHistoryTombstones(tombstones: Map<String, Long>): Map<String, Long> {
        return tombstones.asSequence()
            .filter { (url, _) -> url.length in 1..MAX_COMPAT_URL_CHARS }
            .sortedByDescending(Map.Entry<String, Long>::value)
            .take(MAX_HISTORY_TOMBSTONES)
            .associate { it.toPair() }
    }

    private fun mergeBoards(existing: List<CompatBoard>, additions: List<CompatBoard>): List<CompatBoard> {
        val result = existing.toMutableList()
        val known = existing.mapTo(mutableSetOf(), CompatBoard::canonicalUrl)
        additions.forEach { board -> if (known.add(board.canonicalUrl)) result += board.copy(sortOrder = result.size) }
        return result.sortedBy(CompatBoard::sortOrder).mapIndexed { index, board -> board.copy(sortOrder = index) }
    }

    private fun replaceBoard(items: List<CompatBoard>, value: CompatBoard): List<CompatBoard> =
        (items.filterNot { item -> item.key == value.key || item.canonicalUrl == value.canonicalUrl } + value)
            .sortedBy(CompatBoard::sortOrder).mapIndexed { index, board -> board.copy(sortOrder = index) }

    private fun replaceTab(items: List<CompatTab>, value: CompatTab): List<CompatTab> =
        items.filterNot { item -> item.key == value.key || item.canonicalUrl == value.canonicalUrl } + value

    private fun replaceHistory(items: List<CompatHistoryEntry>, value: CompatHistoryEntry): List<CompatHistoryEntry> =
        items.filterNot { item -> item.canonicalUrl == value.canonicalUrl } + value

    private fun <T, K> replaceBy(items: List<T>, value: T, key: (T) -> K): List<T> =
        items.filterNot { item -> key(item) == key(value) } + value

    // Same rule as Android: favourites and the active tab are never trimmed.
    private fun trimTabs(items: List<CompatTab>, activeTabKey: String?): List<CompatTab> =
        trimCompatTabs(items, activeTabKey)

    private fun trimHistory(items: List<CompatHistoryEntry>): List<CompatHistoryEntry> {
        val sorted = items.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis)
        return if (sorted.size > HISTORY_LIMIT_TRIGGER) sorted.take(HISTORY_LIMIT_AFTER_TRIM) else sorted
    }

    private fun purgeExpiredOutbox(rows: List<ArchiveRow>, now: Long): List<ArchiveRow> = rows.filterNot { row ->
        (row.state == "accepted" || row.state == "abandoned") && row.expiresAt != null && row.expiresAt <= now
    }

    private fun ArchiveReportPayload.isValid(): Boolean =
        bytes.size <= ARCHIVE_REPORT_MAX_BODY_BYTES &&
            buildArchiveReportPayload(requestId, threadIds.zip(urls).map { (id, url) -> NormalizedArchiveThread(id, url) })?.sha256 == sha256

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private companion object {
        const val TAB_LIMIT_TRIGGER = 100
        const val TAB_LIMIT_AFTER_TRIM = 90
        const val HISTORY_LIMIT_TRIGGER = 200
        const val HISTORY_LIMIT_AFTER_TRIM = 190
        const val MAX_TABS = TAB_LIMIT_TRIGGER
        const val MAX_HISTORY = HISTORY_LIMIT_TRIGGER
        const val MAX_BOARDS = 100
        const val MAX_THREAD_SNAPSHOTS = 512
        const val MAX_HISTORY_TOMBSTONES = 1_000
        const val MAX_PREFERENCES = 4_096
        const val MAX_TOOLBAR_ITEMS = 64
        const val MAX_COMPAT_BOARD_FIELD_CHARS = 200
        const val MAX_COMPAT_URL_CHARS = 500
        const val MAX_CATALOG_ITEMS = 3_000
        const val MAX_CATALOG_GENERATIONS = 6
        const val CLOSED_BATCH_CACHE_KEY = "closedBatch"
        const val MAX_DROPPED_ITEMS = 200
        const val UNDO_WINDOW_MILLIS = 7_000L
        val COMPAT_SHARED_SNAPSHOT_KEY_REGEX = Regex("^compat_tab_[0-9a-f]{16}$")
    }
}

@Serializable
private data class PersistedCompatibilityState(
    val version: Int = 1,
    /** Thread/catalog caches and the pending closed tabs are kept in compat_cache_record rows. */
    val partitionedCaches: Boolean = false,
    val boardBootstrapComplete: Boolean = false,
    val boards: List<CompatBoard> = emptyList(),
    val tabs: List<CompatTab> = emptyList(),
    val history: List<CompatHistoryEntry> = emptyList(),
    val workspace: CompatWorkspaceRecord = CompatWorkspaceRecord(),
    val preferences: Map<String, String> = emptyMap(),
    @Serializable(with = LenientCompatNgRuleListSerializer::class)
    val ngRules: List<CompatNgRule> = emptyList(),
    val replyDrafts: List<CompatReplyDraft> = emptyList(),
    val buildDrafts: List<CompatBuildDraft> = emptyList(),
    val snapshots: List<CompatThreadSnapshot> = emptyList(),
    val snapshotAccess: Map<String, Long> = emptyMap(),
    val catalogPreferences: List<CompatCatalogPreference> = emptyList(),
    val catalogSnapshots: List<CatalogSnapshotRecord> = emptyList(),
    val droppedItems: List<DroppedCatalogRecord> = emptyList(),
    val toolbars: List<ToolbarRecord> = emptyList(),
    val closedBatch: ClosedTabBatch? = null,
    val historyTombstones: Map<String, Long> = emptyMap(),
    val archiveRows: List<ArchiveRow> = emptyList()
)

@Serializable
private data class CatalogSnapshotRecord(
    val boardKey: String,
    val sort: String,
    val revision: Long,
    val fetchedAtEpochMillis: Long,
    val items: List<CompatCatalogSnapshotItem>
)

@Serializable
private data class DroppedCatalogRecord(
    val boardKey: String,
    val threadId: String,
    val item: CompatCatalogSnapshotItem,
    val droppedAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val classification: String
)

@Serializable
private data class ToolbarRecord(val surface: String, val items: List<ToolbarItemRecord>)

@Serializable
private data class ToolbarItemRecord(val key: String, val position: Int, val active: Boolean)

@Serializable
private data class ArchiveRow(
    val threadId: String,
    val threadUrl: String,
    val state: String,
    val firstSeenAt: Long,
    val nextAttemptAt: Long,
    val attemptCount: Int = 0,
    val batchRequestId: String? = null,
    val batchPayloadHash: String? = null,
    val sendingStartedAt: Long? = null,
    val acceptedAt: Long? = null,
    val expiresAt: Long? = null,
    val lastError: String? = null
)
