package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage

expect fun createHttpClient(
    platformContext: Any? = null,
    cookieStorage: CookiesStorage? = null
): HttpClient
