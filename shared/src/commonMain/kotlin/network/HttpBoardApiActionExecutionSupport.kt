package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

internal data class HttpBoardApiShortFormRequest(
    val url: String,
    val referer: String,
    val formParameters: Parameters,
    val failureMessage: String,
    val responseFailureMessage: String
)

internal suspend fun executeHttpBoardApiShortFormRequest(
    client: HttpClient,
    request: HttpBoardApiShortFormRequest,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    readSmallResponseSummary: suspend (HttpResponse) -> String?
) {
    val response = try {
        client.submitForm(
            url = request.url,
            formParameters = request.formParameters
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
    try {
        if (!response.status.isSuccess()) {
            val detail = readSmallResponseSummary(response)
            val suffix = detail?.let { ": $it" }.orEmpty()
            throw NetworkException("${request.responseFailureMessage} (HTTP ${response.status.value}$suffix)")
        }
        readSmallResponseSummary(response)
    } finally {
        // Body is already drained by readSmallResponseSummary.
    }
}

internal suspend fun initializeHttpBoardApiCatalogSetup(
    client: HttpClient,
    board: String,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    logTag: String,
    requestAttemptTimeoutMillis: Long,
    readSmallResponseSummary: suspend (HttpResponse) -> String?
) {
    val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
    val url = buildString {
        append(boardBase)
        if (!boardBase.endsWith("/")) append('/')
        append("futaba.php?mode=catset")
    }
    try {
        withHttpBoardApiRetry(
            logTag = logTag,
            requestAttemptTimeoutMillis = requestAttemptTimeoutMillis
        ) {
            val response: HttpResponse = client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("mode", "catset")
                    append("cx", "5")
                    append("cy", "60")
                    append("cl", "4")
                    append("cm", "0")
                    append("ci", "0")
                    append("vh", "on")
                }
            ) {
                headers[HttpHeaders.UserAgent] = userAgent
                headers[HttpHeaders.Accept] = accept
                headers[HttpHeaders.AcceptLanguage] = acceptLanguage
                headers[HttpHeaders.CacheControl] = "max-age=0"
                headers[HttpHeaders.Referrer] = url
            }

            try {
                if (!response.status.isSuccess()) {
                    val detail = readSmallResponseSummary(response)
                    val suffix = detail?.let { ": $it" }.orEmpty()
                    val errorMsg = "HTTP error ${response.status.value} when fetching catalog setup from $url$suffix"
                    Logger.w(logTag, errorMsg)
                    throw NetworkException(errorMsg, response.status.value)
                }
                readSmallResponseSummary(response)
            } finally {
                // Body is already drained by readSmallResponseSummary.
            }

            Logger.i(logTag, "Catalog setup cookies initialized for board=$board (cx=5, cy=60)")
        }
    } catch (e: NetworkException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val errorMsg = "Failed to fetch catalog setup from $url: ${e.message}"
        Logger.e(logTag, errorMsg, e)
        throw NetworkException(errorMsg, cause = e)
    }
}
