package com.valoser.futacha.shared.network

data class BoardEndpoint(
    val catalog: String,
    val thread: String
)

interface BoardApi {
    suspend fun fetchCatalog(board: String): String
    suspend fun fetchThread(board: String, threadId: String): String
}
