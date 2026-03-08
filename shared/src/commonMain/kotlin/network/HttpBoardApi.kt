package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.sanitizeForShiftJis
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// FIX: KMP commonMainでも動くスレッドセーフLRUキャッシュ
// NOTE: LinkedHashMapのaccessOrderコンストラクタ/継承がcommonMainで不可なため、手動で順序管理
private class ThreadSafeLruCache<K, V>(private val maxSize: Int) {
    private val mutex = Mutex()
    private val cache: LinkedHashMap<K, V> = LinkedHashMap()

    // FIX: 読み取り操作もMutexで保護（アクセス順を手動で更新）
    suspend fun get(key: K): V? = mutex.withLock {
        val value = cache.remove(key) ?: return@withLock null
        cache[key] = value
        value
    }

    // FIX: 書き込み操作はMutexで保護（サイズ制限は手動適用）
    suspend fun put(key: K, value: V): V? = mutex.withLock {
        val previous = cache.remove(key)
        cache[key] = value
        if (cache.size > maxSize) {
            val eldestKey = cache.entries.firstOrNull()?.key
            if (eldestKey != null) {
                cache.remove(eldestKey)
            }
        }
        previous
    }

    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
}

class HttpBoardApi(
    private val client: HttpClient
) : BoardApi, AutoCloseable {
    companion object {
        private const val TAG = "HttpBoardApi"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
        // FIX: OOM防止のため、20MB→5MBに制限を削減
        // 通常のHTMLレスポンスは1-2MB程度なので5MBで十分
        private const val MAX_RESPONSE_SIZE = 5 * 1024 * 1024 // 5MB limit
        private const val DEFAULT_UPLOAD_FILE_NAME = "upload.bin"
        private const val SHIFT_JIS_TEXT_MIME = "text/plain; charset=Shift_JIS"
        private const val UTF8_TEXT_MIME = "text/plain; charset=UTF-8"
        private const val ASCII_TEXT_MIME = "text/plain; charset=US-ASCII"
        private const val DEFAULT_SHIFT_JIS_CHRENC_SAMPLE = "文字"

        private const val DEFAULT_SCREEN_SPEC = "1080x1920x24"
        private const val DEFAULT_PTUA_VALUE = "1341647872"
        private val WEBP_CONTENT_TYPE = ContentType.parse("image/webp")
        private val WEBM_CONTENT_TYPE = ContentType.parse("video/webm")
        private val BMP_CONTENT_TYPE = ContentType.parse("image/bmp")
        private val MP4_CONTENT_TYPE = ContentType.parse("video/mp4")
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
        private const val REQUEST_ATTEMPT_TIMEOUT_MILLIS = 45_000L
    }

    private suspend fun readResponseBodyAsString(response: HttpResponse): String {
        val bytes = readResponseBytesWithLimit(response, MAX_RESPONSE_SIZE)
        return withContext(AppDispatchers.parsing) {
            TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
        }
    }

    private suspend fun readResponseHeadAsString(response: HttpResponse, maxLines: Int): String {
        val bytes = readResponseHeadBytesWithLimit(response, maxLines)
        return withContext(AppDispatchers.parsing) {
            TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType]).trimEnd('\n', '\r')
        }
    }

    private suspend fun readResponseBytesWithLimit(response: HttpResponse, maxBytes: Int): ByteArray {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes) {
            throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
        }
        return withContext(AppDispatchers.io) {
            withTimeout(RESPONSE_TOTAL_TIMEOUT_MILLIS) {
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(RESPONSE_READ_BUFFER_BYTES)
                var output = ByteArray(minOf(RESPONSE_READ_BUFFER_BYTES, maxBytes.coerceAtLeast(1)))
                var totalBytes = 0
                var zeroReadCount = 0
                var readLoopCount = 0L
                var fullyConsumed = false
                try {
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) {
                            fullyConsumed = true
                            break
                        }
                        if (read == 0) {
                            zeroReadCount += 1
                            if (zeroReadCount >= MAX_ZERO_READ_RETRIES) {
                                throw NetworkException("Response body read stalled")
                            }
                            delay(ZERO_READ_BACKOFF_MILLIS)
                            continue
                        }

                        zeroReadCount = 0
                        val requiredSize = totalBytes + read
                        if (requiredSize > maxBytes) {
                            throw NetworkException("Response size exceeds maximum allowed ($maxBytes bytes)")
                        }
                        if (requiredSize > output.size) {
                            var newSize = output.size
                            while (newSize < requiredSize) {
                                newSize = (newSize * 2).coerceAtMost(maxBytes)
                                if (newSize == output.size) break
                            }
                            if (newSize < requiredSize) {
                                throw NetworkException("Failed to expand response buffer safely")
                            }
                            output = output.copyOf(newSize)
                        }
                        buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = read)
                        totalBytes = requiredSize
                        readLoopCount += 1
                        if (readLoopCount % 32L == 0L) {
                            yield()
                        }
                    }
                    if (totalBytes == output.size) {
                        output
                    } else {
                        output.copyOf(totalBytes)
                    }
                } finally {
                    if (!fullyConsumed) {
                        runCatching { channel.cancel() }
                    }
                }
            }
        }
    }

    private suspend fun readResponseHeadBytesWithLimit(response: HttpResponse, maxLines: Int): ByteArray {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
            throw NetworkException("Response size exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
        }
        if (maxLines <= 0) {
            return readResponseBytesWithLimit(response, MAX_RESPONSE_SIZE)
        }
        return withContext(AppDispatchers.io) {
            withTimeout(RESPONSE_TOTAL_TIMEOUT_MILLIS) {
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(RESPONSE_READ_BUFFER_BYTES)
                var output = ByteArray(RESPONSE_READ_BUFFER_BYTES)
                var totalBytes = 0
                var lineCount = 0
                var zeroReadCount = 0
                var readLoopCount = 0L
                var fullyConsumed = false
                try {
                    reading@ while (true) {
                        coroutineContext.ensureActive()
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) {
                            fullyConsumed = true
                            break
                        }
                        if (read == 0) {
                            zeroReadCount += 1
                            if (zeroReadCount >= MAX_ZERO_READ_RETRIES) {
                                throw NetworkException("Response head read stalled")
                            }
                            delay(ZERO_READ_BACKOFF_MILLIS)
                            continue
                        }

                        zeroReadCount = 0
                        var writeCount = read
                        for (i in 0 until read) {
                            if (buffer[i] == '\n'.code.toByte()) {
                                lineCount += 1
                                if (lineCount >= maxLines) {
                                    writeCount = i + 1
                                    break
                                }
                            }
                        }
                        val requiredSize = totalBytes + writeCount
                        if (requiredSize > MAX_RESPONSE_SIZE) {
                            throw NetworkException("Response size exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                        }
                        if (requiredSize > output.size) {
                            var newSize = output.size
                            while (newSize < requiredSize) {
                                newSize = (newSize * 2).coerceAtMost(MAX_RESPONSE_SIZE)
                                if (newSize == output.size) break
                            }
                            if (newSize < requiredSize) {
                                throw NetworkException("Failed to expand response head buffer safely")
                            }
                            output = output.copyOf(newSize)
                        }
                        buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = writeCount)
                        totalBytes = requiredSize
                        if (lineCount >= maxLines) break@reading
                        readLoopCount += 1
                        if (readLoopCount % 32L == 0L) {
                            yield()
                        }
                    }
                    if (totalBytes == output.size) {
                        output
                    } else {
                        output.copyOf(totalBytes)
                    }
                } finally {
                    if (!fullyConsumed) {
                        runCatching { channel.cancel() }
                    }
                }
            }
        }
    }

    // FIX: リトライロジック改善（無限ループ防止、一時的障害対策）
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3, // FIX: 2→3に増加（一時的なネットワーク障害対策）
        initialDelayMillis: Long = 500,
        block: suspend () -> T
    ): T {
        val safeMaxAttempts = maxAttempts.coerceAtLeast(1)
        var attempt = 0
        var delayMillis = initialDelayMillis.coerceAtLeast(0L)
        while (true) {
            try {
                return withTimeout(REQUEST_ATTEMPT_TIMEOUT_MILLIS) {
                    block()
                }
            } catch (e: TimeoutCancellationException) {
                attempt += 1
                if (attempt >= safeMaxAttempts) {
                    throw NetworkException(
                        "Request timed out after $REQUEST_ATTEMPT_TIMEOUT_MILLIS ms (attempts=$attempt)",
                        cause = e
                    )
                }
                Logger.w(
                    TAG,
                    "Retrying request after timeout on attempt $attempt/$safeMaxAttempts"
                )
                if (delayMillis > 0L) {
                    delay(delayMillis)
                }
                delayMillis = if (delayMillis >= 2_500L) {
                    5_000L
                } else {
                    (delayMillis * 2).coerceAtMost(5_000L)
                }
            } catch (e: CancellationException) {
                // FIX: キャンセル例外は即座に再スロー（リトライしない）
                throw e
            } catch (e: Exception) {
                attempt += 1
                if (attempt >= safeMaxAttempts || !shouldRetry(e)) {
                    throw e
                }
                // FIX: 指数バックオフ（500ms→1s→2s→4s→5s上限）
                Logger.w(TAG, "Retrying request after attempt $attempt due to ${e::class.simpleName}: ${e.message}")
                if (delayMillis > 0L) {
                    delay(delayMillis)
                }
                delayMillis = if (delayMillis >= 2_500L) {
                    5_000L
                } else {
                    (delayMillis * 2).coerceAtMost(5_000L)
                }
            }
        }
    }

    private fun shouldRetry(error: Exception): Boolean {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is CancellationException -> return false
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is IOException -> return true
                is NetworkException -> {
                    val status = current.statusCode
                    if (status != null && (status == 429 || status >= 500)) {
                        return true
                    }
                }
                is ResponseException -> {
                    val status = current.response.status.value
                    if (status == 429 || status >= 500) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return false
    }

    // FIX: スレッドセーフなLRUキャッシュに変更
    private val postingConfigCache = ThreadSafeLruCache<String, PostingConfig>(MAX_CACHE_SIZE)
    private val postingConfigLocksGuard = Mutex()
    private val postingConfigLocks = mutableMapOf<String, Mutex>()

    override suspend fun fetchCatalogSetup(board: String) {
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?mode=catset")
        }
        try {
            withRetry {
                val response: HttpResponse = client.submitForm(
                    url = url,
                    formParameters = Parameters.build {
                        append("mode", "catset")
                        append("cx", "5")   // カタログの横サイズ
                        append("cy", "60")  // カタログの縦サイズ
                        append("cl", "4")   // 文字数
                        append("cm", "0")   // 文字位置 (0=下, 1=右)
                        append("ci", "0")   // 画像サイズ (0=小)
                        append("vh", "on")  // 見たスレッドを見歴に追加
                    }
                ) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "max-age=0"
                    headers[HttpHeaders.Referrer] = url
                }

                try {
                    if (!response.status.isSuccess()) {
                        val detail = readSmallResponseSummary(response)
                        val suffix = detail?.let { ": $it" }.orEmpty()
                        val errorMsg = "HTTP error ${response.status.value} when fetching catalog setup from $url$suffix"
                        Logger.w(TAG, errorMsg)
                        throw NetworkException(errorMsg, response.status.value)
                    }
                    // Drain body so pooled connection is released promptly.
                    readSmallResponseSummary(response)
                } finally {
                    // Body is already drained by readSmallResponseSummary.
                }

                // Cookies (posttime, cxyl, etc.) are automatically stored by HttpCookies plugin
                Logger.i(TAG, "Catalog setup cookies initialized for board=$board (cx=5, cy=60)")
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch catalog setup from $url: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchCatalog(board: String, mode: CatalogMode): String {
        val url = BoardUrlResolver.resolveCatalogUrl(board, mode)
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    headers[HttpHeaders.Referrer] = board
                }

                try {
                    if (!response.status.isSuccess()) {
                        val detail = readSmallResponseSummary(response)
                        val suffix = detail?.let { ": $it" }.orEmpty()
                        val errorMsg = "HTTP error ${response.status.value} when fetching catalog from $url$suffix"
                        Logger.w(TAG, errorMsg)
                        throw NetworkException(errorMsg, response.status.value)
                    }

                    // Check content length before reading
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                        throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                    }

                    readResponseBodyAsString(response)
                } finally {
                    // Body lifecycle is managed in readResponseBodyAsString.
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch catalog from $url: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThreadHead(board: String, threadId: String, maxLines: Int): String {
        require(maxLines > 0) { "maxLines must be positive" }
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val estimatedRangeBytes = (maxLines * 4096).coerceIn(
            MIN_THREAD_HEAD_RANGE_BYTES,
            MAX_THREAD_HEAD_RANGE_BYTES
        )
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                        if (base.endsWith("/")) base else "$base/"
                    }
                    headers[HttpHeaders.Referrer] = refererBase
                    headers[HttpHeaders.Range] = "bytes=0-${estimatedRangeBytes - 1}"
                }

                try {
                    if (!response.status.isSuccess()) {
                        val detail = readSmallResponseSummary(response)
                        val suffix = detail?.let { ": $it" }.orEmpty()
                        val errorMsg = "HTTP error ${response.status.value} when fetching thread head from $url$suffix"
                        Logger.w(TAG, errorMsg)
                        throw NetworkException(errorMsg, response.status.value)
                    }

                    readResponseHeadAsString(response, maxLines)
                } finally {
                    // We intentionally read only the response head; close remaining body.
                    runCatching { response.bodyAsChannel().cancel(null) }
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread head from $url: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThread(board: String, threadId: String): String {
        val url = BoardUrlResolver.resolveThreadUrl(board, threadId)
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    val refererBase = BoardUrlResolver.resolveBoardBaseUrl(board).let { base ->
                        if (base.endsWith("/")) base else "$base/"
                    }
                    headers[HttpHeaders.Referrer] = refererBase
                }

                try {
                    if (!response.status.isSuccess()) {
                        val detail = readSmallResponseSummary(response)
                        val suffix = detail?.let { ": $it" }.orEmpty()
                        val errorMsg = "HTTP error ${response.status.value} when fetching thread from $url$suffix"
                        Logger.w(TAG, errorMsg)
                        throw NetworkException(errorMsg, response.status.value)
                    }

                    // Check content length before reading
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                        throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                    }

                    readResponseBodyAsString(response)
                } finally {
                    // Body lifecycle is managed in readResponseBodyAsString.
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread from $url: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
        }
    }

    override suspend fun fetchThreadByUrl(threadUrl: String): String {
        val url = threadUrl
        return try {
            withRetry {
                val response: HttpResponse = client.get(url) {
                    headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                    headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                    headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                    headers[HttpHeaders.CacheControl] = "no-cache"
                    headers[HttpHeaders.Pragma] = "no-cache"
                    resolveRefererBaseFromThreadUrl(url)?.let { referer ->
                        headers[HttpHeaders.Referrer] = referer
                    }
                }

                try {
                    if (!response.status.isSuccess()) {
                        val detail = readSmallResponseSummary(response)
                        val suffix = detail?.let { ": $it" }.orEmpty()
                        val errorMsg = "HTTP error ${response.status.value} when fetching thread from $url$suffix"
                        Logger.w(TAG, errorMsg)
                        throw NetworkException(errorMsg, response.status.value)
                    }

                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_RESPONSE_SIZE) {
                        throw NetworkException("Response size ($contentLength bytes) exceeds maximum allowed ($MAX_RESPONSE_SIZE bytes)")
                    }

                    readResponseBodyAsString(response)
                } finally {
                    // Body lifecycle is managed in readResponseBodyAsString.
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch thread from $url: ${e.message}"
            Logger.e(TAG, errorMsg, e)
            throw NetworkException(errorMsg, cause = e)
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
            val response: HttpResponse = client.get(url) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = "*/*"
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
            try {
                if (!response.status.isSuccess()) {
                    val detail = readSmallResponseSummary(response)
                    val suffix = detail?.let { ": $it" }.orEmpty()
                    throw NetworkException("そうだね投票に失敗しました (HTTP ${response.status.value}$suffix)")
                }
                val result = readResponseBodyAsString(response).trim()
                if (result != "1") {
                    throw NetworkException("そうだね投票に失敗しました (応答: '$result')")
                }
            } finally {
                // Body lifecycle is managed in readResponseBodyAsString.
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to vote saidane: ${e.message}", cause = e)
        }
    }

    private fun resolveRefererBaseFromThreadUrl(threadUrl: String): String? {
        return runCatching {
            val parsed = Url(threadUrl)
            val segments = parsed.encodedPath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null
            val baseSegments = segments.dropLast(1)
            val path = if (baseSegments.isEmpty()) "" else "/" + baseSegments.joinToString("/")
            buildString {
                append(parsed.protocol.name)
                append("://")
                append(parsed.host)
                if (parsed.port != parsed.protocol.defaultPort) {
                    append(":${parsed.port}")
                }
                append(path.trimEnd('/'))
                append("/")
            }
        }.getOrNull()
    }

    private suspend fun readSmallResponseSummary(response: HttpResponse): String? {
        val bytes = try {
            readResponseBytesWithLimit(response, 64 * 1024)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        val decoded = try {
            TextEncoding.decodeToString(bytes, response.headers[HttpHeaders.ContentType])
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        return decoded
            .trim()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.take(160)
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
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val response = try {
            client.submitForm(
                url = "$siteRoot/del.php",
                formParameters = Parameters.build {
                    append("mode", "post")
                    append("b", boardSlug)
                    append("d", sanitizedPostId)
                    append("reason", reasonCode)
                    append("responsemode", "ajax")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to send del request: ${e.message}", cause = e)
        }
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException("del依頼に失敗しました (HTTP ${response.status.value}$suffix)")
            }
            // Drain short response body to promptly release underlying connection.
            readSmallResponseSummary(response)
        } finally {
            // Body is already drained by readSmallResponseSummary.
        }
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
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val response = try {
            client.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("guid", "on")
                    // Futaba variants exist in the wild: send both forms for compatibility.
                    append("delete", sanitizedPostId)
                    append(sanitizedPostId, "delete")
                    append("responsemode", "ajax")
                    append("pwd", password)
                    append("onlyimgdel", if (imageOnly) "on" else "")
                    append("mode", "usrdel")
                }
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to delete post: ${e.message}", cause = e)
        }
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException("本人削除に失敗しました (HTTP ${response.status.value}$suffix)")
            }
            // Drain short response body to promptly release underlying connection.
            readSmallResponseSummary(response)
        } finally {
            // Body is already drained by readSmallResponseSummary.
        }
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
        textOnly: Boolean
    ): String? {
        // FIX: 入力検証を最初に実行
        validateHttpBoardApiPostInput(name, email, subject, comment, password, imageFile)
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.htm")
        }
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val postingConfig = getPostingConfig(board)
        val formData = buildPostFormData(
            threadId = null,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly,
            postingConfig = postingConfig,
            forceAjaxResponse = true
        )
        val response = try {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to create thread: ${e.message}", cause = e)
        }
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException("スレッド作成に失敗しました (HTTP ${response.status.value}$suffix)")
            }

            // Parse response to extract thread ID
            val responseBody = readResponseBodyAsString(response)
            val extractedThreadId = tryExtractThreadId(responseBody)
            if (!extractedThreadId.isNullOrBlank()) {
                return extractedThreadId
            }
            val jsonThreadId = tryParseThreadIdFromJson(responseBody)
            if (jsonThreadId != null) {
                return jsonThreadId
            }
            val errorDetail = extractServerError(responseBody)
            val summary = summarizeResponse(responseBody)
            if (errorDetail != null) {
                throw NetworkException("スレッド作成に失敗しました: $errorDetail")
            }
            if (isSuccessfulPostResponse(responseBody)) {
                com.valoser.futacha.shared.util.Logger.w("HttpBoardApi", "Thread created but thread ID was not found in response")
                return null
            }
            throw NetworkException("スレッドIDの取得に失敗しました: $summary")
        } finally {
            // Body lifecycle is managed in readResponseBodyAsString.
        }
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
        textOnly: Boolean
    ): String? {
        // FIX: 入力検証を最初に実行
        validateHttpBoardApiPostInput(name, email, subject, comment, password, imageFile)
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val referer = BoardUrlResolver.resolveThreadUrl(board, threadId)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.php?guid=on")
        }
        val postingConfig = getPostingConfig(board)
        val formData = buildPostFormData(
            threadId = threadId,
            name = name,
            email = email,
            subject = subject,
            comment = comment,
            password = password,
            imageFile = imageFile,
            imageFileName = imageFileName,
            textOnly = textOnly,
            postingConfig = postingConfig
        )
        val response = try {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData
            ) {
                headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
                headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
                headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
                headers[HttpHeaders.CacheControl] = "no-cache"
                headers[HttpHeaders.Pragma] = "no-cache"
                headers[HttpHeaders.Referrer] = referer
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Failed to reply to thread: ${e.message}", cause = e)
        }
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException("返信に失敗しました (HTTP ${response.status.value}$suffix)")
            }
            val responseBody = readResponseBodyAsString(response)
            if (!isSuccessfulPostResponse(responseBody)) {
                val errorDetail = extractServerError(responseBody)
                val summary = summarizeResponse(responseBody)
                val detail = errorDetail ?: summary
                throw NetworkException("返信に失敗しました: $detail")
            }
            return tryExtractThisNo(responseBody)
        } finally {
            // Body lifecycle is managed in readResponseBodyAsString.
        }
    }

    // SECURITY NOTE: パスワードはStringで渡されるため、メモリダンプから漏洩する可能性があります。
    // 将来的な改善案:
    // 1. CharArrayを使用してパスワードを渡し、使用後即座にクリア
    // 2. プラットフォーム固有のSecureString実装を使用
    // 3. パスワードの保持時間を最小化
    // 現状では、呼び出し元がパスワードの寿命管理を行う必要があります。
    private fun buildPostFormData(
        threadId: String?,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean,
        postingConfig: PostingConfig,
        forceAjaxResponse: Boolean = false
    ) = formData {
        appendAsciiField("guid", "on")
        appendAsciiField("mode", "regist")
        appendAsciiField("MAX_FILE_SIZE", "8192000")
        appendTextField("name", name, postingConfig.encoding)
        appendTextField("email", email, postingConfig.encoding)
        appendTextField("sub", subject, postingConfig.encoding)
        appendTextField("com", comment, postingConfig.encoding)
        appendTextField("pwd", password, postingConfig.encoding)
        appendTextField("chrenc", postingConfig.chrencValue, postingConfig.encoding)
        appendAsciiField("js", "on")
        appendAsciiField("baseform", "")
        appendAsciiField("pthb", "")
        appendAsciiField("pthc", generateClientTimestampSeed())
        appendAsciiField("pthd", "")
        appendAsciiField("ptua", DEFAULT_PTUA_VALUE)
        appendAsciiField("scsz", DEFAULT_SCREEN_SPEC)
        appendAsciiField("hash", generateClientHash())
        threadId?.let {
            appendAsciiField("resto", it)
            appendAsciiField("responsemode", "ajax")
        } ?: run {
            if (forceAjaxResponse) {
                appendAsciiField("responsemode", "ajax")
            }
        }

        val attachImage = shouldAttachImage(imageFile, textOnly)
        if (attachImage) {
            val safeName = sanitizeFileName(imageFileName)
            // FIX: requireNotNullの代わりにエルビス演算子で安全にフォールバック
            // shouldAttachImageがtrueならimageFileはnullでないはずだが、
            // 防御的プログラミングとして空配列にフォールバックする
            val fileData = imageFile ?: ByteArray(0)
            if (fileData.isEmpty()) {
                com.valoser.futacha.shared.util.Logger.w("HttpBoardApi", "imageFile is unexpectedly null or empty when attachImage is true")
            }
            append(
                "upfile",
                fileData,
                Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        """form-data; name="upfile"; filename="$safeName""""
                    )
                    append(HttpHeaders.ContentType, guessMediaContentType(safeName).toString())
                }
            )
        } else {
            append("textonly", "on")
            append(
                "upfile",
                ByteArray(0),
                Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        """form-data; name="upfile"; filename="""
                    )
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                }
            )
        }
    }

    private fun shouldAttachImage(imageFile: ByteArray?, textOnly: Boolean): Boolean {
        return !textOnly && imageFile != null && imageFile.isNotEmpty()
    }

    private fun sanitizeFileName(original: String?): String {
        if (original.isNullOrBlank()) return DEFAULT_UPLOAD_FILE_NAME
        val trimmed = original.trim()
        val sanitized = buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '.' || ch == '-' || ch == '_' -> append(ch)
                    ch.isWhitespace() -> append('_')
                    else -> append('_')
                }
            }
        }
        return sanitized.ifBlank { DEFAULT_UPLOAD_FILE_NAME }
    }

    private fun guessMediaContentType(fileName: String): ContentType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "jpe" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "bmp" -> BMP_CONTENT_TYPE
            "webp" -> WEBP_CONTENT_TYPE
            "webm" -> WEBM_CONTENT_TYPE
            "mp4" -> MP4_CONTENT_TYPE
            else -> ContentType.Application.OctetStream
        }
    }

    private fun isSuccessfulPostResponse(body: String): Boolean {
        return isSuccessfulHttpBoardApiPostResponse(body)
    }

    private fun extractServerError(body: String): String? {
        return extractHttpBoardApiServerError(body)
    }

    private fun summarizeResponse(body: String): String {
        return summarizeHttpBoardApiResponse(body)
    }

    private fun tryParseThreadIdFromJson(body: String): String? {
        return tryParseHttpBoardApiThreadIdFromJson(body)
    }

    private fun tryExtractThreadId(body: String): String? {
        return tryExtractHttpBoardApiThreadId(body)
    }

    private fun tryExtractThisNo(body: String): String? {
        return tryExtractHttpBoardApiThisNo(body)
    }

    private fun looksLikeJson(body: String): Boolean {
        return looksLikeHttpBoardApiJson(body)
    }

    private fun isJsonStatusOk(body: String): Boolean {
        return isHttpBoardApiJsonStatusOk(body)
    }

    private fun FormBuilder.appendTextField(name: String, value: String, encoding: PostEncoding) {
        val normalizedValue = when (encoding) {
            PostEncoding.SHIFT_JIS -> {
                val sanitized = sanitizeForShiftJis(value)
                if (sanitized.escapedCodePointCount > 0 || sanitized.removedCodePointCount > 0) {
                    Logger.w(
                        TAG,
                        "Escaped ${sanitized.escapedCodePointCount} and removed ${sanitized.removedCodePointCount} unsupported Shift_JIS character(s) from '$name'"
                    )
                }
                sanitized.sanitizedText
            }
            PostEncoding.UTF8 -> value
        }
        val (bytes, contentType) = when (encoding) {
            PostEncoding.SHIFT_JIS -> TextEncoding.encodeToShiftJis(normalizedValue) to SHIFT_JIS_TEXT_MIME
            PostEncoding.UTF8 -> normalizedValue.encodeToByteArray() to UTF8_TEXT_MIME
        }
        append(
            name,
            bytes,
            Headers.build {
                append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
                append(HttpHeaders.ContentType, contentType)
            }
        )
    }

    private fun FormBuilder.appendAsciiField(name: String, value: String) {
        append(
            name,
            value,
            Headers.build {
                append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
                append(HttpHeaders.ContentType, ASCII_TEXT_MIME)
            }
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun generateClientHash(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val randomHex = buildString(32) {
            repeat(16) {
                val value = Random.nextInt(0, 256)
                append(value.toString(16).padStart(2, '0'))
            }
        }
        return "$timestamp-$randomHex"
    }

    @OptIn(ExperimentalTime::class)
    private fun generateClientTimestampSeed(): String =
        Clock.System.now().toEpochMilliseconds().toString()

    private suspend fun postingConfigLockFor(board: String): Mutex {
        return postingConfigLocksGuard.withLock {
            postingConfigLocks.getOrPut(board) { Mutex() }
        }
    }

    private suspend fun getPostingConfig(board: String): PostingConfig {
        postingConfigCache.get(board)?.let { return it }
        val boardLock = postingConfigLockFor(board)
        return boardLock.withLock {
            postingConfigCache.get(board)?.let { return@withLock it }
            try {
                val fetched = fetchPostingConfig(board)
                if (!fetched.fromFallback) {
                    postingConfigCache.put(board, fetched)
                }
                fetched
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(
                    TAG,
                    "Failed to fetch posting config for board '$board', using non-cached Shift_JIS fallback: ${e.message}"
                )
                PostingConfig(
                    encoding = PostEncoding.SHIFT_JIS,
                    chrencValue = DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
                    fromFallback = true
                )
            }
        }
    }

    private suspend fun fetchPostingConfig(board: String): PostingConfig {
        val boardBase = BoardUrlResolver.resolveBoardBaseUrl(board)
        val url = buildString {
            append(boardBase)
            if (!boardBase.endsWith("/")) append('/')
            append("futaba.htm")
        }
        val response = client.get(url) {
            headers[HttpHeaders.UserAgent] = DEFAULT_USER_AGENT
            headers[HttpHeaders.Accept] = DEFAULT_ACCEPT
            headers[HttpHeaders.AcceptLanguage] = DEFAULT_ACCEPT_LANGUAGE
            headers[HttpHeaders.CacheControl] = "no-cache"
        }
        try {
            if (!response.status.isSuccess()) {
                val detail = readSmallResponseSummary(response)
                val suffix = detail?.let { ": $it" }.orEmpty()
                throw NetworkException("HTTP error ${response.status.value} when fetching posting config from $url$suffix")
            }
            val html = readResponseBodyAsString(response)
            val chrencValue = parseChrencValue(html)
            if (chrencValue == null) {
                Logger.w(TAG, "chrenc not found in posting config response for '$board'; using temporary fallback")
            }
            return PostingConfig(
                encoding = determineEncoding(chrencValue ?: DEFAULT_SHIFT_JIS_CHRENC_SAMPLE),
                chrencValue = chrencValue ?: DEFAULT_SHIFT_JIS_CHRENC_SAMPLE,
                fromFallback = chrencValue == null
            )
        } finally {
            // Body lifecycle is managed in readResponseBodyAsString.
        }
    }

    private fun parseChrencValue(html: String): String? {
        return parseHttpBoardApiChrencValue(html)
    }

    private fun decodeNumericEntities(value: String): String {
        return decodeHttpBoardApiNumericEntities(value)
    }

    private fun determineEncoding(chrencValue: String): PostEncoding {
        return when (determineHttpBoardApiEncoding(chrencValue)) {
            HttpBoardApiPostEncoding.UTF8 -> PostEncoding.UTF8
            HttpBoardApiPostEncoding.SHIFT_JIS -> PostEncoding.SHIFT_JIS
        }
    }

    private data class PostingConfig(
        val encoding: PostEncoding,
        val chrencValue: String,
        val fromFallback: Boolean = false
    )

    private enum class PostEncoding {
        SHIFT_JIS,
        UTF8
    }

    override fun close() {
        client.close()
    }
}
