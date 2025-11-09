package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

private val sharedHttpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}

actual fun createHttpClient(): HttpClient = sharedHttpClient
