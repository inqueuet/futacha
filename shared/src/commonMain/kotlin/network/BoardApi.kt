package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode

data class BoardEndpoint(
    val catalog: String,
    val thread: String
)

interface BoardApi {
    suspend fun fetchCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): String
    suspend fun fetchThread(board: String, threadId: String): String
}
