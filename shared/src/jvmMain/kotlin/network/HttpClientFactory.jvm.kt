package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies

actual fun createHttpClient(platformContext: Any?, cookieStorage: CookiesStorage?): HttpClient = HttpClient(OkHttp) {
    // OkHttp no longer follows redirects; restore its POST 302/303 → GET.
    // Installed first so the read retry inside it can never resend the POST.
    installPostRedirectFollowingAndReadRetry()
    installAmbiguousRequestUrlGuard()
    install(HttpTimeout) {
        requestTimeoutMillis = 75_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 45_000
    }
    install(HttpCookies) { storage = cookieStorage ?: AcceptAllCookiesStorage() }
    // Replaying a buffered POST can duplicate a reply; retry decisions belong to the shared read policy.
    engine {
        config {
            retryOnConnectionFailure(false)
            // Let Ktor process cookies and redirect policy at every hop.
            followRedirects(false)
            followSslRedirects(false)
        }
    }
}
