package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
