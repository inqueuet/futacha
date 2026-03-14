package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel

internal enum class HttpBoardApiTextReadMode {
    BODY,
    HEAD
}

internal data class HttpBoardApiTextGetRequest(
    val url: String,
    val referer: String?,
    val rangeHeader: String? = null,
    val errorLabel: String,
    val maxResponseSize: Long,
    val readMode: HttpBoardApiTextReadMode,
    val maxLines: Int? = null
)

internal suspend fun executeHttpBoardApiTextGet(
    client: HttpClient,
    request: HttpBoardApiTextGetRequest,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    readSmallResponseSummary: suspend (HttpResponse) -> String?,
    readResponseBodyAsString: suspend (HttpResponse) -> String,
    readResponseHeadAsString: suspend (HttpResponse, Int) -> String
): String {
    val response: HttpResponse = client.get(request.url) {
        headers[HttpHeaders.UserAgent] = userAgent
        headers[HttpHeaders.Accept] = accept
        headers[HttpHeaders.AcceptLanguage] = acceptLanguage
        headers[HttpHeaders.CacheControl] = "no-cache"
        headers[HttpHeaders.Pragma] = "no-cache"
        request.referer?.let { headers[HttpHeaders.Referrer] = it }
        request.rangeHeader?.let { headers[HttpHeaders.Range] = it }
    }

    try {
        if (!response.status.isSuccess()) {
            val detail = readSmallResponseSummary(response)
            val suffix = detail?.let { ": $it" }.orEmpty()
            val errorMsg =
                "HTTP error ${response.status.value} when fetching ${request.errorLabel} from ${request.url}$suffix"
            throw NetworkException(errorMsg, response.status.value)
        }

        if (request.readMode == HttpBoardApiTextReadMode.BODY) {
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > request.maxResponseSize) {
                throw NetworkException(
                    "Response size ($contentLength bytes) exceeds maximum allowed (${request.maxResponseSize} bytes)"
                )
            }
        }

        return when (request.readMode) {
            HttpBoardApiTextReadMode.BODY -> readResponseBodyAsString(response)
            HttpBoardApiTextReadMode.HEAD -> readResponseHeadAsString(
                response,
                request.maxLines ?: error("maxLines is required for HEAD mode")
            )
        }
    } finally {
        if (request.readMode == HttpBoardApiTextReadMode.HEAD) {
            runCatching { response.bodyAsChannel().cancel(null) }
        }
    }
}
