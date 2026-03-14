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
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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
    // - history 用のロックは historyCoordinator に閉じ込め、store 本体では
    //   boards / scroll / catalogMode / selfPostIdentifiers を個別に扱う
    private val boardsMutex = Mutex()
    private val scrollPositionMutex = Mutex() // FIX: スクロール専用Mutex
    private val catalogModeMutex = Mutex()
    private val selfPostIdentifiersMutex = Mutex()
    private val selfPostIdentifierMapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val stringListSerializer = ListSerializer(String.serializer())
    private val threadMenuConfigSerializer = ListSerializer(ThreadMenuItemConfig.serializer())
    private val threadSettingsMenuConfigSerializer = ListSerializer(ThreadSettingsMenuItemConfig.serializer())
    private val threadMenuEntriesSerializer = ListSerializer(ThreadMenuEntryConfig.serializer())
    private val catalogNavEntriesSerializer = ListSerializer(CatalogNavEntryConfig.serializer())

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
    private val preferenceSnapshotCoordinator = AppStatePreferenceSnapshotCoordinator(
        storage = storage,
        json = json,
        catalogModeMutex = catalogModeMutex,
        selfPostIdentifiersMutex = selfPostIdentifiersMutex,
        selfPostIdentifierMapSerializer = selfPostIdentifierMapSerializer,
        tag = TAG,
        rethrowIfCancellation = ::rethrowIfCancellation
    )
    private val historyCoordinator = AppStateHistoryCoordinator(
        storage = storage,
        json = json,
        tag = TAG,
        maxPersistPasses = HISTORY_PERSIST_MAX_PASSES,
        rethrowIfCancellation = ::rethrowIfCancellation
    )
    private val scrollPersistenceCoordinator = AppStateHistoryScrollPersistenceCoordinator(
        debounceDelayMillis = SCROLL_DEBOUNCE_DELAY_MS,
        buildScrollKey = { request ->
            buildHistoryScrollJobKey(request.threadId, request.boardId, request.boardUrl)
        },
        performImmediateUpdate = ::updateHistoryScrollPositionImmediate
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

    private val storageMutationHandler = AppStateStorageMutationHandler(
        tag = TAG,
        lastStorageError = _lastStorageError,
        rethrowIfCancellation = ::rethrowIfCancellation
    )
    private val boardsCoordinator = AppStateBoardsCoordinator(
        storage = storage,
        json = json,
        boardsMutex = boardsMutex,
        runStorageMutation = ::runStorageMutation,
        tag = TAG
    )
    private val historyOperations = AppStateHistoryOperations(
        tag = TAG,
        historyCoordinator = historyCoordinator,
        scrollPersistenceCoordinator = scrollPersistenceCoordinator,
        runStorageMutation = ::runStorageMutation
    )

    suspend fun setScrollDebounceScope(scope: CoroutineScope) = setScrollDebounceScopeInternal(scope)

    private suspend fun setScrollDebounceScopeInternal(scope: CoroutineScope) {
        scrollPersistenceCoordinator.setScope(scope)
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
    val isAdsEnabled: Flow<Boolean> = storage.adsEnabled
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
    private val preferenceOperations = AppStatePreferenceOperations(
        storage = storage,
        json = json,
        stringListSerializer = stringListSerializer,
        threadMenuConfigSerializer = threadMenuConfigSerializer,
        threadSettingsMenuConfigSerializer = threadSettingsMenuConfigSerializer,
        threadMenuEntriesSerializer = threadMenuEntriesSerializer,
        catalogNavEntriesSerializer = catalogNavEntriesSerializer,
        preferredFileManagerFlow = preferredFileManagerFlow,
        selfIdentifierMaxEntries = SELF_IDENTIFIER_MAX_ENTRIES,
        tag = TAG,
        rethrowIfCancellation = ::rethrowIfCancellation,
        mutateCatalogModeMap = preferenceSnapshotCoordinator::mutateCatalogModeMap,
        mutateSelfPostIdentifierMap = preferenceSnapshotCoordinator::mutateSelfPostIdentifierMap
    )

    suspend fun setBoards(boards: List<BoardSummary>) = setBoardsInternal(boards)

    suspend fun updateBoards(transform: (List<BoardSummary>) -> List<BoardSummary>) = updateBoardsInternal(transform)

    private suspend fun setBoardsInternal(boards: List<BoardSummary>) {
        boardsCoordinator.setBoards(boards)
    }

    private suspend fun updateBoardsInternal(
        transform: (List<BoardSummary>) -> List<BoardSummary>
    ) {
        boardsCoordinator.updateBoards(transform)
    }

    suspend fun setHistory(history: List<ThreadHistoryEntry>) = setHistoryInternal(history)

    private suspend fun setHistoryInternal(history: List<ThreadHistoryEntry>) {
        historyOperations.setHistory(history)
    }

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) =
        preferenceOperations.setBackgroundRefreshEnabled(enabled)

    suspend fun setAdsEnabled(enabled: Boolean) =
        preferenceOperations.setAdsEnabled(enabled)

    suspend fun setLastUsedDeleteKey(deleteKey: String) =
        preferenceOperations.setLastUsedDeleteKey(deleteKey)

    suspend fun setLightweightModeEnabled(enabled: Boolean) =
        preferenceOperations.setLightweightModeEnabled(enabled)

    suspend fun setManualSaveDirectory(directory: String) =
        preferenceOperations.setManualSaveDirectory(directory)

    /**
     * Set manual save location using SaveLocation (path, URI, or bookmark).
     * Persists the raw string representation internally.
     */
    suspend fun setManualSaveLocation(location: SaveLocation) =
        preferenceOperations.setManualSaveLocation(location)

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) =
        preferenceOperations.setAttachmentPickerPreference(preference)

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) =
        preferenceOperations.setSaveDirectorySelection(selection)

    suspend fun setPreferredFileManager(packageName: String?, label: String?) =
        preferenceOperations.setPreferredFileManager(packageName, label)

    fun getPreferredFileManager(): Flow<PreferredFileManager?> =
        preferenceOperations.getPreferredFileManager()

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) =
        preferenceOperations.setPrivacyFilterEnabled(enabled)

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) =
        preferenceOperations.setCatalogDisplayStyle(style)

    suspend fun setCatalogGridColumns(columns: Int) =
        preferenceOperations.setCatalogGridColumns(columns)

    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) =
        preferenceOperations.setCatalogMode(boardId, mode)

    suspend fun setNgHeaders(headers: List<String>) =
        preferenceOperations.setNgHeaders(headers)

    suspend fun setNgWords(words: List<String>) =
        preferenceOperations.setNgWords(words)

    suspend fun setCatalogNgWords(words: List<String>) =
        preferenceOperations.setCatalogNgWords(words)

    suspend fun setWatchWords(words: List<String>) =
        preferenceOperations.setWatchWords(words)

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) =
        preferenceOperations.setThreadMenuConfig(config)

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) =
        preferenceOperations.setThreadSettingsMenuConfig(config)

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) =
        preferenceOperations.setThreadMenuEntries(config)

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) =
        preferenceOperations.setCatalogNavEntries(config)

    suspend fun addSelfPostIdentifier(threadId: String, identifier: String, boardId: String? = null) =
        preferenceOperations.addSelfPostIdentifier(threadId, identifier, boardId)

    suspend fun removeSelfPostIdentifiersForThread(threadId: String, boardId: String? = null) =
        preferenceOperations.removeSelfPostIdentifiersForThread(threadId, boardId)

    suspend fun clearSelfPostIdentifiers() =
        preferenceOperations.clearSelfPostIdentifiers()

    /**
     * Insert or update a history entry while keeping the existing order intact.
     * This is used for incremental updates (e.g., refreshing metadata) without
     * requiring the caller to manage the whole list manually.
     */
    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) = upsertHistoryEntryInternal(entry)

    private suspend fun upsertHistoryEntryInternal(entry: ThreadHistoryEntry) {
        historyOperations.upsertHistoryEntry(entry)
    }

    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry) = prependOrReplaceHistoryEntryInternal(entry)

    private suspend fun prependOrReplaceHistoryEntryInternal(entry: ThreadHistoryEntry) {
        historyOperations.prependOrReplaceHistoryEntry(entry)
    }

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) = prependOrReplaceHistoryEntriesInternal(entries)

    private suspend fun prependOrReplaceHistoryEntriesInternal(entries: List<ThreadHistoryEntry>) {
        historyOperations.prependOrReplaceHistoryEntries(entries)
    }

    suspend fun mergeHistoryEntries(entries: Collection<ThreadHistoryEntry>) = mergeHistoryEntriesInternal(entries)

    private suspend fun mergeHistoryEntriesInternal(entries: Collection<ThreadHistoryEntry>) {
        historyOperations.mergeHistoryEntries(entries)
    }

    suspend fun removeHistoryEntry(entry: ThreadHistoryEntry) = removeHistoryEntryInternal(entry)

    private suspend fun removeHistoryEntryInternal(entry: ThreadHistoryEntry) {
        historyOperations.removeHistoryEntry(entry)
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
        updateHistoryScrollPosition(
            AppStateHistoryScrollUpdateRequest(
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
        )
    }

    internal suspend fun updateHistoryScrollPosition(
        request: AppStateHistoryScrollUpdateRequest
    ) = updateHistoryScrollPositionInternal(request)

    private suspend fun updateHistoryScrollPositionInternal(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        historyOperations.scheduleHistoryScrollPositionUpdate(request)
    }

    /**
     * Immediate update of scroll position without debouncing.
     * Internal method used by the debounced public method.
     * FIX: スクロール専用Mutexを使用して、他のhistory操作とのロック競合を減らす
     */
    private suspend fun updateHistoryScrollPositionImmediate(
        request: AppStateHistoryScrollUpdateRequest
    ) {
        historyOperations.updateHistoryScrollPositionImmediate(request)
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
        seedIfEmpty(
            AppStateSeedDefaults(
                boards = defaultBoards,
                history = defaultHistory,
                ngHeaders = defaultNgHeaders,
                ngWords = defaultNgWords,
                catalogNgWords = defaultCatalogNgWords,
                watchWords = defaultWatchWords,
                selfPostIdentifierMap = defaultSelfPostIdentifierMap,
                catalogModeMap = defaultCatalogModeMap,
                threadMenuConfig = defaultThreadMenuConfig,
                threadSettingsMenuConfig = defaultThreadSettingsMenuConfig,
                threadMenuEntries = defaultThreadMenuEntries,
                catalogNavEntries = defaultCatalogNavEntries,
                lastUsedDeleteKey = defaultLastUsedDeleteKey
            )
        )
    }

    internal suspend fun seedIfEmpty(defaults: AppStateSeedDefaults) {
        try {
            val seedBundles = buildAppStateSeedPayload(
                AppStateSeedPayloadInputs(
                    defaults = defaults,
                    json = json,
                    normalizedThreadMenuConfig = normalizeThreadMenuConfig(defaults.threadMenuConfig),
                    normalizedThreadSettingsMenuConfig =
                        normalizeThreadSettingsMenuConfig(defaults.threadSettingsMenuConfig),
                    normalizedThreadMenuEntries = normalizeThreadMenuEntries(defaults.threadMenuEntries),
                    normalizedCatalogNavEntries = normalizeCatalogNavEntries(defaults.catalogNavEntries)
                )
            ).toSeedBundles()
            storage.seedIfEmpty(seedBundles)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to seed default data", e)
            // Log error but don't crash - app will work with empty state
        }
    }

    private suspend fun runStorageMutation(
        operation: String,
        failureMessage: () -> String,
        onFailure: suspend () -> Unit = {},
        rethrowOnFailure: Boolean = false,
        block: suspend () -> Unit
    ) {
        storageMutationHandler.run(
            operation = operation,
            failureMessage = failureMessage,
            onFailure = onFailure,
            rethrowOnFailure = rethrowOnFailure,
            block = block
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
    val adsEnabled: Flow<Boolean>
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
    suspend fun updateAdsEnabled(enabled: Boolean)
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

    suspend fun seedIfEmpty(seedBundles: AppStateSeedBundles)
}

internal expect fun createPlatformStateStorage(platformContext: Any? = null): PlatformStateStorage
