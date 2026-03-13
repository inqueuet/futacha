package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal class AppStateBoardsFacade(
    private val setBoardsImpl: suspend (List<BoardSummary>) -> Unit,
    private val updateBoardsImpl: suspend (((List<BoardSummary>) -> List<BoardSummary>)) -> Unit
) {
    suspend fun setBoards(boards: List<BoardSummary>) = setBoardsImpl(boards)

    suspend fun updateBoards(transform: (List<BoardSummary>) -> List<BoardSummary>) =
        updateBoardsImpl(transform)
}

internal fun buildAppStateBoardsFacade(
    setBoardsImpl: suspend (List<BoardSummary>) -> Unit,
    updateBoardsImpl: suspend (((List<BoardSummary>) -> List<BoardSummary>)) -> Unit
): AppStateBoardsFacade {
    return AppStateBoardsFacade(
        setBoardsImpl = setBoardsImpl,
        updateBoardsImpl = updateBoardsImpl
    )
}

internal fun buildAppStateBoardsFlow(
    storage: PlatformStateStorage,
    json: Json,
    tag: String
): Flow<List<BoardSummary>> {
    return storage.boardsJson
        .distinctUntilChanged()
        .map { stored ->
            if (stored == null) {
                emptyList()
            } else {
                decodeAppStateBoards(stored, json, tag)
            }
        }
}
