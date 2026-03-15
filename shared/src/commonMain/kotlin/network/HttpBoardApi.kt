package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class HttpBoardApi(
    private val client: HttpClient
) : BoardApi, AutoCloseable {
    private data class TextFetch(
        val url: String,
        val referer: String?,
        val errorLabel: String,
        val readMode: HttpBoardApiTextReadMode,
        val maxLines: Int? = null,
        val rangeHeader: String? = null,
        val failureDescription: String
    )

    private data class PostSubmission(
        val board: String,
        val threadId: String?,
        val name: String,
        val email: String,
        val subject: String,
        val comment: String,
        val password: String,
        val imageFile: ByteArray?,
        val imageFileName: String?,
        val textOnly: Boolean,
        val responseMode: HttpBoardApiPostResponseMode,
        val requestFailureMessage: String,
        val responseFailureLabel: String,
        val forceAjaxResponse: Boolean = false
    )

    private data class ShortFormSubmission(
        val url: String,
        val referer: String,
        val formParameters: Parameters,
        val requestFailureMessage: String,
        val responseFailureMessage: String
    )

    companion object {
        private const val TAG = "HttpBoardApi"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
        // FIX: OOM防止のため、20MB→5MBに制限を削減
        // 通常のHTMLレスポンスは1-2MB程度なので5MBで十分
        private const val MAX_RESPONSE_SIZE = 5 * 1024 * 1024 // 5MB limit
        private const val DEFAULT_SHIFT_JIS_CHRENC_SAMPLE = "文字"
        // FIX: PostingConfigキャッシュサイズを削減（100→20）
        // PostingConfigは複雑なオブジェクトなので、メモリ節約のため制限
        // ほとんどのユーザーは数個の板しか使わないため、20で十分
        private const val MAX_CACHE_SIZE = 20
        private const val MIN_THREAD_HEAD_RANGE_BYTES = 64 * 1024
        private const val MAX_THREAD_HEAD_RANGE_BYTES = 1024 * 1024
        private const val RESPONSE_READ_BUFFER_BYTES = 8 * 1024
        private const val MAX_ZERO_READ_RETRIES = 250
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        private const val RESPONSE_TOTAL_TIMEOUT_MILLIS = 30_000L
        private const val REQUEST_ATTEMPT_TIMEOUT_MILLIS = 45_000L
    }

    private suspend fun readResponseBodyAsString(response: HttpResponse): String {
        return readHttpBoardApiResponseBodyAsString(
            response = response,
            maxBytes = MAX_RESPONSE_SIZE,
            responseReadBufferBytes = RESPONSE_READ_BUFFER_BYTES,
            maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
            responseTotalTimeoutMillis = RESPONSE_TOTAL_TIMEOUT_MILLIS
        )
    }

    private suspend fun readResponseHeadAsString(response: HttpResponse, maxLines: Int): String {
        return readHttpBoardApiResponseHeadAsString(
            response = response,
            maxLines = maxLines,
            maxBytes = MAX_RESPONSE_SIZE,
            responseReadBufferBytes = RESPONSE_READ_BUFFER_BYTES,
            maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
            responseTotalTimeoutMillis = RESPONSE_TOTAL_TIMEOUT_MILLIS
        )
    }

    // FIX: スレッドセーフなLRUキャッシュに変更
    private val postingRuntime = HttpBoardApiPostingRuntime(MAX_CACHE_SIZE)

    override suspend fun fetchCatalogSetup(board: String) {
        initializeHttpBoardApiCatalogSetup(
            client = client,
            board = board,
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            logTag = TAG,
            requestAttemptTimeoutMillis = REQUEST_ATTEMPT_TIMEOUT_MILLIS,
            readSmallResponseSummary = ::readSmallResponseSummary
        )
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        val url = BoardUrlResolver.resolveCatalogUrl(board, mode)
        return fetchText(
            TextFetch(
                url = url,
                referer = board,
                errorLabel = "catalog",
                readMode = HttpBoardApiTextReadMode.BODY,
                failureDescription = "Failed to fetch catalog from $url"
            )
        )
    }

    override suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int): String {
        require(maxLines > 0) { "maxLines must be positive" }
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val estimatedRangeBytes = (maxLines * 4096).coerceIn(
            MIN_THREAD_HEAD_RANGE_BYTES,
            MAX_THREAD_HEAD_RANGE_BYTES
        )
        return fetchText(
            TextFetch(
                url = url,
                referer = resolveBoardRefererBase(board),
                errorLabel = "thread head",
                readMode = HttpBoardApiTextReadMode.HEAD,
                maxLines = maxLines,
                rangeHeader = "bytes=0-${estimatedRangeBytes - 1}",
                failureDescription = "Failed to fetch thread head from $url"
            )
        )
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return fetchText(
            TextFetch(
                url = url,
                referer = resolveBoardRefererBase(board),
                errorLabel = "thread",
                readMode = HttpBoardApiTextReadMode.BODY,
                failureDescription = "Failed to fetch thread from $url"
            )
        )
    }

    override suspend fun fetchThreadByUrl(threadUrl: String): String {
        val url = threadUrl
        return fetchText(
            TextFetch(
                url = url,
                referer = resolveHttpBoardApiRefererBaseFromThreadUrl(url),
                errorLabel = "thread",
                readMode = HttpBoardApiTextReadMode.BODY,
                failureDescription = "Failed to fetch thread from $url"
            )
        )
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
            try {
                if (!response.status.isSuccess()) {
                    val detail = readSmallResponseSummary(response)
                    val suffix = detail?.let { ": $it" }.orEmpty()
                    throw NetworkException("そうだね投票に失敗しました (HTTP ${response.status.value}$suffix)")
                }
                val result = readResponseBodyAsString(response).trim()
                if (!isSuccessfulHttpBoardApiSaidaneResponse(result)) {
                    throw NetworkException("そうだね投票に失敗しました (応答: '$result')")
                }
            } finally {
                // Body lifecycle is managed in readResponseBodyAsString.
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to vote saidane: ${e.message}", cause = e)
        }
    }

    private suspend fun readSmallResponseSummary(response: HttpResponse): String? {
        return readSmallHttpBoardApiResponseSummary(
            response = response,
            responseReadBufferBytes = RESPONSE_READ_BUFFER_BYTES,
            maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
            responseTotalTimeoutMillis = RESPONSE_TOTAL_TIMEOUT_MILLIS
        )
    }

    private fun resolveBoardRefererBase(board: String): String {
        val base = BoardUrlResolver.resolveBoardBaseUrl(board)
        return if (base.endsWith("/")) base else "$base/"
    }

    private suspend fun fetchText(fetch: TextFetch): String {
        return try {
            withHttpBoardApiRetry(
                logTag = TAG,
                requestAttemptTimeoutMillis = REQUEST_ATTEMPT_TIMEOUT_MILLIS
            ) {
                executeHttpBoardApiTextGet(
                    client = client,
                    request = HttpBoardApiTextGetRequest(
                        url = fetch.url,
                        referer = fetch.referer,
                        rangeHeader = fetch.rangeHeader,
                        errorLabel = fetch.errorLabel,
                        maxResponseSize = MAX_RESPONSE_SIZE.toLong(),
                        readMode = fetch.readMode,
                        maxLines = fetch.maxLines
                    ),
                    userAgent = DEFAULT_USER_AGENT,
                    accept = DEFAULT_ACCEPT,
                    acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
                    readSmallResponseSummary = ::readSmallResponseSummary,
                    readResponseBodyAsString = ::readResponseBodyAsString,
                    readResponseHeadAsString = ::readResponseHeadAsString
                )
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "${fetch.failureDescription}: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    private suspend fun submitPost(submission: PostSubmission): String? {
        validateHttpBoardApiPostInput(
            submission.name,
            submission.email,
            submission.subject,
            submission.comment,
            submission.password,
            submission.imageFile
        )
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(submission.board)
        val referer = submission.threadId
            ?.let { BoardUrlResolver.resolveThreadUrl(submission.board, it) }
            ?: buildString {
                append(boardBase)
                if (!boardBase.endsWith("/")) append('/')
                append("futaba.htm")
            }
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val postingConfig = getPostingConfig(submission.board)
        val formData = buildHttpBoardApiPostFormData(
            logTag = TAG,
            threadId = submission.threadId,
            name = submission.name,
            email = submission.email,
            subject = submission.subject,
            comment = submission.comment,
            password = submission.password,
            imageFile = submission.imageFile,
            imageFileName = submission.imageFileName,
            textOnly = submission.textOnly,
            postingConfig = postingConfig,
            forceAjaxResponse = submission.forceAjaxResponse
        )
        val response = submitHttpBoardApiBinaryForm(
            client = client,
            request = HttpBoardApiBinarySubmitRequest(
                url = url,
                referer = referer,
                formData = formData,
                failureMessage = submission.requestFailureMessage
            ),
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE
        )
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException(
                    "${submission.responseFailureLabel}に失敗しました (HTTP ${response.status.value}$suffix)"
                )
            }
            val responseBody = readResponseBodyAsString(response)
            return resolveHttpBoardApiPostResponseOrThrow(
                mode = submission.responseMode,
                responseBody = responseBody,
                logTag = TAG
            )
        } finally {
            // Body lifecycle is managed in readResponseBodyAsString.
        }
    }

    private suspend fun submitShortForm(submission: ShortFormSubmission) {
        executeHttpBoardApiShortFormRequest(
            client = client,
            request = HttpBoardApiShortFormRequest(
                url = submission.url,
                referer = submission.referer,
                formParameters = submission.formParameters,
                failureMessage = submission.requestFailureMessage,
                responseFailureMessage = submission.responseFailureMessage
            ),
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            readSmallResponseSummary = ::readSmallResponseSummary
        )
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        // FIX: 入力検証を最初に実行
        validateHttpBoardApiReasonCode(reasonCode)
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for del request")
        }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board)
        val siteRoot = BoardUrlResolver.resolveSiteRoot(board)
        submitShortForm(
            ShortFormSubmission(
                url = "$siteRoot/del.php",
                referer = BoardUrlResolver.resolveThreadUrl(board, threadId),
                formParameters = Parameters.build {
                    append("mode", "post")
                    append("b", boardSlug)
                    append("d", sanitizedPostId)
                    append("reason", reasonCode)
                    append("responsemode", "ajax")
                },
                requestFailureMessage = "Failed to send del request",
                responseFailureMessage = "del依頼に失敗しました"
            )
        )
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        // FIX: 入力検証を最初に実行（既存のチェックを統合）
        validateHttpBoardApiDeletionPassword(password)
        val sanitizedPostId = BoardUrlResolver.sanitizePostId(postId)
        if (sanitizedPostId.isBlank()) {
            throw IllegalArgumentException("Invalid post ID for user deletion")
        }
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        submitShortForm(
            ShortFormSubmission(
                url = url,
                referer = BoardUrlResolver.resolveThreadUrl(board, threadId),
                formParameters = Parameters.build {
                    append("guid", "on")
                    // Futaba variants exist in the wild: send both forms for compatibility.
                    append("delete", sanitizedPostId)
                    append(sanitizedPostId, "delete")
                    append("responsemode", "ajax")
                    append("pwd", password)
                    append("onlyimgdel", if (imageOnly) "on" else "")
                    append("mode", "usrdel")
                },
                requestFailureMessage = "Failed to delete post",
                responseFailureMessage = "本人削除に失敗しました"
            )
        )
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
    ): String? {
        return submitPost(
            PostSubmission(
                board = board,
                threadId = null,
                name = name,
                email = email,
                subject = subject,
                comment = comment,
                password = password,
                imageFile = imageFile,
                imageFileName = imageFileName,
                textOnly = textOnly,
                responseMode = HttpBoardApiPostResponseMode.CREATE_THREAD,
                requestFailureMessage = "Failed to create thread",
                responseFailureLabel = "スレッド作成",
                forceAjaxResponse = true
            )
        )
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
        return submitPost(
            PostSubmission(
                board = board,
                threadId = threadId,
                name = name,
                email = email,
                subject = subject,
                comment = comment,
                password = password,
                imageFile = imageFile,
                imageFileName = imageFileName,
                textOnly = textOnly,
                responseMode = HttpBoardApiPostResponseMode.REPLY,
                requestFailureMessage = "Failed to reply to thread",
                responseFailureLabel = "返信"
            )
        )
    }

    private suspend fun getPostingConfig(board: String): HttpBoardApiPostingConfig {
        return getOrLoadHttpBoardApiPostingConfig(
            board = board,
            runtime = postingRuntime,
            fallbackChrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
            logTag = TAG
        ) {
            fetchPostingConfig(board)
        }
    }

    private suspend fun fetchPostingConfig(board: String): HttpBoardApiPostingConfig {
        return fetchHttpBoardApiPostingConfig(
            client = client,
            board = board,
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            cacheControl = "no-cache",
            logTag = TAG,
            fallbackChrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
            readSmallResponseSummary = ::readSmallResponseSummary,
            readResponseBodyAsString = ::readResponseBodyAsString
        )
    }

    override fun close() {
        client.close()
    }
}
