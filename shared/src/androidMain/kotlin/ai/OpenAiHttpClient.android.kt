package com.valoser.futacha.shared.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import com.valoser.futacha.shared.network.MainThreadSafeResponseCloseInterceptor

internal actual fun createOpenAiHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 120_000
    }
    engine { config { addInterceptor(MainThreadSafeResponseCloseInterceptor); retryOnConnectionFailure(false); followRedirects(false); followSslRedirects(false) } }
}
