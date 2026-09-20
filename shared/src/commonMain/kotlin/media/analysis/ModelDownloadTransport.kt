package com.valoser.futacha.shared.media.analysis

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.BufferedSink
import okio.IOException
import okio.Path

internal fun HttpClientConfig<*>.configureModelDownloads() {
    followRedirects = false // Validate every redirect before following it; never allow HTTP downgrade.
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 10 * 60_000
    }
}

/** Owns a fresh cookie-free client, never a clone of the board or media client. */
internal class KtorModelDownloader(private val client: HttpClient = createModelDownloadClient()) : ModelDownloader, AutoCloseable {
    override suspend fun download(distribution: ModelDistribution, sink: BufferedSink) {
        var next = Url(distribution.url)
        repeat(6) { redirects ->
            require(next.protocol == URLProtocol.HTTPS && next.user == null && next.password == null) { "モデル配布URLが不正です" }
            var location: String? = null
            var complete = false
            client.prepareGet(next) {
                headers.append(HttpHeaders.AcceptEncoding, "identity")
            }.execute { response ->
                if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                    location = response.headers[HttpHeaders.Location] ?: throw IOException("モデル配布先を確認できません")
                } else {
                    if (response.status.value != 200) throw IOException("モデルを取得できません（HTTP ${response.status.value}）")
                    val encoding = response.headers[HttpHeaders.ContentEncoding]
                    if (encoding != null && !encoding.equals("identity", ignoreCase = true)) throw IOException("モデルの転送形式が不正です")
                    val length = response.headers[HttpHeaders.ContentLength]
                    if (length != null && length.toLongOrNull() != distribution.bytes) throw IOException("モデルのサイズが一致しません")
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = channel.readAvailable(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        if (n > distribution.bytes - count) throw IOException("モデルのサイズが一致しません")
                        sink.write(buffer, 0, n); sink.emit()
                        count += n
                    }
                    if (count != distribution.bytes) throw IOException("モデルの取得が完了しませんでした")
                    complete = true
                }
            }
            if (complete) return
            if (redirects == 5) throw IOException("モデル配布先の転送回数が多すぎます")
            next = URLBuilder(next).takeFrom(requireNotNull(location)).build()
        }
    }
    override fun close() = client.close()
}

internal expect fun createModelDownloadClient(): HttpClient
internal expect fun modelStoreDirectory(platformContext: Any?): Path
