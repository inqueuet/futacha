package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.CancellationException

/**
 * Creates a properly configured HttpClient with lifecycle management.
 * Note: Callers should manage the lifecycle and call close() when done.
 * This is not a singleton to prevent memory leaks.
 */
actual fun createHttpClient(
    platformContext: Any?,
    cookieStorage: CookiesStorage?
): HttpClient {
    return HttpClient(Darwin) {
        install(HttpRequestRetry) {
            maxRetries = 2
            exponentialDelay()
            retryIf(maxRetries) { request, response ->
                shouldUseClientAutomaticRetry(
                    method = request.method,
                    higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                ) && response.status.value in 500..599
            }
            retryOnExceptionIf { request, cause ->
                shouldUseClientAutomaticRetry(
                    method = request.method,
                    higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                ) && cause !is CancellationException
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 75_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 45_000
        }

        install(HttpCookies) {
            storage = cookieStorage ?: AcceptAllCookiesStorage()
        }

        engine {
            configureRequest {
                // Set proper timeout for connections
                setTimeoutInterval(75.0)
            }
        }
    }
}
