package com.valoser.futacha.shared.util

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

internal fun isSupportedMediaSaveSource(url: String): Boolean {
    val value = url.trim()
    return value.startsWith("https://", true) || value.startsWith("http://", true) ||
        value.startsWith('/') || value.startsWith("file:///", true) || value.startsWith("content://", true)
}

internal fun localMediaSavePath(url: String): String =
    if (url.startsWith("file:///", true)) url.substring(7).decodeURLPart() else url

internal data class MediaSaveSource(
    val contentType: ContentType?,
    val declaredSize: Long,
    val read: suspend (ByteArray) -> Int
)

internal suspend fun <T> withMediaSaveSource(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    url: String,
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
