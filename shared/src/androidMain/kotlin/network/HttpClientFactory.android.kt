package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

/**
 * Creates a properly configured HttpClient with lifecycle management.
 * Note: Callers should manage the lifecycle and call close() when done.
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }

        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }

        engine {
            config {
                // Connection pool with timeout to prevent resource leaks
                connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

                // Follow redirects
                followRedirects(true)

                // Retry on connection failure
                retryOnConnectionFailure(true)
            }
        }
    }
}
