package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
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
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

private const val DEFAULT_CATALOG_GRID_COLUMNS = 5
private const val MIN_CATALOG_GRID_COLUMNS = 2
private const val MAX_CATALOG_GRID_COLUMNS = 8

// FIX: KMP対応のスレッドセーフJobマップクラス
private class AtomicJobMap {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, Job>()

    suspend fun putAndCancelOld(key: String, newJob: Job): Job? {
        return mutex.withLock {
            val oldJob = map.put(key, newJob)
            oldJob
        }
    }

    suspend fun removeIfSame(key: String, job: Job?) {
        mutex.withLock {
            if (map[key] == job) {
                map.remove(key)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
class AppStateStore internal constructor(
    private val storage: PlatformStateStorage,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {
    private val boardsSerializer = ListSerializer(BoardSummary.serializer())
    private val historySerializer = ListSerializer(ThreadHistoryEntry.serializer())
    private val boardsMutex = Mutex()
    private val historyMutex = Mutex()
    private val selfPostIdentifiersMutex = Mutex()
    private val catalogModeMutex = Mutex()
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

    companion object {
        private const val TAG = "AppStateStore"
        private const val SCROLL_DEBOUNCE_DELAY_MS = 500L
        private const val SELF_IDENTIFIER_MAX_ENTRIES = 20
    }

    fun setScrollDebounceScope(scope: CoroutineScope) {
        scrollDebounceScope = scope
    }

    val boards: Flow<List<BoardSummary>> = storage.boardsJson.map { stored ->
        stored?.let { decodeBoards(it) } ?: emptyList()
    }

    val history: Flow<List<ThreadHistoryEntry>> = storage.historyJson.map { stored ->
        stored?.let { decodeHistory(it) } ?: emptyList()
    }

    val isPrivacyFilterEnabled: Flow<Boolean> = storage.privacyFilterEnabled
    val isBackgroundRefreshEnabled: Flow<Boolean> = storage.backgroundRefreshEnabled
    val isLightweightModeEnabled: Flow<Boolean> = storage.lightweightModeEnabled

    /**
     * Manual save directory as string (legacy support).
     */
    val manualSaveDirectory: Flow<String> = storage.manualSaveDirectory
        .map { manualPath ->
            sanitizeManualSaveDirectory(manualPath)
        }

    /**
     * Manual save location as SaveLocation (supports paths, content URIs, and bookmarks).
     * Legacy string paths are automatically converted via SaveLocation.fromString().
     */
    val manualSaveLocation: Flow<SaveLocation> = storage.manualSaveDirectory
        .map { raw ->
            val sanitized = sanitizeManualSaveDirectory(raw)
            SaveLocation.fromString(sanitized)
        }

    val attachmentPickerPreference: Flow<AttachmentPickerPreference> = storage.attachmentPickerPreference
        .map { raw -> decodeAttachmentPickerPreference(raw) }
    val saveDirectorySelection: Flow<SaveDirectorySelection> = storage.saveDirectorySelection
        .map { raw -> decodeSaveDirectorySelection(raw) }
    val lastUsedDeleteKey: Flow<String> = storage.lastUsedDeleteKey
        .map { raw -> raw?.take(8).orEmpty() }

    val catalogModes: Flow<Map<String, CatalogMode>> = storage.catalogModeMapJson.map { raw ->
        decodeCatalogModeMap(raw)
    }
    val catalogDisplayStyle: Flow<CatalogDisplayStyle> = storage.catalogDisplayStyle.map { raw ->
        decodeCatalogDisplayStyle(raw)
    }
    val catalogGridColumns: Flow<Int> = storage.catalogGridColumns.map { raw ->
        decodeCatalogGridColumns(raw)
    }
    val ngHeaders: Flow<List<String>> = storage.ngHeadersJson.map { raw ->
        decodeStringList(raw)
    }
    val ngWords: Flow<List<String>> = storage.ngWordsJson.map { raw ->
        decodeStringList(raw)
    }
    val catalogNgWords: Flow<List<String>> = storage.catalogNgWordsJson.map { raw ->
        decodeStringList(raw)
    }
    val watchWords: Flow<List<String>> = storage.watchWordsJson.map { raw ->
        decodeStringList(raw)
    }
    val selfPostIdentifiersByThread: Flow<Map<String, List<String>>> = storage.selfPostIdentifiersJson.map { raw ->
        decodeSelfPostIdentifierMap(raw)
    }
    val selfPostIdentifiers: Flow<List<String>> = storage.selfPostIdentifiersJson.map { raw ->
        aggregateIdentifiers(decodeSelfPostIdentifierMap(raw))
    }
    val threadMenuConfig: Flow<List<ThreadMenuItemConfig>> = storage.threadMenuConfigJson.map { raw ->
        decodeThreadMenuConfig(raw)
    }
    val threadSettingsMenuConfig: Flow<List<ThreadSettingsMenuItemConfig>> = storage.threadSettingsMenuConfigJson.map { raw ->
        decodeThreadSettingsMenuConfig(raw)
    }
    val threadMenuEntries: Flow<List<ThreadMenuEntryConfig>> = storage.threadMenuEntriesConfigJson.map { raw ->
        decodeThreadMenuEntries(raw)
    }
    val catalogNavEntries: Flow<List<CatalogNavEntryConfig>> = storage.catalogNavEntriesConfigJson.map { raw ->
        decodeCatalogNavEntries(raw)
    }

    suspend fun setBoards(boards: List<BoardSummary>) {
        boardsMutex.withLock {
            try {
                storage.updateBoardsJson(json.encodeToString(boardsSerializer, boards))
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save ${boards.size} boards", e)
                // Log error but don't crash - data will be lost but app continues
            }
        }
    }

    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        historyMutex.withLock {
            try {
                persistHistory(history)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save history with ${history.size} entries", e)
                // Log error but don't crash - data will be lost but app continues
            }
        }
    }

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        try {
            storage.updateBackgroundRefreshEnabled(enabled)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save background refresh state: $enabled", e)
            // Log error but don't crash
        }
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        val sanitized = deleteKey.trim().take(8)
        try {
            storage.updateLastUsedDeleteKey(sanitized)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save last used delete key", e)
        }
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        try {
            storage.updateLightweightModeEnabled(enabled)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save lightweight mode state: $enabled", e)
        }
    }

    suspend fun setManualSaveDirectory(directory: String) {
        val sanitized = sanitizeManualSaveDirectory(directory)
        try {
            storage.updateManualSaveDirectory(sanitized)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save manual save directory: $sanitized", e)
            // Log error but don't crash
        }
    }

    /**
     * Set manual save location using SaveLocation (path, URI, or bookmark).
     * Persists the raw string representation internally.
     */
    suspend fun setManualSaveLocation(location: SaveLocation) {
        val rawString = location.toRawString()
        try {
            storage.updateManualSaveDirectory(rawString)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save manual save location: $rawString", e)
        }
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        try {
            storage.updateAttachmentPickerPreference(preference.name)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save attachment picker preference: $preference", e)
        }
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        try {
            storage.updateSaveDirectorySelection(selection.name)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save save directory selection: $selection", e)
        }
    }

    suspend fun setPreferredFileManager(packageName: String?, label: String?) {
        try {
            storage.updatePreferredFileManagerPackage(packageName ?: "")
            storage.updatePreferredFileManagerLabel(label ?: "")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save preferred file manager: $packageName", e)
        }
    }

    fun getPreferredFileManager(): Flow<PreferredFileManager?> =
        combine(storage.preferredFileManagerPackage, storage.preferredFileManagerLabel) { pkg, label ->
            if (pkg.isBlank()) {
                null
            } else {
                PreferredFileManager(pkg, label)
            }
        }

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) {
        try {
            storage.updatePrivacyFilterEnabled(enabled)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save privacy filter state: $enabled", e)
            // Log error but don't crash
        }
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        try {
            storage.updateCatalogDisplayStyle(style.name)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save catalog display style: ${style.name}", e)
        }
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)
        try {
            storage.updateCatalogGridColumns(clamped.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save catalog grid columns: $clamped", e)
        }
    }

    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) {
        // FIX: デッドロック対策 - Mutex外でsuspend関数を呼び出す
        val currentRaw = try {
            storage.catalogModeMapJson.first()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read current catalog mode map", e)
            null
        }

        catalogModeMutex.withLock {
            try {
                val current = decodeCatalogModeMap(currentRaw)
                val updated = current + (boardId to mode)
                storage.updateCatalogModeMapJson(
                    json.encodeToString(catalogModeMapSerializer, encodeCatalogModeMap(updated))
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save catalog mode for $boardId: ${mode.name}", e)
            }
        }
    }

    suspend fun setNgHeaders(headers: List<String>) {
        try {
            storage.updateNgHeadersJson(json.encodeToString(stringListSerializer, headers))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save NG headers (${headers.size})", e)
        }
    }

    suspend fun setNgWords(words: List<String>) {
        try {
            storage.updateNgWordsJson(json.encodeToString(stringListSerializer, words))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save NG words (${words.size})", e)
        }
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        try {
            storage.updateCatalogNgWordsJson(json.encodeToString(stringListSerializer, words))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save catalog NG words (${words.size})", e)
        }
    }

    suspend fun setWatchWords(words: List<String>) {
        try {
            storage.updateWatchWordsJson(json.encodeToString(stringListSerializer, words))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save watch words (${words.size})", e)
        }
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        val normalized = normalizeThreadMenuConfig(config)
        try {
            storage.updateThreadMenuConfigJson(json.encodeToString(threadMenuConfigSerializer, normalized))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save thread menu config (${normalized.size} items)", e)
        }
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        val normalized = normalizeThreadSettingsMenuConfig(config)
        try {
            storage.updateThreadSettingsMenuConfigJson(json.encodeToString(threadSettingsMenuConfigSerializer, normalized))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save thread settings menu config (${normalized.size} items)", e)
        }
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        val normalized = normalizeThreadMenuEntries(config)
        try {
            storage.updateThreadMenuEntriesConfigJson(json.encodeToString(threadMenuEntriesSerializer, normalized))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save thread menu entries (${normalized.size} items)", e)
        }
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        val normalized = normalizeCatalogNavEntries(config)
        try {
            storage.updateCatalogNavEntriesConfigJson(json.encodeToString(catalogNavEntriesSerializer, normalized))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save catalog nav entries (${normalized.size} items)", e)
        }
    }

    suspend fun addSelfPostIdentifier(threadId: String, identifier: String) {
        val trimmed = identifier.trim().takeIf { it.isNotBlank() } ?: return
        selfPostIdentifiersMutex.withLock {
            val currentMap = readSelfPostIdentifierMapSnapshot()
            val existingForThread = currentMap[threadId] ?: emptyList()
            val normalized = existingForThread
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableList()
            if (normalized.none { it.equals(trimmed, ignoreCase = true) }) {
                normalized.add(trimmed)
            }
            val updatedThreadList = normalized
                .asSequence()
                .distinctBy { it.lowercase() }
                .take(SELF_IDENTIFIER_MAX_ENTRIES)
                .toList()
            val updatedMap = currentMap.toMutableMap()
            updatedMap[threadId] = updatedThreadList
            persistSelfPostIdentifierMap(updatedMap)
        }
    }

    suspend fun removeSelfPostIdentifiersForThread(threadId: String) {
        selfPostIdentifiersMutex.withLock {
            val currentMap = readSelfPostIdentifierMapSnapshot()
            if (threadId !in currentMap) return
            val updatedMap = currentMap.toMutableMap()
            updatedMap.remove(threadId)
            persistSelfPostIdentifierMap(updatedMap)
        }
    }

    suspend fun clearSelfPostIdentifiers() {
        selfPostIdentifiersMutex.withLock {
            persistSelfPostIdentifierMap(emptyMap())
        }
    }

    /**
     * Insert or update a history entry while keeping the existing order intact.
     * This is used for incremental updates (e.g., refreshing metadata) without
     * requiring the caller to manage the whole list manually.
     */
    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) {
        historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: run {
                Logger.w(TAG, "Skipping history upsert due to missing snapshot")
                return
            }
            val existingIndex = currentHistory.indexOfFirst { it.threadId == entry.threadId }
            val updatedHistory = if (existingIndex >= 0) {
                currentHistory.toMutableList().also { it[existingIndex] = entry }
            } else {
                buildList {
                    addAll(currentHistory)
                    add(entry)
                }
            }

            try {
                persistHistory(updatedHistory)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to upsert history entry ${entry.threadId}", e)
                // Log error but don't crash
            }
        }
    }

    /**
     * Update multiple history entries in a single locked transaction.
     * This avoids races with concurrent history writes and prevents lost updates.
     */
    suspend fun updateHistoryEntries(updatedEntries: Map<String, ThreadHistoryEntry>) {
        if (updatedEntries.isEmpty()) return
        historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: run {
                Logger.w(TAG, "Skipping history update due to missing snapshot")
                return
            }
            val merged = currentHistory.map { entry ->
                updatedEntries[entry.threadId] ?: entry
            }
            try {
                persistHistory(merged)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update history entries (${updatedEntries.size})", e)
            }
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
        val scope = scrollDebounceScope
        if (scope == null) {
            updateHistoryScrollPositionImmediate(threadId, index, offset, boardId, title, titleImageUrl, boardName, boardUrl, replyCount)
            return
        }

        // FIX: メモリリーク対策 - Jobの確実なクリーンアップ
        // 先にジョブを作成し、即座にマップに登録することで競合を回避
        val newJob = scope.launch {
            delay(SCROLL_DEBOUNCE_DELAY_MS)

            try {
                updateHistoryScrollPositionImmediate(
                    threadId, index, offset, boardId, title,
                    titleImageUrl, boardName, boardUrl, replyCount
                )
            } finally {
                // FIX: 完了後に自動的にマップから削除（nullチェック追加）
                val currentJob = coroutineContext[Job]
                if (currentJob != null) {
                    scrollPositionJobs.removeIfSame(threadId, currentJob)
                } else {
                    // フォールバック: currentJobがnullの場合もクリーンアップ
                    Logger.w(TAG, "Job context is null for thread $threadId, forcing cleanup")
                    scrollPositionJobs.removeIfSame(threadId, null)
                }
            }
        }

        // 古いジョブをキャンセルして新しいジョブを登録
        val oldJob = scrollPositionJobs.putAndCancelOld(threadId, newJob)
        oldJob?.cancel()
    }

    /**
     * Immediate update of scroll position without debouncing.
     * Internal method used by the debounced public method.
     * FIX: デッドロック回避のため、historyMutexのみを使用する（ネストされたロックを避ける）
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
        // FIX: 単一のMutexで読み取りと書き込みを一括処理（デッドロック回避）
        historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: run {
                Logger.w(TAG, "Skipping scroll position update due to missing snapshot")
                return
            }

            val existingEntry = currentHistory.firstOrNull { it.threadId == threadId }

            // Skip update if scroll position hasn't changed to reduce unnecessary writes
            if (existingEntry != null &&
                existingEntry.lastReadItemIndex == index &&
                existingEntry.lastReadItemOffset == offset
            ) {
                return
            }

            val updatedHistory = when {
                existingEntry != null -> {
                    // Update existing entry's scroll position while preserving other fields
                    currentHistory.map { entry ->
                        if (entry.threadId == threadId) {
                            entry.copy(
                                lastReadItemIndex = index,
                                lastReadItemOffset = offset,
                                // Update visit time to reflect recent activity
                                lastVisitedEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
                            )
                        } else {
                            entry
                        }
                    }
                }

                else -> {
                    // Entry doesn't exist yet - create a new one
                    buildList {
                        add(
                            ThreadHistoryEntry(
                                threadId = threadId,
                                boardId = boardId,
                                title = title,
                                titleImageUrl = titleImageUrl,
                                boardName = boardName,
                                boardUrl = boardUrl,
                                lastVisitedEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                                replyCount = replyCount,
                                lastReadItemIndex = index,
                                lastReadItemOffset = offset
                            )
                        )
                        addAll(currentHistory)
                    }
                }
            }

            try {
                persistHistory(updatedHistory)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to update scroll position for thread $threadId", e)
                // Log error but don't crash
            }
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
            storage.seedIfEmpty(
                json.encodeToString(boardsSerializer, defaultBoards),
                json.encodeToString(historySerializer, defaultHistory),
                json.encodeToString(stringListSerializer, defaultNgHeaders),
                json.encodeToString(stringListSerializer, defaultNgWords),
                json.encodeToString(stringListSerializer, defaultCatalogNgWords),
                json.encodeToString(stringListSerializer, defaultWatchWords),
                json.encodeToString(selfPostIdentifierMapSerializer, defaultSelfPostIdentifierMap),
                json.encodeToString(encodeCatalogModeMap(defaultCatalogModeMap)),
                AttachmentPickerPreference.MEDIA.name,
                SaveDirectorySelection.MANUAL_INPUT.name,
                defaultLastUsedDeleteKey.take(8),
                json.encodeToString(threadMenuConfigSerializer, normalizeThreadMenuConfig(defaultThreadMenuConfig)),
                json.encodeToString(threadSettingsMenuConfigSerializer, normalizeThreadSettingsMenuConfig(defaultThreadSettingsMenuConfig)),
                json.encodeToString(threadMenuEntriesSerializer, normalizeThreadMenuEntries(defaultThreadMenuEntries)),
                json.encodeToString(catalogNavEntriesSerializer, normalizeCatalogNavEntries(defaultCatalogNavEntries))
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to seed default data", e)
            // Log error but don't crash - app will work with empty state
        }
    }

    private fun decodeBoards(raw: String): List<BoardSummary> = runCatching {
        json.decodeFromString(boardsSerializer, raw)
    }.getOrElse { e ->
        Logger.e(TAG, "Failed to decode boards from JSON", e)
        emptyList()
    }

    private suspend fun readHistorySnapshot(): List<ThreadHistoryEntry>? {
        return try {
            val raw = storage.historyJson.first()
            raw?.let { decodeHistory(it) } ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read history state", e)
            null
        }
    }

    /**
     * Read history snapshot while already holding the historyMutex lock.
     * This should only be called from within a historyMutex.withLock block.
     */
    private suspend fun readHistorySnapshotLocked(): List<ThreadHistoryEntry>? {
        return try {
            val raw = storage.historyJson.first()
            raw?.let { decodeHistory(it) } ?: emptyList()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read history state", e)
            null
        }
    }

    private suspend fun persistHistory(history: List<ThreadHistoryEntry>) {
        storage.updateHistoryJson(json.encodeToString(historySerializer, history))
    }

    private fun decodeHistory(raw: String): List<ThreadHistoryEntry> = runCatching {
        json.decodeFromString(historySerializer, raw)
    }.getOrElse { e ->
        Logger.e(TAG, "Failed to decode history from JSON", e)
        emptyList()
    }

    private fun decodeCatalogDisplayStyle(raw: String?): CatalogDisplayStyle {
        return raw?.let { value ->
            CatalogDisplayStyle.entries.firstOrNull { it.name == value }
        } ?: CatalogDisplayStyle.Grid
    }

    private fun decodeCatalogGridColumns(raw: String?): Int {
        val parsed = raw?.toIntOrNull() ?: DEFAULT_CATALOG_GRID_COLUMNS
        return parsed.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)
    }

    private fun decodeCatalogModeMap(raw: String?): Map<String, CatalogMode> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val decoded = json.decodeFromString(catalogModeMapSerializer, raw)
            decoded.mapNotNull { (boardId, modeName) ->
                val mode = CatalogMode.entries.firstOrNull { it.name == modeName }
                mode?.let { boardId to it }
            }.toMap()
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode catalog mode map", e)
            emptyMap()
        }
    }

    private fun encodeCatalogModeMap(map: Map<String, CatalogMode>): Map<String, String> {
        return map.mapValues { it.value.name }
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return runCatching {
            json.decodeFromString(stringListSerializer, raw)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode NG list", e)
            emptyList()
        }
    }

    private fun decodeSelfPostIdentifierMap(raw: String?): Map<String, List<String>> {
        if (raw == null) return emptyMap()
        return runCatching {
            json.decodeFromString(selfPostIdentifierMapSerializer, raw)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode self post identifiers map", e)
            emptyMap()
        }
    }

    private fun aggregateIdentifiers(map: Map<String, List<String>>): List<String> {
        val seenKeys = mutableSetOf<String>()
        val aggregated = mutableListOf<String>()
        map.values.forEach { identifiers ->
            identifiers.forEach { identifier ->
                val trimmed = identifier.trim()
                if (trimmed.isBlank()) return@forEach
                val key = trimmed.lowercase()
                if (key in seenKeys) return@forEach
                aggregated.add(trimmed)
                seenKeys.add(key)
                if (aggregated.size >= SELF_IDENTIFIER_MAX_ENTRIES) {
                    return aggregated
                }
            }
        }
        return aggregated
    }

    private fun decodeThreadMenuConfig(raw: String?): List<ThreadMenuItemConfig> {
        if (raw.isNullOrBlank()) return defaultThreadMenuConfig()
        return runCatching {
            json.decodeFromString(threadMenuConfigSerializer, raw)
        }.map { stored ->
            normalizeThreadMenuConfig(stored)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode thread menu config", e)
            defaultThreadMenuConfig()
        }
    }

    private fun decodeThreadSettingsMenuConfig(raw: String?): List<ThreadSettingsMenuItemConfig> {
        if (raw.isNullOrBlank()) return defaultThreadSettingsMenuConfig()
        return runCatching {
            json.decodeFromString(threadSettingsMenuConfigSerializer, raw)
        }.map { stored ->
            normalizeThreadSettingsMenuConfig(stored)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode thread settings menu config", e)
            defaultThreadSettingsMenuConfig()
        }
    }

    private fun decodeThreadMenuEntries(raw: String?): List<ThreadMenuEntryConfig> {
        if (raw.isNullOrBlank()) return defaultThreadMenuEntries()
        return runCatching {
            json.decodeFromString(threadMenuEntriesSerializer, raw)
        }.map { stored ->
            normalizeThreadMenuEntries(stored)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode thread menu entries", e)
            defaultThreadMenuEntries()
        }
    }

    private fun decodeCatalogNavEntries(raw: String?): List<CatalogNavEntryConfig> {
        if (raw.isNullOrBlank()) return defaultCatalogNavEntries()
        return runCatching {
            json.decodeFromString(catalogNavEntriesSerializer, raw)
        }.map { stored ->
            normalizeCatalogNavEntries(stored)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode catalog nav entries", e)
            defaultCatalogNavEntries()
        }
    }

    private suspend fun readSelfPostIdentifierMapSnapshot(): Map<String, List<String>> {
        return try {
            val raw = storage.selfPostIdentifiersJson.first()
            decodeSelfPostIdentifierMap(raw)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read self post identifier map", e)
            emptyMap()
        }
    }

    private suspend fun persistSelfPostIdentifierMap(map: Map<String, List<String>>) {
        storage.updateSelfPostIdentifiersJson(json.encodeToString(selfPostIdentifierMapSerializer, map))
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

private fun sanitizeManualSaveDirectory(input: String?): String {
    val trimmed = input?.trim().orEmpty()
    if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
    if (trimmed == MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
    val withoutCurrentDirPrefix = if (trimmed.startsWith("./")) {
        trimmed.removePrefix("./")
    } else {
        trimmed
    }
    return withoutCurrentDirPrefix.ifBlank { DEFAULT_MANUAL_SAVE_ROOT }
}

private fun decodeAttachmentPickerPreference(raw: String?): AttachmentPickerPreference {
    return runCatching {
        raw?.let { AttachmentPickerPreference.valueOf(it) }
    }.getOrNull() ?: AttachmentPickerPreference.MEDIA
}

private fun decodeSaveDirectorySelection(raw: String?): SaveDirectorySelection {
    return runCatching {
        raw?.let { SaveDirectorySelection.valueOf(it) }
    }.getOrNull() ?: SaveDirectorySelection.MANUAL_INPUT
}
