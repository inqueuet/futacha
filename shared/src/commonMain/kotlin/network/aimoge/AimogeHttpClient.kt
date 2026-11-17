package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

/**
 * The client for あいもげ only: no cookies, no redirects followed (a 3xx is a failure), no replayed requests. The
 * Futaba client keeps its cookies and is never used for あいもげ.
 */
expect fun createAimogeHttpClient(appVersion: String): HttpClient

internal const val AIMOGE_REQUEST_TIMEOUT_MS = 30_000L
internal const val AIMOGE_CONNECT_TIMEOUT_MS = 15_000L
internal const val AIMOGE_SOCKET_TIMEOUT_MS = 30_000L

internal fun HttpClientConfig<*>.configureAimogeCommon(appVersion: String) {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = AIMOGE_REQUEST_TIMEOUT_MS
        connectTimeoutMillis = AIMOGE_CONNECT_TIMEOUT_MS
        socketTimeoutMillis = AIMOGE_SOCKET_TIMEOUT_MS
    }
    install(UserAgent) { agent = aimogeUserAgent(appVersion) }
}

/** The app's own name and version, never a browser's. */
fun aimogeUserAgent(appVersion: String): String = "futacha/${appVersion.ifBlank { "unknown" }}"
