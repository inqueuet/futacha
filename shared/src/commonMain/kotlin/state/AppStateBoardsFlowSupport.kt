package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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
