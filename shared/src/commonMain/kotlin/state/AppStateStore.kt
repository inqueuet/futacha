package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
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

    val boards: Flow<List<BoardSummary>> = storage.boardsJson.map { stored ->
        stored?.let { decodeBoards(it) } ?: emptyList()
    }

    val history: Flow<List<ThreadHistoryEntry>> = storage.historyJson.map { stored ->
        stored?.let { decodeHistory(it) } ?: emptyList()
    }

    suspend fun setBoards(boards: List<BoardSummary>) {
        boardsMutex.withLock {
            storage.updateBoardsJson(json.encodeToString(boardsSerializer, boards))
        }
    }

    suspend fun setHistory(history: List<ThreadHistoryEntry>) {
        historyMutex.withLock {
            storage.updateHistoryJson(json.encodeToString(historySerializer, history))
        }
    }

    /**
     * Thread-safe update of scroll position in history.
     * This prevents race conditions when multiple scroll updates occur concurrently.
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
        historyMutex.withLock {
            // Read current state within the lock to prevent race conditions
            val currentHistoryJson = storage.historyJson.first()
            val currentHistory = currentHistoryJson?.let { decodeHistory(it) } ?: emptyList()

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

            storage.updateHistoryJson(json.encodeToString(historySerializer, updatedHistory))
        }
    }

    suspend fun seedIfEmpty(
        defaultBoards: List<BoardSummary>,
        defaultHistory: List<ThreadHistoryEntry>
    ) {
        storage.seedIfEmpty(
            json.encodeToString(boardsSerializer, defaultBoards),
            json.encodeToString(historySerializer, defaultHistory)
        )
    }
    private fun decodeBoards(raw: String): List<BoardSummary> = runCatching {
        json.decodeFromString(boardsSerializer, raw)
    }.getOrElse { e ->
        println("AppStateStore: Failed to decode boards: ${e.message}")
        emptyList()
    }

    private fun decodeHistory(raw: String): List<ThreadHistoryEntry> = runCatching {
        json.decodeFromString(historySerializer, raw)
    }.getOrElse { e ->
        println("AppStateStore: Failed to decode history: ${e.message}")
        emptyList()
    }
}

fun createAppStateStore(platformContext: Any? = null): AppStateStore {
    return AppStateStore(createPlatformStateStorage(platformContext))
}

internal interface PlatformStateStorage {
    val boardsJson: Flow<String?>
    val historyJson: Flow<String?>

    suspend fun updateBoardsJson(value: String)
    suspend fun updateHistoryJson(value: String)
    suspend fun seedIfEmpty(defaultBoardsJson: String, defaultHistoryJson: String)
}

internal expect fun createPlatformStateStorage(platformContext: Any? = null): PlatformStateStorage
