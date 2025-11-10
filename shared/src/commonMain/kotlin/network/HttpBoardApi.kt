package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

class HttpBoardApi(
    private val client: HttpClient
) : BoardApi, AutoCloseable {
    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; FutachaApp) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
        private const val MAX_RESPONSE_SIZE = 20 * 1024 * 1024 // 20MB limit
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        val url = BoardUrlResolver.resolveCatalogUrl(board, mode)
        return try {
            val response: HttpResponse = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = board
            }

            if (!response.status.isSuccess()) {
                val errorMsg = "HTTP error ${response.status.value} when fetching catalog from $url"
                println("HttpBoardApi: $errorMsg")
                throw NetworkException(errorMsg, response.status.value)
            }

            // Check content length before reading
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
            }

            val body = response.bodyAsText()

            // Additional check after reading
            if (body.length > MAX_RESPONSE_SIZE) {
                throw NetworkException("Response body size (${body.length} bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
            }

            body
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch catalog from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return try {
            val response: HttpResponse = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                    if (base.endsWith("/")) base else "$base/"
                }
                headers[HttpHeaders.Referrer] = refererBase
            }

            if (!response.status.isSuccess()) {
                val errorMsg = "HTTP error ${response.status.value} when fetching thread from $url"
                println("HttpBoardApi: $errorMsg")
                throw NetworkException(errorMsg, response.status.value)
            }

            // Check content length before reading
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
            }

            val body = response.bodyAsText()

            // Additional check after reading
            if (body.length > MAX_RESPONSE_SIZE) {
                throw NetworkException("Response body size (${body.length} bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
            }

            body
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread from $url: ${e.message}"
            println("HttpBoardApi: $errorMsg")
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for saidane vote")
        }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board)
        val siteRoot = BoardUrlResolver.resolveSiteRoot(board)
        val url = "$siteRoot/sd.php?$boardSlug.$sanitizedPostId"
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        try {
            val response: HttpResponse = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
            if (!response.status.isSuccess()) {
                throw NetworkException("そうだね投票に失敗しました (HTTP ${response.status.value})")
            }
            val result = response.bodyAsText().trim()
            if (result != "1") {
                throw NetworkException("そうだね投票に失敗しました (応答: '$result')")
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to vote saidane: ${e.message}", cause = e)
        }
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for del request")
        }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board)
        val siteRoot = BoardUrlResolver.resolveSiteRoot(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val response = try {
            client.submitForm(
                url = "$siteRoot/del.php",
                formParameters = Parameters.build {
                    append("mode", "post")
                    append("b", boardSlug)
                    append("d", sanitizedPostId)
                    append("reason", reasonCode)
                    append("responsemode", "ajax")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: Exception) {
            throw NetworkException("Failed to send del request: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("del依頼に失敗しました (HTTP ${response.status.value})")
        }
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for user deletion")
        }
        if (password.isBlank()) {
            throw IllegalArgumentException("Deletion password must not be blank")
        }
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val response = try {
            client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("guid", "on")
                    append(sanitizedPostId, "delete")
                    append("responsemode", "ajax")
                    append("pwd", password)
                    append("onlyimgdel", if (imageOnly) "on" else "")
                    append("mode", "usrdel")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: Exception) {
            throw NetworkException("Failed to delete post: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("本人削除に失敗しました (HTTP ${response.status.value})")
        }
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
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.htm")
        }
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val response = try {
            client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("guid", "on")
                    append("mode", "regist")
                    append("MAX_FILE_SIZE", "8192000")
                    append("name", name)
                    append("email", email)
                    append("sub", subject)
                    append("com", comment)
                    append("pwd", password)
                    if (textOnly) {
                        append("textonly", "on")
                    }
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: Exception) {
            throw NetworkException("Failed to create thread: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("スレッド作成に失敗しました (HTTP ${response.status.value})")
        }

        // Parse response to extract thread ID
        val responseBody = response.bodyAsText()
        // Look for thread ID in response - it should redirect to the new thread
        // Example: res/1364612020.htm
        val threadIdRegex = """res/(\d+)\.htm""".toRegex()
        val match = threadIdRegex.find(responseBody)
        return match?.groupValues?.get(1) ?: throw NetworkException("スレッドIDの取得に失敗しました")
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
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val response = try {
            client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("guid", "on")
                    append("mode", "regist")
                    append("MAX_FILE_SIZE", "8192000")
                    append("resto", threadId)
                    append("name", name)
                    append("email", email)
                    append("sub", subject)
                    append("com", comment)
                    append("pwd", password)
                    if (textOnly) {
                        append("textonly", "on")
                    }
                    append("responsemode", "ajax")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: Exception) {
            throw NetworkException("Failed to reply to thread: ${e.message}", cause = e)
        }
        if (!response.status.isSuccess()) {
            throw NetworkException("返信に失敗しました (HTTP ${response.status.value})")
        }
    }

    override fun close() {
        client.close()
    }
}
