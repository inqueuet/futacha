package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.media.source.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.IOException
import okio.use

internal fun isSupportedMediaSaveSource(url: String): Boolean {
    val value = url.trim()
    return value.startsWith("https://", true) || value.startsWith("http://", true) ||
        isAbsoluteLocalMediaPath(value) || value.startsWith("file:/", true) || value.startsWith("content://", true)
}

internal fun isAbsoluteLocalMediaPath(value: String): Boolean = value.startsWith('/') ||
    value.startsWith("\\\\") || (value.length >= 3 && value[0].isLetter() && value[1] == ':' && value[2] in "/\\")

internal fun localMediaFileUri(path: String): String {
    require(isAbsoluteLocalMediaPath(path))
    val normalized = if (path.startsWith('/')) path else path.replace('\\', '/')
    val encoded = buildString {
        for (byte in normalized.encodeToByteArray()) {
            val value = byte.toInt() and 255
            val char = value.toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~/:") append(char)
            else { append('%'); append("0123456789ABCDEF"[value ushr 4]); append("0123456789ABCDEF"[value and 15]) }
        }
    }
    return when {
        normalized.startsWith("//") -> "file:$encoded"
        normalized.startsWith('/') -> "file://$encoded"
        else -> "file:///$encoded"
    }
}

internal fun localMediaSavePath(url: String): String {
    if (url.startsWith("file:/", true) && !url.startsWith("file://", true)) {
        return localMediaSavePath("file://" + url.substring(5))
    }
    if (!url.startsWith("file://", true)) return url
    val address = url.substring(7)
    val slash = address.indexOf('/')
    val authority = if (slash >= 0) address.substring(0, slash) else address
    val path = if (slash >= 0) address.substring(slash).decodeURLPart() else ""
    if (authority.isNotEmpty() && !authority.equals("localhost", true)) return "//$authority$path"
    return if (path.length >= 4 && path[0] == '/' && path[1].isLetter() && path[2] == ':' && path[3] == '/') path.drop(1) else path
}

internal data class MediaSaveSource(
    val contentType: ContentType?,
    val declaredSize: Long,
    val read: suspend (ByteArray) -> Int
)

/** The shared source owns network retries; callers must not retry the whole save. */
internal class OriginalMediaSaveFailure(cause: Throwable) : IOException(cause.message ?: "Original media save failed", cause)

internal suspend fun <T> withOriginalMediaSaveSourceOrElse(
    originalMediaSource: OriginalMediaSource?,
    url: String,
    block: suspend (MediaSaveSource) -> T,
    fallback: suspend () -> T
): T {
    if (originalMediaSource == null || !isSharedOriginalMediaUrl(url)) return fallback()
    val lease = try {
        originalMediaSource.acquireForExport(OriginalMediaRequest(url))
    } catch (_: OriginalMediaCacheUnavailable) {
        // Cache opening failed BEFORE any HTTP: preserve the legacy streaming save.
        return fallback()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw OriginalMediaSaveFailure(failure)
    }
    try {
        return withContext(AppDispatchers.io) {
            lease.fileSystem.openReadOnly(lease.file).use { handle ->
                var position = 0L
                block(MediaSaveSource(
                    contentType = lease.info.mimeType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
                    declaredSize = lease.info.sizeBytes,
                    read = { buffer ->
                        currentCoroutineContext().ensureActive()
                        val count = handle.read(position, buffer, 0, buffer.size)
                        if (count > 0) position += count
                        count
                    }
                ))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw OriginalMediaSaveFailure(failure)
    } finally {
        lease.close()
    }
}

internal suspend fun <T> withMediaSaveSource(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    url: String,
    originalMediaSource: OriginalMediaSource? = null,
    block: suspend (MediaSaveSource) -> T
): T {
    require(isSupportedMediaSaveSource(url)) { "このメディアURLは保存に対応していません" }
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
        val path = localMediaSavePath(url)
        val size = fileSystem.getFileSize(path)
        return fileSystem.readByteStream(path) { source ->
            block(MediaSaveSource(null, size) { source.read(it) })
        }.getOrThrow()
    }
    return withOriginalMediaSaveSourceOrElse(originalMediaSource, url, block) {
        withRemoteMediaSaveSource(httpClient, url, block)
    }
}

private suspend fun <T> withRemoteMediaSaveSource(
    httpClient: HttpClient,
    url: String,
    block: suspend (MediaSaveSource) -> T
): T {
    val response = withTimeoutOrNull(30_000L) {
        httpClient.get(url) {
            headers[HttpHeaders.Accept] = "image/*,video/*;q=0.9,*/*;q=0.2"
            timeout { requestTimeoutMillis = 15 * 60_000L }
        }
    } ?: error("ダウンロードがタイムアウトしました")
    try {
        check(response.status.isSuccess()) { "保存に失敗しました: HTTP ${response.status.value}" }
        val channel = response.bodyAsChannel()
        return block(MediaSaveSource(
            contentType = response.headers[HttpHeaders.ContentType]?.let { runCatching { ContentType.parse(it) }.getOrNull() },
            declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L,
            read = { channel.readAvailable(it, 0, it.size) }
        ))
    } finally {
        runCatching { response.bodyAsChannel().cancel() }
    }
}
