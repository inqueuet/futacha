package com.valoser.futacha.shared.media.analysis

import android.content.Context
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
    requireNotNull(platformContext as? Context).applicationContext.noBackupFilesDir.absolutePath.toPath().resolve("analysis_models")
