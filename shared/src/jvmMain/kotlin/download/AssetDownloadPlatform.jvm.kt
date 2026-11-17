package com.valoser.futacha.shared.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createAssetDownloadClient(): HttpClient = HttpClient(OkHttp) {
    configureAssetDownloads()
    engine { config { cookieJar(CookieJar.NO_COOKIES); followRedirects(false); followSslRedirects(false) } }
}
