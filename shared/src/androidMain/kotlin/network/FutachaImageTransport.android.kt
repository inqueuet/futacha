package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import java.io.IOException
import javax.net.ssl.SSLException

fun createAndroidImageTransport(cookieStorage: CookiesStorage): FutachaImageTransport = FutachaImageTransport(
    HttpClient(OkHttp) {
        configureImageRequests()
        install(HttpCookies) { storage = cookieStorage }
        engine { config {
            dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 8
            })
            retryOnConnectionFailure(true)
            // Ktor must process each hop's cookies and image URL policy.
            followRedirects(false)
            addInterceptor(MainThreadSafeResponseCloseInterceptor)
        } }
    }
)

internal actual fun isRetryableImageConnectionFailure(error: Throwable): Boolean =
    when (error) {
        is SSLException -> false
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> true
        else -> error is IOException
    }
