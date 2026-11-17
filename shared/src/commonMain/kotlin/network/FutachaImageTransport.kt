package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSIONS
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.decodeURLPart

/** Application-owned image transport. It cannot be passed to BoardApi as an HttpClient. */
class FutachaImageTransport internal constructor(internal val client: HttpClient) {
    val originalMediaSession = com.valoser.futacha.shared.media.source.createOriginalMediaSession(client)
    fun close() {
        originalMediaSession.close()
        client.close()
    }
}

internal class ImageRequestRejected : IllegalArgumentException("Not an image transport request")

internal fun isAllowedImageRequest(method: HttpMethod, url: Url): Boolean {
    if (method != HttpMethod.Get && method != HttpMethod.Head) return false
    if (url.protocol.name !in setOf("http", "https") || url.host.isBlank()) return false
    // All network media formats accepted by the parser/viewer use static media
    // paths. Keep signed query strings untouched. A script followed by a fake
    // image suffix/path must not enter the reconnecting client either.
    val path = runCatching { url.encodedPath.decodeURLPart().lowercase() }.getOrElse { return false }
    if ('%' in path || '\\' in path || path.split('/').any { segment ->
            segment.contains(".php") || segment.contains(".cgi") || segment == ".."
        }) return false
    return path.substringAfterLast('.', "") in FUTABA_COMPAT_MEDIA_EXTENSIONS
}

private val ImageRequestPolicy = createClientPlugin("FutachaImageRequestPolicy") {
    // Unlike onRequest, this also runs for every redirect/retry send.
    on(SendingRequest) { request, _ ->
        if (!isAllowedImageRequest(request.method, request.url.build())) throw ImageRequestRejected()
    }
}

internal fun HttpClientConfig<*>.configureImageRequests() {
    install(ImageRequestPolicy)
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    // Coil owns the retry budget for headers AND streaming response bodies.
    install(HttpRequestRetry) { maxRetries = 0 }

}

internal expect fun isRetryableImageConnectionFailure(error: Throwable): Boolean
