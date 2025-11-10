package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.parser.HtmlParser

interface BoardRepository {
    suspend fun getCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): List<CatalogItem>
    suspend fun getThread(board: String, threadId: String): ThreadPage
    suspend fun voteSaidane(board: String, threadId: String, postId: String)
    suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String)
    suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    )
    suspend fun replyToThread(
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
    )

    suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String

    /**
     * Close the repository and release resources (e.g., HTTP client connections)
     */
    fun close()
}

class DefaultBoardRepository(
    private val api: BoardApi,
    private val parser: HtmlParser
) : BoardRepository {
    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        val html = api.fetchCatalog(board, mode)
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
        return parser.parseCatalog(html, baseUrl)
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        val html = api.fetchThread(board, threadId)
        return parser.parseThread(html)
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        api.voteSaidane(board, threadId, postId)
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        api.requestDeletion(board, threadId, postId, reasonCode)
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        api.deleteByUser(board, threadId, postId, password, imageOnly)
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
        api.replyToThread(board, threadId, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
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
        return api.createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
    }

    override fun close() {
        // Close the underlying API if it supports cleanup
        (api as? AutoCloseable)?.close()
    }
}
