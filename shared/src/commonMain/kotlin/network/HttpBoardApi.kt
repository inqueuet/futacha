package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

class HttpBoardApi(
    private val client: HttpClient
) : BoardApi {
    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        val url = BoardUrlResolver.resolveCatalogUrl(board, mode)
        return client.get(url) {
            headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
            headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
            headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
            headers[HttpHeaders.CacheControl] = "no-cache"
            headers[HttpHeaders.Pragma] = "no-cache"
            headers[HttpHeaders.Referrer] = board
        }.bodyAsText()
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return client.get(url) {
            headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
            headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
            headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
            headers[HttpHeaders.CacheControl] = "no-cache"
            headers[HttpHeaders.Pragma] = "no-cache"
            val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                if (base.endsWith("/")) base else "$base/"
            }
            headers[HttpHeaders.Referrer] = refererBase
        }.bodyAsText()
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; FutachaApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
    }
}
