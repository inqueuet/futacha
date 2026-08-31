package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal class AppStateStorageMutationHandler(
    private val tag: String,
    private val lastStorageError: MutableStateFlow<StorageError?>,
    private val rethrowIfCancellation: (Throwable) -> Unit
) {
    suspend fun run(
        operation: String,
        failureMessage: () -> String,
        onFailure: suspend () -> Unit = {},
        rethrowOnFailure: Boolean = false,
        block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (error: Exception) {
            rethrowIfCancellation(error)
            onFailure()
            recordFailure(operation, failureMessage(), error)
            if (rethrowOnFailure) {
                throw error
            }
        }
    }

    private fun recordFailure(
        operation: String,
        failureMessage: String,
        error: Throwable
    ) {
        Logger.e(tag, failureMessage, error)
        lastStorageError.value = StorageError(
            operation = operation,
            message = error.message ?: "Unknown error",
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
    }
}

internal class AppStateBoardsCoordinator(
    private val storage: PlatformStateStorage,
    private val json: Json,
    private val boardsMutex: Mutex,
    private val runStorageMutation: suspend (
        operation: String,
        failureMessage: () -> String,
        onFailure: suspend () -> Unit,
        rethrowOnFailure: Boolean,
        block: suspend () -> Unit
    ) -> Unit,
    private val tag: String
) {
    suspend fun setBoards(boards: List<BoardSummary>) {
        val encoded = encodeAppStateBoards(boards, json)
        boardsMutex.withLock {
            runStorageMutation(
                "setBoards",
                { "Failed to save ${boards.size} boards" },
                {},
                false
            ) {
                storage.updateBoardsJson(encoded)
            }
        }
    }

    suspend fun updateBoards(transform: (List<BoardSummary>) -> List<BoardSummary>) {
        boardsMutex.withLock {
            val currentBoards = storage.boardsJson.first()?.let { stored ->
                decodeAppStateBoards(stored, json, tag)
            } ?: emptyList()
            val updatedBoards = transform(currentBoards)
            if (updatedBoards == currentBoards) {
                return
            }
            val encoded = encodeAppStateBoards(updatedBoards, json)
            runStorageMutation(
                "updateBoards",
                { "Failed to update ${updatedBoards.size} boards" },
                {},
                false
            ) {
                storage.updateBoardsJson(encoded)
            }
        }
    }
}
