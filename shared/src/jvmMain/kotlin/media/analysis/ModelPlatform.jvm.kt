package com.valoser.futacha.shared.media.analysis

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CookieJar
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createModelDownloadClient(): HttpClient = HttpClient(OkHttp) {
    configureModelDownloads()
    engine { config { cookieJar(CookieJar.NO_COOKIES); followRedirects(false); followSslRedirects(false) } }
}

internal actual fun modelStoreDirectory(platformContext: Any?): Path =
    java.io.File(requireNotNull(com.valoser.futacha.shared.desktop.DesktopEnvironment.current) {
        "JVM tests must supply an isolated model directory"
    }.dataDirectory, "models").absolutePath.toPath()
