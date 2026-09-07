package com.valoser.futacha.shared.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException

internal actual fun isRetryableImageConnectionFailure(error: Throwable): Boolean = when (error) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> true
    is DarwinHttpRequestException -> error.origin.code in setOf(-1001L, -1003L, -1004L, -1005L, -1006L, -1009L)
    else -> false
}
