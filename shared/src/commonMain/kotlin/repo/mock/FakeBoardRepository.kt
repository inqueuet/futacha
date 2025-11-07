package com.valoser.futacha.shared.repo.mock

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository

/**
 * Lightweight mock that surfaces deterministic catalog / thread data derived from `/example`.
 *
 * @param onAccess optional suspend hook to simulate latency or track calls.
 */
class FakeBoardRepository(
    private val onAccess: suspend () -> Unit = {}
) : BoardRepository {
    override suspend fun getCatalog(board: String): List<CatalogItem> {
        onAccess()
        return MockBoardData.catalogItems
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        onAccess()
        return MockBoardData.thread(threadId)
    }
}
