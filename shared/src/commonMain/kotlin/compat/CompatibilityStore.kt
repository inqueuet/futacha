package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

const val MAX_COMPAT_PREFERENCE_KEY_CHARS = 300
const val MAX_COMPAT_PREFERENCE_VALUE_CHARS = 20_000

fun isValidCompatPreference(key: String, value: String): Boolean =
    key.startsWith("compat.") &&
        key.length <= MAX_COMPAT_PREFERENCE_KEY_CHARS &&
        value.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS

fun requireValidCompatPreference(key: String, value: String) {
    require(isValidCompatPreference(key, value)) { "Compatibility preference is invalid or too large" }
}

private val ALWAYS_LOADED_COMPAT_STORE: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

interface CompatibilityStore {
    val boards: Flow<List<CompatBoard>>
    val tabs: Flow<List<CompatTab>>
    val history: Flow<List<CompatHistoryEntry>>
    val workspace: Flow<CompatWorkspaceRecord>
    val preferences: Flow<Map<String, String>>
    val ngRules: Flow<List<CompatNgRule>>

    /**
     * True once the observable values above reflect persisted data. A store
     * that loads asynchronously starts with empty placeholders; callers must
     * not treat those as the user's settings (theme, NG rules, save location)
     * or write defaults over them. Stores that load synchronously keep this
     * default.
     */
    val isLoaded: StateFlow<Boolean> get() = ALWAYS_LOADED_COMPAT_STORE

    suspend fun bootstrapBoardsIfNeeded(modernBoards: List<BoardSummary>): Boolean
    suspend fun importModernBoards(modernBoards: List<BoardSummary>): Int
    suspend fun importModernHistory(
        modernHistory: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>
    ): Int = 0
    suspend fun upsertBoard(board: CompatBoard)
    suspend fun upsertBoards(boards: List<CompatBoard>) {
        boards.forEach { board -> upsertBoard(board) }
    }
    suspend fun reorderBoards(orderedKeys: List<String>)
    suspend fun deleteBoard(boardKey: String)

    suspend fun openTab(tab: CompatTab, historyEntry: CompatHistoryEntry? = null)
    suspend fun updateTab(tab: CompatTab)

    /**
     * Applies [transform] to the current stored tab and returns whether it was
     * written. Background refreshers must use this instead of [updateTab] with a
     * copy taken before a slow request: that copy rolls back read counts and
     * favourites changed meanwhile, and [updateTab] re-adds a closed tab.
     *
     * A missing tab is never created. [transform] returns null for "no change".
     * Key, canonical URL and scroll anchor always keep their current values.
     * Stores override this to run inside their write lock; this default is for
     * test doubles only.
     */
    suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean {
        val current = tabs.first().firstOrNull { it.key == tabKey } ?: return false
        val next = transform(current)?.pinnedTo(current) ?: return false
        updateTab(next)
        return true
    }
    suspend fun selectTab(tabKey: String?)
    suspend fun closeTabs(
        tabKeys: Set<String>,
        nowEpochMillis: Long,
        finalScrollAnchors: Map<String, ScrollAnchor> = emptyMap()
    ): ClosedTabBatch?
    suspend fun restoreClosedTabs(batch: ClosedTabBatch)
    suspend fun loadPendingClosedTabs(nowEpochMillis: Long): ClosedTabBatch?

    suspend fun upsertHistory(entry: CompatHistoryEntry)

    /**
     * History counterpart of [updateTabIfPresent]: a deleted entry stays deleted
     * and newer titles or visit times are not overwritten by an old copy.
     */
    suspend fun updateHistoryIfPresent(
        canonicalUrl: String,
        transform: (CompatHistoryEntry) -> CompatHistoryEntry?
    ): Boolean {
        val current = history.first().firstOrNull { it.canonicalUrl == canonicalUrl } ?: return false
        val next = transform(current)?.copy(canonicalUrl = current.canonicalUrl) ?: return false
        upsertHistory(next)
        return true
    }
    /** Called on thread activation, before any network request completes. */
    suspend fun recordHistoryVisit(entry: CompatHistoryEntry)
    suspend fun deleteHistory(canonicalUrl: String)
    suspend fun clearHistory()

    suspend fun saveDraft(draft: CompatReplyDraft)
    suspend fun loadDraft(tabKey: String): CompatReplyDraft?
    suspend fun deleteDraft(tabKey: String)

    suspend fun saveBuildDraft(draft: CompatBuildDraft)
    suspend fun loadBuildDraft(boardKey: String): CompatBuildDraft?
    suspend fun deleteBuildDraft(boardKey: String)

    /** Returns false when a newer or equal revision is already committed. */
    suspend fun saveThreadSnapshot(snapshot: CompatThreadSnapshot): Boolean
    suspend fun loadThreadSnapshot(tabKey: String): CompatThreadSnapshot?
    /** Read a cached body by canonical Futaba URL so both UIs share it. */
    suspend fun loadThreadSnapshotByCanonicalUrl(canonicalUrl: String): CompatThreadSnapshot? = null
    /** Store a modern-mode response in the shared compatibility body cache. */
    suspend fun saveSharedThreadSnapshot(
        canonicalUrl: String,
        originalUrl: String,
        boardName: String,
        title: String,
        thumbnailUrl: String?,
        snapshot: CompatThreadSnapshot
    ): Boolean = false
    suspend fun threadSnapshotCacheUsageBytes(): Long
    /** Clears cached thread bodies only. Tabs, history, drafts, and closed Undo payloads remain. */
    suspend fun clearThreadSnapshotCache(): Long
    suspend fun updateScrollAnchor(tabKey: String, anchor: ScrollAnchor)
    suspend fun updateWorkspace(record: CompatWorkspaceRecord)

    suspend fun loadCatalogPreference(boardKey: String): CompatCatalogPreference
    suspend fun saveCatalogPreference(preference: CompatCatalogPreference)
    /** Returns false when an equal or newer snapshot already exists for this board/sort. */
    suspend fun saveCatalogSnapshot(
        snapshot: CompatCatalogSnapshot,
        trackDropped: Boolean = false,
        requestedThreadCount: Int = snapshot.items.size,
        activeDroppedThreadIds: Set<String> = emptySet()
    ): Boolean
    /**
     * Loads a catalog generation in newest-first order. `generation = 0` is
     * the current snapshot, `1` is the state before the last reload, and so
     * on. The compatibility UI intentionally exposes only four previous
     * generations even though stores retain six for crash-safe rotation.
     */
    suspend fun loadCatalogSnapshot(
        boardKey: String,
        sort: CompatCatalogSort,
        generation: Int = 0
    ): CompatCatalogSnapshot?
    suspend fun loadDroppedCatalogItems(boardKey: String): List<CompatDroppedCatalogItem>
    suspend fun deleteDroppedCatalogItems(
        boardKey: String,
        classification: CompatCatalogDroppedClass
    ): Int

    suspend fun loadPreference(key: String): String?
    suspend fun savePreference(key: String, value: String)

    /**
     * Writes several preferences as one change; a null value removes the key.
     * Stores override this to persist once (iOS and desktop rewrite their whole
     * state per change). This default is for test doubles.
     */
    suspend fun savePreferences(values: Map<String, String?>) {
        values.forEach { (key, value) -> savePreference(key, value ?: "") }
    }

    /**
     * Catalog image hashes by [compatImagePhashCachePreferenceKey]. They are a
     * bounded cache in their own table, not preferences: stored as preferences
     * they grew without limit, were rewritten with the whole iOS state on each
     * save and pushed real settings past the 4096-preference limit. Stores
     * without a cache table (test doubles) keep nothing.
     */
    suspend fun loadImagePhashes(keys: Collection<String>): Map<String, String> = emptyMap()

    suspend fun saveImagePhashes(entries: Map<String, String>) {}

    /**
     * Export/import user settings only. Implementations that cannot persist a
     * portable file may keep the default unsupported behavior; Android's
     * compatibility store provides the transactional implementation.
     */
    suspend fun exportSettingsBackup(): String =
        error("互換モードのバックアップはこのプラットフォームでは利用できません")

    suspend fun importSettingsBackup(
        payload: String,
        restoreUserSettings: Boolean = true,
        restoreNgRules: Boolean = true
    ): CompatSettingsBackupImportReport =
        error("互換モードの復元はこのプラットフォームでは利用できません")

    suspend fun enqueueArchiveReport(rawThreadUrl: String, nowEpochMillis: Long): ArchiveReportEnqueueResult
    /** Runs the bounded 4,500 -> 4,000 outbox cleanup from a background worker. */
    suspend fun maintainArchiveReportOutbox(nowEpochMillis: Long): Int
    suspend fun recoverStaleArchiveReports(nowEpochMillis: Long): Int
    suspend fun claimArchiveReportBatch(nowEpochMillis: Long, newRequestId: String): ArchiveReportOutboxBatch?
    suspend fun reassignSendingArchiveReportBatch(
        oldRequestId: String,
        payload: ArchiveReportPayload,
        nowEpochMillis: Long
    ): Boolean
    suspend fun splitSendingArchiveReportBatch(
        oldRequestId: String,
        first: ArchiveReportPayload,
        second: ArchiveReportPayload,
        nowEpochMillis: Long
    ): Boolean
    suspend fun markArchiveReportAccepted(requestId: String, nowEpochMillis: Long): Int
    suspend fun markArchiveReportRetry(requestId: String, nextAttemptAt: Long, errorCode: String): Int
    suspend fun markArchiveReportBatchForSplit(requestId: String, nowEpochMillis: Long): Int
    suspend fun markArchiveReportAbandoned(requestId: String, nowEpochMillis: Long, errorCode: String): Int
    suspend fun archiveReportOutboxStats(): ArchiveReportOutboxStats
    suspend fun archiveReportNextAttemptAt(): Long?
    suspend fun clearArchiveReportOutbox(): Int

    suspend fun loadToolbar(surface: CompatToolbarSurface): List<CompatToolbarItem>
    suspend fun saveToolbar(surface: CompatToolbarSurface, items: List<CompatToolbarItem>)

    /** Returns false when the board/tab scope disappeared before the write. */
    suspend fun upsertNgRule(rule: CompatNgRule): Boolean
    suspend fun deleteNgRule(ruleId: String)

    /** Deletes a set of NG rules as one logical user action. */
    suspend fun deleteNgRules(ruleIds: Collection<String>) {
        for (ruleId in ruleIds) deleteNgRule(ruleId)
    }
}

/** Keeps the identity and the durable scroll anchor of the stored tab. */
fun CompatTab.pinnedTo(current: CompatTab): CompatTab =
    copy(key = current.key, canonicalUrl = current.canonicalUrl, scrollAnchor = current.scrollAnchor)
