package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val maxLines: Int? = null,
    /** After this long without headers a second request is sent (see [executeWithHeaderHedge]). */
    val headerTimeoutMillis: Long = 35_000L
)

/** Header stalls have a shorter deadline; progressing bodies retain the overall budget. */
internal suspend fun <T> HttpStatement.executeWithHeaderTimeout(
    timeoutMillis: Long, block: suspend (HttpResponse) -> T
): T = coroutineScope {
    val watchdog = launch {
        delay(timeoutMillis.coerceAtLeast(1L))
        throw httpBoardApiResponseReadTimeout("headers", timeoutMillis)
    }
    try {
        execute { response ->
            watchdog.cancel()
            block(response)
        }
    } finally { watchdog.cancel() }
}

/**
 * Sends the request from [statement] and, if its headers have not arrived after
 * [hedgeAfterMillis], one more identical request, without giving up the first.
 * The first to receive headers is handed to [block] and the other is
 * cancelled. A stalled connection is thus replaced as with a header timeout,
 * while a congested server that answers after the hedge delay (but within the
 * caller's attempt budget) still succeeds on the first request instead of
 * failing every attempt at the same deadline. A failure before headers waits
 * for the other request; when both fail, the later failure is thrown.
 */
internal suspend fun <T> executeWithHeaderHedge(
    hedgeAfterMillis: Long,
    statement: suspend () -> HttpStatement,
    block: suspend (HttpResponse) -> T
): T = supervisorScope {
    val headersOwner = CompletableDeferred<Int>()
    val requests = ArrayList<Deferred<T>>(2)
    fun start(): Deferred<T> {
        val index = requests.size
        return async {
            statement().execute { response ->
                if (!headersOwner.complete(index)) {
                    throw CancellationException("Another request received headers first")
                }
                block(response)
            }
        }.also(requests::add)
    }
    try {
        val first = start()
        val hedgeDue = withTimeoutOrNull(hedgeAfterMillis.coerceAtLeast(1L)) {
            select<Unit> {
                headersOwner.onAwait {}
                first.onJoin {}
            }
        } == null
        if (hedgeDue) start()
        var lastEnded: Deferred<T> = requests.last()
        while (!headersOwner.isCompleted && requests.any { !it.isCompleted }) {
            select<Unit> {
                headersOwner.onAwait {}
                requests.filter { !it.isCompleted }.forEach { request ->
                    request.onJoin { lastEnded = request }
                }
            }
        }
        if (headersOwner.isCompleted) {
            requests[headersOwner.getCompleted()].await()
        } else {
            // No request received headers: rethrow the failure that ended last.
            lastEnded.await()
        }
    } finally {
        requests.forEach { it.cancel() }
    }
}

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
    return executeWithHeaderHedge(
        request.headerTimeoutMillis,
        statement = {
            client.prepareGet(request.url) {
                applyHttpBoardApiTextGetHeaders(request, userAgent, accept, acceptLanguage)
            }
        }
    ) { response ->
        readHttpBoardApiTextResponse(response, request, readSmallResponseSummary,
            readResponseBodyAsString, readResponseHeadAsString)
    }
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
    return executeWithHeaderHedge(
        request.headerTimeoutMillis,
        statement = {
            client.prepareGet(request.url) {
                applyHttpBoardApiTextGetHeaders(request, userAgent, accept, acceptLanguage)
                val etag = sentValidators?.etag?.takeIf { it.isNotBlank() }
                if (etag != null) {
                    headers[HttpHeaders.IfNoneMatch] = etag
                } else {
                    sentValidators?.lastModified?.takeIf { it.isNotBlank() }?.let {
                        headers[HttpHeaders.IfModifiedSince] = it
                    }
                }
            }
        }
    ) { response ->
        if (sentValidators != null && response.status == HttpStatusCode.NotModified) {
            runCatching { response.bodyAsChannel().cancel(null) }
            return@executeWithHeaderHedge ConditionalTextFetchResult.NotModified
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
