package com.valoser.futacha.compat

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import com.valoser.futacha.shared.compat.ClosedCompatTab
import com.valoser.futacha.shared.compat.ClosedTabBatch
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_ROWS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAINTENANCE_BATCH_ROWS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAINTENANCE_START_ROWS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAINTENANCE_TARGET_ROWS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_RETENTION_MILLIS
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_SENDING_STALE_MILLIS
import com.valoser.futacha.shared.compat.archiveReportStaleCutoffEpochMillis
import com.valoser.futacha.shared.util.saturatingEpochAdd
import com.valoser.futacha.shared.compat.ArchiveReportEnqueueResult
import com.valoser.futacha.shared.compat.ArchiveReportOutboxBatch
import com.valoser.futacha.shared.compat.ArchiveReportOutboxStats
import com.valoser.futacha.shared.compat.ArchiveReportPayload
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatCatalogLayout
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSnapshotItem
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatCatalogDroppedClass
import com.valoser.futacha.shared.compat.CompatDroppedCatalogItem
import com.valoser.futacha.shared.compat.buildCompatCatalogItemStates
import com.valoser.futacha.shared.compat.diffCompatCatalogGenerations
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.isCompatNgScopeValid
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatSettingsBackupImportReport
import com.valoser.futacha.shared.compat.CompatToolbarBackup
import com.valoser.futacha.shared.compat.CompatToolbarBackupItem
import com.valoser.futacha.shared.compat.COMPAT_THREAD_CACHE_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.MAX_COMPAT_THREAD_SNAPSHOT_POSTS
import com.valoser.futacha.shared.compat.MAX_COMPAT_PREFERENCE_KEY_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_PREFERENCE_VALUE_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_IMAGE_URL_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_MEMO_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_RULE_ID_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_RULES
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_SCOPE_KEY_CHARS
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_VALUE_CHARS
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.SelectorPresentation
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.parseCompatThreadCacheQuotaBytes
import com.valoser.futacha.shared.compat.buildArchiveReportPayload
import com.valoser.futacha.shared.compat.normalizeArchiveReportThreadUrl
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import com.valoser.futacha.shared.compat.validateCompatToolbar
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.validateCompatSettingsBackup
import com.valoser.futacha.shared.compat.requireValidCompatPreference
import com.valoser.futacha.shared.compat.isValidCompatNgRule
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.compat.cleanupCompatPostAttachmentLocators
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

private enum class CompatObservableState { BOARDS, TABS, HISTORY, WORKSPACE, PREFERENCES, NG_RULES }
private val ALL_COMPAT_OBSERVABLE_STATES = CompatObservableState.entries.toSet()
private val NO_COMPAT_OBSERVABLE_STATES = emptySet<CompatObservableState>()

class AndroidCompatibilityStore(
    context: Context,
    private val attachmentFileSystem: FileSystem? = null,
    databaseName: String = "classic_tabs_compat.db",
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val threadSnapshotQuotaOverrideBytes: (() -> Long?)? = null
) : CompatibilityStore {
    private val helper = CompatibilityDatabaseHelper(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val lifecycleJob = SupervisorJob()
    private val lifecycleScope = CoroutineScope(lifecycleJob + Dispatchers.IO)
    private var closedBatchExpiryJob: Job? = null
    @Volatile
    private var closed = false
    private val json = Json { ignoreUnknownKeys = true }
    private val anchorSerializer = ScrollAnchor.serializer()
    private val closedBatchSerializer = ClosedTabBatch.serializer()
    private val postsSerializer = ListSerializer(CompatPostSnapshot.serializer())
    private val catalogItemSerializer = CompatCatalogSnapshotItem.serializer()

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

    suspend fun initialize() {
        val cleanup = mutate(refreshOnly = true) { db ->
            db.repairCanonicalBoards()
            db.repairThreadSnapshotCache()
            db.enforceThreadSnapshotQuota()
            val expired = db.readClosedBatches { it.expiresAtEpochMillis <= currentTimeMillis() }
            expired.forEach { batch ->
                db.delete("compat_closed_batch", "batch_id=?", arrayOf(batch.id))
            }
            val pending = db.readClosedBatches { it.expiresAtEpochMillis > currentTimeMillis() }
                .maxByOrNull { it.expiresAtEpochMillis }
            AttachmentCleanupMutation(
                value = pending,
                candidates = expired.attachmentLocators(),
                retained = db.readRetainedAttachmentLocators()
            )
        }
        cleanupAttachments(cleanup)
        scheduleClosedBatchExpiry(cleanup.value)
    }

    /** Test-only lifecycle shutdown. Production code never closes this store synchronously. */
    internal suspend fun closeForTest() {
        // Mark the store closed before waiting for the lifecycle/mutation lock.
        // Calls already inside a transaction are allowed to finish; callbacks
        // queued behind this point fail as coroutine cancellation instead of
        // reopening the helper while teardown is in progress.
        closed = true
        closedBatchExpiryJob?.cancel()
        lifecycleScope.cancel()
        // An expiry job may already be inside mutate(). Waiting only for the
        // mutex is not sufficient when cancellation is propagating: a canceled
        // coroutine can still be unwinding its transaction while the helper is
        // being closed. Join the whole lifecycle job first, then close while
        // holding the same mutex used by every DB operation.
        lifecycleJob.join()
        mutex.withLock {
            helper.close()
        }
    }

    override suspend fun bootstrapBoardsIfNeeded(modernBoards: List<BoardSummary>): Boolean = mutate(
        refresh = setOf(CompatObservableState.BOARDS)
    ) { db ->
        if (db.metadataValue(METADATA_BOARD_BOOTSTRAP_VERSION) != null) return@mutate false
        val imported = validatedModernBoards(modernBoards)
        imported.forEach { board -> db.insertBoardIfMissing(board) }
        db.putMetadata(METADATA_BOARD_BOOTSTRAP_VERSION, BOARD_BOOTSTRAP_VERSION.toString())
        true
    }

    override suspend fun importModernBoards(modernBoards: List<BoardSummary>): Int = mutate(
        refresh = setOf(CompatObservableState.BOARDS)
    ) { db ->
        validatedModernBoards(modernBoards).count { board -> db.insertBoardIfMissing(board) }
    }

    override suspend fun importModernHistory(
        modernHistory: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>
    ): Int = mutate(refresh = setOf(CompatObservableState.HISTORY)) { db ->
        val existing = db.readHistory().associateBy { it.canonicalUrl }
        val knownBoardKeys = db.readBoards().mapTo(mutableSetOf()) { it.key }
        modernHistory.mapNotNull { it.toCompatHistoryEntry() }
            .filter { it.boardKey in knownBoardKeys }
            .count { entry ->
            val deletedAt = db.historyTombstoneAt(entry.canonicalUrl)
            if (deletedAt != null && entry.contentUpdatedAtEpochMillis <= deletedAt) {
                return@count false
            }
            if (existing[entry.canonicalUrl] == entry) {
                false
            } else {
                if (deletedAt != null) db.deleteHistoryTombstone(entry.canonicalUrl)
                // Modern and compatibility lists have different item
                // layouts. Import metadata, but keep an existing
                // compatibility-local anchor intact.
                val currentAnchor = existing[entry.canonicalUrl]?.scrollAnchor
                db.upsertHistory(
                    if (currentAnchor != null) entry.copy(scrollAnchor = currentAnchor) else entry
                )
                true
            }
        }.also { changed ->
            if (changed > 0) db.trimHistory()
        }
    }

    override suspend fun upsertBoard(board: CompatBoard) = mutate(
        refresh = setOf(CompatObservableState.BOARDS)
    ) { db ->
        require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) { "Invalid Futaba board URL" }
        db.upsertBoard(board)
    }

    override suspend fun upsertBoards(boards: List<CompatBoard>) = mutate(
        refresh = setOf(CompatObservableState.BOARDS)
    ) { db ->
        boards.forEach { board ->
            require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) {
                "Invalid Futaba board URL"
            }
            db.upsertBoard(board)
        }
    }

    override suspend fun reorderBoards(orderedKeys: List<String>) = mutate(
        refresh = setOf(CompatObservableState.BOARDS)
    ) { db ->
        val existing = db.readBoards().map { it.key }
        require(existing.toSet() == orderedKeys.toSet() && existing.size == orderedKeys.size) {
            "Board reorder must contain every existing board exactly once"
        }
        orderedKeys.forEachIndexed { index, key ->
            db.update("compat_board", ContentValues().apply { put("sort_order", index) }, "board_key=?", arrayOf(key))
        }
    }

    override suspend fun deleteBoard(boardKey: String) {
        val cleanup = mutate(
            refresh = setOf(
                CompatObservableState.BOARDS,
                CompatObservableState.TABS,
                CompatObservableState.HISTORY,
                CompatObservableState.WORKSPACE,
                CompatObservableState.NG_RULES
            )
        ) { db ->
            val candidates = db.readDraftAttachmentLocatorsForBoard(boardKey).toMutableSet()
            db.query(
                "compat_tab",
                arrayOf("tab_key"),
                "board_key=?",
                arrayOf(boardKey),
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) db.removeThreadSnapshotAccess(cursor.getString(0))
            }
            db.readClosedBatches().forEach { batch ->
                val removed = batch.tabs.filter { it.tab.boardKey == boardKey }
                if (removed.isEmpty()) return@forEach
                candidates += removed.mapNotNull { it.draft?.attachmentUri }
                val remaining = batch.tabs.filterNot { it.tab.boardKey == boardKey }
                if (remaining.isEmpty()) {
                    db.delete("compat_closed_batch", "batch_id=?", arrayOf(batch.id))
                } else {
                    val repaired = batch.copy(
                        tabs = remaining,
                        selectedTabKey = batch.selectedTabKey?.takeIf { selected ->
                            remaining.any { it.tab.key == selected }
                        }
                    )
                    db.updateClosedBatch(repaired)
                }
            }
            db.delete("compat_ng_rule", "scope_key=?", arrayOf(boardKey))
            db.delete("compat_board", "board_key=?", arrayOf(boardKey))
            val active = db.readWorkspace().activeTabKey
            if (active != null && db.readTab(active) == null) {
                db.updateWorkspace(db.readWorkspace().copy(activeTabKey = db.readTabs().firstOrNull()?.key))
            }
            AttachmentCleanupMutation(Unit, candidates, db.readRetainedAttachmentLocators())
        }
        cleanupAttachments(cleanup)
    }

    override suspend fun openTab(tab: CompatTab, historyEntry: CompatHistoryEntry?) {
        val cleanup = mutate(
            refresh = setOf(
                CompatObservableState.TABS,
                CompatObservableState.HISTORY,
                CompatObservableState.WORKSPACE
            )
        ) { db ->
            val currentAnchor = db.readTab(tab.key)?.scrollAnchor
            val durableTab = currentAnchor?.let { tab.copy(scrollAnchor = it) } ?: tab
            db.upsertTab(durableTab)
            historyEntry?.let { entry ->
                db.deleteHistoryTombstone(entry.canonicalUrl)
                val currentHistoryAnchor = db.readHistory()
                    .firstOrNull { it.canonicalUrl == entry.canonicalUrl }
                    ?.scrollAnchor
                db.upsertHistory(
                    currentHistoryAnchor?.let { entry.copy(scrollAnchor = it) } ?: entry
                )
            }
            db.updateWorkspace(db.readWorkspace().copy(activeTabKey = tab.key, generation = db.readWorkspace().generation + 1))
            val trimmedAttachments = db.trimTabs()
            db.trimHistory()
            AttachmentCleanupMutation(Unit, trimmedAttachments, db.readRetainedAttachmentLocators())
        }
        cleanupAttachments(cleanup)
    }

    override suspend fun updateTab(tab: CompatTab) = mutate(
        refresh = setOf(CompatObservableState.TABS)
    ) { db ->
        // Metadata/status refreshes frequently operate on a snapshot captured
        // before the latest scroll write. Never let that stale whole-tab copy
        // roll the durable anchor back.
        val currentAnchor = db.readTab(tab.key)?.scrollAnchor
        db.upsertTab(
            if (currentAnchor != null) tab.copy(scrollAnchor = currentAnchor) else tab
        )
    }

    override suspend fun selectTab(tabKey: String?) = mutate(
        refresh = setOf(CompatObservableState.WORKSPACE)
    ) { db ->
        require(tabKey == null || db.readTab(tabKey) != null) { "Unknown compatibility tab: $tabKey" }
        val current = db.readWorkspace()
        db.updateWorkspace(current.copy(activeTabKey = tabKey, generation = current.generation + 1))
    }

    override suspend fun closeTabs(
        tabKeys: Set<String>,
        nowEpochMillis: Long,
        finalScrollAnchors: Map<String, ScrollAnchor>
    ): ClosedTabBatch? {
        val cleanup = mutate(
            refresh = setOf(
                CompatObservableState.TABS,
                CompatObservableState.HISTORY,
                CompatObservableState.WORKSPACE
            )
        ) { db ->
            // Commit the last frame's anchor in the same transaction that
            // snapshots and removes the tab. A DisposableEffect write can run
            // after deletion and cannot repair the Undo payload.
            finalScrollAnchors.forEach { (tabKey, anchor) ->
                db.readTab(tabKey)?.let { tab ->
                    db.upsertTab(tab.copy(scrollAnchor = anchor))
                    db.readHistory()
                        .firstOrNull { it.canonicalUrl == tab.canonicalUrl }
                        ?.let { db.upsertHistory(it.copy(scrollAnchor = anchor)) }
                }
            }
            val currentTabs = db.readTabs()
            val closed = currentTabs.mapIndexedNotNull { index, tab ->
                if (tab.key !in tabKeys) null else ClosedCompatTab(tab = tab, originalIndex = index)
            }
            if (closed.isEmpty()) {
                return@mutate AttachmentCleanupMutation<ClosedTabBatch?>(null)
            }
            val supersededAttachments = db.readClosedBatches().attachmentLocators()
            val closedDraftAttachments = closed.mapNotNullTo(mutableSetOf()) { db.readDraft(it.tab.key)?.attachmentUri }
            closed.forEach { db.deleteTabAndRetainSnapshot(it.tab.key) }
            val workspace = db.readWorkspace()
            val remaining = db.readTabs()
            val selected = workspace.activeTabKey?.takeIf { key -> remaining.any { it.key == key } }
                ?: remaining.firstOrNull()?.key
            db.updateWorkspace(workspace.copy(activeTabKey = selected, generation = workspace.generation + 1))
            val batch = ClosedTabBatch(
                id = "close-$nowEpochMillis-${closed.joinToString("-") { it.tab.key }}",
                tabs = closed,
                selectedTabKey = workspace.activeTabKey,
                expiresAtEpochMillis = saturatingEpochAdd(nowEpochMillis, UNDO_WINDOW_MILLIS)
            )
            db.delete("compat_closed_batch", null, null)
            db.insertClosedBatch(batch)
            AttachmentCleanupMutation(batch, supersededAttachments + closedDraftAttachments, db.readRetainedAttachmentLocators())
        }
        cleanupAttachments(cleanup)
        scheduleClosedBatchExpiry(cleanup.value)
        return cleanup.value
    }

    override suspend fun restoreClosedTabs(batch: ClosedTabBatch) {
        val cleanup = mutate(
            refresh = setOf(CompatObservableState.TABS, CompatObservableState.WORKSPACE)
        ) { db ->
            // Match the reference APK: Undo restores the closed thread row only.
            // The thread body remains in the independent cache, while the tab
            // draft was removed when the tab row was closed.
            val durableBatch = db.readClosedBatches().firstOrNull { it.id == batch.id } ?: batch
            val boardKeys = db.readBoards().mapTo(mutableSetOf()) { it.key }
            val restorable = durableBatch.tabs.filter { it.tab.boardKey in boardKeys }
            restorable.forEach { closed ->
                db.upsertTab(closed.tab)
            }
            val current = db.readWorkspace()
            val selected = durableBatch.selectedTabKey?.takeIf { db.readTab(it) != null } ?: current.activeTabKey
            db.updateWorkspace(current.copy(activeTabKey = selected, generation = current.generation + 1))
            db.delete("compat_closed_batch", "batch_id=?", arrayOf(durableBatch.id))
            AttachmentCleanupMutation(
                Unit,
                durableBatch.tabs.mapNotNullTo(mutableSetOf()) { it.draft?.attachmentUri },
                db.readRetainedAttachmentLocators()
            )
        }
        cleanupAttachments(cleanup)
        closedBatchExpiryJob?.cancel()
        closedBatchExpiryJob = null
    }

    override suspend fun loadPendingClosedTabs(nowEpochMillis: Long): ClosedTabBatch? {
        val cleanup = mutate(
            refreshOnly = true,
            refresh = NO_COMPAT_OBSERVABLE_STATES
        ) { db ->
            val expired = db.readClosedBatches { it.expiresAtEpochMillis <= nowEpochMillis }
            expired.forEach { batch ->
                db.delete("compat_closed_batch", "batch_id=?", arrayOf(batch.id))
            }
            val pending = db.readClosedBatches { it.expiresAtEpochMillis > nowEpochMillis }
                .maxByOrNull { it.expiresAtEpochMillis }
            AttachmentCleanupMutation(
                pending,
                expired.attachmentLocators(),
                db.readRetainedAttachmentLocators()
            )
        }
        cleanupAttachments(cleanup)
        scheduleClosedBatchExpiry(cleanup.value)
        return cleanup.value
    }

    override suspend fun upsertHistory(entry: CompatHistoryEntry) = mutate(
        refresh = setOf(CompatObservableState.HISTORY)
    ) { db ->
        if (db.hasHistoryTombstone(entry.canonicalUrl)) return@mutate
        // History metadata updates have the same stale-snapshot hazard as
        // tab updates. The dedicated updateScrollAnchor() operation is the
        // only normal path allowed to replace an existing anchor.
        val currentAnchor = db.readHistory()
            .firstOrNull { it.canonicalUrl == entry.canonicalUrl }
            ?.scrollAnchor
        db.upsertHistory(
            if (currentAnchor != null) entry.copy(scrollAnchor = currentAnchor) else entry
        )
        db.trimHistory()
    }

    override suspend fun deleteHistory(canonicalUrl: String) = mutate(
        refresh = setOf(CompatObservableState.HISTORY)
    ) { db ->
        db.putHistoryTombstone(canonicalUrl)
        db.delete("compat_history", "canonical_url=?", arrayOf(canonicalUrl))
        Unit
    }

    override suspend fun clearHistory() = mutate(
        refresh = setOf(CompatObservableState.HISTORY)
    ) { db ->
        (db.readHistory().map { it.canonicalUrl } + db.readTabs().map { it.canonicalUrl })
            .distinct()
            .forEach { canonicalUrl -> db.putHistoryTombstone(canonicalUrl) }
        db.delete("compat_history", null, null)
        Unit
    }

    override suspend fun saveDraft(draft: CompatReplyDraft) = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db -> db.upsertDraft(draft) }

    override suspend fun loadDraft(tabKey: String): CompatReplyDraft? = read { it.readDraft(tabKey) }

    override suspend fun deleteDraft(tabKey: String) = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.delete("compat_reply_draft", "tab_key=?", arrayOf(tabKey))
        Unit
    }

    override suspend fun saveBuildDraft(draft: CompatBuildDraft) = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db -> db.upsertBuildDraft(draft) }

    override suspend fun loadBuildDraft(boardKey: String): CompatBuildDraft? = read { it.readBuildDraft(boardKey) }

    override suspend fun deleteBuildDraft(boardKey: String) = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.delete("compat_build_draft", "board_key=?", arrayOf(boardKey))
        Unit
    }

    override suspend fun saveThreadSnapshot(snapshot: CompatThreadSnapshot): Boolean = mutate(
        refresh = setOf(CompatObservableState.TABS)
    ) { db ->
        validateThreadSnapshot(snapshot)
        val saved = db.writeThreadSnapshot(snapshot, rejectStale = true)
        if (saved) db.enforceThreadSnapshotQuota()
        saved
    }

    override suspend fun loadThreadSnapshot(tabKey: String): CompatThreadSnapshot? = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        db.readThreadSnapshot(tabKey, touch = true)
    }

    override suspend fun loadThreadSnapshotByCanonicalUrl(
        canonicalUrl: String
    ): CompatThreadSnapshot? = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        val parsed = canonicalizeThreadUrl(canonicalUrl) ?: return@mutate null
        val tabKey = compatTabKey(parsed.canonicalUrl)
        db.readThreadSnapshot(tabKey, touch = true)
            ?: db.readTabs().firstOrNull { it.canonicalUrl == parsed.canonicalUrl }
                ?.let { tab -> db.readThreadSnapshot(tab.key, touch = true) }
    }

    override suspend fun saveSharedThreadSnapshot(
        canonicalUrl: String,
        originalUrl: String,
        boardName: String,
        title: String,
        thumbnailUrl: String?,
        snapshot: CompatThreadSnapshot
    ): Boolean = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        val parsed = canonicalizeThreadUrl(canonicalUrl) ?: return@mutate false
        val tabKey = compatTabKey(parsed.canonicalUrl)
        validateThreadSnapshot(snapshot)
        // Body cache writes are not visits. Keep compatibility tabs/history
        // untouched so deleting history cannot be undone by a modern load.
        val sharedSnapshot = snapshot.copy(tabKey = tabKey)
        val saved = db.writeThreadSnapshot(
            sharedSnapshot,
            rejectStale = true,
            allowMissingTab = true
        )
        if (saved) db.enforceThreadSnapshotQuota()
        saved
    }

    override suspend fun threadSnapshotCacheUsageBytes(): Long = read { db ->
        db.readThreadSnapshotCacheRows().sumOf(ThreadSnapshotCacheRow::byteCount)
    }

    override suspend fun clearThreadSnapshotCache(): Long = mutate(
        refresh = setOf(CompatObservableState.TABS)
    ) { db ->
        val removedBytes = db.readThreadSnapshotCacheRows().sumOf(ThreadSnapshotCacheRow::byteCount)
        db.delete("compat_post", null, null)
        db.delete("compat_thread_snapshot", null, null)
        db.update("compat_tab", ContentValues().apply { put("snapshot_revision", 0L) }, null, null)
        db.delete(
            "compat_metadata",
            "key LIKE ?",
            arrayOf("$THREAD_SNAPSHOT_ACCESS_PREFIX%")
        )
        removedBytes
    }

    override suspend fun updateScrollAnchor(tabKey: String, anchor: ScrollAnchor) = mutate(
        refresh = setOf(CompatObservableState.TABS, CompatObservableState.HISTORY)
    ) { db ->
        val encodedAnchor = json.encodeToString(anchorSerializer, anchor)
        db.update(
            "compat_tab",
            ContentValues().apply { put("scroll_anchor_json", encodedAnchor) },
            "tab_key=?",
            arrayOf(tabKey)
        )
        // History entries are also used to recreate a tab after it was closed.
        // Keeping only compat_tab up to date made a close/reopen path fall back
        // to the older history anchor even though the open tab had been saved.
        db.query(
            "compat_tab",
            arrayOf("canonical_url"),
            "tab_key=?",
            arrayOf(tabKey),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                db.update(
                    "compat_history",
                    ContentValues().apply { put("scroll_anchor_json", encodedAnchor) },
                    "canonical_url=?",
                    arrayOf(cursor.getString(0))
                )
            }
        }
        Unit
    }

    override suspend fun updateWorkspace(record: CompatWorkspaceRecord) = mutate(
        refresh = setOf(CompatObservableState.WORKSPACE)
    ) { db -> db.updateWorkspace(record) }

    override suspend fun loadCatalogPreference(boardKey: String): CompatCatalogPreference = read { db ->
        db.readCatalogPreference(boardKey)
    }

    private fun SQLiteDatabase.readCatalogPreference(boardKey: String): CompatCatalogPreference = query(
        "compat_catalog_preference",
        arrayOf("sort_mode", "layout_mode", "reply_priority_enabled", "show_non_priority", "few_replies_delay"),
        "board_key=?",
        arrayOf(boardKey),
        null,
        null,
        null
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use CompatCatalogPreference(boardKey = boardKey)
        CompatCatalogPreference(
            boardKey = boardKey,
            sort = runCatching { CompatCatalogSort.valueOf(cursor.getString(0)) }.getOrDefault(CompatCatalogSort.CATALOG),
            layout = runCatching { CompatCatalogLayout.valueOf(cursor.getString(1)) }.getOrDefault(CompatCatalogLayout.GRID),
            replyPriorityEnabled = cursor.getInt(2) != 0,
            showNonPriority = cursor.getInt(3) != 0,
            fewRepliesDelay = cursor.getInt(4).coerceIn(0, 30)
        )
    }

    private fun SQLiteDatabase.upsertCatalogPreference(preference: CompatCatalogPreference) {
        val values = ContentValues().apply {
            put("board_key", preference.boardKey)
            put("sort_mode", preference.sort.name)
            put("layout_mode", preference.layout.name)
            put("reply_priority_enabled", preference.replyPriorityEnabled.asInt())
            put("show_non_priority", preference.showNonPriority.asInt())
            put("few_replies_delay", preference.fewRepliesDelay.coerceIn(0, 30))
        }
        insertWithOnConflict("compat_catalog_preference", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun SQLiteDatabase.readToolbarItems(surface: CompatToolbarSurface): List<CompatToolbarItem> = query(
        "compat_toolbar",
        arrayOf("command_key", "position", "active"),
        "surface=?",
        arrayOf(surface.name),
        null,
        null,
        "position ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(CompatToolbarItem(cursor.getString(0), cursor.getInt(1), cursor.getInt(2) != 0))
            }
        }
    }

    override suspend fun saveCatalogPreference(preference: CompatCatalogPreference) = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        require(db.readBoards().any { it.key == preference.boardKey }) { "Unknown compatibility board" }
        db.upsertCatalogPreference(preference)
        Unit
    }

    override suspend fun saveCatalogSnapshot(
        snapshot: CompatCatalogSnapshot,
        trackDropped: Boolean,
        requestedThreadCount: Int,
        activeDroppedThreadIds: Set<String>
    ): Boolean = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        require(snapshot.boardKey.isNotBlank()) { "Compatibility catalog snapshot requires a board" }
        require(snapshot.revision >= 0L) { "Compatibility catalog revision must be non-negative" }
        require(snapshot.items.size <= MAX_COMPAT_CATALOG_SNAPSHOT_ITEMS) {
            "Compatibility catalog snapshot exceeds $MAX_COMPAT_CATALOG_SNAPSHOT_ITEMS items"
        }
        require(snapshot.items.map { it.id }.toSet().size == snapshot.items.size) {
            "Compatibility catalog item IDs must be unique"
        }
        require(db.readBoards().any { it.key == snapshot.boardKey }) { "Unknown compatibility board" }
        val mode = snapshot.sort.name
        val currentHeader = db.query(
            "compat_catalog_snapshot",
            arrayOf("revision", "fetched_at"),
            "board_key=? AND mode=?",
            arrayOf(snapshot.boardKey, mode),
            null,
            null,
            "revision DESC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null }
        val currentRevision = currentHeader?.first
        if (currentRevision != null && currentRevision >= snapshot.revision) return@mutate false
        val previousItems = if (currentRevision != null) {
            db.query(
                "compat_catalog_item",
                arrayOf("item_json"),
                "board_key=? AND mode=? AND revision=? AND length(item_json)<=?",
                arrayOf(
                    snapshot.boardKey,
                    mode,
                    currentRevision.toString(),
                    MAX_COMPAT_CATALOG_ITEM_JSON_CHARS.toString()
                ),
                null,
                null,
                "position ASC",
                MAX_COMPAT_CATALOG_SNAPSHOT_ITEMS.toString()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        runCatching {
                            json.decodeFromString(catalogItemSerializer, cursor.getString(0))
                        }.getOrNull()?.let(::add)
                    }
                }
            }
        } else {
            emptyList()
        }
        val previousStates = previousItems.associate { it.id to it.toState() }
        val itemStates = buildCompatCatalogItemStates(
            items = snapshot.items,
            previousStates = previousStates + snapshot.itemStates,
            fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis,
            sort = snapshot.sort,
            requestedThreadCount = requestedThreadCount
        )
        db.insertOrThrow(
            "compat_catalog_snapshot",
            null,
            ContentValues().apply {
                put("board_key", snapshot.boardKey)
                put("mode", mode)
                put("revision", snapshot.revision)
                put("fetched_at", snapshot.fetchedAtEpochMillis)
            }
        )
        snapshot.items.forEachIndexed { position, item ->
            db.insertOrThrow(
                "compat_catalog_item",
                null,
                ContentValues().apply {
                    put("board_key", snapshot.boardKey)
                    put("mode", mode)
                    put("revision", snapshot.revision)
                    put("position", position)
                    put(
                        "item_json",
                        json.encodeToString(
                            catalogItemSerializer,
                            CompatCatalogSnapshotItem.from(item, itemStates[item.id])
                        )
                    )
                }
            )
        }
        if (trackDropped) {
            val currentIds = snapshot.items.mapTo(mutableSetOf()) { it.id }
            currentIds.forEach { threadId ->
                db.delete(
                    "compat_catalog_dropped",
                    "board_key=? AND thread_id=?",
                    arrayOf(snapshot.boardKey, threadId)
                )
            }
            val diff = diffCompatCatalogGenerations(
                current = snapshot.items,
                previous = previousItems.map(CompatCatalogSnapshotItem::toCatalogItem),
                requestedThreadCount = requestedThreadCount,
                enabled = true
            )
            val classified = buildList {
                diff.vanishedWithin.forEach { item ->
                    add(
                        item to if (item.id in activeDroppedThreadIds) {
                            CompatCatalogDroppedClass.ISOLATED
                        } else {
                            CompatCatalogDroppedClass.DELETED
                        }
                    )
                }
                diff.vanishedBottom.forEach { add(it to CompatCatalogDroppedClass.DIE) }
            }
            val previousById = previousItems.associateBy(CompatCatalogSnapshotItem::id)
            classified.forEach { (droppedItem, classification) ->
                    val dropped = previousById[droppedItem.id]
                        ?: CompatCatalogSnapshotItem.from(droppedItem)
                    db.insertWithOnConflict(
                        "compat_catalog_dropped",
                        null,
                        ContentValues().apply {
                            put("board_key", snapshot.boardKey)
                            put("thread_id", dropped.id)
                            put(
                                "item_json",
                                json.encodeToString(catalogItemSerializer, dropped)
                            )
                            put("dropped_at", snapshot.fetchedAtEpochMillis)
                            put("last_seen_at", currentHeader?.second ?: 0L)
                            put("drop_class", classification.name)
                            put("inserted_at", snapshot.fetchedAtEpochMillis)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            db.execSQL(
                """DELETE FROM compat_catalog_dropped
                    WHERE board_key=? AND thread_id NOT IN (
                        SELECT thread_id FROM compat_catalog_dropped
                        WHERE board_key=? ORDER BY last_seen_at DESC LIMIT ?
                    )""".trimIndent(),
                arrayOf<Any?>(snapshot.boardKey, snapshot.boardKey, MAX_COMPAT_CATALOG_DROPPED_ITEMS)
            )
        }
        db.execSQL(
            """DELETE FROM compat_catalog_snapshot
                WHERE board_key=? AND mode=? AND revision NOT IN (
                    SELECT revision FROM compat_catalog_snapshot
                    WHERE board_key=? AND mode=? ORDER BY revision DESC LIMIT ?
                )""".trimIndent(),
            arrayOf<Any?>(
                snapshot.boardKey,
                mode,
                snapshot.boardKey,
                mode,
                MAX_COMPAT_CATALOG_SNAPSHOT_GENERATIONS
            )
        )
        true
    }

    override suspend fun loadCatalogSnapshot(
        boardKey: String,
        sort: CompatCatalogSort,
        generation: Int
    ): CompatCatalogSnapshot? = read { db ->
        if (generation < 0 || generation >= MAX_COMPAT_CATALOG_SNAPSHOT_GENERATIONS) return@read null
        val mode = sort.name
        val header = db.query(
            "compat_catalog_snapshot",
            arrayOf("revision", "fetched_at"),
            "board_key=? AND mode=?",
            arrayOf(boardKey, mode),
            null,
            null,
            "revision DESC",
            // Android 8's SQLiteQueryBuilder only accepts the legacy
            // "offset,count" spelling.  Although SQLite itself accepts
            // "count OFFSET offset", the framework rejects that clause
            // before it reaches SQLite on API 26.
            "$generation,1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getLong(0) to cursor.getLong(1)
        } ?: return@read null
        val storedItems = db.query(
            "compat_catalog_item",
            arrayOf("item_json"),
            "board_key=? AND mode=? AND revision=? AND length(item_json)<=?",
            arrayOf(boardKey, mode, header.first.toString(), MAX_COMPAT_CATALOG_ITEM_JSON_CHARS.toString()),
            null,
            null,
            "position ASC",
            MAX_COMPAT_CATALOG_SNAPSHOT_ITEMS.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        json.decodeFromString(catalogItemSerializer, cursor.getString(0))
                    }.getOrNull()?.let(::add)
                }
            }
        }
        CompatCatalogSnapshot(
            boardKey = boardKey,
            sort = sort,
            revision = header.first,
            fetchedAtEpochMillis = header.second,
            items = storedItems.map(CompatCatalogSnapshotItem::toCatalogItem),
            itemStates = storedItems.associate { it.id to it.toState() }
        )
    }

    override suspend fun loadDroppedCatalogItems(boardKey: String): List<CompatDroppedCatalogItem> = read { db ->
        db.query(
            "compat_catalog_dropped",
            arrayOf("item_json", "dropped_at", "last_seen_at", "drop_class"),
            "board_key=? AND length(item_json)<=?",
            arrayOf(boardKey, MAX_COMPAT_CATALOG_ITEM_JSON_CHARS.toString()),
            null,
            null,
            "last_seen_at DESC",
            MAX_COMPAT_CATALOG_DROPPED_ITEMS.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching {
                        json.decodeFromString(catalogItemSerializer, cursor.getString(0)).toCatalogItem()
                    }.getOrNull()?.let { item ->
                        add(
                            CompatDroppedCatalogItem(
                                boardKey = boardKey,
                                item = item,
                                droppedAtEpochMillis = cursor.getLong(1),
                                lastSeenAtEpochMillis = cursor.getLong(2),
                                classification = runCatching {
                                    CompatCatalogDroppedClass.valueOf(cursor.getString(3))
                                }.getOrDefault(CompatCatalogDroppedClass.DIE)
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun deleteDroppedCatalogItems(
        boardKey: String,
        classification: CompatCatalogDroppedClass
    ): Int = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.delete(
            "compat_catalog_dropped",
            "board_key=? AND drop_class=?",
            arrayOf(boardKey, classification.name)
        )
    }

    override suspend fun loadPreference(key: String): String? = read { db ->
        db.query(
            "compat_preference",
            arrayOf("value_json"),
            "key=?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    override suspend fun savePreference(key: String, value: String) = mutate(
        refresh = setOf(CompatObservableState.PREFERENCES, CompatObservableState.TABS)
    ) { db ->
        requireValidCompatPreference(key, value)
        db.insertWithOnConflict(
            "compat_preference",
            null,
            ContentValues().apply { put("key", key); put("value_json", value) },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        if (key == COMPAT_THREAD_CACHE_PREFERENCE_KEY) db.enforceThreadSnapshotQuota()
        Unit
    }

    override suspend fun exportSettingsBackup(): String = read { db ->
        val boards = db.readBoards()
        val boardKeys = boards.mapTo(mutableSetOf()) { it.key }
        val tabs = db.readTabs()
        val tabKeys = tabs.mapTo(mutableSetOf()) { it.key }
        val catalogPreferences = boards.map { board -> db.readCatalogPreference(board.key) }
        val toolbars = CompatToolbarSurface.entries.mapNotNull { surface ->
            val items = db.readToolbarItems(surface)
            items.takeIf { it.isNotEmpty() }?.let { persisted ->
                CompatToolbarBackup(
                    surface = surface.name,
                    items = persisted.map { item ->
                        CompatToolbarBackupItem(item.key, item.position, item.active)
                    }
                )
            }
        }
        encodeCompatSettingsBackup(
            CompatSettingsBackup(
                exportedAtEpochMillis = currentTimeMillis(),
                boards = boards,
                tabs = tabs.filter { it.boardKey in boardKeys },
                history = db.readHistory().filter { it.boardKey in boardKeys },
                catalogPreferences = catalogPreferences,
                preferences = db.readPreferences(),
                ngRules = db.readNgRules().filter { rule ->
                    com.valoser.futacha.shared.compat.isCompatNgScopeValid(
                        rule.kind,
                        rule.scopeKey,
                        boardKeys,
                        tabKeys
                    )
                },
                workspace = db.readWorkspace(),
                toolbars = toolbars
            )
        )
    }

    override suspend fun importSettingsBackup(
        payload: String,
        restoreUserSettings: Boolean,
        restoreNgRules: Boolean
    ): CompatSettingsBackupImportReport {
        val backup = decodeCompatSettingsBackup(payload)
        validateCompatSettingsBackup(backup)
        return mutate { db ->
            val existingBoardKeys = db.readBoards().mapTo(mutableSetOf()) { it.key }
            val importedBoards = if (restoreUserSettings) {
                backup.boards.forEach { board ->
                    require(canonicalizeBoardUrl(board.originalUrl) == board.canonicalUrl) {
                        "バックアップの板URLが不正です"
                    }
                    db.upsertBoard(board)
                }
                backup.boards.size
            } else 0
            val validBoardKeys = db.readBoards().mapTo(mutableSetOf()) { it.key }
            val importedTabs = if (restoreUserSettings) {
                backup.tabs.filter { it.boardKey in validBoardKeys }.also { tabs ->
                    tabs.forEach { tab -> db.upsertTab(tab) }
                }.size
            } else 0
            val importedHistory = if (restoreUserSettings) {
                backup.history.filter { it.boardKey in validBoardKeys }.also { entries ->
                    entries.forEach { entry ->
                        db.deleteHistoryTombstone(entry.canonicalUrl)
                        db.upsertHistory(entry)
                    }
                }.size
            } else 0
            val importedPreferences = if (restoreUserSettings) {
                backup.preferences.forEach { (key, value) ->
                    require(key.startsWith("compat.")) { "バックアップの設定キーが不正です" }
                    db.insertWithOnConflict(
                        "compat_preference",
                        null,
                        ContentValues().apply {
                            put("key", key)
                            put("value_json", value)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                backup.catalogPreferences.filter { it.boardKey in validBoardKeys }.forEach { preference ->
                    db.upsertCatalogPreference(preference)
                }
                backup.toolbars.forEach { toolbar ->
                    val surface = runCatching { CompatToolbarSurface.valueOf(toolbar.surface) }.getOrNull()
                        ?: return@forEach
                    val items = toolbar.items.map { item ->
                        CompatToolbarItem(item.key, item.position, item.active)
                    }
                    if (validateCompatToolbar(surface, items)) db.replaceToolbar(surface, items)
                }
                val workspace = backup.workspace
                db.updateWorkspace(
                    workspace.copy(
                        activeTabKey = workspace.activeTabKey?.takeIf { key -> db.readTab(key) != null },
                        catalogHostBoardKey = workspace.catalogHostBoardKey?.takeIf { key -> key in validBoardKeys }
                    )
                )
                backup.preferences.size
            } else 0
            val importedNgRules = if (restoreNgRules) {
                val tabKeys = db.readTabs().mapTo(mutableSetOf()) { it.key }
                backup.ngRules.count { rule ->
                    if (!com.valoser.futacha.shared.compat.isCompatNgScopeValid(
                            rule.kind,
                            rule.scopeKey,
                            validBoardKeys,
                            tabKeys
                        )
                    ) return@count false
                    db.insertWithOnConflict(
                        "compat_ng_rule",
                        null,
                        ContentValues().apply {
                            put("rule_id", rule.id)
                            put("kind", rule.kind.name)
                            put("scope_key", rule.scopeKey)
                            put("normalized_value", rule.normalizedValue)
                            put("payload_json", rule.compatPayloadJson())
                            put("created_at", rule.createdAtEpochMillis)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    ) > 0
                }
            } else 0
            CompatSettingsBackupImportReport(
                boardsImported = importedBoards,
                tabsImported = importedTabs,
                historyImported = importedHistory,
                preferencesImported = importedPreferences,
                ngRulesImported = importedNgRules,
                toolbarsImported = if (restoreUserSettings) backup.toolbars.size else 0
            )
        }
    }

    override suspend fun enqueueArchiveReport(
        rawThreadUrl: String,
        nowEpochMillis: Long
    ): ArchiveReportEnqueueResult {
        val normalized = normalizeArchiveReportThreadUrl(rawThreadUrl)
            ?: return ArchiveReportEnqueueResult(inserted = false, sendableCount = 0)
        return mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
            db.delete(
                "archive_report_outbox",
                "state IN ('accepted','abandoned') AND expires_at IS NOT NULL AND expires_at<=?",
                arrayOf(nowEpochMillis.toString())
            )
            db.delete(
                "archive_report_outbox",
                "thread_id=? AND state IN ('accepted','abandoned') AND expires_at IS NOT NULL AND expires_at<=?",
                arrayOf(normalized.threadId, nowEpochMillis.toString())
            )
            val droppedForCapacity = if (db.archiveReportRowCount() >= ARCHIVE_REPORT_MAX_ROWS) {
                !db.makeArchiveReportCapacity()
            } else false
            val inserted = if (droppedForCapacity) {
                false
            } else {
                db.insertWithOnConflict(
                    "archive_report_outbox",
                    null,
                    ContentValues().apply {
                        put("thread_id", normalized.threadId)
                        put("thread_url", normalized.url)
                        put("state", "pending")
                        put("first_seen_at", nowEpochMillis)
                        put(
                            "next_attempt_at",
                            saturatingEpochAdd(
                                nowEpochMillis,
                                com.valoser.futacha.shared.compat.ARCHIVE_REPORT_SEND_DELAY_MILLIS
                            )
                        )
                        put("attempt_count", 0)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                ) != -1L
            }
            ArchiveReportEnqueueResult(
                inserted = inserted,
                sendableCount = db.archiveReportSendableCount(nowEpochMillis),
                droppedForCapacity = droppedForCapacity
            )
        }
    }

    override suspend fun maintainArchiveReportOutbox(nowEpochMillis: Long): Int = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        db.maintainArchiveReportOutbox(nowEpochMillis)
    }

    override suspend fun recoverStaleArchiveReports(nowEpochMillis: Long): Int = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        val staleBefore = archiveReportStaleCutoffEpochMillis(nowEpochMillis)
        db.update(
            "archive_report_outbox",
            ContentValues().apply {
                put("state", "retry")
                put("next_attempt_at", nowEpochMillis)
                putNull("sending_started_at")
                put("last_error", "stale_sending")
            },
            "state='sending' AND sending_started_at IS NOT NULL AND sending_started_at<=?",
            arrayOf(staleBefore.toString())
        )
    }

    override suspend fun claimArchiveReportBatch(
        nowEpochMillis: Long,
        newRequestId: String
    ): ArchiveReportOutboxBatch? = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        val retainedRequestId = db.query(
            "archive_report_outbox",
            arrayOf("batch_request_id"),
            "state='retry' AND batch_request_id IS NOT NULL AND next_attempt_at<=?",
            arrayOf(nowEpochMillis.toString()),
            null,
            null,
            "next_attempt_at ASC, thread_id ASC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

        val rows = if (retainedRequestId != null) {
            db.readArchiveReportRows(
                "state='retry' AND batch_request_id=?",
                arrayOf(retainedRequestId)
            ).takeIf { group -> group.isNotEmpty() && group.all { it.nextAttemptAt <= nowEpochMillis } }
                ?: return@mutate null
        } else {
            db.readArchiveReportRows(
                "state IN ('pending','retry') AND batch_request_id IS NULL AND next_attempt_at<=?",
                arrayOf(nowEpochMillis.toString()),
                limit = com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_BATCH_SIZE.toString()
            )
        }
        if (rows.isEmpty()) return@mutate null
        val retainedPayload = retainedRequestId?.let { id ->
            buildArchiveReportPayload(id, rows.map(ArchiveReportDbRow::normalized))
        }
        val expectedHash = rows.mapNotNull(ArchiveReportDbRow::batchPayloadHash).distinct().singleOrNull()
        val payload = retainedPayload?.takeIf {
            it.threadIds.size == rows.size && expectedHash != null && it.sha256 == expectedHash
        } ?: buildArchiveReportPayload(newRequestId, rows.map(ArchiveReportDbRow::normalized))
            ?: return@mutate null
        val selectedRows = rows.filter { it.threadId in payload.threadIds }
        val updated = db.updateArchiveReportRows(
            payload.threadIds,
            ContentValues().apply {
                put("state", "sending")
                put("batch_request_id", payload.requestId)
                put("batch_payload_hash", payload.sha256)
                put("sending_started_at", nowEpochMillis)
                putNull("last_error")
            },
            additionalWhere = "state IN ('pending','retry')"
        )
        if (updated != payload.threadIds.size) return@mutate null
        ArchiveReportOutboxBatch(payload, selectedRows.maxOfOrNull(ArchiveReportDbRow::attemptCount) ?: 0)
    }

    override suspend fun reassignSendingArchiveReportBatch(
        oldRequestId: String,
        payload: ArchiveReportPayload,
        nowEpochMillis: Long
    ): Boolean = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        if (payload.bytes.size > com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_BODY_BYTES ||
            buildArchiveReportPayload(payload.requestId, payload.threadIds.zip(payload.urls).map {
                com.valoser.futacha.shared.compat.NormalizedArchiveThread(it.first, it.second)
            })?.sha256 != payload.sha256
        ) return@mutate false
        db.updateArchiveReportRows(
            payload.threadIds,
            ContentValues().apply {
                put("batch_request_id", payload.requestId)
                put("batch_payload_hash", payload.sha256)
                put("sending_started_at", nowEpochMillis)
            },
            additionalWhere = "state='sending' AND batch_request_id=?",
            additionalArgs = arrayOf(oldRequestId)
        ) == payload.threadIds.size
    }

    override suspend fun splitSendingArchiveReportBatch(
        oldRequestId: String,
        first: ArchiveReportPayload,
        second: ArchiveReportPayload,
        nowEpochMillis: Long
    ): Boolean = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        if (first.requestId == second.requestId ||
            first.threadIds.toSet().intersect(second.threadIds.toSet()).isNotEmpty() ||
            !first.isValidArchiveReportPayload() || !second.isValidArchiveReportPayload()
        ) return@mutate false
        val sourceRows = db.readArchiveReportRows(
            "state='sending' AND batch_request_id=?",
            arrayOf(oldRequestId)
        )
        val sourceIds = sourceRows.mapTo(linkedSetOf(), ArchiveReportDbRow::threadId)
        if (sourceIds != (first.threadIds + second.threadIds).toSet()) return@mutate false
        val firstUpdated = db.updateArchiveReportRows(
            first.threadIds,
            ContentValues().apply {
                put("batch_request_id", first.requestId)
                put("batch_payload_hash", first.sha256)
                put("sending_started_at", nowEpochMillis)
                putNull("last_error")
            },
            additionalWhere = "state='sending' AND batch_request_id=?",
            additionalArgs = arrayOf(oldRequestId)
        )
        check(firstUpdated == first.threadIds.size) { "Archive report split first-half update failed" }
        val secondUpdated = db.updateArchiveReportRows(
            second.threadIds,
            ContentValues().apply {
                put("batch_request_id", second.requestId)
                put("batch_payload_hash", second.sha256)
                put("sending_started_at", nowEpochMillis)
                putNull("last_error")
            },
            additionalWhere = "state='sending' AND batch_request_id=?",
            additionalArgs = arrayOf(oldRequestId)
        )
        check(secondUpdated == second.threadIds.size) { "Archive report split second-half update failed" }
        true
    }

    override suspend fun markArchiveReportAccepted(requestId: String, nowEpochMillis: Long): Int = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        db.update(
            "archive_report_outbox",
            ContentValues().apply {
                put("state", "accepted")
                put("accepted_at", nowEpochMillis)
                put("expires_at", saturatingEpochAdd(nowEpochMillis, ARCHIVE_REPORT_RETENTION_MILLIS))
                putNull("batch_request_id")
                putNull("batch_payload_hash")
                putNull("sending_started_at")
                putNull("last_error")
            },
            "state='sending' AND batch_request_id=?",
            arrayOf(requestId)
        )
    }

    override suspend fun markArchiveReportRetry(
        requestId: String,
        nextAttemptAt: Long,
        errorCode: String
    ): Int = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.update(
            "archive_report_outbox",
            ContentValues().apply {
                put("state", "retry")
                put("next_attempt_at", nextAttemptAt)
                putNull("sending_started_at")
                put("last_error", errorCode.take(256))
            },
            "state='sending' AND batch_request_id=?",
            arrayOf(requestId)
        ).also { updated ->
            if (updated > 0) {
                // ContentValues cannot express arithmetic; apply it with bound arguments.
                db.execSQL(
                    "UPDATE archive_report_outbox SET attempt_count=CASE " +
                        "WHEN attempt_count<0 THEN 1 WHEN attempt_count>=2147483647 THEN 2147483647 " +
                        "ELSE attempt_count+1 END WHERE state='retry' AND batch_request_id=? AND next_attempt_at=?",
                    arrayOf<Any>(requestId, nextAttemptAt)
                )
            }
        }
    }

    override suspend fun markArchiveReportBatchForSplit(requestId: String, nowEpochMillis: Long): Int = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        db.update(
            "archive_report_outbox",
            ContentValues().apply {
                put("state", "retry")
                put("next_attempt_at", nowEpochMillis)
                putNull("batch_request_id")
                putNull("batch_payload_hash")
                putNull("sending_started_at")
                put("last_error", "batch_split")
            },
            "state='sending' AND batch_request_id=?",
            arrayOf(requestId)
        )
    }

    override suspend fun markArchiveReportAbandoned(
        requestId: String,
        nowEpochMillis: Long,
        errorCode: String
    ): Int = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.update(
            "archive_report_outbox",
            ContentValues().apply {
                put("state", "abandoned")
                put("expires_at", saturatingEpochAdd(nowEpochMillis, ARCHIVE_REPORT_RETENTION_MILLIS))
                putNull("batch_request_id")
                putNull("batch_payload_hash")
                putNull("sending_started_at")
                put("last_error", errorCode.take(256))
            },
            "state='sending' AND batch_request_id=?",
            arrayOf(requestId)
        )
    }

    override suspend fun archiveReportOutboxStats(): ArchiveReportOutboxStats = read { db ->
        ArchiveReportOutboxStats(
            total = db.archiveReportRowCount(),
            pendingOrRetry = db.query(
                "archive_report_outbox", arrayOf("COUNT(*)"), "state IN ('pending','retry')",
                null, null, null, null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        )
    }

    override suspend fun archiveReportNextAttemptAt(): Long? = read { db ->
        val readyAt = db.rawQuery(
            "SELECT MIN(next_attempt_at) FROM archive_report_outbox " +
                "WHERE state IN ('pending','retry')",
            null
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        val sendingStartedAt = db.rawQuery(
            "SELECT MIN(COALESCE(sending_started_at,next_attempt_at)) " +
                "FROM archive_report_outbox WHERE state='sending'",
            null
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        listOfNotNull(
            readyAt,
            sendingStartedAt?.let { saturatingEpochAdd(it, ARCHIVE_REPORT_SENDING_STALE_MILLIS) }
        ).minOrNull()
    }

    override suspend fun clearArchiveReportOutbox(): Int = mutate(refresh = NO_COMPAT_OBSERVABLE_STATES) { db ->
        db.delete("archive_report_outbox", null, null)
    }

    override suspend fun loadToolbar(surface: CompatToolbarSurface): List<CompatToolbarItem> = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        val persisted = db.query(
            "compat_toolbar",
            arrayOf("command_key", "position", "active"),
            "surface=?",
            arrayOf(surface.name),
            null,
            null,
            "position ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CompatToolbarItem(cursor.getString(0), cursor.getInt(1), cursor.getInt(2) != 0))
                }
            }
        }
        val reconciled = reconcileCompatToolbar(surface, persisted)
        if (reconciled != persisted) db.replaceToolbar(surface, reconciled)
        reconciled
    }

    override suspend fun saveToolbar(surface: CompatToolbarSurface, items: List<CompatToolbarItem>) = mutate(
        refresh = NO_COMPAT_OBSERVABLE_STATES
    ) { db ->
        require(validateCompatToolbar(surface, items)) { "Invalid compatibility toolbar" }
        db.replaceToolbar(surface, items)
        Unit
    }

    override suspend fun upsertNgRule(rule: CompatNgRule): Boolean = mutate(
        refresh = setOf(CompatObservableState.NG_RULES)
    ) { db ->
        if (!isValidCompatNgRule(rule)) return@mutate false
        val boardKeys = db.readBoards().mapTo(mutableSetOf()) { it.key }
        val tabKeys = db.readTabs().mapTo(mutableSetOf()) { it.key }
        if (!isCompatNgScopeValid(rule.kind, rule.scopeKey, boardKeys, tabKeys)) {
            // A user can tap NG while a board/tab close or mode transition is still
            // committing. The rule is no longer applicable in that case; do not let
            // an expected stale UI callback crash the app.
            Logger.w(
                "AndroidCompatibilityStore",
                "Ignoring compatibility NG rule for a missing scope: kind=${rule.kind}"
            )
            return@mutate false
        }
        val alreadyExists = DatabaseUtils.longForQuery(
            db,
            "SELECT EXISTS(SELECT 1 FROM compat_ng_rule WHERE rule_id=? LIMIT 1)",
            arrayOf(rule.id)
        ) != 0L
        if (
            !alreadyExists &&
            DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM compat_ng_rule", null) >= MAX_COMPAT_NG_RULES
        ) {
            return@mutate false
        }
        db.insertWithOnConflict(
            "compat_ng_rule",
            null,
            ContentValues().apply {
                put("rule_id", rule.id)
                put("kind", rule.kind.name)
                put("scope_key", rule.scopeKey)
                put("normalized_value", rule.normalizedValue)
                put("payload_json", rule.compatPayloadJson())
                put("created_at", rule.createdAtEpochMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        true
    }

    override suspend fun deleteNgRule(ruleId: String) = mutate(
        refresh = setOf(CompatObservableState.NG_RULES)
    ) { db ->
        db.delete("compat_ng_rule", "rule_id=?", arrayOf(ruleId))
        Unit
    }

    override suspend fun deleteNgRules(ruleIds: Collection<String>) = mutate(
        refresh = setOf(CompatObservableState.NG_RULES)
    ) { db ->
        // SQLite limits bind arguments. Chunking keeps a 1,000+ item cleanup
        // in one transaction/Flow refresh instead of issuing one mutation per
        // row from the Compose dialog.
        ruleIds.filter(String::isNotBlank).distinct().chunked(900).forEach { chunk ->
            db.delete(
                "compat_ng_rule",
                "rule_id IN (${chunk.joinToString(",") { "?" }})",
                chunk.toTypedArray()
            )
        }
        Unit
    }

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Do not let a late Flow collector or Compose callback reopen the
            // SQLiteOpenHelper after test/process teardown has begun.  Apart
            // from producing a closed-database exception, reopening here can
            // race the closing transaction and leave the caller waiting on the
            // same database mutex indefinitely.
            if (closed) throw CancellationException("Compatibility store is closed")
            block(helper.readableDatabase)
        }
    }

    private suspend fun <T> mutate(
        refreshOnly: Boolean = false,
        refresh: Set<CompatObservableState> = ALL_COMPAT_OBSERVABLE_STATES,
        block: (SQLiteDatabase) -> T
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Compose/test teardown can cancel the lifecycle while a UI
            // callback is still queued on its own scope. Treat a late write
            // as normal coroutine cancellation instead of throwing an
            // uncaught IllegalStateException from the worker thread.
            if (closed) throw CancellationException("Compatibility store is closed")
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val result = if (refreshOnly) block(db) else block(db)
                db.setTransactionSuccessful()
                refreshStates(db, refresh)
                result
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun refreshStates(db: SQLiteDatabase, refresh: Set<CompatObservableState>) {
        if (CompatObservableState.BOARDS in refresh) boardsState.value = db.readBoards()
        if (CompatObservableState.TABS in refresh) tabsState.value = db.readTabs()
        if (CompatObservableState.HISTORY in refresh) historyState.value = db.readHistory()
        if (CompatObservableState.WORKSPACE in refresh) workspaceState.value = db.readWorkspace()
        if (CompatObservableState.PREFERENCES in refresh) preferencesState.value = db.readPreferences()
        if (CompatObservableState.NG_RULES in refresh) ngRulesState.value = db.readNgRules()
    }

    private suspend fun <T> cleanupAttachments(mutation: AttachmentCleanupMutation<T>) {
        val fileSystem = attachmentFileSystem
        if (fileSystem == null || mutation.candidates.isEmpty()) return
        cleanupCompatPostAttachmentLocators(
            fileSystem = fileSystem,
            candidateLocators = mutation.candidates,
            retainedLocators = mutation.retained
        ).onSuccess { removed ->
            Logger.d("AndroidCompatibilityStore", "Cleaned $removed compatibility post attachment payload(s)")
        }.onFailure { error ->
            Logger.e("AndroidCompatibilityStore", "Failed to clean compatibility post attachments", error)
        }
    }

    private fun validateThreadSnapshot(snapshot: CompatThreadSnapshot) {
        require(snapshot.tabKey.isNotBlank()) { "Compatibility snapshot requires a tab key" }
        require(snapshot.revision >= 0L) { "Compatibility snapshot revision must be non-negative" }
        require(snapshot.posts.size <= MAX_COMPAT_THREAD_SNAPSHOT_POSTS) {
            "Compatibility snapshot exceeds $MAX_COMPAT_THREAD_SNAPSHOT_POSTS posts"
        }
        require(snapshot.posts.indices.all { index -> snapshot.posts[index].position == index }) {
            "Compatibility snapshot positions must be contiguous and zero-based"
        }
    }

    private fun SQLiteDatabase.writeThreadSnapshot(
        snapshot: CompatThreadSnapshot,
        rejectStale: Boolean,
        allowMissingTab: Boolean = false
    ): Boolean {
        val existingTab = readTab(snapshot.tabKey)
        require(allowMissingTab || existingTab != null) { "Unknown compatibility tab: ${snapshot.tabKey}" }
        val currentRevision = query(
            "compat_thread_snapshot",
            arrayOf("revision"),
            "tab_key=?",
            arrayOf(snapshot.tabKey),
            null,
            null,
            null,
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        if (rejectStale && currentRevision != null && currentRevision >= snapshot.revision) return false

        val values = ContentValues().apply {
            put("tab_key", snapshot.tabKey)
            put("revision", snapshot.revision)
            put("fetched_at", snapshot.fetchedAtEpochMillis)
            put("board_title", snapshot.boardTitle)
            put("expires_label", snapshot.expiresAtLabel)
            put("deleted_notice", snapshot.deletedNotice)
        }
        if (update("compat_thread_snapshot", values, "tab_key=?", arrayOf(snapshot.tabKey)) == 0) {
            insertOrThrow("compat_thread_snapshot", null, values)
        }
        delete("compat_post", "tab_key=?", arrayOf(snapshot.tabKey))
        snapshot.posts.forEach { post ->
            insertOrThrow("compat_post", null, ContentValues().apply {
                put("tab_key", snapshot.tabKey)
                put("revision", snapshot.revision)
                put("position", post.position)
                put("post_json", json.encodeToString(CompatPostSnapshot.serializer(), post))
            })
        }
        if (existingTab != null) {
            check(update(
                "compat_tab",
                ContentValues().apply {
                    put("reply_count", snapshot.posts.size)
                    put("content_updated_at", snapshot.fetchedAtEpochMillis)
                    put("snapshot_revision", snapshot.revision)
                },
                "tab_key=?",
                arrayOf(snapshot.tabKey)
            ) == 1) { "Compatibility tab disappeared while saving its snapshot" }
        }
        putThreadSnapshotAccess(snapshot.tabKey, currentTimeMillis())
        return true
    }

    private fun SQLiteDatabase.hasHistoryTombstone(canonicalUrl: String): Boolean = query(
        "compat_history_tombstone",
        arrayOf("canonical_url"),
        "canonical_url=?",
        arrayOf(canonicalUrl),
        null,
        null,
        null,
        "1"
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.historyTombstoneAt(canonicalUrl: String): Long? = query(
        "compat_history_tombstone",
        arrayOf("deleted_at"),
        "canonical_url=?",
        arrayOf(canonicalUrl),
        null,
        null,
        null,
        "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun SQLiteDatabase.putHistoryTombstone(canonicalUrl: String) {
        if (canonicalUrl.isBlank()) return
        insertWithOnConflict(
            "compat_history_tombstone",
            null,
            ContentValues().apply {
                put("canonical_url", canonicalUrl)
                put("deleted_at", currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun SQLiteDatabase.deleteHistoryTombstone(canonicalUrl: String) {
        delete("compat_history_tombstone", "canonical_url=?", arrayOf(canonicalUrl))
    }

    private fun SQLiteDatabase.readThreadSnapshot(
        tabKey: String,
        touch: Boolean
    ): CompatThreadSnapshot? = query(
        "compat_thread_snapshot",
        arrayOf("revision", "fetched_at", "board_title", "expires_label", "deleted_notice"),
        "tab_key=?",
        arrayOf(tabKey),
        null,
        null,
        null,
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            removeThreadSnapshotAccess(tabKey)
            return@use null
        }
        val revision = cursor.getLong(0)
        val corruptPositions = mutableListOf<Int>()
        val posts = query(
            "compat_post",
            arrayOf("position", "post_json"),
            "tab_key=? AND revision=? AND length(post_json)<=?",
            arrayOf(tabKey, revision.toString(), MAX_COMPAT_POST_JSON_CHARS.toString()),
            null,
            null,
            "position ASC",
            MAX_COMPAT_THREAD_SNAPSHOT_POSTS.toString()
        ).use { postCursor ->
            buildList {
                while (postCursor.moveToNext()) {
                    val position = postCursor.getInt(0)
                    runCatching {
                        json.decodeFromString(CompatPostSnapshot.serializer(), postCursor.getString(1))
                    }.onSuccess { decoded ->
                        add(if (decoded.position == position) decoded else decoded.copy(position = position))
                    }.onFailure {
                        corruptPositions += position
                        Logger.w(
                            "AndroidCompatibilityStore",
                            "Skipping one corrupt cached post for a compatibility thread"
                        )
                    }
                }
            }
        }
        corruptPositions.forEach { position ->
            delete(
                "compat_post",
                "tab_key=? AND revision=? AND position=?",
                arrayOf(tabKey, revision.toString(), position.toString())
            )
        }
        if (touch) putThreadSnapshotAccess(tabKey, currentTimeMillis())
        CompatThreadSnapshot(
            tabKey = tabKey,
            revision = revision,
            fetchedAtEpochMillis = cursor.getLong(1),
            boardTitle = cursor.getNullableString(2),
            expiresAtLabel = cursor.getNullableString(3),
            deletedNotice = cursor.getNullableString(4),
            posts = posts
        )
    }

    private fun SQLiteDatabase.enforceThreadSnapshotQuota() {
        val quota = threadSnapshotQuotaOverrideBytes?.invoke()
            ?: if (threadSnapshotQuotaOverrideBytes == null) {
                parseCompatThreadCacheQuotaBytes(readPreferenceValue(COMPAT_THREAD_CACHE_PREFERENCE_KEY))
            } else {
                null
            }
        if (quota == null) return
        val rows = readThreadSnapshotCacheRows()
        var totalBytes = rows.sumOf(ThreadSnapshotCacheRow::byteCount)
        if (totalBytes <= quota) return
        val activeTabKey = readWorkspace().activeTabKey
        val protected = readTabs().filter { it.favorite }.mapTo(mutableSetOf()) { it.key }
        activeTabKey?.let(protected::add)
        rows.asSequence()
            .filterNot { it.tabKey in protected }
            .sortedWith(compareBy<ThreadSnapshotCacheRow> { it.lastAccessedAt }.thenBy { it.tabKey })
            .forEach { row ->
                if (totalBytes <= quota) return@forEach
                deleteThreadSnapshotBody(row.tabKey)
                totalBytes -= row.byteCount
            }
    }

    private fun SQLiteDatabase.readThreadSnapshotCacheRows(): List<ThreadSnapshotCacheRow> = rawQuery(
            """SELECT s.tab_key,s.fetched_at,
                COALESCE(SUM(length(CAST(p.post_json AS BLOB))),0)
                + length(CAST(s.tab_key AS BLOB))
                + COALESCE(length(CAST(s.board_title AS BLOB)),0)
                + COALESCE(length(CAST(s.expires_label AS BLOB)),0)
                + COALESCE(length(CAST(s.deleted_notice AS BLOB)),0)
                + 32
                FROM compat_thread_snapshot s
                LEFT JOIN compat_post p ON p.tab_key=s.tab_key AND p.revision=s.revision
                GROUP BY s.tab_key,s.fetched_at""".trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val tabKey = cursor.getString(0)
                    add(
                        ThreadSnapshotCacheRow(
                            tabKey = tabKey,
                            byteCount = cursor.getLong(2),
                            lastAccessedAt = threadSnapshotAccess(tabKey) ?: cursor.getLong(1)
                        )
                    )
                }
            }
        }

    private fun SQLiteDatabase.repairThreadSnapshotCache() {
        execSQL(
            """DELETE FROM compat_post
                WHERE NOT EXISTS (
                    SELECT 1 FROM compat_thread_snapshot s
                    WHERE s.tab_key=compat_post.tab_key AND s.revision=compat_post.revision
                )""".trimIndent()
        )
        val live = query(
            "compat_thread_snapshot",
            arrayOf("tab_key"),
            null,
            null,
            null,
            null,
            null
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val orphanAccessKeys = query(
            "compat_metadata",
            arrayOf("key"),
            "key LIKE ?",
            arrayOf("$THREAD_SNAPSHOT_ACCESS_PREFIX%"),
            null,
            null,
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val tabKey = key.removePrefix(THREAD_SNAPSHOT_ACCESS_PREFIX)
                    if (tabKey !in live) add(key)
                }
            }
        }
        orphanAccessKeys.forEach { key -> delete("compat_metadata", "key=?", arrayOf(key)) }
    }

    private fun SQLiteDatabase.deleteThreadSnapshotBody(tabKey: String) {
        delete("compat_post", "tab_key=?", arrayOf(tabKey))
        delete("compat_thread_snapshot", "tab_key=?", arrayOf(tabKey))
        update(
            "compat_tab",
            ContentValues().apply { put("snapshot_revision", 0L) },
            "tab_key=?",
            arrayOf(tabKey)
        )
        removeThreadSnapshotAccess(tabKey)
    }

    private fun SQLiteDatabase.deleteTabAndRetainSnapshot(tabKey: String) {
        removeThreadSnapshotAccess(tabKey)
        delete("compat_tab", "tab_key=?", arrayOf(tabKey))
    }

    private fun SQLiteDatabase.putThreadSnapshotAccess(tabKey: String, timestamp: Long) {
        putMetadata(THREAD_SNAPSHOT_ACCESS_PREFIX + tabKey, timestamp.toString())
    }

    private fun SQLiteDatabase.threadSnapshotAccess(tabKey: String): Long? =
        metadataValue(THREAD_SNAPSHOT_ACCESS_PREFIX + tabKey)?.toLongOrNull()

    private fun SQLiteDatabase.removeThreadSnapshotAccess(tabKey: String) {
        delete("compat_metadata", "key=?", arrayOf(THREAD_SNAPSHOT_ACCESS_PREFIX + tabKey))
    }

    private fun SQLiteDatabase.readPreferenceValue(key: String): String? = query(
        "compat_preference",
        arrayOf("value_json"),
        "key=? AND length(value_json)<=?",
        arrayOf(key, MAX_COMPAT_PREFERENCE_VALUE_CHARS.toString()),
        null,
        null,
        null,
        "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun scheduleClosedBatchExpiry(batch: ClosedTabBatch?) {
        closedBatchExpiryJob?.cancel()
        closedBatchExpiryJob = null
        if (batch == null) return
        closedBatchExpiryJob = lifecycleScope.launch {
            while (true) {
                val nowMillis = System.currentTimeMillis()
                val remaining = when {
                    batch.expiresAtEpochMillis <= nowMillis -> 0L
                    batch.expiresAtEpochMillis - nowMillis < 0L -> Long.MAX_VALUE
                    else -> batch.expiresAtEpochMillis - nowMillis
                }
                if (remaining <= 0L) break
                delay(remaining.coerceIn(1L, CLOSED_BATCH_DEADLINE_RECHECK_MILLIS))
            }
            // The store owns the durable deadline. This still runs when the Compose tree
            // that displayed Undo has been replaced; process death is covered by initialize().
            try {
                loadPendingClosedTabs(System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // Expiry is best-effort maintenance. A transient close/rotation
                // race must not become an uncaught exception on the store scope.
                Logger.e(
                    "AndroidCompatibilityStore",
                    "Failed to expire closed compatibility tabs",
                    failure
                )
            }
        }
    }

    private fun SQLiteDatabase.readClosedBatches(
        predicate: (ClosedTabBatch) -> Boolean = { true }
    ): List<ClosedTabBatch> = query(
        "compat_closed_batch",
        arrayOf("batch_json"),
        "length(batch_json)<=?",
        arrayOf(MAX_COMPAT_CLOSED_BATCH_JSON_CHARS.toString()),
        null,
        null,
        "expires_at DESC",
        MAX_COMPAT_CLOSED_BATCH_ROWS.toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                runCatching { json.decodeFromString(closedBatchSerializer, cursor.getString(0)) }
                    .getOrNull()
                    ?.takeIf(predicate)
                    ?.let(::add)
            }
        }
    }

    private fun SQLiteDatabase.insertClosedBatch(batch: ClosedTabBatch) {
        insertOrThrow(
            "compat_closed_batch",
            null,
            ContentValues().apply {
                put("batch_id", batch.id)
                put("expires_at", batch.expiresAtEpochMillis)
                put("batch_json", json.encodeToString(closedBatchSerializer, batch))
            }
        )
    }

    private fun SQLiteDatabase.updateClosedBatch(batch: ClosedTabBatch) {
        update(
            "compat_closed_batch",
            ContentValues().apply {
                put("expires_at", batch.expiresAtEpochMillis)
                put("batch_json", json.encodeToString(closedBatchSerializer, batch))
            },
            "batch_id=?",
            arrayOf(batch.id)
        )
    }

    private fun SQLiteDatabase.readDraftAttachmentLocatorsForBoard(boardKey: String): Set<String> = buildSet {
        rawQuery(
            "SELECT d.attachment_uri FROM compat_reply_draft d JOIN compat_tab t ON t.tab_key=d.tab_key WHERE t.board_key=? AND d.attachment_uri IS NOT NULL",
            arrayOf(boardKey)
        ).use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
        }
        query(
            "compat_build_draft",
            arrayOf("attachment_uri"),
            "board_key=? AND attachment_uri IS NOT NULL",
            arrayOf(boardKey),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
        }
    }

    private fun SQLiteDatabase.readRetainedAttachmentLocators(): Set<String> {
        val active = query(
            "compat_reply_draft",
            arrayOf("attachment_uri"),
            "attachment_uri IS NOT NULL",
            null,
            null,
            null,
            null
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        }
        val build = query(
            "compat_build_draft",
            arrayOf("attachment_uri"),
            "attachment_uri IS NOT NULL",
            null,
            null,
            null,
            null
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        }
        return active + build + readClosedBatches().attachmentLocators()
    }

    private fun validatedModernBoards(modernBoards: List<BoardSummary>): List<CompatBoard> {
        val seen = mutableSetOf<String>()
        return modernBoards.mapIndexedNotNull { index, board ->
            val canonical = canonicalizeBoardUrl(board.url)
                ?: return@mapIndexedNotNull null
            if (!seen.add(canonical)) return@mapIndexedNotNull null
            CompatBoard(
                key = compatBoardKey(canonical),
                name = board.name.ifBlank { canonical.substringAfter("//").substringBefore('/') },
                canonicalUrl = canonical,
                originalUrl = board.url,
                sortOrder = index
            )
        }
    }

    private fun SQLiteDatabase.insertBoardIfMissing(board: CompatBoard): Boolean {
        val values = board.toValues()
        return insertWithOnConflict("compat_board", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    private fun SQLiteDatabase.replaceToolbar(
        surface: CompatToolbarSurface,
        items: List<CompatToolbarItem>
    ) {
        delete("compat_toolbar", "surface=?", arrayOf(surface.name))
        items.forEach { item ->
            insertOrThrow(
                "compat_toolbar",
                null,
                ContentValues().apply {
                    put("surface", surface.name)
                    put("command_key", item.key)
                    put("position", item.position)
                    put("active", item.active.asInt())
                }
            )
        }
    }

    private fun SQLiteDatabase.repairCanonicalBoards() {
        val repairs = readBoards().mapNotNull { board ->
            val canonical = canonicalizeBoardUrl(board.originalUrl) ?: return@mapNotNull null
            if (canonical == board.canonicalUrl) null else board to canonical
        }
        if (repairs.isEmpty()) return
        execSQL("PRAGMA defer_foreign_keys=ON")
        repairs.forEach { (board, canonical) ->
            val repairedKey = compatBoardKey(canonical)
            insertWithOnConflict(
                "compat_board",
                null,
                board.copy(key = repairedKey, canonicalUrl = canonical).toValues(),
                SQLiteDatabase.CONFLICT_IGNORE
            )
            listOf("compat_tab", "compat_history", "compat_catalog_preference", "compat_catalog_item", "compat_catalog_snapshot", "compat_watch_rule")
                .forEach { table ->
                    update(table, ContentValues().apply { put("board_key", repairedKey) }, "board_key=?", arrayOf(board.key))
                }
            update(
                "compat_workspace",
                ContentValues().apply { put("catalog_host_board_key", repairedKey) },
                "catalog_host_board_key=?",
                arrayOf(board.key)
            )
            update(
                "compat_ng_rule",
                ContentValues().apply { put("scope_key", repairedKey) },
                "scope_key=?",
                arrayOf(board.key)
            )
            delete("compat_board", "board_key=?", arrayOf(board.key))
        }
    }

    private fun SQLiteDatabase.upsertBoard(board: CompatBoard) {
        val values = board.toValues()
        if (update("compat_board", values, "board_key=?", arrayOf(board.key)) == 0) {
            insertOrThrow("compat_board", null, values)
        }
    }

    private fun CompatBoard.toValues() = ContentValues().apply {
        put("board_key", key)
        put("name", name)
        put("canonical_url", canonicalUrl)
        put("original_url", originalUrl)
        put("sort_order", sortOrder)
    }

    private fun SQLiteDatabase.readBoards(): List<CompatBoard> = query(
        "compat_board",
        arrayOf("board_key", "name", "canonical_url", "original_url", "sort_order"),
        null,
        null,
        null,
        null,
        "sort_order ASC, board_key ASC",
        MAX_COMPAT_BOARDS.toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(CompatBoard(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4)))
            }
        }
    }

    private fun SQLiteDatabase.readPreferences(): Map<String, String> = query(
        "compat_preference",
        arrayOf("key", "value_json"),
        "key GLOB 'compat.*' AND length(key) BETWEEN 1 AND ? AND length(value_json) <= ?",
        arrayOf(MAX_COMPAT_PREFERENCE_KEY_CHARS.toString(), MAX_COMPAT_PREFERENCE_VALUE_CHARS.toString()),
        null,
        null,
        "key ASC",
        MAX_COMPAT_PREFERENCES.toString()
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
        }
    }

    private fun SQLiteDatabase.readNgRules(): List<CompatNgRule> = query(
        "compat_ng_rule",
        arrayOf("rule_id", "kind", "scope_key", "normalized_value", "created_at", "payload_json"),
        "length(rule_id) BETWEEN 1 AND ? AND length(scope_key) BETWEEN 1 AND ? AND " +
            "length(normalized_value) BETWEEN 1 AND ? AND length(payload_json) <= ?",
        arrayOf(
            MAX_COMPAT_NG_RULE_ID_CHARS.toString(),
            MAX_COMPAT_NG_SCOPE_KEY_CHARS.toString(),
            MAX_COMPAT_NG_VALUE_CHARS.toString(),
            (MAX_COMPAT_NG_IMAGE_URL_CHARS + MAX_COMPAT_NG_MEMO_CHARS + 512).toString()
        ),
        null,
        null,
        "created_at DESC",
        MAX_COMPAT_NG_RULES.toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val kind = runCatching { CompatNgKind.valueOf(cursor.getString(1)) }.getOrNull() ?: continue
                val scope = cursor.getString(2) ?: continue
                val payload = runCatching { cursor.getString(5)?.let(::JSONObject) }.getOrNull()
                val imageUrl = payload?.optString("imageUrl")?.takeIf(String::isNotBlank)
                val memo = payload?.optString("memo").orEmpty().take(MAX_COMPAT_NG_MEMO_CHARS)
                add(CompatNgRule(cursor.getString(0), kind, scope, cursor.getString(3), cursor.getLong(4), imageUrl, memo))
            }
        }
    }

    private fun SQLiteDatabase.upsertTab(tab: CompatTab) {
        val values = ContentValues().apply {
            put("tab_key", tab.key)
            put("canonical_url", tab.canonicalUrl)
            put("original_url", tab.originalUrl)
            put("board_key", tab.boardKey)
            put("board_name", tab.boardName)
            put("thread_no", tab.threadNo)
            put("title", tab.title)
            put("thumbnail_url", tab.thumbnailUrl)
            put("reply_count", tab.replyCount)
            put("checked_reply_count", tab.checkedReplyCount)
            put("is_dead", tab.isDead.asInt())
            put("is_isolated", tab.isIsolated.asInt())
            put("is_exploded", tab.isExploded.asInt())
            put("is_old", tab.isOld.asInt())
            put("favorite", tab.favorite.asInt())
            put("inserted_at", tab.insertedAtEpochMillis)
            put("content_updated_at", tab.contentUpdatedAtEpochMillis)
            put("scroll_anchor_json", json.encodeToString(anchorSerializer, tab.scrollAnchor))
            put("snapshot_revision", tab.snapshotRevision)
        }
        if (update("compat_tab", values, "tab_key=?", arrayOf(tab.key)) == 0) {
            insertOrThrow("compat_tab", null, values)
        }
    }

    private fun SQLiteDatabase.readTabs(): List<CompatTab> = query(
        "compat_tab",
        TAB_COLUMNS,
        null,
        null,
        null,
        null,
        "inserted_at DESC, tab_key ASC",
        MAX_COMPAT_TAB_READ_ROWS.toString()
    ).use { cursor ->
        // compat_tab.tab_key is a primary key in current schemas, but keep
        // the Flow contract defensive for databases created by an older
        // build or restored from a malformed backup. Compose selectors use
        // this key directly and must never receive duplicates.
        buildList { while (cursor.moveToNext()) add(cursor.toTab()) }
            .distinctBy(CompatTab::key)
    }

    private fun SQLiteDatabase.readTab(tabKey: String): CompatTab? = query(
        "compat_tab", TAB_COLUMNS, "tab_key=?", arrayOf(tabKey), null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toTab() else null }

    private fun Cursor.toTab(): CompatTab = CompatTab(
        key = getString(0),
        canonicalUrl = getString(1),
        originalUrl = getString(2),
        boardKey = getString(3),
        boardName = getString(4),
        threadNo = getString(5),
        title = getString(6),
        thumbnailUrl = getNullableString(7),
        replyCount = getInt(8),
        checkedReplyCount = getInt(9),
        isDead = getInt(10) != 0,
        isIsolated = getInt(11) != 0,
        isExploded = getInt(12) != 0,
        isOld = getInt(13) != 0,
        favorite = getInt(14) != 0,
        insertedAtEpochMillis = getLong(15),
        contentUpdatedAtEpochMillis = getLong(16),
        scrollAnchor = runCatching { json.decodeFromString(anchorSerializer, getString(17)) }.getOrDefault(ScrollAnchor()),
        snapshotRevision = getLong(18)
    )

    private fun SQLiteDatabase.trimTabs(): Set<String> {
        val tabs = readTabs()
        // ThreadTabCleanService in both APKs runs only after the 100th tab is
        // exceeded, then drops the oldest range back to 90.
        if (tabs.size <= TAB_LIMIT_TRIGGER) return emptySet()
        val active = readWorkspace().activeTabKey
        val removable = tabs.asReversed().filter { !it.favorite && it.key != active }
        val trimmed = removable.take((tabs.size - TAB_LIMIT_AFTER_TRIM).coerceAtLeast(0))
        val attachmentLocators = trimmed.mapNotNullTo(mutableSetOf()) { readDraft(it.key)?.attachmentUri }
        trimmed.forEach {
            deleteTabAndRetainSnapshot(it.key)
        }
        return attachmentLocators
    }

    private fun SQLiteDatabase.upsertHistory(entry: CompatHistoryEntry) {
        val values = ContentValues().apply {
            put("canonical_url", entry.canonicalUrl)
            put("original_url", entry.originalUrl)
            put("board_key", entry.boardKey)
            put("board_name", entry.boardName)
            put("thread_no", entry.threadNo)
            put("title", entry.title)
            put("thumbnail_url", entry.thumbnailUrl)
            put("reply_count", entry.replyCount)
            put("content_updated_at", entry.contentUpdatedAtEpochMillis)
            put("scroll_anchor_json", json.encodeToString(anchorSerializer, entry.scrollAnchor))
        }
        if (update("compat_history", values, "canonical_url=?", arrayOf(entry.canonicalUrl)) == 0) {
            insertOrThrow("compat_history", null, values)
        }
    }

    private fun SQLiteDatabase.readHistory(): List<CompatHistoryEntry> = query(
        "compat_history",
        arrayOf("canonical_url", "original_url", "board_key", "board_name", "thread_no", "title", "thumbnail_url", "reply_count", "content_updated_at", "scroll_anchor_json"),
        null,
        null,
        null,
        null,
        "content_updated_at DESC, canonical_url ASC",
        (HISTORY_LIMIT_TRIGGER + 1).toString()
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CompatHistoryEntry(
                        canonicalUrl = cursor.getString(0),
                        originalUrl = cursor.getString(1),
                        boardKey = cursor.getString(2),
                        boardName = cursor.getString(3),
                        threadNo = cursor.getString(4),
                        title = cursor.getString(5),
                        thumbnailUrl = cursor.getNullableString(6),
                        replyCount = cursor.getInt(7),
                        contentUpdatedAtEpochMillis = cursor.getLong(8),
                        scrollAnchor = runCatching { json.decodeFromString(anchorSerializer, cursor.getString(9)) }.getOrDefault(ScrollAnchor())
                    )
                )
            }
        }
    }

    private fun SQLiteDatabase.trimHistory() {
        val count = longForQuery("SELECT COUNT(*) FROM compat_history").coerceAtLeast(0L)
        if (count <= HISTORY_LIMIT_TRIGGER.toLong()) return
        execSQL(
            "DELETE FROM compat_history WHERE canonical_url IN (SELECT canonical_url FROM compat_history ORDER BY content_updated_at ASC, canonical_url ASC LIMIT ?)",
            arrayOf((count - HISTORY_LIMIT_AFTER_TRIM.toLong()).coerceAtLeast(0L))
        )
    }

    private fun SQLiteDatabase.upsertDraft(draft: CompatReplyDraft) {
        val values = ContentValues().apply {
            put("tab_key", draft.tabKey)
            put("name", draft.name)
            put("email", draft.email)
            put("subject", draft.subject)
            put("comment", draft.comment)
            put("attachment_uri", draft.attachmentUri)
            put("delete_key", draft.deleteKey)
            put("updated_at", draft.updatedAtEpochMillis)
        }
        if (update("compat_reply_draft", values, "tab_key=?", arrayOf(draft.tabKey)) == 0) {
            insertOrThrow("compat_reply_draft", null, values)
        }
    }

    private fun SQLiteDatabase.readDraft(tabKey: String): CompatReplyDraft? = query(
        "compat_reply_draft",
        arrayOf("tab_key", "name", "email", "subject", "comment", "attachment_uri", "delete_key", "updated_at"),
        "tab_key=?",
        arrayOf(tabKey),
        null,
        null,
        null,
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else CompatReplyDraft(
            tabKey = cursor.getString(0),
            name = cursor.getString(1),
            email = cursor.getString(2),
            subject = cursor.getString(3),
            comment = cursor.getString(4),
            attachmentUri = cursor.getNullableString(5),
            deleteKey = cursor.getString(6),
            updatedAtEpochMillis = cursor.getLong(7)
        )
    }

    private fun SQLiteDatabase.upsertBuildDraft(draft: CompatBuildDraft) {
        val values = ContentValues().apply {
            put("board_key", draft.boardKey)
            put("name", draft.name)
            put("email", draft.email)
            put("subject", draft.subject)
            put("comment", draft.comment)
            put("attachment_uri", draft.attachmentUri)
            put("delete_key", draft.deleteKey)
            put("updated_at", draft.updatedAtEpochMillis)
        }
        if (update("compat_build_draft", values, "board_key=?", arrayOf(draft.boardKey)) == 0) {
            insertOrThrow("compat_build_draft", null, values)
        }
    }

    private fun SQLiteDatabase.readBuildDraft(boardKey: String): CompatBuildDraft? = query(
        "compat_build_draft",
        arrayOf("board_key", "name", "email", "subject", "comment", "attachment_uri", "delete_key", "updated_at"),
        "board_key=?",
        arrayOf(boardKey),
        null,
        null,
        null,
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else CompatBuildDraft(
            boardKey = cursor.getString(0),
            name = cursor.getString(1),
            email = cursor.getString(2),
            subject = cursor.getString(3),
            comment = cursor.getString(4),
            attachmentUri = cursor.getNullableString(5),
            deleteKey = cursor.getString(6),
            updatedAtEpochMillis = cursor.getLong(7)
        )
    }

    private fun SQLiteDatabase.readWorkspace(): CompatWorkspaceRecord = query(
        "compat_workspace",
        arrayOf("active_tab_key", "catalog_host_board_key", "main_selector_open", "catalog_selector_open", "thread_selector_open", "selector_presentation", "generation"),
        "singleton_id=1",
        null,
        null,
        null,
        null,
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) CompatWorkspaceRecord() else CompatWorkspaceRecord(
            activeTabKey = cursor.getNullableString(0),
            catalogHostBoardKey = cursor.getNullableString(1),
            mainSelectorOpen = cursor.getInt(2) != 0,
            catalogSelectorOpen = cursor.getInt(3) != 0,
            threadSelectorOpen = cursor.getInt(4) != 0,
            selectorPresentation = runCatching { SelectorPresentation.valueOf(cursor.getString(5)) }.getOrDefault(SelectorPresentation.ABOVE),
            generation = cursor.getLong(6)
        )
    }

    private fun SQLiteDatabase.updateWorkspace(record: CompatWorkspaceRecord) {
        val values = ContentValues().apply {
            put("singleton_id", 1)
            put("active_tab_key", record.activeTabKey)
            put("catalog_host_board_key", record.catalogHostBoardKey)
            put("main_selector_open", record.mainSelectorOpen.asInt())
            put("catalog_selector_open", record.catalogSelectorOpen.asInt())
            put("thread_selector_open", record.threadSelectorOpen.asInt())
            put("selector_presentation", record.selectorPresentation.name)
            put("generation", record.generation)
        }
        if (update("compat_workspace", values, "singleton_id=1", null) == 0) {
            insertOrThrow("compat_workspace", null, values)
        }
    }

    private fun SQLiteDatabase.metadataValue(key: String): String? = query(
        "compat_metadata", arrayOf("value"), "key=?", arrayOf(key), null, null, null, "1"
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun SQLiteDatabase.putMetadata(key: String, value: String) {
        val values = ContentValues().apply { put("key", key); put("value", value) }
        if (update("compat_metadata", values, "key=?", arrayOf(key)) == 0) insertOrThrow("compat_metadata", null, values)
    }

    private fun SQLiteDatabase.longForQuery(sql: String): Long = rawQuery(sql, null).use {
        if (it.moveToFirst()) it.getLong(0) else 0L
    }

    private fun Cursor.getNullableString(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private companion object {
        const val METADATA_BOARD_BOOTSTRAP_VERSION = "board_bootstrap_version"
        const val BOARD_BOOTSTRAP_VERSION = 1
        const val TAB_LIMIT_TRIGGER = 100
        const val TAB_LIMIT_AFTER_TRIM = 90
        const val HISTORY_LIMIT_TRIGGER = 200
        const val HISTORY_LIMIT_AFTER_TRIM = 190
        const val MAX_COMPAT_BOARDS = 100
        const val MAX_COMPAT_PREFERENCES = 4_096
        const val MAX_COMPAT_TAB_READ_ROWS = 1_000
        const val MAX_COMPAT_CATALOG_SNAPSHOT_ITEMS = 3_000
        const val MAX_COMPAT_CATALOG_SNAPSHOT_GENERATIONS = 6
        const val MAX_COMPAT_CATALOG_DROPPED_ITEMS = 200
        const val MAX_COMPAT_CATALOG_ITEM_JSON_CHARS = 64 * 1024
        const val MAX_COMPAT_POST_JSON_CHARS = 2 * 1024 * 1024
        const val MAX_COMPAT_CLOSED_BATCH_JSON_CHARS = 4 * 1024 * 1024
        const val MAX_COMPAT_CLOSED_BATCH_ROWS = 8
        const val UNDO_WINDOW_MILLIS = 7_000L
        const val CLOSED_BATCH_DEADLINE_RECHECK_MILLIS = 1_000L
        const val THREAD_SNAPSHOT_ACCESS_PREFIX = "thread_snapshot_access:"
        val TAB_COLUMNS = arrayOf(
            "tab_key", "canonical_url", "original_url", "board_key", "board_name", "thread_no", "title", "thumbnail_url",
            "reply_count", "checked_reply_count", "is_dead", "is_isolated", "is_exploded", "is_old", "favorite", "inserted_at",
            "content_updated_at", "scroll_anchor_json", "snapshot_revision"
        )
    }
}

private data class AttachmentCleanupMutation<T>(
    val value: T,
    val candidates: Set<String> = emptySet(),
    val retained: Set<String> = emptySet()
)

private data class ThreadSnapshotCacheRow(
    val tabKey: String,
    val byteCount: Long,
    val lastAccessedAt: Long
)

private data class ArchiveReportDbRow(
    val threadId: String,
    val threadUrl: String,
    val nextAttemptAt: Long,
    val attemptCount: Int,
    val batchRequestId: String?,
    val batchPayloadHash: String?
) {
    fun normalized() = com.valoser.futacha.shared.compat.NormalizedArchiveThread(threadId, threadUrl)
}

private fun SQLiteDatabase.archiveReportRowCount(): Int = query(
    "archive_report_outbox", arrayOf("COUNT(*)"), null, null, null, null, null
).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

private fun SQLiteDatabase.archiveReportSendableCount(@Suppress("UNUSED_PARAMETER") nowEpochMillis: Long): Int = query(
    "archive_report_outbox",
    arrayOf("COUNT(*)"),
    "state IN ('pending','retry')",
    null,
    null,
    null,
    null
).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

private fun ArchiveReportPayload.isValidArchiveReportPayload(): Boolean =
    bytes.size <= com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_BODY_BYTES &&
        threadIds.size == urls.size &&
        buildArchiveReportPayload(
            requestId,
            threadIds.zip(urls).map { (threadId, url) ->
                com.valoser.futacha.shared.compat.NormalizedArchiveThread(threadId, url)
            }
        )?.let { rebuilt ->
            rebuilt.threadIds == threadIds && rebuilt.urls == urls && rebuilt.sha256 == sha256
        } == true

private fun SQLiteDatabase.readArchiveReportRows(
    selection: String,
    selectionArgs: Array<String>,
    limit: String? = null
): List<ArchiveReportDbRow> = query(
    "archive_report_outbox",
    arrayOf(
        "thread_id", "thread_url", "next_attempt_at", "CASE WHEN attempt_count<0 THEN 0 " +
            "WHEN attempt_count>2147483647 THEN 2147483647 ELSE attempt_count END",
        "batch_request_id", "batch_payload_hash"
    ),
    selection,
    selectionArgs,
    null,
    null,
    "thread_id ASC",
    limit
).use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            add(
                ArchiveReportDbRow(
                    threadId = cursor.getString(0),
                    threadUrl = cursor.getString(1),
                    nextAttemptAt = cursor.getLong(2),
                    attemptCount = cursor.getInt(3),
                    batchRequestId = if (cursor.isNull(4)) null else cursor.getString(4),
                    batchPayloadHash = if (cursor.isNull(5)) null else cursor.getString(5)
                )
            )
        }
    }
}

private fun SQLiteDatabase.updateArchiveReportRows(
    threadIds: List<String>,
    values: ContentValues,
    additionalWhere: String,
    additionalArgs: Array<String> = emptyArray()
): Int {
    if (threadIds.isEmpty()) return 0
    val placeholders = threadIds.joinToString(",") { "?" }
    return update(
        "archive_report_outbox",
        values,
        "thread_id IN ($placeholders) AND ($additionalWhere)",
        (threadIds + additionalArgs).toTypedArray()
    )
}

/** Makes exactly one recoverable slot without ever deleting a sending row. */
private fun SQLiteDatabase.makeArchiveReportCapacity(): Boolean {
    val oldestPending = query(
        "archive_report_outbox", arrayOf("thread_id"),
        "state='pending' AND batch_request_id IS NULL", null, null, null,
        "first_seen_at ASC", "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    if (oldestPending != null) {
        return delete("archive_report_outbox", "thread_id=?", arrayOf(oldestPending)) > 0
    }
    val retry = query(
        "archive_report_outbox", arrayOf("thread_id", "batch_request_id"),
        "state='retry'", null, null, null, "first_seen_at ASC", "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getString(1)
    }
    if (retry != null) {
        return if (retry.second == null) {
            delete("archive_report_outbox", "thread_id=?", arrayOf(retry.first)) > 0
        } else {
            delete("archive_report_outbox", "state='retry' AND batch_request_id=?", arrayOf(retry.second)) > 0
        }
    }
    val finalized = query(
        "archive_report_outbox", arrayOf("thread_id"),
        "state IN ('accepted','abandoned')", null, null, null, "first_seen_at ASC", "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    return finalized != null && delete("archive_report_outbox", "thread_id=?", arrayOf(finalized)) > 0
}

/**
 * Bounded background maintenance required by client.txt §6. It never touches sending
 * rows and never runs in the foreground enqueue transaction. Finalized rows are only
 * eligible after their retention expiry; pending/retry rows are removed oldest-first,
 * keeping retry batches intact.
 */
private fun SQLiteDatabase.maintainArchiveReportOutbox(nowEpochMillis: Long): Int {
    delete(
        "archive_report_outbox",
        "state IN ('accepted','abandoned') AND expires_at IS NOT NULL AND expires_at<=?",
        arrayOf(nowEpochMillis.toString())
    )
    val count = archiveReportRowCount()
    // The first call starts at 4,500; subsequent bounded worker calls continue an
    // already-started drain while the count remains above the 4,000 target.
    if (count < ARCHIVE_REPORT_MAINTENANCE_START_ROWS && count <= ARCHIVE_REPORT_MAINTENANCE_TARGET_ROWS) {
        return 0
    }
    val target = (count - ARCHIVE_REPORT_MAINTENANCE_TARGET_ROWS)
        .coerceAtMost(ARCHIVE_REPORT_MAINTENANCE_BATCH_ROWS)
    if (target <= 0) return 0

    var removed = 0
    while (removed < target) {
        val oldestPending = query(
            "archive_report_outbox", arrayOf("thread_id"),
            "state='pending' AND batch_request_id IS NULL", null, null, null,
            "first_seen_at ASC, thread_id ASC", "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (oldestPending != null) {
            removed += delete("archive_report_outbox", "thread_id=?", arrayOf(oldestPending))
            continue
        }

        val retryBatch = query(
            "archive_report_outbox", arrayOf("batch_request_id"),
            "state='retry'", null, null, null,
            "first_seen_at ASC, thread_id ASC", "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else if (cursor.isNull(0)) "" else cursor.getString(0)
        }
        if (retryBatch != null) {
            val where = if (retryBatch.isEmpty()) {
                "state='retry' AND batch_request_id IS NULL"
            } else {
                "state='retry' AND batch_request_id=?"
            }
            val args = if (retryBatch.isEmpty()) null else arrayOf(retryBatch)
            val batchCount = query(
                "archive_report_outbox", arrayOf("COUNT(*)"), where, args,
                null, null, null
            ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            // A retry batch is kept atomic. Normal batches are <=20, so this remains
            // within the 100-row maintenance bound from the specification.
            if (removed + batchCount > target && removed > 0) break
            removed += delete("archive_report_outbox", where, args)
            continue
        }

        val finalized = query(
            "archive_report_outbox", arrayOf("thread_id"),
            "state IN ('accepted','abandoned')", null, null, null,
            "first_seen_at ASC, thread_id ASC", "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (finalized == null) break
        removed += delete("archive_report_outbox", "thread_id=?", arrayOf(finalized))
    }
    return removed
}

private fun List<ClosedTabBatch>.attachmentLocators(): Set<String> =
    flatMapTo(mutableSetOf()) { batch ->
        batch.tabs.mapNotNull { closed -> closed.draft?.attachmentUri }
    }

private class CompatibilityDatabaseHelper(context: Context, databaseName: String) : SQLiteOpenHelper(
    context,
    databaseName,
    null,
    CompatibilityDatabaseSchema.version
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        // DELETE must also overwrite the freed cell payload. VACUUM is too
        // disruptive for a drawer action; secure_delete removes recoverable
        // title/thumbnail/history bytes while keeping tabs and caches intact.
        db.rawQuery("PRAGMA secure_delete=ON", null).use { cursor ->
            cursor.moveToFirst()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        CompatibilityDatabaseSchema.createStatements.forEach(db::execSQL)
        db.execSQL(CompatibilityDatabaseSchema.initialWorkspaceStatement)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == newVersion) return
        var version = oldVersion
        if (version == 1 && newVersion >= 2) {
            CompatibilityDatabaseSchema.migration1To2.forEach(db::execSQL)
            version = 2
        }
        if (version == 2 && newVersion >= 3) {
            CompatibilityDatabaseSchema.migration2To3.forEach(db::execSQL)
            version = 3
        }
        if (version == 3 && newVersion >= 4) {
            CompatibilityDatabaseSchema.migration3To4.forEach(db::execSQL)
            version = 4
        }
        if (version == 4 && newVersion >= 5) {
            CompatibilityDatabaseSchema.migration4To5.forEach(db::execSQL)
            version = 5
        }
        if (version == 5 && newVersion >= 6) {
            CompatibilityDatabaseSchema.migration5To6.forEach(db::execSQL)
            version = 6
        }
        if (version == 6 && newVersion >= 7) {
            CompatibilityDatabaseSchema.migration6To7.forEach(db::execSQL)
            version = 7
        }
        if (version == 7 && newVersion >= 8) {
            CompatibilityDatabaseSchema.migration7To8.forEach(db::execSQL)
            version = 8
        }
        if (version == 8 && newVersion >= 9) {
            CompatibilityDatabaseSchema.migration8To9.forEach(db::execSQL)
            version = 9
        }
        check(version == newVersion) {
            "Unsupported compatibility DB migration $oldVersion -> $newVersion (stopped at $version)"
        }
    }

}

private fun CompatNgRule.compatPayloadJson(): String = JSONObject().apply {
    imageUrl?.takeIf(String::isNotBlank)?.let { put("imageUrl", it) }
    memo.takeIf(String::isNotBlank)?.let { put("memo", it) }
}.toString()

internal object CompatibilityDatabaseSchema {
    const val version = 9
    const val initialWorkspaceStatement =
        "INSERT INTO compat_workspace(singleton_id, selector_presentation, generation) VALUES(1, 'ABOVE', 0)"
    val migration1To2 = listOf(
        "CREATE TABLE IF NOT EXISTS compat_closed_batch(batch_id TEXT PRIMARY KEY NOT NULL, expires_at INTEGER NOT NULL, batch_json TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS compat_closed_batch_expires_idx ON compat_closed_batch(expires_at DESC)"
    )
    val migration2To3 = listOf(
        "CREATE TABLE IF NOT EXISTS compat_build_draft(board_key TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL, subject TEXT NOT NULL, comment TEXT NOT NULL, attachment_uri TEXT, delete_key TEXT NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)"
    )
    val migration3To4 = listOf(
        "CREATE TABLE IF NOT EXISTS archive_report_outbox(thread_id TEXT PRIMARY KEY NOT NULL, thread_url TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending', first_seen_at INTEGER NOT NULL, next_attempt_at INTEGER NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, batch_request_id TEXT, batch_payload_hash TEXT, sending_started_at INTEGER, accepted_at INTEGER, expires_at INTEGER, last_error TEXT)",
        "CREATE INDEX IF NOT EXISTS idx_archive_report_outbox_due ON archive_report_outbox(state, next_attempt_at)",
        "CREATE INDEX IF NOT EXISTS idx_archive_report_outbox_batch ON archive_report_outbox(batch_request_id, state)",
        "CREATE INDEX IF NOT EXISTS idx_archive_report_outbox_expiry ON archive_report_outbox(state, expires_at)"
    )
    val migration4To5 = listOf(
        "CREATE TABLE IF NOT EXISTS compat_catalog_dropped(board_key TEXT NOT NULL, thread_id TEXT NOT NULL, item_json TEXT NOT NULL, dropped_at INTEGER NOT NULL, PRIMARY KEY(board_key, thread_id), FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE INDEX IF NOT EXISTS compat_catalog_dropped_recent_idx ON compat_catalog_dropped(board_key, dropped_at DESC, thread_id DESC)"
    )
    val migration5To6 = listOf(
        "ALTER TABLE compat_catalog_dropped ADD COLUMN last_seen_at INTEGER NOT NULL DEFAULT 0",
        "CREATE INDEX IF NOT EXISTS compat_catalog_dropped_class_idx ON compat_catalog_dropped(board_key, last_seen_at DESC)",
        "ALTER TABLE compat_catalog_dropped ADD COLUMN drop_class TEXT NOT NULL DEFAULT 'DIE'",
        "ALTER TABLE compat_catalog_dropped ADD COLUMN inserted_at INTEGER NOT NULL DEFAULT 0",
        "UPDATE compat_catalog_dropped SET last_seen_at=dropped_at, inserted_at=dropped_at WHERE last_seen_at=0 AND inserted_at=0"
    )
    val migration6To7 = listOf(
        "CREATE TABLE compat_thread_snapshot_v7(tab_key TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL, fetched_at INTEGER NOT NULL, board_title TEXT, expires_label TEXT, deleted_notice TEXT)",
        "INSERT INTO compat_thread_snapshot_v7(tab_key,revision,fetched_at,board_title,expires_label,deleted_notice) SELECT tab_key,revision,fetched_at,board_title,expires_label,deleted_notice FROM compat_thread_snapshot",
        "DROP TABLE compat_thread_snapshot",
        "ALTER TABLE compat_thread_snapshot_v7 RENAME TO compat_thread_snapshot",
        "CREATE TABLE compat_post_v7(tab_key TEXT NOT NULL, revision INTEGER NOT NULL, position INTEGER NOT NULL, post_json TEXT NOT NULL, PRIMARY KEY(tab_key, revision, position))",
        "INSERT INTO compat_post_v7(tab_key,revision,position,post_json) SELECT tab_key,revision,position,post_json FROM compat_post",
        "DROP TABLE compat_post",
        "ALTER TABLE compat_post_v7 RENAME TO compat_post"
    )
    val migration7To8 = listOf(
        "CREATE TABLE IF NOT EXISTS compat_history_tombstone(canonical_url TEXT PRIMARY KEY NOT NULL, deleted_at INTEGER NOT NULL)"
    )
    val migration8To9 = listOf(
        "ALTER TABLE compat_catalog_preference ADD COLUMN show_non_priority INTEGER NOT NULL DEFAULT 1"
    )
    val createStatements = listOf(
        "CREATE TABLE compat_metadata(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
        "CREATE TABLE compat_board(board_key TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, canonical_url TEXT UNIQUE NOT NULL, original_url TEXT NOT NULL, sort_order INTEGER NOT NULL)",
        "CREATE TABLE compat_workspace(singleton_id INTEGER PRIMARY KEY CHECK(singleton_id=1), active_tab_key TEXT, catalog_host_board_key TEXT, main_selector_open INTEGER NOT NULL DEFAULT 0, catalog_selector_open INTEGER NOT NULL DEFAULT 0, thread_selector_open INTEGER NOT NULL DEFAULT 0, selector_presentation TEXT NOT NULL, generation INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE compat_tab(tab_key TEXT PRIMARY KEY NOT NULL, canonical_url TEXT UNIQUE NOT NULL, original_url TEXT NOT NULL, board_key TEXT NOT NULL, board_name TEXT NOT NULL, thread_no TEXT NOT NULL, title TEXT NOT NULL, thumbnail_url TEXT, reply_count INTEGER NOT NULL, checked_reply_count INTEGER NOT NULL, is_dead INTEGER NOT NULL, is_isolated INTEGER NOT NULL, is_exploded INTEGER NOT NULL, is_old INTEGER NOT NULL, favorite INTEGER NOT NULL, inserted_at INTEGER NOT NULL, content_updated_at INTEGER NOT NULL, scroll_anchor_json TEXT NOT NULL, snapshot_revision INTEGER NOT NULL, FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE INDEX compat_tab_inserted_idx ON compat_tab(inserted_at DESC)",
        "CREATE TABLE compat_reply_draft(tab_key TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL, subject TEXT NOT NULL, comment TEXT NOT NULL, attachment_uri TEXT, delete_key TEXT NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(tab_key) REFERENCES compat_tab(tab_key) ON DELETE CASCADE)",
        "CREATE TABLE compat_build_draft(board_key TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL, subject TEXT NOT NULL, comment TEXT NOT NULL, attachment_uri TEXT, delete_key TEXT NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE TABLE compat_history(canonical_url TEXT PRIMARY KEY NOT NULL, original_url TEXT NOT NULL, board_key TEXT NOT NULL, board_name TEXT NOT NULL, thread_no TEXT NOT NULL, title TEXT NOT NULL, thumbnail_url TEXT, reply_count INTEGER NOT NULL, content_updated_at INTEGER NOT NULL, scroll_anchor_json TEXT NOT NULL, FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE INDEX compat_history_updated_idx ON compat_history(content_updated_at DESC)",
        "CREATE TABLE compat_thread_snapshot(tab_key TEXT PRIMARY KEY NOT NULL, revision INTEGER NOT NULL, fetched_at INTEGER NOT NULL, board_title TEXT, expires_label TEXT, deleted_notice TEXT)",
        "CREATE TABLE compat_post(tab_key TEXT NOT NULL, revision INTEGER NOT NULL, position INTEGER NOT NULL, post_json TEXT NOT NULL, PRIMARY KEY(tab_key, revision, position))",
        "CREATE TABLE compat_catalog_preference(board_key TEXT PRIMARY KEY NOT NULL, sort_mode TEXT NOT NULL, layout_mode TEXT NOT NULL, reply_priority_enabled INTEGER NOT NULL, show_non_priority INTEGER NOT NULL, few_replies_delay INTEGER NOT NULL, FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE TABLE compat_catalog_snapshot(board_key TEXT NOT NULL, mode TEXT NOT NULL, revision INTEGER NOT NULL, fetched_at INTEGER NOT NULL, PRIMARY KEY(board_key, mode, revision), FOREIGN KEY(board_key) REFERENCES compat_board(board_key) ON DELETE CASCADE)",
        "CREATE TABLE compat_catalog_item(board_key TEXT NOT NULL, mode TEXT NOT NULL, revision INTEGER NOT NULL, position INTEGER NOT NULL, item_json TEXT NOT NULL, PRIMARY KEY(board_key, mode, revision, position), FOREIGN KEY(board_key, mode, revision) REFERENCES compat_catalog_snapshot(board_key, mode, revision) ON DELETE CASCADE)",
        "CREATE TABLE compat_preference(key TEXT PRIMARY KEY NOT NULL, value_json TEXT NOT NULL)",
        "CREATE TABLE compat_toolbar(surface TEXT NOT NULL, command_key TEXT NOT NULL, position INTEGER NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(surface, command_key))",
        "CREATE TABLE compat_ng_rule(rule_id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, scope_key TEXT, normalized_value TEXT NOT NULL, payload_json TEXT NOT NULL, created_at INTEGER NOT NULL)",
        "CREATE TABLE compat_watch_rule(rule_id TEXT PRIMARY KEY NOT NULL, board_key TEXT, normalized_value TEXT NOT NULL, display_value TEXT NOT NULL, created_at INTEGER NOT NULL)"
    ) + migration1To2 + migration3To4 + migration4To5 + migration5To6 + migration7To8
}
