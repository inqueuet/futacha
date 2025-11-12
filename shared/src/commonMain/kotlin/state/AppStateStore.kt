package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

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
    private val stringListSerializer = ListSerializer(String.serializer())

    // Debouncing for scroll position updates
    private val scrollPositionJobs = mutableMapOf<String, Job>()
    private val scrollPositionMutex = Mutex()
    private var scrollDebounceScope: CoroutineScope? = null

    companion object {
        private const val TAG = "AppStateStore"
        private const val SCROLL_DEBOUNCE_DELAY_MS = 500L // Debounce scroll updates by 500ms
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

    val catalogDisplayStyle: Flow<CatalogDisplayStyle> = storage.catalogDisplayStyle.map { raw ->
        decodeCatalogDisplayStyle(raw)
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
            // Fallback to immediate update if scope not set
            updateHistoryScrollPositionImmediate(threadId, index, offset, boardId, title, titleImageUrl, boardName, boardUrl, replyCount)
            return
        }

        // FIX: Create job OUTSIDE of mutex to prevent deadlock
        // Cancel and clean up inside mutex, but launch outside
        val previousJob = scrollPositionMutex.withLock {
            val oldJob = scrollPositionJobs[threadId]

            // Clean up completed or cancelled jobs to prevent memory leak
            // Use iterator to safely remove during iteration
            val iterator = scrollPositionJobs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!entry.value.isActive) {
                    iterator.remove()
                }
            }

            oldJob
        }

        // Cancel previous job OUTSIDE of mutex
        previousJob?.cancel()

        // Launch new job OUTSIDE of mutex to prevent nested lock
        val newJob = scope.launch {
            try {
                delay(SCROLL_DEBOUNCE_DELAY_MS)
                updateHistoryScrollPositionImmediate(threadId, index, offset, boardId, title, titleImageUrl, boardName, boardUrl, replyCount)
            } finally {
                // Clean up after completion - no nested mutex here
                scrollPositionMutex.withLock {
                    // Only remove if this job is still the current one
                    if (scrollPositionJobs[threadId] == coroutineContext[Job]) {
                        scrollPositionJobs.remove(threadId)
                    }
                }
            }
        }

        // Store new job in map
        scrollPositionMutex.withLock {
            scrollPositionJobs[threadId] = newJob
        }
    }

    /**
     * Immediate update of scroll position without debouncing.
     * Internal method used by the debounced public method.
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
        defaultCatalogNgWords: List<String> = emptyList()
    ) {
        try {
            storage.seedIfEmpty(
                json.encodeToString(boardsSerializer, defaultBoards),
                json.encodeToString(historySerializer, defaultHistory),
                json.encodeToString(stringListSerializer, defaultNgHeaders),
                json.encodeToString(stringListSerializer, defaultNgWords),
                json.encodeToString(stringListSerializer, defaultCatalogNgWords)
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

    private fun decodeStringList(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return runCatching {
            json.decodeFromString(stringListSerializer, raw)
        }.getOrElse { e ->
            Logger.e(TAG, "Failed to decode NG list", e)
            emptyList()
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
    val catalogDisplayStyle: Flow<String?>
    val ngHeadersJson: Flow<String?>
    val ngWordsJson: Flow<String?>
    val catalogNgWordsJson: Flow<String?>

    suspend fun updateBoardsJson(value: String)
    suspend fun updateHistoryJson(value: String)
    suspend fun updatePrivacyFilterEnabled(enabled: Boolean)
    suspend fun updateCatalogDisplayStyle(style: String)
    suspend fun updateNgHeadersJson(value: String)
    suspend fun updateNgWordsJson(value: String)
    suspend fun updateCatalogNgWordsJson(value: String)
    suspend fun seedIfEmpty(
        defaultBoardsJson: String,
        defaultHistoryJson: String,
        defaultNgHeadersJson: String?,
        defaultNgWordsJson: String?,
        defaultCatalogNgWordsJson: String?
    )
}

internal expect fun createPlatformStateStorage(platformContext: Any? = null): PlatformStateStorage
