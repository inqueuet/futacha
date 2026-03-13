package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuConfig
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.defaultThreadSettingsMenuConfig
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuConfig
import com.valoser.futacha.shared.model.normalizeThreadSettingsMenuConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Central state store for shared preferences, boards, and thread history.
 * Large helper types and persistence utilities live in sibling support files.
 */

@OptIn(ExperimentalTime::class)
class AppStateStore internal constructor(
    private val storage: PlatformStateStorage,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {
    // FIX: 複数のMutexを使用する際のデッドロック防止ガイドライン
    // - 各Mutexは独立したデータを保護しており、ネストしたロックは避けること
    // - もしネストが必要な場合は、常に以下の順序でロックすること:
    //   1. boardsMutex
    //   2. historyMutex
    //   3. scrollPositionMutex
    //   4. selfPostIdentifiersMutex
    // - 現在の実装では各Mutexは独立して使用されており、デッドロックのリスクは低い
    private val boardsMutex = Mutex()
    private val historyMutex = Mutex()
    private val historyPersistMutex = Mutex()
    private val scrollPositionMutex = Mutex() // FIX: スクロール専用Mutex
    private val catalogModeMutex = Mutex()
    private val selfPostIdentifiersMutex = Mutex()
    private val selfPostIdentifierMapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val stringListSerializer = ListSerializer(String.serializer())
    private val threadMenuConfigSerializer = ListSerializer(ThreadMenuItemConfig.serializer())
    private val threadSettingsMenuConfigSerializer = ListSerializer(ThreadSettingsMenuItemConfig.serializer())
    private val threadMenuEntriesSerializer = ListSerializer(ThreadMenuEntryConfig.serializer())
    private val catalogNavEntriesSerializer = ListSerializer(CatalogNavEntryConfig.serializer())
    private val catalogModeMapSerializer = MapSerializer(String.serializer(), String.serializer())

    // FIX: スレッドセーフなJobマップに変更
    private val scrollPositionJobs = AtomicJobMap()
    private var scrollDebounceScope: CoroutineScope? = null
    private var cachedHistory: List<ThreadHistoryEntry>? = null
    private var historyRevision: Long = 0L
    private var cachedCatalogModeMap: Map<String, CatalogMode>? = null
    private var cachedSelfPostIdentifierMap: Map<String, List<String>>? = null
    private val boardsFacade = buildAppStateBoardsFacade(
        setBoardsImpl = ::setBoardsInternal
    )
    private val preferenceFacade = buildAppStatePreferenceFacade(
        setBackgroundRefreshEnabledImpl = ::setBackgroundRefreshEnabledInternal,
        setLastUsedDeleteKeyImpl = ::setLastUsedDeleteKeyInternal,
        setLightweightModeEnabledImpl = ::setLightweightModeEnabledInternal,
        setManualSaveDirectoryImpl = ::setManualSaveDirectoryInternal,
        setManualSaveLocationImpl = ::setManualSaveLocationInternal,
        setAttachmentPickerPreferenceImpl = ::setAttachmentPickerPreferenceInternal,
        setSaveDirectorySelectionImpl = ::setSaveDirectorySelectionInternal,
        setPreferredFileManagerImpl = ::setPreferredFileManagerInternal,
        setPrivacyFilterEnabledImpl = ::setPrivacyFilterEnabledInternal,
        setCatalogDisplayStyleImpl = ::setCatalogDisplayStyleInternal,
        setCatalogGridColumnsImpl = ::setCatalogGridColumnsInternal,
        setCatalogModeImpl = ::setCatalogModeInternal,
        setNgHeadersImpl = ::setNgHeadersInternal,
        setNgWordsImpl = ::setNgWordsInternal,
        setCatalogNgWordsImpl = ::setCatalogNgWordsInternal,
        setWatchWordsImpl = ::setWatchWordsInternal,
        setThreadMenuConfigImpl = ::setThreadMenuConfigInternal,
        setThreadSettingsMenuConfigImpl = ::setThreadSettingsMenuConfigInternal,
        setThreadMenuEntriesImpl = ::setThreadMenuEntriesInternal,
        setCatalogNavEntriesImpl = ::setCatalogNavEntriesInternal,
        addSelfPostIdentifierImpl = ::addSelfPostIdentifierInternal,
        removeSelfPostIdentifiersForThreadImpl = ::removeSelfPostIdentifiersForThreadInternal,
        clearSelfPostIdentifiersImpl = ::clearSelfPostIdentifiersInternal
    )
    private val historyFacade = buildAppStateHistoryFacade(
        setHistoryImpl = ::setHistoryInternal,
        upsertHistoryEntryImpl = ::upsertHistoryEntryInternal,
        prependOrReplaceHistoryEntryImpl = ::prependOrReplaceHistoryEntryInternal,
        prependOrReplaceHistoryEntriesImpl = ::prependOrReplaceHistoryEntriesInternal,
        mergeHistoryEntriesImpl = ::mergeHistoryEntriesInternal,
        removeHistoryEntryImpl = ::removeHistoryEntryInternal,
        updateHistoryScrollPositionImpl = ::updateHistoryScrollPositionInternal,
        setScrollDebounceScopeImpl = ::setScrollDebounceScopeInternal
    )
    private val preferenceFlows = buildAppStatePreferenceFlows(
        storage = storage,
        json = json,
        stringListSerializer = stringListSerializer,
        selfPostIdentifierMapSerializer = selfPostIdentifierMapSerializer,
        threadMenuConfigSerializer = threadMenuConfigSerializer,
        threadSettingsMenuConfigSerializer = threadSettingsMenuConfigSerializer,
        threadMenuEntriesSerializer = threadMenuEntriesSerializer,
        catalogNavEntriesSerializer = catalogNavEntriesSerializer,
        selfIdentifierMaxEntries = SELF_IDENTIFIER_MAX_ENTRIES
    )

    // Error propagation
    private val _lastStorageError = MutableStateFlow<StorageError?>(null)
    val lastStorageError: StateFlow<StorageError?> = _lastStorageError.asStateFlow()

    companion object {
        private const val TAG = "AppStateStore"
        private const val SCROLL_DEBOUNCE_DELAY_MS = 1_000L
        private const val SELF_IDENTIFIER_MAX_ENTRIES = 20
        private const val SELF_POST_KEY_DELIMITER = "::"
        private const val HISTORY_PERSIST_MAX_PASSES = 8
    }

    suspend fun setScrollDebounceScope(scope: CoroutineScope) {
        historyFacade.setScrollDebounceScope(scope)
    }

    private suspend fun setScrollDebounceScopeInternal(scope: CoroutineScope) {
        scrollPositionMutex.withLock {
            scrollDebounceScope = scope
        }
    }

    val boards: Flow<List<BoardSummary>> = buildAppStateBoardsFlow(
        storage = storage,
        json = json,
        tag = TAG
    )

    val history: Flow<List<ThreadHistoryEntry>> = storage.historyJson
        .distinctUntilChanged()
        .map { stored ->
            if (stored == null) {
                emptyList()
            } else {
                decodeAppStateHistory(stored, json, TAG)
            }
        }

    val isPrivacyFilterEnabled: Flow<Boolean> = storage.privacyFilterEnabled
    val isBackgroundRefreshEnabled: Flow<Boolean> = storage.backgroundRefreshEnabled
    val isLightweightModeEnabled: Flow<Boolean> = storage.lightweightModeEnabled

    /**
     * Manual save directory as string (legacy support).
     */
    val manualSaveDirectory: Flow<String> = preferenceFlows.manualSaveDirectory

    /**
     * Manual save location as SaveLocation (supports paths, content URIs, and bookmarks).
     * Legacy string paths are automatically converted via SaveLocation.fromString().
     */
    val manualSaveLocation: Flow<SaveLocation> = preferenceFlows.manualSaveLocation

    val attachmentPickerPreference: Flow<AttachmentPickerPreference> = preferenceFlows.attachmentPickerPreference
    val saveDirectorySelection: Flow<SaveDirectorySelection> = preferenceFlows.saveDirectorySelection
    val lastUsedDeleteKey: Flow<String> = preferenceFlows.lastUsedDeleteKey

    val catalogModes: Flow<Map<String, CatalogMode>> = preferenceFlows.catalogModes
    val catalogDisplayStyle: Flow<CatalogDisplayStyle> = preferenceFlows.catalogDisplayStyle
    val catalogGridColumns: Flow<Int> = preferenceFlows.catalogGridColumns
    val ngHeaders: Flow<List<String>> = preferenceFlows.ngHeaders
    val ngWords: Flow<List<String>> = preferenceFlows.ngWords
    val catalogNgWords: Flow<List<String>> = preferenceFlows.catalogNgWords
    val watchWords: Flow<List<String>> = preferenceFlows.watchWords
    private val selfPostIdentifierMapFlow: Flow<Map<String, List<String>>> = preferenceFlows.selfPostIdentifierMapFlow
    val selfPostIdentifiersByThread: Flow<Map<String, List<String>>> = selfPostIdentifierMapFlow
    val selfPostIdentifiers: Flow<List<String>> = preferenceFlows.selfPostIdentifiers
    private val preferredFileManagerFlow: Flow<PreferredFileManager?> = preferenceFlows.preferredFileManagerFlow
    val threadMenuConfig: Flow<List<ThreadMenuItemConfig>> = preferenceFlows.threadMenuConfig
    val threadSettingsMenuConfig: Flow<List<ThreadSettingsMenuItemConfig>> = preferenceFlows.threadSettingsMenuConfig
    val threadMenuEntries: Flow<List<ThreadMenuEntryConfig>> = preferenceFlows.threadMenuEntries
    val catalogNavEntries: Flow<List<CatalogNavEntryConfig>> = preferenceFlows.catalogNavEntries

    suspend fun setBoards(boards: List<BoardSummary>) {
        boardsFacade.setBoards(boards)
    }

    private suspend fun setBoardsInternal(boards: List<BoardSummary>) {
        val encoded = encodeAppStateBoards(boards, json)
        boardsMutex.withLock {
            try {
                storage.updateBoardsJson(encoded)
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                Logger.e(TAG, "Failed to save ${boards.size} boards", e)
                _lastStorageError.value = StorageError(
                    operation = "setBoards",
                    message = e.message ?: "Unknown error",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
                // Log error but don't crash - data will be lost but app continues
            }
        }
    }

    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        historyFacade.setHistory(history)
    }

    private suspend fun setHistoryInternal(history: List<ThreadHistoryEntry>) {
        val (revision, previousRevision, previousHistory) = historyMutex.withLock {
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = history
            historyRevision = beforeRevision + 1L
            Triple(historyRevision, beforeRevision, beforeHistory)
        }
        try {
            persistHistory(revision, history)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to save history with ${history.size} entries", e)
            _lastStorageError.value = StorageError(
                operation = "setHistory",
                message = e.message ?: "Unknown error",
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            throw e
        }
    }

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        preferenceFacade.setBackgroundRefreshEnabled(enabled)
    }

    private suspend fun setBackgroundRefreshEnabledInternal(enabled: Boolean) {
        setAppStateBackgroundRefreshEnabled(
            storage = storage,
            enabled = enabled,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        preferenceFacade.setLastUsedDeleteKey(deleteKey)
    }

    private suspend fun setLastUsedDeleteKeyInternal(deleteKey: String) {
        setAppStateLastUsedDeleteKey(
            storage = storage,
            deleteKey = deleteKey,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        preferenceFacade.setLightweightModeEnabled(enabled)
    }

    private suspend fun setLightweightModeEnabledInternal(enabled: Boolean) {
        setAppStateLightweightModeEnabled(
            storage = storage,
            enabled = enabled,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setManualSaveDirectory(directory: String) {
        preferenceFacade.setManualSaveDirectory(directory)
    }

    private suspend fun setManualSaveDirectoryInternal(directory: String) {
        setAppStateManualSaveDirectory(
            storage = storage,
            directory = directory,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    /**
     * Set manual save location using SaveLocation (path, URI, or bookmark).
     * Persists the raw string representation internally.
     */
    suspend fun setManualSaveLocation(location: SaveLocation) {
        preferenceFacade.setManualSaveLocation(location)
    }

    private suspend fun setManualSaveLocationInternal(location: SaveLocation) {
        setAppStateManualSaveLocation(
            storage = storage,
            location = location,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        preferenceFacade.setAttachmentPickerPreference(preference)
    }

    private suspend fun setAttachmentPickerPreferenceInternal(preference: AttachmentPickerPreference) {
        setAppStateAttachmentPickerPreference(
            storage = storage,
            preference = preference,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        preferenceFacade.setSaveDirectorySelection(selection)
    }

    private suspend fun setSaveDirectorySelectionInternal(selection: SaveDirectorySelection) {
        setAppStateSaveDirectorySelection(
            storage = storage,
            selection = selection,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setPreferredFileManager(packageName: String?, label: String?) {
        preferenceFacade.setPreferredFileManager(packageName, label)
    }

    private suspend fun setPreferredFileManagerInternal(packageName: String?, label: String?) {
        setAppStatePreferredFileManager(
            storage = storage,
            packageName = packageName,
            label = label,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    // Flowインスタンスを固定化して、UI再コンポーズ時の再生成を防ぐ
    fun getPreferredFileManager(): Flow<PreferredFileManager?> = preferredFileManagerFlow

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) {
        preferenceFacade.setPrivacyFilterEnabled(enabled)
    }

    private suspend fun setPrivacyFilterEnabledInternal(enabled: Boolean) {
        setAppStatePrivacyFilterEnabled(
            storage = storage,
            enabled = enabled,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        preferenceFacade.setCatalogDisplayStyle(style)
    }

    private suspend fun setCatalogDisplayStyleInternal(style: CatalogDisplayStyle) {
        setAppStateCatalogDisplayStyle(
            storage = storage,
            style = style,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        preferenceFacade.setCatalogGridColumns(columns)
    }

    private suspend fun setCatalogGridColumnsInternal(columns: Int) {
        setAppStateCatalogGridColumns(
            storage = storage,
            columns = columns,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) {
        preferenceFacade.setCatalogMode(boardId, mode)
    }

    private suspend fun setCatalogModeInternal(boardId: String, mode: CatalogMode) {
        setAppStateCatalogMode(boardId, mode, TAG, ::mutateCatalogModeMap)
    }

    suspend fun setNgHeaders(headers: List<String>) {
        preferenceFacade.setNgHeaders(headers)
    }

    private suspend fun setNgHeadersInternal(headers: List<String>) {
        setAppStateNgHeaders(
            storage = storage,
            headers = headers,
            serializer = stringListSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setNgWords(words: List<String>) {
        preferenceFacade.setNgWords(words)
    }

    private suspend fun setNgWordsInternal(words: List<String>) {
        setAppStateNgWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        preferenceFacade.setCatalogNgWords(words)
    }

    private suspend fun setCatalogNgWordsInternal(words: List<String>) {
        setAppStateCatalogNgWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setWatchWords(words: List<String>) {
        preferenceFacade.setWatchWords(words)
    }

    private suspend fun setWatchWordsInternal(words: List<String>) {
        setAppStateWatchWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        preferenceFacade.setThreadMenuConfig(config)
    }

    private suspend fun setThreadMenuConfigInternal(config: List<ThreadMenuItemConfig>) {
        setAppStateThreadMenuConfig(
            storage = storage,
            config = config,
            serializer = threadMenuConfigSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        preferenceFacade.setThreadSettingsMenuConfig(config)
    }

    private suspend fun setThreadSettingsMenuConfigInternal(config: List<ThreadSettingsMenuItemConfig>) {
        setAppStateThreadSettingsMenuConfig(
            storage = storage,
            config = config,
            serializer = threadSettingsMenuConfigSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        preferenceFacade.setThreadMenuEntries(config)
    }

    private suspend fun setThreadMenuEntriesInternal(config: List<ThreadMenuEntryConfig>) {
        setAppStateThreadMenuEntries(
            storage = storage,
            config = config,
            serializer = threadMenuEntriesSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        preferenceFacade.setCatalogNavEntries(config)
    }

    private suspend fun setCatalogNavEntriesInternal(config: List<CatalogNavEntryConfig>) {
        setAppStateCatalogNavEntries(
            storage = storage,
            config = config,
            serializer = catalogNavEntriesSerializer,
            json = json,
            tag = TAG,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    suspend fun addSelfPostIdentifier(threadId: String, identifier: String, boardId: String? = null) {
        preferenceFacade.addSelfPostIdentifier(threadId, identifier, boardId)
    }

    private suspend fun addSelfPostIdentifierInternal(threadId: String, identifier: String, boardId: String? = null) {
        addAppStateSelfPostIdentifier(
            threadId = threadId,
            identifier = identifier,
            boardId = boardId,
            maxEntries = SELF_IDENTIFIER_MAX_ENTRIES,
            mutateSelfPostIdentifierMap = ::mutateSelfPostIdentifierMap
        )
    }

    suspend fun removeSelfPostIdentifiersForThread(threadId: String, boardId: String? = null) {
        preferenceFacade.removeSelfPostIdentifiersForThread(threadId, boardId)
    }

    private suspend fun removeSelfPostIdentifiersForThreadInternal(threadId: String, boardId: String? = null) {
        removeAppStateSelfPostIdentifiersForThread(
            threadId = threadId,
            boardId = boardId,
            mutateSelfPostIdentifierMap = ::mutateSelfPostIdentifierMap
        )
    }

    suspend fun clearSelfPostIdentifiers() {
        preferenceFacade.clearSelfPostIdentifiers()
    }

    private suspend fun clearSelfPostIdentifiersInternal() {
        clearAppStateSelfPostIdentifiers(::mutateSelfPostIdentifierMap)
    }

    /**
     * Insert or update a history entry while keeping the existing order intact.
     * This is used for incremental updates (e.g., refreshing metadata) without
     * requiring the caller to manage the whole list manually.
     */
    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) {
        historyFacade.upsertHistoryEntry(entry)
    }

    private suspend fun upsertHistoryEntryInternal(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history upsert due to missing snapshot")
            return
        }
        val mutation = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryUpsertPlan(currentHistory, entry)
            }
        ) ?: return
        persistHistoryMutation(mutation) { threadId ->
            "Failed to upsert history entry $threadId"
        }
    }

    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry) {
        historyFacade.prependOrReplaceHistoryEntry(entry)
    }

    private suspend fun prependOrReplaceHistoryEntryInternal(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history prepend due to missing snapshot")
            return
        }
        val updateResult = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryPrependPlan(currentHistory, entry) ?: run {
                    Logger.w(TAG, "Skipping history prepend due to invalid identity")
                    null
                }
            }
        ) ?: return
        persistHistoryMutation(updateResult) { threadId ->
            "Failed to prepend history entry $threadId"
        }
    }

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) {
        historyFacade.prependOrReplaceHistoryEntries(entries)
    }

    private suspend fun prependOrReplaceHistoryEntriesInternal(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history prepend batch due to missing snapshot")
            return
        }
        val updateResult = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryBatchPrependPlan(currentHistory, entries)
            }
        ) ?: return
        persistHistoryMutation(updateResult) { dedupedSize ->
            "Failed to prepend $dedupedSize history entries"
        }
    }

    suspend fun mergeHistoryEntries(entries: Collection<ThreadHistoryEntry>) {
        historyFacade.mergeHistoryEntries(entries)
    }

    private suspend fun mergeHistoryEntriesInternal(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history merge due to missing snapshot")
            return
        }
        val mergeResult = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryMergePlan(currentHistory, entries)?.let { plan ->
                    AppStateHistoryMutationPlan(
                    updatedHistory = plan.updatedHistory,
                    metadata = plan.droppedUpdateCount
                )
                }
            }
        ) ?: return
        persistHistoryMutation(
            mutation = mergeResult,
            onCommitted = { appendedSize ->
                if (appendedSize > 0) {
                    Logger.i(TAG, "Dropped $appendedSize stale history update(s) during merge")
                }
            }
        ) { _ ->
            "Failed to merge ${entries.size} history entries"
        }
    }

    suspend fun removeHistoryEntry(entry: ThreadHistoryEntry) {
        historyFacade.removeHistoryEntry(entry)
    }

    private suspend fun removeHistoryEntryInternal(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history removal due to missing snapshot")
            return
        }
        val removeResult = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryRemovalPlan(currentHistory, entry) ?: run {
                    Logger.w(TAG, "Skipping history removal due to invalid identity")
                    null
                }
            }
        ) ?: return
        persistHistoryMutation(removeResult) { threadId ->
            "Failed to remove history entry $threadId"
        }
    }

    /**
     * Thread-safe update of scroll position in history with debouncing.
     * This prevents race conditions when multiple scroll updates occur concurrently
     * and reduces disk I/O by debouncing rapid scroll events.
     * Note: This should only be called for scroll position updates, not initial navigation.
     */
    suspend fun updateHistoryScrollPosition(
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) {
        historyFacade.updateHistoryScrollPosition(
            threadId = threadId,
            index = index,
            offset = offset,
            boardId = boardId,
            title = title,
            titleImageUrl = titleImageUrl,
            boardName = boardName,
            boardUrl = boardUrl,
            replyCount = replyCount
        )
    }

    private suspend fun updateHistoryScrollPositionInternal(
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) {
        scheduleAppStateHistoryScrollPersistence(
            scrollPositionMutex = scrollPositionMutex,
            currentScope = { scrollDebounceScope },
            clearScope = { scrollDebounceScope = null },
            scrollPositionJobs = scrollPositionJobs,
            scrollKey = buildHistoryScrollJobKey(threadId, boardId, boardUrl),
            startDebouncedJob = { scope, scrollKey ->
                scope.launch {
                    delay(SCROLL_DEBOUNCE_DELAY_MS)
                    try {
                        updateHistoryScrollPositionImmediate(
                            threadId, index, offset, boardId, title,
                            titleImageUrl, boardName, boardUrl, replyCount
                        )
                    } finally {
                        scrollPositionJobs.removeIfSame(scrollKey, this.coroutineContext[Job])
                    }
                }
            },
            performImmediateUpdate = {
                updateHistoryScrollPositionImmediate(
                    threadId, index, offset, boardId, title,
                    titleImageUrl, boardName, boardUrl, replyCount
                )
            }
        )
    }

    /**
     * Immediate update of scroll position without debouncing.
     * Internal method used by the debounced public method.
     * FIX: スクロール専用Mutexを使用して、他のhistory操作とのロック競合を減らす
     */
    private suspend fun updateHistoryScrollPositionImmediate(
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) {
        val historySnapshot = readHistorySnapshot() ?: return
        val updateResult = buildAppStateHistoryMutation(
            historyMutex = historyMutex,
            historySnapshot = historySnapshot,
            readLockedHistory = ::readHistorySnapshotLocked,
            currentRevision = { historyRevision },
            previousHistory = { cachedHistory },
            updateCachedState = { revision, history ->
                historyRevision = revision
                cachedHistory = history
            },
            buildPlan = { currentHistory ->
                resolveAppStateHistoryScrollUpdatePlan(
                    currentHistory = currentHistory,
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    boardId = boardId,
                    title = title,
                    titleImageUrl = titleImageUrl,
                    boardName = boardName,
                    boardUrl = boardUrl,
                    replyCount = replyCount,
                    nowMillis = Clock.System.now().toEpochMilliseconds()
                )
            }
        ) ?: return
        persistHistoryMutation(updateResult) { targetThreadId ->
            "Failed to persist updated history for thread $targetThreadId"
        }
    }

    suspend fun seedIfEmpty(
        defaultBoards: List<BoardSummary>,
        defaultHistory: List<ThreadHistoryEntry>,
        defaultNgHeaders: List<String> = emptyList(),
        defaultNgWords: List<String> = emptyList(),
        defaultCatalogNgWords: List<String> = emptyList(),
        defaultWatchWords: List<String> = emptyList(),
        defaultSelfPostIdentifierMap: Map<String, List<String>> = emptyMap(),
        defaultCatalogModeMap: Map<String, CatalogMode> = emptyMap(),
        defaultThreadMenuConfig: List<ThreadMenuItemConfig> = defaultThreadMenuConfig(),
        defaultThreadSettingsMenuConfig: List<ThreadSettingsMenuItemConfig> = defaultThreadSettingsMenuConfig(),
        defaultThreadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
        defaultCatalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
        defaultLastUsedDeleteKey: String = ""
    ) {
        try {
            val seedBundles = buildAppStateSeedPayload(
                defaultBoards = defaultBoards,
                defaultHistory = defaultHistory,
                defaultNgHeaders = defaultNgHeaders,
                defaultNgWords = defaultNgWords,
                defaultCatalogNgWords = defaultCatalogNgWords,
                defaultWatchWords = defaultWatchWords,
                defaultSelfPostIdentifierMap = defaultSelfPostIdentifierMap,
                defaultCatalogModeMap = defaultCatalogModeMap,
                defaultThreadMenuConfig = defaultThreadMenuConfig,
                defaultThreadSettingsMenuConfig = defaultThreadSettingsMenuConfig,
                defaultThreadMenuEntries = defaultThreadMenuEntries,
                defaultCatalogNavEntries = defaultCatalogNavEntries,
                defaultLastUsedDeleteKey = defaultLastUsedDeleteKey,
                json = json,
                threadMenuConfig = normalizeThreadMenuConfig(defaultThreadMenuConfig),
                threadSettingsMenuConfig = normalizeThreadSettingsMenuConfig(defaultThreadSettingsMenuConfig),
                threadMenuEntries = normalizeThreadMenuEntries(defaultThreadMenuEntries),
                catalogNavEntries = normalizeCatalogNavEntries(defaultCatalogNavEntries)
            ).toSeedBundles()
            storage.seedIfEmpty(
                seedBundles.boards.boardsJson,
                seedBundles.history.historyJson,
                seedBundles.preferences.ngHeadersJson,
                seedBundles.preferences.ngWordsJson,
                seedBundles.preferences.catalogNgWordsJson,
                seedBundles.preferences.watchWordsJson,
                seedBundles.preferences.selfPostIdentifiersJson,
                seedBundles.preferences.catalogModeMapJson,
                seedBundles.preferences.attachmentPickerPreference,
                seedBundles.preferences.saveDirectorySelection,
                seedBundles.preferences.lastUsedDeleteKey,
                seedBundles.preferences.threadMenuConfigJson,
                seedBundles.preferences.threadSettingsMenuConfigJson,
                seedBundles.preferences.threadMenuEntriesConfigJson,
                seedBundles.preferences.catalogNavEntriesJson
            )
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to seed default data", e)
            // Log error but don't crash - app will work with empty state
        }
    }

    private suspend fun readHistorySnapshot(): List<ThreadHistoryEntry>? {
        return readAppStateHistorySnapshot(
            historyMutex = historyMutex,
            currentCachedHistory = { cachedHistory },
            readStorageHistory = {
                val raw = storage.historyJson.first()
                if (raw == null) {
                    emptyList()
                } else {
                    decodeAppStateHistory(raw, json, TAG)
                }
            },
            setCachedHistory = { cachedHistory = it },
            onReadFailure = { error ->
                Logger.e(TAG, "Failed to read history state", error)
            },
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    /**
     * Read history snapshot while already holding the historyMutex lock.
     * This should only be called from within a historyMutex.withLock block.
     */
    private fun readHistorySnapshotLocked(): List<ThreadHistoryEntry>? {
        return cachedHistory
    }

    private suspend fun rollbackHistoryMutation(
        failedRevision: Long,
        previousRevision: Long,
        previousHistory: List<ThreadHistoryEntry>?
    ) {
        rollbackAppStateHistoryMutation(
            historyMutex = historyMutex,
            failedRevision = failedRevision,
            previousRevision = previousRevision,
            previousHistory = previousHistory,
            currentHistoryRevision = { historyRevision },
            restoreHistoryState = { restoredRevision, restoredHistory ->
                historyRevision = restoredRevision
                cachedHistory = restoredHistory
            }
        )
    }

    private suspend fun <T> persistHistoryMutation(
        mutation: HistoryMutation<T>,
        onCommitted: (T) -> Unit = {},
        buildFailureMessage: (T) -> String
    ) {
        persistAppStateHistoryMutation(
            mutation = mutation,
            persistHistory = ::persistHistory,
            rollbackHistoryMutation = ::rollbackHistoryMutation,
            onPersistFailure = { message, error ->
                Logger.e(TAG, message, error)
            },
            rethrowIfCancellation = ::rethrowIfCancellation,
            onCommitted = onCommitted,
            buildFailureMessage = buildFailureMessage
        )
    }

    private suspend fun persistHistory(revision: Long, history: List<ThreadHistoryEntry>) {
        persistAppStateHistory(
            historyPersistMutex = historyPersistMutex,
            revision = revision,
            history = history,
            maxPasses = HISTORY_PERSIST_MAX_PASSES,
            writeHistoryJson = { updatedHistory ->
                storage.updateHistoryJson(encodeAppStateHistory(updatedHistory, json))
            },
            readLatestHistoryContinuation = { targetRevision ->
                historyMutex.withLock {
                    if (historyRevision > targetRevision) {
                        val latest = cachedHistory
                        if (latest != null) {
                            AppStatePersistedHistoryContinuation(
                                revision = historyRevision,
                                history = latest
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        )
    }

    private suspend fun readSelfPostIdentifierMapSnapshot(): Map<String, List<String>> {
        return readAppStateCachedSnapshot(
            mutex = selfPostIdentifiersMutex,
            currentCachedValue = { cachedSelfPostIdentifierMap },
            readStorageSnapshot = {
                val raw = storage.selfPostIdentifiersJson.first()
                withContext(AppDispatchers.parsing) {
                    decodeSelfPostIdentifierMapValue(raw, json, selfPostIdentifierMapSerializer)
                }
            },
            setCachedValue = { cachedSelfPostIdentifierMap = it },
            onReadFailure = { error ->
                Logger.e(TAG, "Failed to read self post identifier map", error)
                emptyMap()
            },
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    private suspend fun persistSelfPostIdentifierMap(map: Map<String, List<String>>) {
        persistAppStateSelfPostIdentifierMap(
            map = map,
            json = json,
            update = storage::updateSelfPostIdentifiersJson
        )
    }

    private suspend fun readCatalogModeSnapshot(): Map<String, CatalogMode> {
        return readAppStateCachedSnapshot(
            mutex = catalogModeMutex,
            currentCachedValue = { cachedCatalogModeMap },
            readStorageSnapshot = {
                val raw = storage.catalogModeMapJson.first()
                withContext(AppDispatchers.parsing) {
                    decodeCatalogModeMapValue(raw)
                }
            },
            setCachedValue = { cachedCatalogModeMap = it },
            onReadFailure = { error ->
                throw error
            },
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    private suspend fun persistCatalogModeMap(map: Map<String, CatalogMode>) {
        persistAppStateCatalogModeMap(
            map = map,
            json = json,
            update = storage::updateCatalogModeMapJson
        )
    }

    private suspend fun mutateCatalogModeMap(
        onReadFailure: (Throwable) -> Unit,
        onWriteFailure: (Throwable) -> Unit,
        transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
    ) {
        val loadedSnapshot = runCatching {
            readCatalogModeSnapshot()
        }.getOrElse { error ->
            onReadFailure(error)
            return
        }
        mutateAppStateCachedSnapshot(
            mutex = catalogModeMutex,
            loadedSnapshot = loadedSnapshot,
            currentCachedValue = { cachedCatalogModeMap },
            setCachedValue = { cachedCatalogModeMap = it },
            transform = transform,
            persistUpdatedValue = ::persistCatalogModeMap,
            onWriteFailure = onWriteFailure,
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    private suspend fun mutateSelfPostIdentifierMap(
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) {
        val loadedSnapshot = readSelfPostIdentifierMapSnapshot()
        mutateAppStateCachedSnapshot(
            mutex = selfPostIdentifiersMutex,
            loadedSnapshot = loadedSnapshot,
            currentCachedValue = { cachedSelfPostIdentifierMap },
            setCachedValue = { cachedSelfPostIdentifierMap = it },
            transform = transform,
            persistUpdatedValue = ::persistSelfPostIdentifierMap,
            onWriteFailure = { error -> throw error },
            rethrowIfCancellation = ::rethrowIfCancellation
        )
    }

    private fun rethrowIfCancellation(error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
    }
}

fun createAppStateStore(platformContext: Any? = null): AppStateStore {
    return AppStateStore(createPlatformStateStorage(platformContext))
}

internal interface PlatformStateStorage {
    val boardsJson: Flow<String?>
    val historyJson: Flow<String?>
    val privacyFilterEnabled: Flow<Boolean>
    val backgroundRefreshEnabled: Flow<Boolean>
    val lightweightModeEnabled: Flow<Boolean>
    val manualSaveDirectory: Flow<String>
    val attachmentPickerPreference: Flow<String?>
    val saveDirectorySelection: Flow<String?>
    val lastUsedDeleteKey: Flow<String?>
    val catalogModeMapJson: Flow<String?>
    val catalogDisplayStyle: Flow<String?>
    val catalogGridColumns: Flow<String?>
    val ngHeadersJson: Flow<String?>
    val ngWordsJson: Flow<String?>
    val catalogNgWordsJson: Flow<String?>
    val watchWordsJson: Flow<String?>
    val selfPostIdentifiersJson: Flow<String?>
    val preferredFileManagerPackage: Flow<String>
    val preferredFileManagerLabel: Flow<String>
    val threadMenuConfigJson: Flow<String?>
    val threadSettingsMenuConfigJson: Flow<String?>
    val threadMenuEntriesConfigJson: Flow<String?>
    val catalogNavEntriesConfigJson: Flow<String?>

    suspend fun updateBoardsJson(value: String)
    suspend fun updateHistoryJson(value: String)
    suspend fun updatePrivacyFilterEnabled(enabled: Boolean)
    suspend fun updateBackgroundRefreshEnabled(enabled: Boolean)
    suspend fun updateLightweightModeEnabled(enabled: Boolean)
    suspend fun updateManualSaveDirectory(directory: String)
    suspend fun updateAttachmentPickerPreference(preference: String)
    suspend fun updateSaveDirectorySelection(selection: String)
    suspend fun updateLastUsedDeleteKey(value: String)
    suspend fun updateCatalogModeMapJson(value: String)
    suspend fun updateCatalogDisplayStyle(style: String)
    suspend fun updateCatalogGridColumns(columns: String)
    suspend fun updateNgHeadersJson(value: String)
    suspend fun updateNgWordsJson(value: String)
    suspend fun updateCatalogNgWordsJson(value: String)
    suspend fun updateWatchWordsJson(value: String)
    suspend fun updateSelfPostIdentifiersJson(value: String)
    suspend fun updatePreferredFileManager(packageName: String, label: String)
    suspend fun updatePreferredFileManagerPackage(packageName: String)
    suspend fun updatePreferredFileManagerLabel(label: String)
    suspend fun updateThreadMenuConfigJson(value: String)
    suspend fun updateThreadSettingsMenuConfigJson(value: String)
    suspend fun updateThreadMenuEntriesConfigJson(value: String)
    suspend fun updateCatalogNavEntriesConfigJson(value: String)

    suspend fun seedIfEmpty(
        defaultBoardsJson: String,
        defaultHistoryJson: String,
        defaultNgHeadersJson: String?,
        defaultNgWordsJson: String?,
        defaultCatalogNgWordsJson: String?,
        defaultWatchWordsJson: String?,
        defaultSelfPostIdentifiersJson: String?,
        defaultCatalogModeMapJson: String?,
        defaultAttachmentPickerPreference: String?,
        defaultSaveDirectorySelection: String?,
        defaultLastUsedDeleteKey: String?,
        defaultThreadMenuConfigJson: String?,
        defaultThreadSettingsMenuConfigJson: String?,
        defaultThreadMenuEntriesConfigJson: String?,
        defaultCatalogNavEntriesJson: String?
    )
}

internal expect fun createPlatformStateStorage(platformContext: Any? = null): PlatformStateStorage
