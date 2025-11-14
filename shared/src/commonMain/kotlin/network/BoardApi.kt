package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode

data class BoardEndpoint(
    val catalog: String,
    val thread: String
)

interface BoardApi {
    /**
     * Fetches catalog setup page to initialize cookies (posttime, cxyl, etc.)
     * This should be called before any catalog operations to ensure proper cookie setup.
     */
    suspend fun fetchCatalogSetup(board: String)

    suspend fun fetchCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): String
    suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int = 65): String
    suspend fun fetchThread(board: String, threadId: String): String
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
    ): String?
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
}
