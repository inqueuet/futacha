package com.valoser.futacha.shared.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import javax.net.ssl.SSLException

internal actual fun isRetryableImageConnectionFailure(error: Throwable): Boolean =
    error !is SSLException && (error is IOException || error is HttpRequestTimeoutException)
