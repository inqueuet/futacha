package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.head
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
        val failureDescription: String,
        val requestAttemptTimeoutMillis: Long = REQUEST_ATTEMPT_TIMEOUT_MILLIS,
        val maxAttempts: Int = REQUEST_MAX_ATTEMPTS,
        val overallBudgetMillis: Long? = null
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
        val handwriting: Boolean,
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
        val responseFailureMessage: String,
        val encodedBody: String? = null
    )

    companion object {
        private const val TAG = "HttpBoardApi"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
        // Image-heavy threads can legitimately exceed 5 MiB before reaching
        // 1,000 replies. Keep the read bounded, but leave enough room for a
        // full Futaba thread and large compatibility catalogs.
        private const val MAX_RESPONSE_SIZE = 20 * 1024 * 1024
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
        private const val REQUEST_ATTEMPT_TIMEOUT_MILLIS = 35_000L
        // Catalog/thread bodies can be several MiB. On a slow line a fixed 30 s
        // read (inside a 35 s attempt) failed and restarted a download that was
        // still progressing. Both attempts now share one budget that still fits
        // the screens' 75 s loaders; a stalled body is caught by the reader's
        // 10 s idle timeout and a dead connection by the socket timeout.
        private const val BODY_REQUEST_BUDGET_MILLIS = 70_000L
        private const val BODY_RESPONSE_TOTAL_TIMEOUT_MILLIS = 68_000L
        // Only the posting form (near the top of futaba.htm / res/N.htm) is
        // needed before a post, not the whole thread.
        private const val POSTING_CONFIG_PREFIX_MAX_BYTES = 256 * 1024
        private const val HELPER_REQUEST_ATTEMPT_TIMEOUT_MILLIS = 5_000L
        private const val REQUEST_MAX_ATTEMPTS = 2
        private const val HELPER_REQUEST_MAX_ATTEMPTS = 1
    }

    private suspend fun readResponseBodyAsString(response: HttpResponse): String {
        return readHttpBoardApiResponseBodyAsString(
            response = response,
            maxBytes = MAX_RESPONSE_SIZE,
            responseReadBufferBytes = RESPONSE_READ_BUFFER_BYTES,
            maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
            responseTotalTimeoutMillis = BODY_RESPONSE_TOTAL_TIMEOUT_MILLIS
        )
    }

    private suspend fun readPostingConfigPrefix(response: HttpResponse): String {
        return readHttpBoardApiResponsePrefixAsString(
            response = response,
            maxBytes = POSTING_CONFIG_PREFIX_MAX_BYTES,
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

    override suspend fun fetchCatalogSetup(
        board: String,
        settings: CatalogFetchSettings
    ) {
        initializeHttpBoardApiCatalogSetup(
            client = client,
            board = board,
            settings = settings,
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            logTag = TAG,
            requestAttemptTimeoutMillis = REQUEST_ATTEMPT_TIMEOUT_MILLIS,
            maxAttempts = REQUEST_MAX_ATTEMPTS,
            readSmallResponseSummary = ::readSmallResponseSummary
        )
    }

    override suspend fun fetchPostingCapabilities(board: String, threadId: String?): BoardPostingCapabilities {
        val config = getPostingConfig(board, threadId = threadId)
        return resolveBoardPostingCapabilities(
            board = board,
            serverMaxFileSizeBytes = config.maxFileSizeBytes,
            serverSupportedExtensions = config.supportedExtensions
        ).copy(
            nameAllowed = "name" in config.formFields,
            subjectAllowed = "sub" in config.formFields,
            replyAttachmentsAllowed = if (threadId != null) "upfile" in config.formFields else defaultBoardPostingCapabilities(board).replyAttachmentsAllowed
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
                failureDescription = "Failed to fetch catalog from $url",
                requestAttemptTimeoutMillis = BODY_REQUEST_BUDGET_MILLIS,
                overallBudgetMillis = BODY_REQUEST_BUDGET_MILLIS
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
                failureDescription = "Failed to fetch thread head from $url",
                requestAttemptTimeoutMillis = HELPER_REQUEST_ATTEMPT_TIMEOUT_MILLIS,
                maxAttempts = HELPER_REQUEST_MAX_ATTEMPTS
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
                failureDescription = "Failed to fetch thread from $url",
                requestAttemptTimeoutMillis = BODY_REQUEST_BUDGET_MILLIS,
                overallBudgetMillis = BODY_REQUEST_BUDGET_MILLIS
            )
        )
    }

    override suspend fun fetchThreadIfModified(
        board: String,
        threadId: String,
        validators: HttpConditionalValidators?
    ): ConditionalTextFetchResult {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val failureDescription = "Failed to fetch thread from $url"
        return try {
            withHttpBoardApiRetry(
                logTag = TAG,
                requestAttemptTimeoutMillis = BODY_REQUEST_BUDGET_MILLIS,
                maxAttempts = REQUEST_MAX_ATTEMPTS,
                overallBudgetMillis = BODY_REQUEST_BUDGET_MILLIS
            ) {
                executeHttpBoardApiConditionalTextGet(
                    client = client,
                    request = HttpBoardApiTextGetRequest(
                        url = url,
                        referer = resolveBoardRefererBase(board),
                        errorLabel = "thread",
                        maxResponseSize = MAX_RESPONSE_SIZE.toLong(),
                        readMode = HttpBoardApiTextReadMode.BODY
                    ),
                    validators = validators,
                    userAgent = DEFAULT_USER_AGENT,
                    accept = DEFAULT_ACCEPT,
                    acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
                    readSmallResponseSummary = ::readSmallResponseSummary,
                    readResponseBodyAsString = ::readResponseBodyAsString
                )
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "$failureDescription: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThreadByUrl(threadUrl: String): String {
        val url = threadUrl
        return fetchText(
            TextFetch(
                url = url,
                referer = resolveHttpBoardApiRefererBaseFromThreadUrl(url),
                errorLabel = "thread",
                readMode = HttpBoardApiTextReadMode.BODY,
                failureDescription = "Failed to fetch thread from $url",
                requestAttemptTimeoutMillis = BODY_REQUEST_BUDGET_MILLIS,
                overallBudgetMillis = BODY_REQUEST_BUDGET_MILLIS
            )
        )
    }

    override suspend fun probeThreadExists(threadUrl: String): Boolean {
        return try {
            client.head(threadUrl) {
                // A failed probe is simply repeated on the next run; the client's own
                // retries (up to 3 x 75 s on Android) only burn the background budget.
                attributes.put(HigherLayerRetryManaged, true)
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                resolveHttpBoardApiRefererBaseFromThreadUrl(threadUrl)?.let { referer ->
                    headers[HttpHeaders.Referrer] = referer
                }
            }.status.isSuccess()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun probeThreadGone(threadUrl: String): Boolean {
        return try {
            client.head(threadUrl) {
                // Same as probeThreadExists: an inconclusive probe keeps the entry and is
                // repeated later, so platform retries would only delay the caller.
                attributes.put(HigherLayerRetryManaged, true)
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                resolveHttpBoardApiRefererBaseFromThreadUrl(threadUrl)?.let { referer ->
                    headers[HttpHeaders.Referrer] = referer
                }
            }.status.value in setOf(404, 410)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
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
            client.prepareGet(url) {
                // A vote is a GET but not idempotent: a replay after a lost response
                // can count twice. Opt out of the platform's automatic GET retries;
                // nothing above retries a vote either, the user can simply tap again.
                attributes.put(HigherLayerRetryManaged, true)
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val detail = readSmallResponseSummary(response)
                    val suffix = detail?.let { ": $it" }.orEmpty()
                    throw NetworkException("そうだね投票に失敗しました (HTTP ${response.status.value}$suffix)")
                }
                val result = readSmallResponseBody(response)?.trim().orEmpty()
                if (!isSuccessfulHttpBoardApiSaidaneResponse(result)) {
                    throw NetworkException("そうだね投票に失敗しました (応答: '${result.take(160)}')")
                }
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

    private suspend fun readSmallResponseBody(response: HttpResponse): String? {
        return readSmallHttpBoardApiResponseBody(
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
                requestAttemptTimeoutMillis = fetch.requestAttemptTimeoutMillis,
                maxAttempts = fetch.maxAttempts,
                overallBudgetMillis = fetch.overallBudgetMillis
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
        val browser = client.attributes.getOrNull(PostingBrowserKey)
        val (postingUserAgent, postingConfig) = try {
            val agent = browser?.userAgent() ?: DEFAULT_USER_AGENT
            val fetched = fetchPostingConfig(submission.board, submission.threadId, agent)
            agent to if (browser != null) prepareOfficialPostingEnvironment(client, browser, referer, fetched, agent) else applyLegacyPostingEnvironment(fetched)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            throw NetworkException("投稿の準備ができないため送信していません。下書きを保持しています: ${e.message}", cause = e)
        }
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
            forceAjaxResponse = submission.forceAjaxResponse,
            handwriting = submission.handwriting
        )
        return submitHttpBoardApiBinaryForm(
            client = client,
            request = HttpBoardApiBinarySubmitRequest(
                url = url,
                referer = referer,
                formData = formData,
                failureMessage = submission.requestFailureMessage
            ),
            userAgent = postingUserAgent,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE
        ) { response ->
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val prefix = "${submission.responseFailureLabel}に失敗しました (HTTP ${response.status.value})"
                detail?.let {
                    logHttpBoardApiPostingFailureClassification(
                        logTag = TAG,
                        operation = submission.responseFailureLabel,
                        detail = it
                    )
                }
                throw NetworkException(
                    detail?.let {
                        buildHttpBoardApiPostingFailureMessage(
                            prefix = prefix,
                            detail = it
                        )
                    } ?: prefix
                )
            }
            val responseBody = readResponseBodyAsString(response)
            resolveHttpBoardApiPostResponseOrThrow(
                mode = submission.responseMode,
                responseBody = responseBody,
                logTag = TAG,
                redirectedRequestUrl = response.call.request.url.toString().takeIf { it != url }
            )
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
                responseFailureMessage = submission.responseFailureMessage,
                encodedBody = submission.encodedBody
            ),
            userAgent = DEFAULT_USER_AGENT,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            readSmallResponseSummary = ::readSmallResponseSummary,
            readSmallResponseBody = ::readSmallResponseBody
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
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val formFields = listOf(
            "guid" to "on",
            // Futaba variants exist in the wild: send both forms for compatibility.
            "delete" to sanitizedPostId,
            sanitizedPostId to "delete",
            "responsemode" to "ajax",
            "pwd" to password,
            "onlyimgdel" to if (imageOnly) "on" else "",
            "mode" to "usrdel"
        )
        // The posting form sends the key in the board's charset (Shift_JIS), so a non-ASCII key
        // must be deleted with the same bytes. ASCII keys keep the original UTF-8 form body.
        val encodedBody = if (password.all { it.code < 0x80 }) {
            null
        } else {
            val encoding = try {
                getPostingConfig(board, threadId).encoding
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HttpBoardApiPostEncoding.SHIFT_JIS
            }
            buildHttpBoardApiFormUrlEncodedBody(formFields, encoding)
        }
        submitShortForm(
            ShortFormSubmission(
                url = url,
                referer = referer,
                formParameters = Parameters.build {
                    formFields.forEach { (name, value) -> append(name, value) }
                },
                requestFailureMessage = "Failed to delete post",
                responseFailureMessage = "本人削除に失敗しました",
                encodedBody = encodedBody
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
        textOnly: Boolean,
        handwriting: Boolean
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
                handwriting = handwriting,
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
        textOnly: Boolean,
        handwriting: Boolean
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
                handwriting = handwriting,
                responseMode = HttpBoardApiPostResponseMode.REPLY,
                requestFailureMessage = "Failed to reply to thread",
                responseFailureLabel = "返信"
            )
        )
    }

    private suspend fun getPostingConfig(board: String, threadId: String?): HttpBoardApiPostingConfig {
        val cacheKey = threadId?.let { "$board::thread::$it" } ?: board
        return getOrLoadHttpBoardApiPostingConfig(
            board = cacheKey,
            runtime = postingRuntime,
            fallbackChrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
            logTag = TAG
        ) {
            fetchPostingConfig(board, threadId)
        }
    }

    private suspend fun fetchPostingConfig(board: String, threadId: String?, userAgent: String = DEFAULT_USER_AGENT): HttpBoardApiPostingConfig {
        return fetchHttpBoardApiPostingConfig(
            client = client,
            board = board,
            threadId = threadId,
            userAgent = userAgent,
            accept = DEFAULT_ACCEPT,
            acceptLanguage = DEFAULT_ACCEPT_LANGUAGE,
            cacheControl = "no-cache",
            logTag = TAG,
            fallbackChrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
            readSmallResponseSummary = ::readSmallResponseSummary,
            readResponseBodyAsString = ::readPostingConfigPrefix
        )
    }

    override fun close() {
        client.close()
    }
}
