package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitFormWithBinaryData
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

internal suspend fun submitHttpBoardApiBinaryForm(
    client: HttpClient,
    request: HttpBoardApiBinarySubmitRequest,
    userAgent: String,
    accept: String,
    acceptLanguage: String
): HttpResponse {
    return try {
        client.submitFormWithBinaryData(
            url = request.url,
            formData = request.formData
        ) {
            headers[HttpHeaders.UserAgent] = userAgent
            headers[HttpHeaders.Accept] = accept
            headers[HttpHeaders.AcceptLanguage] = acceptLanguage
            headers[HttpHeaders.CacheControl] = "no-cache"
            headers[HttpHeaders.Pragma] = "no-cache"
            headers[HttpHeaders.Referrer] = request.referer
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw NetworkException("${request.failureMessage}: ${e.message}", cause = e)
    }
}

internal fun resolveHttpBoardApiPostResponseOrThrow(
    mode: HttpBoardApiPostResponseMode,
    responseBody: String,
    logTag: String
): String? {
    return when (mode) {
        HttpBoardApiPostResponseMode.CREATE_THREAD -> {
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
                throw NetworkException("スレッド作成に失敗しました: $errorDetail")
            }
            if (isSuccessfulHttpBoardApiPostResponse(responseBody)) {
                Logger.w(logTag, "Thread created but thread ID was not found in response")
                null
            } else {
                throw NetworkException("スレッドIDの取得に失敗しました: $summary")
            }
        }

        HttpBoardApiPostResponseMode.REPLY -> {
            if (!isSuccessfulHttpBoardApiPostResponse(responseBody)) {
                val errorDetail = extractHttpBoardApiServerError(responseBody)
                val summary = summarizeHttpBoardApiResponse(responseBody)
                val detail = errorDetail ?: summary
                throw NetworkException("返信に失敗しました: $detail")
            }
            tryExtractHttpBoardApiThisNo(responseBody)
        }
    }
}
