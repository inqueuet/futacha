package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.network.MainThreadSafeResponseCloseInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createAimogeHttpClient(appVersion: String): HttpClient = HttpClient(OkHttp) {
    configureAimogeCommon(appVersion)
    engine {
        config {
            // Same four guards as the Futaba client: no main-thread TLS close, no redirects, and no silent replay of a
            // buffered POST (a reply, presign or self-delete must never go twice).
            addInterceptor(MainThreadSafeResponseCloseInterceptor)
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
        }
    }
}
