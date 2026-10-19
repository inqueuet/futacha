package com.valoser.futacha.shared.download

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.valoser.futacha.shared.network.MainThreadSafeResponseCloseInterceptor
import okhttp3.CookieJar
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createAssetDownloadClient(): HttpClient = HttpClient(OkHttp) {
    configureAssetDownloads()
    engine { config { addInterceptor(MainThreadSafeResponseCloseInterceptor); cookieJar(CookieJar.NO_COOKIES); followRedirects(false); followSslRedirects(false) } }
}
