package com.valoser.futacha.shared.repo.mock

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.numericId
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
    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        onAccess()
        val base = MockBoardData.catalogItems
        return when (mode) {
            CatalogMode.New -> base.sortedByDescending { it.numericId() }
            CatalogMode.Old -> base.sortedBy { it.numericId() }
            CatalogMode.Few -> base.sortedBy { it.replyCount }
            CatalogMode.Momentum -> base.sortedByDescending { it.replyCount / (it.numericId() % 10 + 1) }
            CatalogMode.So -> base.filterIndexed { index, _ -> index % 2 == 0 }
            CatalogMode.Seen -> base.take(4)
            CatalogMode.History -> base.takeLast(4)
            CatalogMode.Catalog, CatalogMode.Many -> base.sortedByDescending { it.replyCount }
        }
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        onAccess()
        return MockBoardData.thread(threadId)
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        onAccess()
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        onAccess()
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        onAccess()
    }

    override suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ) {
        onAccess()
    }

    override fun close() {
        // No resources to clean up for fake repository
    }
}
