package com.valoser.futacha.shared.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

internal class HttpBoardApiThreadSafeLruCache<K, V>(private val maxSize: Int) {
    private val mutex = Mutex()
    private val cache: LinkedHashMap<K, V> = LinkedHashMap()

    suspend fun get(key: K): V? = mutex.withLock {
        val value = cache.remove(key) ?: return@withLock null
        cache[key] = value
        value
    }

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

internal class HttpBoardApiPostingRuntime(maxCacheSize: Int) {
    val cache = HttpBoardApiThreadSafeLruCache<String, HttpBoardApiPostingConfig>(maxCacheSize)
    val locksGuard = Mutex()
    val locks = mutableMapOf<String, Mutex>()
}

internal fun shouldRetryHttpBoardApiRequest(error: Exception): Boolean {
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

internal fun resolveHttpBoardApiRefererBaseFromThreadUrl(threadUrl: String): String? {
    return runCatching {
        if (!threadUrl.contains("://")) return null
        val parsed = Url(threadUrl)
        if (parsed.host.isBlank()) return null
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

internal fun shouldAttachHttpBoardApiImage(imageFile: ByteArray?, textOnly: Boolean): Boolean {
    return !textOnly && imageFile != null && imageFile.isNotEmpty()
}

internal fun sanitizeHttpBoardApiUploadFileName(original: String?, defaultFileName: String): String {
    if (original.isNullOrBlank()) return defaultFileName
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
    return sanitized.ifBlank { defaultFileName }
}

internal fun guessHttpBoardApiMediaContentType(
    fileName: String,
    webpContentType: ContentType,
    webmContentType: ContentType,
    bmpContentType: ContentType,
    mp4ContentType: ContentType
): ContentType {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "jpg", "jpeg", "jpe" -> ContentType.Image.JPEG
        "png" -> ContentType.Image.PNG
        "gif" -> ContentType.Image.GIF
        "bmp" -> bmpContentType
        "webp" -> webpContentType
        "webm" -> webmContentType
        "mp4" -> mp4ContentType
        else -> ContentType.Application.OctetStream
    }
}

internal suspend fun getOrLoadHttpBoardApiPostingConfig(
    board: String,
    runtime: HttpBoardApiPostingRuntime,
    fallbackChrencValue: String,
    logTag: String,
    fetchPostingConfig: suspend () -> HttpBoardApiPostingConfig
): HttpBoardApiPostingConfig {
    return getOrLoadHttpBoardApiPostingConfig(
        board = board,
        cache = runtime.cache,
        locksGuard = runtime.locksGuard,
        locks = runtime.locks,
        fallbackChrencValue = fallbackChrencValue,
        logTag = logTag,
        fetchPostingConfig = fetchPostingConfig
    )
}
