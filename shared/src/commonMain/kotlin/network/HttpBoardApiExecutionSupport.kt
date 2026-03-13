package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal suspend fun <T> withHttpBoardApiRetry(
    logTag: String,
    requestAttemptTimeoutMillis: Long,
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 500,
    block: suspend () -> T
): T {
    val safeMaxAttempts = maxAttempts.coerceAtLeast(1)
    var attempt = 0
    var delayMillis = initialDelayMillis.coerceAtLeast(0L)
    while (true) {
        try {
            return withTimeout(requestAttemptTimeoutMillis) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            attempt += 1
            if (attempt >= safeMaxAttempts) {
                throw NetworkException(
                    "Request timed out after $requestAttemptTimeoutMillis ms (attempts=$attempt)",
                    cause = e
                )
            }
            Logger.w(logTag, "Retrying request after timeout on attempt $attempt/$safeMaxAttempts")
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            delayMillis = nextHttpBoardApiRetryDelayMillis(delayMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempt += 1
            if (attempt >= safeMaxAttempts || !shouldRetryHttpBoardApiRequest(e)) {
                throw e
            }
            Logger.w(
                logTag,
                "Retrying request after attempt $attempt due to ${e::class.simpleName}: ${e.message}"
            )
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            delayMillis = nextHttpBoardApiRetryDelayMillis(delayMillis)
        }
    }
}

internal suspend fun getOrLoadHttpBoardApiPostingConfig(
    board: String,
    cache: HttpBoardApiThreadSafeLruCache<String, HttpBoardApiPostingConfig>,
    locksGuard: Mutex,
    locks: MutableMap<String, Mutex>,
    fallbackChrencValue: String,
    logTag: String,
    fetchPostingConfig: suspend () -> HttpBoardApiPostingConfig
): HttpBoardApiPostingConfig {
    cache.get(board)?.let { return it }
    val boardLock = locksGuard.withLock {
        locks.getOrPut(board) { Mutex() }
    }
    return boardLock.withLock {
        cache.get(board)?.let { return@withLock it }
        try {
            val fetched = fetchPostingConfig()
            if (!fetched.fromFallback) {
                cache.put(board, fetched)
            }
            fetched
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(
                logTag,
                "Failed to fetch posting config for board '$board', using non-cached Shift_JIS fallback: ${e.message}"
            )
            fallbackHttpBoardApiPostingConfig(fallbackChrencValue)
        }
    }
}

internal suspend fun fetchHttpBoardApiPostingConfig(
    client: HttpClient,
    board: String,
    userAgent: String,
    accept: String,
    acceptLanguage: String,
    cacheControl: String,
    logTag: String,
    fallbackChrencValue: String,
    readSmallResponseSummary: suspend (HttpResponse) -> String?,
    readResponseBodyAsString: suspend (HttpResponse) -> String
): HttpBoardApiPostingConfig {
    val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
    val url = buildString {
        append(boardBase)
        if (!boardBase.endsWith("/")) append('/')
        append("futaba.htm")
    }
    val response = client.get(url) {
        headers[HttpHeaders.UserAgent] = userAgent
        headers[HttpHeaders.Accept] = accept
        headers[HttpHeaders.AcceptLanguage] = acceptLanguage
        headers[HttpHeaders.CacheControl] = cacheControl
    }
    try {
        if (!response.status.isSuccess()) {
            val detail = readSmallResponseSummary(response)
            val suffix = detail?.let { ": $it" }.orEmpty()
            throw NetworkException("HTTP error ${response.status.value} when fetching posting config from $url$suffix")
        }
        val html = readResponseBodyAsString(response)
        val chrencValue = parseHttpBoardApiChrencValue(html)
        if (chrencValue == null) {
            Logger.w(logTag, "chrenc not found in posting config response for '$board'; using temporary fallback")
        }
        return resolveHttpBoardApiPostingConfig(
            chrencValue = chrencValue,
            fallbackChrencValue = fallbackChrencValue
        )
    } finally {
        // Body lifecycle is managed in readResponseBodyAsString.
    }
}

private fun nextHttpBoardApiRetryDelayMillis(delayMillis: Long): Long {
    return if (delayMillis >= 2_500L) {
        5_000L
    } else {
        (delayMillis * 2).coerceAtMost(5_000L)
    }
}
