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
import kotlinx.coroutines.flow.combine
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a storage error that occurred during DataStore operations
 */
data class StorageError(
    val operation: String,
    val message: String,
    val timestamp: Long
)

/**
 * FIX: アーキテクチャと依存関係について
 *
 * ## レイヤー構造：
 * UI層 → Store層 → Repository層 → Service層 → Network/Storage層
 *
 * ## 依存方向（循環依存の防止）：
 * - UI → AppStateStore (状態管理)
 * - AppStateStore → PlatformStateStorage (永続化)
 * - BoardRepository → CookieRepository (認証)
 * - Repository → Service → API/FileSystem
 *
 * ## 設計原則：
 * 1. 上位レイヤーは下位レイヤーに依存可能
 * 2. 下位レイヤーは上位レイヤーに依存禁止（循環依存防止）
 * 3. 同一レイヤー内の依存は最小限に
 * 4. インターフェースによる抽象化を優先
 *
 * ## 既知の大規模クラス：
 * - AppStateStore: 多くの責務を持つが、状態管理の中心として機能
 *   将来的な分割案: UserPreferencesStore, ThreadStateStore, BoardStateStore
 *
 * ## エラーハンドリングのベストプラクティス：
 *
 * ### 1. 例外の種類と処理
 * - **CancellationException**: 必ず再スローすること（正常なキャンセル）
 * - **NetworkException**: ユーザーに通知してリトライを提案
 * - **IllegalArgumentException**: 入力検証エラー、ユーザーに修正を促す
 * - **IOException**: ファイル/ネットワークエラー、リトライ可能
 *
 * ### 2. Result型の使用
 * - I/O操作は必ずResult<T>を返す（FileSystem、Repository層）
 * - .getOrThrow()、.getOrElse()、.onFailure()で明示的に処理
 * - Resultを無視すると、エラーが見逃され、データ破損の原因となる
 *
 * ### 3. ログ出力
 * - エラーログには必ず例外オブジェクトを含める
 * - ユーザー操作のエラーはw (Warning)レベル
 * - システムエラーはe (Error)レベル
 * - 個人情報・機密情報をログに出力しない
 *
 * ## パフォーマンスのベストプラクティス：
 *
 * ### 1. スレッド/Dispatcher使用
 * - UI更新: Dispatchers.Main（自動）
 * - CPU集約的処理: AppDispatchers.parsing または Dispatchers.Default
 * - I/O処理: AppDispatchers.io または Dispatchers.IO
 * - メインスレッドでの重い処理は厳禁
 *
 * ### 2. メモリ管理
 * - LRUキャッシュのサイズ制限を設定（20-100エントリ）
 * - 大量リストはLazyColumn/LazyRowを使用
 * - 画像は適切にキャッシュしてメモリ使用を制御
 *
 * ### 3. リソースリーク防止
 * - Flowのcollectは必ずLaunchedEffect/LaunchedEffectKeyで管理
 * - CoroutineScopeは必ずライフサイクルに紐付ける
 * - 一時ファイルは確実にクリーンアップ
 *
 * ## セキュリティのベストプラクティス：
 *
 * ### 1. 入力検証
 * - ユーザー入力は必ず検証（長さ、null文字、パストラバーサル）
 * - ファイルサイズは上限チェック（100MB推奨）
 * - ReDoS攻撃防止のため、正規表現に長さ制限を追加
 *
 * ### 2. データ保護
 * - パスワードは平文でログに出力しない
 * - 一時ファイルは削除前に確実にクリーンアップ
 * - アトミック書き込みでデータ破損を防止
 *
 * ### 3. 権限管理
 * - ファイルアクセスは必要最小限に
 * - Android 10+ではSAF（Storage Access Framework）を使用
 * - 権限喪失時は適切なエラーメッセージを表示
 */

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

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

@OptIn(ExperimentalTime::class)
class AppStateStore internal constructor(
    private val storage: PlatformStateStorage,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {
    private val boardsSerializer = ListSerializer(BoardSummary.serializer())
    private val historySerializer = ListSerializer(ThreadHistoryEntry.serializer())

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

    // Error propagation
    private val _lastStorageError = MutableStateFlow<StorageError?>(null)
    val lastStorageError: StateFlow<StorageError?> = _lastStorageError.asStateFlow()

    companion object {
        private const val TAG = "AppStateStore"
        private const val SCROLL_DEBOUNCE_DELAY_MS = 1_000L
        private const val SCROLL_OFFSET_WRITE_THRESHOLD_PX = 24
        private const val SCROLL_PERSIST_MIN_INTERVAL_MS = 2_000L
        private const val SCROLL_VISITED_UPDATE_INTERVAL_MS = 15_000L
        private const val SELF_IDENTIFIER_MAX_ENTRIES = 20
        private const val SELF_POST_KEY_DELIMITER = "::"
        private const val HISTORY_PERSIST_MAX_PASSES = 8
    }

    suspend fun setScrollDebounceScope(scope: CoroutineScope) {
        scrollPositionMutex.withLock {
            scrollDebounceScope = scope
        }
    }

    val boards: Flow<List<BoardSummary>> = storage.boardsJson
        .distinctUntilChanged()
        .map { stored ->
            if (stored == null) {
                emptyList()
            } else {
                withContext(AppDispatchers.parsing) {
                    decodeBoards(stored)
                }
            }
        }

    val history: Flow<List<ThreadHistoryEntry>> = storage.historyJson
        .distinctUntilChanged()
        .map { stored ->
            if (stored == null) {
                emptyList()
            } else {
                withContext(AppDispatchers.parsing) {
                    decodeHistory(stored)
                }
            }
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
        .distinctUntilChanged()

    /**
     * Manual save location as SaveLocation (supports paths, content URIs, and bookmarks).
     * Legacy string paths are automatically converted via SaveLocation.fromString().
     */
    val manualSaveLocation: Flow<SaveLocation> = manualSaveDirectory
        .map(SaveLocation::fromString)
        .distinctUntilChanged()

    val attachmentPickerPreference: Flow<AttachmentPickerPreference> = storage.attachmentPickerPreference
        .map { raw -> decodeAttachmentPickerPreference(raw) }
        .distinctUntilChanged()
    val saveDirectorySelection: Flow<SaveDirectorySelection> = storage.saveDirectorySelection
        .map { raw -> decodeSaveDirectorySelection(raw) }
        .distinctUntilChanged()
    val lastUsedDeleteKey: Flow<String> = storage.lastUsedDeleteKey
        .map { raw -> raw?.take(8).orEmpty() }
        .distinctUntilChanged()

    val catalogModes: Flow<Map<String, CatalogMode>> = storage.catalogModeMapJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeCatalogModeMap(raw)
            }
        }
    val catalogDisplayStyle: Flow<CatalogDisplayStyle> = storage.catalogDisplayStyle
        .map { raw ->
            decodeCatalogDisplayStyle(raw)
        }
        .distinctUntilChanged()
    val catalogGridColumns: Flow<Int> = storage.catalogGridColumns
        .map { raw ->
            decodeCatalogGridColumns(raw)
        }
        .distinctUntilChanged()
    val ngHeaders: Flow<List<String>> = storage.ngHeadersJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeStringList(raw)
            }
        }
    val ngWords: Flow<List<String>> = storage.ngWordsJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeStringList(raw)
            }
        }
    val catalogNgWords: Flow<List<String>> = storage.catalogNgWordsJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeStringList(raw)
            }
        }
    val watchWords: Flow<List<String>> = storage.watchWordsJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeStringList(raw)
            }
        }
    private val selfPostIdentifierMapFlow: Flow<Map<String, List<String>>> = storage.selfPostIdentifiersJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeSelfPostIdentifierMap(raw)
            }
        }
    val selfPostIdentifiersByThread: Flow<Map<String, List<String>>> = selfPostIdentifierMapFlow
    val selfPostIdentifiers: Flow<List<String>> = selfPostIdentifierMapFlow
        .map { decoded -> aggregateIdentifiers(decoded) }
        .distinctUntilChanged()
    private val preferredFileManagerFlow: Flow<PreferredFileManager?> =
        storage.preferredFileManagerPackage.combine(storage.preferredFileManagerLabel) { pkg, label ->
            if (pkg.isBlank()) {
                null
            } else {
                PreferredFileManager(pkg, label)
            }
        }.distinctUntilChanged()
    val threadMenuConfig: Flow<List<ThreadMenuItemConfig>> = storage.threadMenuConfigJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeThreadMenuConfig(raw)
            }
        }
    val threadSettingsMenuConfig: Flow<List<ThreadSettingsMenuItemConfig>> = storage.threadSettingsMenuConfigJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeThreadSettingsMenuConfig(raw)
            }
        }
    val threadMenuEntries: Flow<List<ThreadMenuEntryConfig>> = storage.threadMenuEntriesConfigJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeThreadMenuEntries(raw)
            }
        }
    val catalogNavEntries: Flow<List<CatalogNavEntryConfig>> = storage.catalogNavEntriesConfigJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeCatalogNavEntries(raw)
            }
        }

    suspend fun setBoards(boards: List<BoardSummary>) {
        val encoded = encodeBoards(boards)
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
        try {
            storage.updateBackgroundRefreshEnabled(enabled)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save background refresh state: $enabled", e)
            // Log error but don't crash
        }
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        val sanitized = deleteKey.trim().take(8)
        try {
            storage.updateLastUsedDeleteKey(sanitized)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save last used delete key", e)
        }
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        try {
            storage.updateLightweightModeEnabled(enabled)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save lightweight mode state: $enabled", e)
        }
    }

    suspend fun setManualSaveDirectory(directory: String) {
        val sanitized = sanitizeManualSaveDirectory(directory)
        try {
            storage.updateManualSaveDirectory(sanitized)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
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
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save manual save location: $rawString", e)
        }
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        try {
            storage.updateAttachmentPickerPreference(preference.name)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save attachment picker preference: $preference", e)
        }
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        try {
            storage.updateSaveDirectorySelection(selection.name)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save save directory selection: $selection", e)
        }
    }

    suspend fun setPreferredFileManager(packageName: String?, label: String?) {
        try {
            storage.updatePreferredFileManager(packageName ?: "", label ?: "")
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save preferred file manager: $packageName", e)
            throw e
        }
    }

    // Flowインスタンスを固定化して、UI再コンポーズ時の再生成を防ぐ
    fun getPreferredFileManager(): Flow<PreferredFileManager?> = preferredFileManagerFlow

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) {
        try {
            storage.updatePrivacyFilterEnabled(enabled)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save privacy filter state: $enabled", e)
            // Log error but don't crash
        }
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        try {
            storage.updateCatalogDisplayStyle(style.name)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save catalog display style: ${style.name}", e)
        }
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)
        try {
            storage.updateCatalogGridColumns(clamped.toString())
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save catalog grid columns: $clamped", e)
        }
    }

    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) {
        val normalizedBoardId = boardId.trim()
        if (normalizedBoardId.isBlank()) {
            Logger.w(TAG, "Ignoring catalog mode update with blank boardId")
            return
        }
        val loadedSnapshot = runCatching {
            val currentRaw = storage.catalogModeMapJson.first()
            withContext(AppDispatchers.parsing) {
                decodeCatalogModeMap(currentRaw)
            }
        }.getOrElse { error ->
            rethrowIfCancellation(error)
            Logger.e(TAG, "Failed to read catalog mode map snapshot", error)
            emptyMap()
        }

        catalogModeMutex.withLock {
            val current = cachedCatalogModeMap ?: loadedSnapshot
            if (current[normalizedBoardId] == mode) {
                return@withLock
            }
            val updated = current + (normalizedBoardId to mode)
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(catalogModeMapSerializer, encodeCatalogModeMap(updated))
            }
            try {
                storage.updateCatalogModeMapJson(encoded)
                cachedCatalogModeMap = updated
            } catch (e: Exception) {
                cachedCatalogModeMap = current
                rethrowIfCancellation(e)
                Logger.e(TAG, "Failed to save catalog mode for $normalizedBoardId: ${mode.name}", e)
            }
        }
    }

    suspend fun setNgHeaders(headers: List<String>) {
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(stringListSerializer, headers)
            }
            storage.updateNgHeadersJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save NG headers (${headers.size})", e)
        }
    }

    suspend fun setNgWords(words: List<String>) {
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(stringListSerializer, words)
            }
            storage.updateNgWordsJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save NG words (${words.size})", e)
        }
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(stringListSerializer, words)
            }
            storage.updateCatalogNgWordsJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save catalog NG words (${words.size})", e)
        }
    }

    suspend fun setWatchWords(words: List<String>) {
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(stringListSerializer, words)
            }
            storage.updateWatchWordsJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save watch words (${words.size})", e)
        }
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        val normalized = normalizeThreadMenuConfig(config)
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(threadMenuConfigSerializer, normalized)
            }
            storage.updateThreadMenuConfigJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save thread menu config (${normalized.size} items)", e)
        }
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        val normalized = normalizeThreadSettingsMenuConfig(config)
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(threadSettingsMenuConfigSerializer, normalized)
            }
            storage.updateThreadSettingsMenuConfigJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save thread settings menu config (${normalized.size} items)", e)
        }
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        val normalized = normalizeThreadMenuEntries(config)
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(threadMenuEntriesSerializer, normalized)
            }
            storage.updateThreadMenuEntriesConfigJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save thread menu entries (${normalized.size} items)", e)
        }
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        val normalized = normalizeCatalogNavEntries(config)
        try {
            val encoded = withContext(AppDispatchers.parsing) {
                json.encodeToString(catalogNavEntriesSerializer, normalized)
            }
            storage.updateCatalogNavEntriesConfigJson(encoded)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to save catalog nav entries (${normalized.size} items)", e)
        }
    }

    suspend fun addSelfPostIdentifier(threadId: String, identifier: String, boardId: String? = null) {
        val trimmed = identifier.trim().takeIf { it.isNotBlank() } ?: return
        val loadedSnapshot = readSelfPostIdentifierMapSnapshot()
        selfPostIdentifiersMutex.withLock {
            val currentMap = cachedSelfPostIdentifierMap ?: loadedSnapshot
            val scopedKey = buildSelfPostKey(threadId, boardId)
            val legacyKey = threadId.trim()
            val existingForThread = buildList {
                addAll(currentMap[scopedKey].orEmpty())
                if (scopedKey != legacyKey) {
                    addAll(currentMap[legacyKey].orEmpty())
                }
            }
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
            val nextMap = currentMap.toMutableMap()
            nextMap[scopedKey] = updatedThreadList
            val updatedMap = nextMap.toMap()
            try {
                persistSelfPostIdentifierMap(updatedMap)
                cachedSelfPostIdentifierMap = updatedMap
            } catch (e: Exception) {
                cachedSelfPostIdentifierMap = currentMap
                rethrowIfCancellation(e)
                throw e
            }
        }
    }

    suspend fun removeSelfPostIdentifiersForThread(threadId: String, boardId: String? = null) {
        val loadedSnapshot = readSelfPostIdentifierMapSnapshot()
        selfPostIdentifiersMutex.withLock {
            val currentMap = cachedSelfPostIdentifierMap ?: loadedSnapshot
            val scopedKey = buildSelfPostKey(threadId, boardId)
            val mutable = currentMap.toMutableMap()
            val removedScoped = mutable.remove(scopedKey) != null
            if (boardId.isNullOrBlank()) {
                mutable.remove(threadId.trim())
                val scopedSuffix = "$SELF_POST_KEY_DELIMITER${threadId.trim()}"
                mutable.keys
                    .filter { it.endsWith(scopedSuffix) }
                    .forEach { key -> mutable.remove(key) }
            } else if (!removedScoped) {
                return@withLock
            }
            val updatedMap = mutable.toMap()
            try {
                persistSelfPostIdentifierMap(updatedMap)
                cachedSelfPostIdentifierMap = updatedMap
            } catch (e: Exception) {
                cachedSelfPostIdentifierMap = currentMap
                rethrowIfCancellation(e)
                throw e
            }
        }
    }

    suspend fun clearSelfPostIdentifiers() {
        val loadedSnapshot = readSelfPostIdentifierMapSnapshot()
        selfPostIdentifiersMutex.withLock {
            val currentMap = cachedSelfPostIdentifierMap ?: loadedSnapshot
            try {
                persistSelfPostIdentifierMap(emptyMap())
                cachedSelfPostIdentifierMap = emptyMap()
            } catch (e: Exception) {
                cachedSelfPostIdentifierMap = currentMap
                rethrowIfCancellation(e)
                throw e
            }
        }
    }

    /**
     * Insert or update a history entry while keeping the existing order intact.
     * This is used for incremental updates (e.g., refreshing metadata) without
     * requiring the caller to manage the whole list manually.
     */
    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history upsert due to missing snapshot")
            return
        }
        val (revision, updatedHistory, previousRevision, previousHistory) = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val existingIndex = currentHistory.indexOfFirst {
                matchesHistoryEntry(it, entry.threadId, entry.boardId, entry.boardUrl)
            }
            val updatedHistory = if (existingIndex >= 0) {
                currentHistory.toMutableList().also { it[existingIndex] = entry }
            } else {
                buildList {
                    addAll(currentHistory)
                    add(entry)
                }
            }
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = updatedHistory
            historyRevision = beforeRevision + 1L
            Quadruple(historyRevision, updatedHistory, beforeRevision, beforeHistory)
        }

        try {
            persistHistory(revision, updatedHistory)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to upsert history entry ${entry.threadId}", e)
            throw e
        }
    }

    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history prepend due to missing snapshot")
            return
        }
        val updateResult = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val targetKey = historyIdentity(entry)
            if (targetKey.isBlank()) {
                Logger.w(TAG, "Skipping history prepend due to invalid identity")
                return@withLock null
            }
            val updatedHistory = buildList {
                add(entry)
                addAll(
                    currentHistory.filterNot {
                        historyIdentity(it) == targetKey
                    }
                )
            }
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = updatedHistory
            historyRevision = beforeRevision + 1L
            Quadruple(historyRevision, updatedHistory, beforeRevision, beforeHistory)
        } ?: return
        val (revision, updatedHistory, previousRevision, previousHistory) = updateResult
        try {
            persistHistory(revision, updatedHistory)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to prepend history entry ${entry.threadId}", e)
            throw e
        }
    }

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history prepend batch due to missing snapshot")
            return
        }
        val updateResult = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val dedupedByKey = linkedMapOf<String, ThreadHistoryEntry>()
            entries.forEach { candidate ->
                val key = historyIdentity(candidate)
                if (key.isNotBlank() && key !in dedupedByKey) {
                    dedupedByKey[key] = candidate
                }
            }
            val dedupedEntries = dedupedByKey.values.toList()
            if (dedupedEntries.isEmpty()) return@withLock null
            val dedupedKeys = dedupedByKey.keys
            val updatedHistory = buildList {
                addAll(dedupedEntries)
                addAll(
                    currentHistory.filterNot { existing ->
                        historyIdentity(existing) in dedupedKeys
                    }
                )
            }
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = updatedHistory
            historyRevision = beforeRevision + 1L
            Quintuple(historyRevision, updatedHistory, dedupedEntries.size, beforeRevision, beforeHistory)
        } ?: return
        val (revision, updatedHistory, dedupedSize, previousRevision, previousHistory) = updateResult
        try {
            persistHistory(revision, updatedHistory)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to prepend ${dedupedSize} history entries", e)
            throw e
        }
    }

    suspend fun mergeHistoryEntries(entries: Collection<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history merge due to missing snapshot")
            return
        }
        val mergeResult = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val updatesByKey = linkedMapOf<String, ThreadHistoryEntry>()
            entries.forEach { candidate ->
                val key = historyIdentity(candidate)
                if (key.isNotBlank()) {
                    updatesByKey[key] = candidate
                }
            }
            if (updatesByKey.isEmpty()) return@withLock null

            var changed = false
            val remainingUpdates = updatesByKey.toMutableMap()
            val merged = currentHistory.map { existing ->
                val key = historyIdentity(existing)
                val replacement = remainingUpdates.remove(key)
                if (replacement != null) {
                    val mergedEntry = mergeHistoryEntry(existing, replacement)
                    if (mergedEntry != existing) changed = true
                    mergedEntry
                } else {
                    existing
                }
            }

            val appended = remainingUpdates.values.toList()
            if (!changed) return@withLock null
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = merged
            historyRevision = beforeRevision + 1L
            Quintuple(historyRevision, merged, appended.size, beforeRevision, beforeHistory)
        } ?: return
        val (revision, merged, appendedSize, previousRevision, previousHistory) = mergeResult
        try {
            // Drop stale updates that no longer exist in current history.
            // This avoids resurrecting entries removed while refresh was running.
            persistHistory(revision, merged)
            if (appendedSize > 0) {
                Logger.i(TAG, "Dropped $appendedSize stale history update(s) during merge")
            }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to merge ${entries.size} history entries", e)
            throw e
        }
    }

    private fun mergeHistoryEntry(
        existing: ThreadHistoryEntry,
        incoming: ThreadHistoryEntry
    ): ThreadHistoryEntry {
        val keepExistingReadState = existing.lastVisitedEpochMillis >= incoming.lastVisitedEpochMillis
        val mergedLastVisited = maxOf(existing.lastVisitedEpochMillis, incoming.lastVisitedEpochMillis)
        val mergedReplyCount = maxOf(existing.replyCount, incoming.replyCount)

        return incoming.copy(
            boardId = incoming.boardId.ifBlank { existing.boardId },
            title = incoming.title.ifBlank { existing.title },
            titleImageUrl = incoming.titleImageUrl.ifBlank { existing.titleImageUrl },
            boardName = incoming.boardName.ifBlank { existing.boardName },
            boardUrl = incoming.boardUrl.ifBlank { existing.boardUrl },
            lastVisitedEpochMillis = mergedLastVisited,
            replyCount = mergedReplyCount,
            lastReadItemIndex = if (keepExistingReadState) {
                existing.lastReadItemIndex
            } else {
                incoming.lastReadItemIndex
            },
            lastReadItemOffset = if (keepExistingReadState) {
                existing.lastReadItemOffset
            } else {
                incoming.lastReadItemOffset
            },
            hasAutoSave = existing.hasAutoSave || incoming.hasAutoSave
        )
    }

    suspend fun removeHistoryEntry(entry: ThreadHistoryEntry) {
        val historySnapshot = readHistorySnapshot() ?: run {
            Logger.w(TAG, "Skipping history removal due to missing snapshot")
            return
        }
        val removeResult = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val targetKey = historyIdentity(entry)
            if (targetKey.isBlank()) {
                Logger.w(TAG, "Skipping history removal due to invalid identity")
                return@withLock null
            }
            val updatedHistory = currentHistory.filterNot {
                historyIdentity(it) == targetKey
            }
            if (updatedHistory.size == currentHistory.size) {
                return@withLock null
            }
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = updatedHistory
            historyRevision = beforeRevision + 1L
            Quadruple(historyRevision, updatedHistory, beforeRevision, beforeHistory)
        } ?: return
        val (revision, updatedHistory, previousRevision, previousHistory) = removeResult
        try {
            persistHistory(revision, updatedHistory)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to remove history entry ${entry.threadId}", e)
            throw e
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
        var runImmediate = false
        var oldJob: Job? = null
        scrollPositionMutex.withLock {
            val scope = scrollDebounceScope
            val scopeJob = scope?.coroutineContext?.get(Job)
            val isScopeInactive = scopeJob != null && !scopeJob.isActive
            if (scope == null || isScopeInactive) {
                if (isScopeInactive) {
                    scrollDebounceScope = null
                }
                runImmediate = true
                return@withLock
            }

            val scrollKey = buildScrollJobKey(threadId, boardId, boardUrl)
            val newJob = scope.launch {
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
            if (!newJob.isActive) {
                runImmediate = true
                return@withLock
            }

            oldJob = scrollPositionJobs.putAndCancelOld(scrollKey, newJob)
        }
        oldJob?.cancel()
        if (runImmediate) {
            updateHistoryScrollPositionImmediate(
                threadId, index, offset, boardId, title,
                titleImageUrl, boardName, boardUrl, replyCount
            )
        }
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
        val updateResult = historyMutex.withLock {
            val currentHistory = readHistorySnapshotLocked() ?: historySnapshot
            val existingEntry = currentHistory.firstOrNull {
                matchesHistoryEntry(it, threadId, boardId, boardUrl)
            }
            val nowMillis = Clock.System.now().toEpochMilliseconds()

            // Skip tiny offset movements in the same item to avoid excessive JSON writes.
            if (existingEntry != null &&
                existingEntry.lastReadItemIndex == index &&
                abs(existingEntry.lastReadItemOffset - offset) < SCROLL_OFFSET_WRITE_THRESHOLD_PX
            ) {
                return@withLock null
            }
            if (existingEntry != null) {
                val indexDelta = abs(existingEntry.lastReadItemIndex - index)
                val offsetDelta = abs(existingEntry.lastReadItemOffset - offset)
                val isFrequentNearbyUpdate =
                    nowMillis - existingEntry.lastVisitedEpochMillis < SCROLL_PERSIST_MIN_INTERVAL_MS &&
                        indexDelta <= 2 &&
                        offsetDelta < (SCROLL_OFFSET_WRITE_THRESHOLD_PX * 8)
                if (isFrequentNearbyUpdate) {
                    return@withLock null
                }
            }

            val updatedHistory = if (existingEntry != null) {
                // Update existing entry's scroll position while preserving other fields
                currentHistory.map { entry ->
                    if (matchesHistoryEntry(entry, threadId, boardId, boardUrl)) {
                        entry.copy(
                            lastReadItemIndex = index,
                            lastReadItemOffset = offset,
                            lastVisitedEpochMillis = if (
                                entry.lastReadItemIndex != index ||
                                abs(entry.lastReadItemOffset - offset) >= SCROLL_OFFSET_WRITE_THRESHOLD_PX ||
                                nowMillis - entry.lastVisitedEpochMillis >= SCROLL_VISITED_UPDATE_INTERVAL_MS
                            ) {
                                nowMillis
                            } else {
                                entry.lastVisitedEpochMillis
                            }
                        )
                    } else {
                        entry
                    }
                }
            } else {
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
                            lastVisitedEpochMillis = nowMillis,
                            replyCount = replyCount,
                            lastReadItemIndex = index,
                            lastReadItemOffset = offset
                        )
                    )
                    addAll(currentHistory)
                }
            }
            val beforeRevision = historyRevision
            val beforeHistory = cachedHistory
            cachedHistory = updatedHistory
            historyRevision = beforeRevision + 1L
            Quadruple(historyRevision, updatedHistory, beforeRevision, beforeHistory)
        } ?: return
        val (revision, updatedHistory, previousRevision, previousHistory) = updateResult
        try {
            persistHistory(revision, updatedHistory)
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            rollbackHistoryMutation(revision, previousRevision, previousHistory)
            Logger.e(TAG, "Failed to persist updated history for thread $threadId", e)
            throw e
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
            rethrowIfCancellation(e)
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
        val cached = historyMutex.withLock { cachedHistory }
        if (cached != null) {
            return cached
        }
        val decoded = try {
            val raw = storage.historyJson.first()
            if (raw == null) {
                emptyList()
            } else {
                withContext(AppDispatchers.parsing) {
                    decodeHistory(raw)
                }
            }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to read history state", e)
            return null
        }
        return historyMutex.withLock {
            val latest = cachedHistory
            if (latest != null) {
                latest
            } else {
                cachedHistory = decoded
                decoded
            }
        }
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
        historyMutex.withLock {
            if (historyRevision == failedRevision) {
                historyRevision = previousRevision
                cachedHistory = previousHistory
            }
        }
    }

    private suspend fun persistHistory(revision: Long, history: List<ThreadHistoryEntry>) {
        historyPersistMutex.withLock {
            var targetRevision = revision
            var targetHistory = history
            var passCount = 0
            while (true) {
                passCount += 1
                if (passCount > HISTORY_PERSIST_MAX_PASSES) {
                    throw IllegalStateException(
                        "History persistence exceeded $HISTORY_PERSIST_MAX_PASSES passes; aborting to prevent lock starvation"
                    )
                }
                storage.updateHistoryJson(encodeHistory(targetHistory))
                val nextHistory = historyMutex.withLock {
                    if (historyRevision > targetRevision) {
                        val latest = cachedHistory
                        if (latest != null) {
                            targetRevision = historyRevision
                            latest
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                if (nextHistory == null) {
                    break
                }
                targetHistory = nextHistory
            }
        }
    }

    private suspend fun encodeBoards(boards: List<BoardSummary>): String {
        return withContext(AppDispatchers.parsing) {
            json.encodeToString(boardsSerializer, boards)
        }
    }

    private suspend fun encodeHistory(history: List<ThreadHistoryEntry>): String {
        return withContext(AppDispatchers.parsing) {
            json.encodeToString(historySerializer, history)
        }
    }

    private fun decodeHistory(raw: String): List<ThreadHistoryEntry> = runCatching {
        json.decodeFromString(historySerializer, raw)
    }.getOrElse { e ->
        Logger.e(TAG, "Failed to decode history from JSON", e)
        emptyList()
    }

    private fun matchesHistoryEntry(
        entry: ThreadHistoryEntry,
        threadId: String,
        boardId: String,
        boardUrl: String
    ): Boolean {
        if (entry.threadId != threadId) return false
        val normalizedBoardId = boardId.trim()
        val entryBoardId = entry.boardId.trim()
        if (normalizedBoardId.isNotBlank() && entryBoardId.isNotBlank()) {
            return entryBoardId == normalizedBoardId
        }
        val normalizedBoardUrl = normalizeHistoryBoardUrlForIdentity(boardUrl)
        val entryBoardUrl = normalizeHistoryBoardUrlForIdentity(entry.boardUrl)
        if (normalizedBoardUrl.isNotBlank() && entryBoardUrl.isNotBlank()) {
            return entryBoardUrl == normalizedBoardUrl
        }
        return normalizedBoardId.isBlank() && entryBoardId.isBlank() &&
            normalizedBoardUrl.isBlank() && entryBoardUrl.isBlank()
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
        val cached = selfPostIdentifiersMutex.withLock { cachedSelfPostIdentifierMap }
        if (cached != null) {
            return cached
        }
        return try {
            val raw = storage.selfPostIdentifiersJson.first()
            val decoded = withContext(AppDispatchers.parsing) {
                decodeSelfPostIdentifierMap(raw)
            }
            selfPostIdentifiersMutex.withLock {
                val latest = cachedSelfPostIdentifierMap
                if (latest != null) {
                    latest
                } else {
                    cachedSelfPostIdentifierMap = decoded
                    decoded
                }
            }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e(TAG, "Failed to read self post identifier map", e)
            emptyMap()
        }
    }

    private suspend fun persistSelfPostIdentifierMap(map: Map<String, List<String>>) {
        val encoded = withContext(AppDispatchers.parsing) {
            json.encodeToString(selfPostIdentifierMapSerializer, map)
        }
        storage.updateSelfPostIdentifiersJson(encoded)
    }

    private fun buildSelfPostKey(threadId: String, boardId: String?): String {
        val cleanThreadId = threadId.trim()
        val cleanBoardId = boardId?.trim().orEmpty()
        return if (cleanBoardId.isBlank()) {
            cleanThreadId
        } else {
            "$cleanBoardId$SELF_POST_KEY_DELIMITER$cleanThreadId"
        }
    }

    private fun rethrowIfCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }

    private fun historyIdentity(entry: ThreadHistoryEntry): String {
        return historyIdentity(
            threadId = entry.threadId,
            boardId = entry.boardId,
            boardUrl = entry.boardUrl
        )
    }

    private fun historyIdentity(threadId: String, boardId: String, boardUrl: String): String {
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isBlank()) return ""
        val normalizedBoardId = boardId.trim()
        if (normalizedBoardId.isNotBlank()) {
            return "$normalizedBoardId$SELF_POST_KEY_DELIMITER$normalizedThreadId"
        }
        val normalizedBoardUrl = normalizeHistoryBoardUrlForIdentity(boardUrl)
        if (normalizedBoardUrl.isNotBlank()) {
            return "$normalizedBoardUrl$SELF_POST_KEY_DELIMITER$normalizedThreadId"
        }
        return normalizedThreadId
    }

    private fun normalizeHistoryBoardUrlForIdentity(boardUrl: String): String {
        val normalized = boardUrl
            .trim()
            .substringBefore('?')
            .trimEnd('/')
            .lowercase()
        if (normalized.isBlank()) return ""
        return normalized.substringBefore("/res/")
    }

    private fun buildScrollJobKey(threadId: String, boardId: String, boardUrl: String): String {
        val normalizedBoardId = boardId.trim()
        if (normalizedBoardId.isNotBlank()) {
            return "$normalizedBoardId$SELF_POST_KEY_DELIMITER${threadId.trim()}"
        }
        val normalizedBoardUrl = boardUrl.trimEnd('/')
        return if (normalizedBoardUrl.isNotBlank()) {
            "$normalizedBoardUrl$SELF_POST_KEY_DELIMITER${threadId.trim()}"
        } else {
            threadId.trim()
        }
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
