package com.valoser.futacha.shared.repo.mock

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.numericId
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

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
        val base = MockBoardData.catalogItems()
        return when (mode) {
            CatalogMode.New -> base.sortedByDescending { it.numericId() }
            CatalogMode.Old -> base.sortedBy { it.numericId() }
            CatalogMode.Few -> base.sortedBy { it.replyCount }
            CatalogMode.Momentum -> base.sortedByDescending { it.replyCount / (it.numericId() % 10 + 1) }
            CatalogMode.So -> base.filterIndexed { index, _ -> index % 2 == 0 }
            CatalogMode.Catalog, CatalogMode.Many -> base.sortedByDescending { it.replyCount }
        }
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? {
        onAccess()
        return null
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
    ): String? {
        onAccess()
        return null
    }

    override suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String {
        onAccess()
        // Return a mock thread ID
        return "1234567890"
    }

    override suspend fun clearOpImageCache(board: String?, threadId: String?) {
        onAccess()
    }

    override suspend fun invalidateCookies(board: String) {
        onAccess()
    }

    override fun close() {
        // No resources to clean up for fake repository
    }

    override fun closeAsync(): Job {
        val job: CompletableJob = SupervisorJob()
        job.complete()
        return job
    }
}
