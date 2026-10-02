package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.prepareFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import kotlin.coroutines.cancellation.CancellationException

internal enum class HttpBoardApiPostResponseMode {
    CREATE_THREAD,
    REPLY
}

internal data class HttpBoardApiBinarySubmitRequest(
    val url: String,
    val referer: String,
    val formData: List<PartData>,
    val failureMessage: String
)

/**
 * A multipart post may upload several MiB. The clients' 75 s request timeout
 * covered the whole upload, so a large attachment on a slow line always
 * failed; the socket timeout still catches a stalled transfer.
 */
internal const val HTTP_BOARD_API_MULTIPART_REQUEST_TIMEOUT_MILLIS = 5L * 60L * 1000L

internal suspend fun <T> submitHttpBoardApiBinaryForm(
    client: HttpClient,
    request: HttpBoardApiBinarySubmitRequest,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    handleResponse: suspend (HttpResponse) -> T
): T {
    var responseReceived = false
    return try {
        // Streamed: the response body is read by [handleResponse] with a bound
        // instead of being buffered whole by submitFormWithBinaryData().
        client.prepareFormWithBinaryData(
            url = request.url,
            formData = request.formData
        ) {
            timeout { requestTimeoutMillis = HTTP_BOARD_API_MULTIPART_REQUEST_TIMEOUT_MILLIS }
            headers[HttpHeaders.UserAgent] = userAgent
            headers[HttpHeaders.Accept] = accept
            headers[HttpHeaders.AcceptLanguage] = acceptLanguage
            headers[HttpHeaders.CacheControl] = "no-cache"
            headers[HttpHeaders.Pragma] = "no-cache"
            headers[HttpHeaders.Referrer] = request.referer
        }.execute { response ->
            responseReceived = true
            handleResponse(response)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Failures while handling the received response keep their own message.
        if (responseReceived) throw e
        throw NetworkException("${request.failureMessage}: ${e.message}", cause = e)
    }
}

internal fun resolveHttpBoardApiPostResponseOrThrow(
    mode: HttpBoardApiPostResponseMode,
    responseBody: String,
    logTag: String,
    redirectedRequestUrl: String? = null
): String? {
    return when (mode) {
        HttpBoardApiPostResponseMode.CREATE_THREAD -> {
            // A non-ajax post answered with 302/303 lands on the new thread's
            // res/NNN.htm; that URL identifies it more reliably than its page,
            // which also links other threads.
            redirectedRequestUrl?.let(::tryExtractHttpBoardApiThreadId)?.let { return it }
            val extractedThreadId = tryExtractHttpBoardApiThreadId(responseBody)
            if (!extractedThreadId.isNullOrBlank()) {
                return extractedThreadId
            }
            val jsonThreadId = tryParseHttpBoardApiThreadIdFromJson(responseBody)
            if (jsonThreadId != null) {
                return jsonThreadId
            }
            val errorDetail = extractHttpBoardApiServerError(responseBody)
            val summary = summarizeHttpBoardApiResponse(responseBody)
            if (errorDetail != null) {
                logHttpBoardApiPostingFailureClassification(
                    logTag = logTag,
                    operation = "create-thread",
                    detail = errorDetail
                )
                throw NetworkException(
                    buildHttpBoardApiPostingFailureMessage(
                        prefix = "スレッド作成に失敗しました",
                        detail = errorDetail
                    )
                )
            }
            if (isSuccessfulHttpBoardApiPostResponse(responseBody)) {
                Logger.w(logTag, "Thread created but thread ID was not found in response")
                null
            } else {
                logHttpBoardApiPostingFailureClassification(
                    logTag = logTag,
                    operation = "create-thread-id-resolution",
                    detail = summary
                )
                throw NetworkException(
                    buildHttpBoardApiPostingFailureMessage(
                        prefix = "スレッドIDの取得に失敗しました",
                        detail = summary
                    )
                )
            }
        }

        HttpBoardApiPostResponseMode.REPLY -> {
            if (!isSuccessfulHttpBoardApiPostResponse(responseBody)) {
                val errorDetail = extractHttpBoardApiServerError(responseBody)
                val summary = summarizeHttpBoardApiResponse(responseBody)
                val detail = errorDetail ?: summary
                logHttpBoardApiPostingFailureClassification(
                    logTag = logTag,
                    operation = "reply",
                    detail = detail
                )
                throw NetworkException(
                    buildHttpBoardApiPostingFailureMessage(
                        prefix = "返信に失敗しました",
                        detail = detail
                    )
                )
            }
            tryExtractHttpBoardApiThisNo(responseBody)
        }
    }
}
