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
    val safeMaxAttempts = maxAttempts.coerceIn(1, 10)
    val safeAttemptTimeoutMillis = requestAttemptTimeoutMillis.coerceIn(1L, 2L * 60L * 1000L)
    var attempt = 0
    var delayMillis = initialDelayMillis.coerceIn(0L, 5_000L)
    while (true) {
        try {
            return withTimeout(safeAttemptTimeoutMillis) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            attempt += 1
            if (attempt >= safeMaxAttempts) {
                throw NetworkException(
                    "Request timed out after $safeAttemptTimeoutMillis ms (attempts=$attempt)",
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
    locks: MutableMap<String, HttpBoardApiPostingConfigLockEntry>,
    fallbackChrencValue: String,
    logTag: String,
    fetchPostingConfig: suspend () -> HttpBoardApiPostingConfig
): HttpBoardApiPostingConfig {
    cache.get(board)?.let { return it }
    val lockEntry = locksGuard.withLock {
        locks.getOrPut(board) { HttpBoardApiPostingConfigLockEntry(Mutex()) }
            .also { it.holders += 1 }
    }
    return try {
        lockEntry.mutex.withLock {
            cache.get(board)?.let { return@withLock it }
            try {
                val fetched = fetchPostingConfig()
                if (!fetched.fromFallback && fetched.cacheable) {
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
    } finally {
        locksGuard.withLock {
            val current = locks[board]
            if (current === lockEntry) {
                current.holders -= 1
                if (current.holders <= 0 && !current.mutex.isLocked) {
                    locks.remove(board)
                }
            }
        }
    }
}

internal suspend fun fetchHttpBoardApiPostingConfig(
    client: HttpClient,
    board: String,
    threadId: String?,
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
    val url = threadId
        ?.let { BoardUrlResolver.resolveThreadUrl(board, it) }
        ?: buildString {
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
        val hashValue = parseHttpBoardApiInputValue(html, "hash")
        val ptuaValue = parseHttpBoardApiInputValue(html, "ptua")
        val maxFileSizeBytes = parseHttpBoardApiInputValue(html, "MAX_FILE_SIZE")?.toLongOrNull()
        val supportedExtensions = parseHttpBoardApiPostingExtensions(html)
        if (chrencValue == null) {
            Logger.w(logTag, "chrenc not found in posting config response for '$board'; using temporary fallback")
        }
        return resolveHttpBoardApiPostingConfig(
            chrencValue = chrencValue,
            fallbackChrencValue = fallbackChrencValue,
            hashValue = hashValue,
            ptuaValue = ptuaValue,
            maxFileSizeBytes = maxFileSizeBytes,
            supportedExtensions = supportedExtensions,
            cacheable = hashValue == null && ptuaValue == null
        )
    } finally {
        // Body lifecycle is managed in readResponseBodyAsString.
    }
}

private val HTTP_BOARD_API_ATTACHMENT_LINE_REGEX = Regex(
    pattern = "添付可能[：:]?\\s*([^<\\r\\n]{1,160})",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val HTTP_BOARD_API_ATTACHMENT_EXTENSION_REGEX = Regex(
    pattern = "(?i)(?:^|[^A-Z0-9])(GIF|JPE?G|PNG|WEBP|WEBM|MP4)(?=$|[^A-Z0-9])"
)

internal fun parseHttpBoardApiPostingExtensions(html: String): Set<String> {
    val line = HTTP_BOARD_API_ATTACHMENT_LINE_REGEX.find(html)?.groupValues?.getOrNull(1).orEmpty()
    return HTTP_BOARD_API_ATTACHMENT_EXTENSION_REGEX.findAll(line)
        .map { it.groupValues[1].lowercase() }
        .flatMap { extension ->
            if (extension == "jpg" || extension == "jpeg") sequenceOf("jpg", "jpeg")
            else sequenceOf(extension)
        }
        .toSet()
}

private fun nextHttpBoardApiRetryDelayMillis(delayMillis: Long): Long {
    return if (delayMillis >= 2_500L) {
        5_000L
    } else {
        (delayMillis * 2).coerceAtMost(5_000L)
    }
}
