package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.CancellationException

actual fun createHttpClient(platformContext: Any?, cookieStorage: CookiesStorage?): HttpClient = HttpClient(OkHttp) {
    install(HttpRequestRetry) {
        maxRetries = 2
        exponentialDelay()
        retryIf(maxRetries) { request, response ->
            shouldUseClientAutomaticRetry(request.method, request.attributes.getOrNull(HigherLayerRetryManaged) == true) && response.status.value in 500..599
        }
        retryOnExceptionIf { request, failure ->
            shouldUseClientAutomaticRetry(request.method, request.attributes.getOrNull(HigherLayerRetryManaged) == true) && failure !is CancellationException
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 75_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 45_000
    }
    install(HttpCookies) { storage = cookieStorage ?: AcceptAllCookiesStorage() }
    // Replaying a buffered POST can duplicate a reply; retry decisions belong to the shared read policy.
    engine { config { retryOnConnectionFailure(false); followRedirects(true) } }
}
