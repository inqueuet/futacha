package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createAimogeHttpClient(appVersion: String): HttpClient = HttpClient(OkHttp) {
    configureAimogeCommon(appVersion)
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
        }
    }
}
