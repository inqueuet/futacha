package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
    // prepareGet/execute streams the body: a plain get() buffers the whole
    // response before returning, so the size limit and the head-only read
    // below could not stop an oversized or endless body from being received.
    return client.prepareGet(request.url) {
        applyHttpBoardApiTextGetHeaders(request, userAgent, accept, acceptLanguage)
    }.execute { response -> readHttpBoardApiTextResponse(response, request, readSmallResponseSummary,
        readResponseBodyAsString, readResponseHeadAsString) }
}

/**
 * A body GET that sends [validators] from an earlier response. A 304 answers
 * [ConditionalTextFetchResult.NotModified] (without reading a body) only when
 * validators were actually sent; any other status is handled like a plain GET.
 * An ETag is preferred: with both, servers ignore If-Modified-Since, whose
 * one-second granularity could hide a reply posted in the same second.
 */
internal suspend fun executeHttpBoardApiConditionalTextGet(
    client: HttpClient,
    request: HttpBoardApiTextGetRequest,
    validators: HttpConditionalValidators?,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    readSmallResponseSummary: suspend (HttpResponse) -> String?,
    readResponseBodyAsString: suspend (HttpResponse) -> String
): ConditionalTextFetchResult {
    require(request.readMode == HttpBoardApiTextReadMode.BODY) { "Conditional GET reads whole bodies" }
    val sentValidators = validators?.takeUnless { it.isEmpty }
    return client.prepareGet(request.url) {
        applyHttpBoardApiTextGetHeaders(request, userAgent, accept, acceptLanguage)
        val etag = sentValidators?.etag?.takeIf { it.isNotBlank() }
        if (etag != null) {
            headers[HttpHeaders.IfNoneMatch] = etag
        } else {
            sentValidators?.lastModified?.takeIf { it.isNotBlank() }?.let {
                headers[HttpHeaders.IfModifiedSince] = it
            }
        }
    }.execute { response ->
        if (sentValidators != null && response.status == HttpStatusCode.NotModified) {
            runCatching { response.bodyAsChannel().cancel(null) }
            return@execute ConditionalTextFetchResult.NotModified
        }
        val body = readHttpBoardApiTextResponse(
            response,
            request,
            readSmallResponseSummary,
            readResponseBodyAsString,
            readResponseHeadAsString = { _, _ -> error("Conditional GET reads whole bodies") }
        )
        ConditionalTextFetchResult.Modified(body, response.conditionalValidatorsOrNull())
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.applyHttpBoardApiTextGetHeaders(
    request: HttpBoardApiTextGetRequest,
    userAgent: String,
    accept: String,
    acceptLanguage: String
) {
    // HttpBoardApi owns a bounded retry loop around this entire request,
    // including response-body reading. Suppress the platform retry plugin
    // so a closed Futaba keep-alive connection produces two attempts, not
    // two groups of three attempts that can outlive the screen loader.
    attributes.put(HigherLayerRetryManaged, true)
    headers[HttpHeaders.UserAgent] = userAgent
    headers[HttpHeaders.Accept] = accept
    headers[HttpHeaders.AcceptLanguage] = acceptLanguage
    headers[HttpHeaders.CacheControl] = "no-cache"
    headers[HttpHeaders.Pragma] = "no-cache"
    request.referer?.let { headers[HttpHeaders.Referrer] = it }
    request.rangeHeader?.let { headers[HttpHeaders.Range] = it }
}

private suspend fun readHttpBoardApiTextResponse(
    response: HttpResponse,
    request: HttpBoardApiTextGetRequest,
    readSmallResponseSummary: suspend (HttpResponse) -> String?,
    readResponseBodyAsString: suspend (HttpResponse) -> String,
    readResponseHeadAsString: suspend (HttpResponse, Int) -> String
): String {
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
