package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.prepareForm
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
    readSmallResponseSummary: suspend (HttpResponse) -> String?,
    readSmallResponseBody: suspend (HttpResponse) -> String? = readSmallResponseSummary
) {
    // Streamed so the small-response reader bounds the body; submitForm()
    // buffered the whole response before it could be checked.
    val statement = try {
        client.prepareForm(
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
    val failure: NetworkException? = try {
        statement.execute { response ->
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                NetworkException("${request.responseFailureMessage} (HTTP ${response.status.value}$suffix)")
            } else {
                // Futaba answers a rejected deletion (wrong key, missing post,
                // duplicate request...) with HTTP 200 and the reason as text.
                extractHttpBoardApiShortFormFailure(readSmallResponseBody(response))
                    ?.let { detail -> NetworkException("${request.responseFailureMessage}: $detail") }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NetworkException) {
        throw e
    } catch (e: Exception) {
        throw NetworkException("${request.failureMessage}: ${e.message}", cause = e)
    }
    failure?.let { throw it }
}

internal suspend fun initializeHttpBoardApiCatalogSetup(
    client: HttpClient,
    board: String,
    settings: CatalogFetchSettings,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    logTag: String,
    requestAttemptTimeoutMillis: Long,
    maxAttempts: Int = 3,
    readSmallResponseSummary: suspend (HttpResponse) -> String?
) {
    val normalizedSettings = settings.normalized()
    val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
    val url = buildString {
        append(boardBase)
        if (!boardBase.endsWith("/")) append('/')
        append("futaba.php?mode=catset")
    }
    try {
        withHttpBoardApiRetry(
            logTag = logTag,
            requestAttemptTimeoutMillis = requestAttemptTimeoutMillis,
            maxAttempts = maxAttempts
        ) {
            client.prepareForm(
                url = url,
                formParameters = Parameters.build {
                    append("mode", "catset")
                    append("cx", normalizedSettings.columns.toString())
                    append("cy", normalizedSettings.rows.toString())
                    append("cl", normalizedSettings.titleLines.toString())
                    append("cm", "0")
                    append("ci", "0")
                    if (normalizedSettings.showVisitedHistory) {
                        append("vh", "on")
                    }
                }
            ) {
                headers[HttpHeaders.UserAgent] = userAgent
                headers[HttpHeaders.Accept] = accept
                headers[HttpHeaders.AcceptLanguage] = acceptLanguage
                headers[HttpHeaders.CacheControl] = "max-age=0"
                headers[HttpHeaders.Referrer] = url
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val detail = readSmallResponseSummary(response)
                    val suffix = detail?.let { ": $it" }.orEmpty()
                    val errorMsg = "HTTP error ${response.status.value} when fetching catalog setup from $url$suffix"
                    Logger.w(logTag, errorMsg)
                    throw NetworkException(errorMsg, response.status.value)
                }
                readSmallResponseSummary(response)
            }

            Logger.i(
                logTag,
                "Catalog setup cookies initialized for board=$board " +
                    "(cx=${normalizedSettings.columns}, cy=${normalizedSettings.rows})"
            )
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
