package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage

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
        // Ktor's HttpRedirect follows only GET/HEAD; answer a POST 302/303 with a GET.
        // Installed first so the read retry inside it can never resend the POST.
        installPostRedirectFollowingAndReadRetry()
        installAmbiguousRequestUrlGuard()

        install(HttpTimeout) {
            requestTimeoutMillis = 75_000
            connectTimeoutMillis = 15_000
            // Preserve the general client's previous effective timeout while allowing
            // image requests to override it through HttpTimeout.
            socketTimeoutMillis = 75_000
        }

        install(HttpCookies) {
            storage = cookieStorage ?: AcceptAllCookiesStorage()
        }

    }
}
