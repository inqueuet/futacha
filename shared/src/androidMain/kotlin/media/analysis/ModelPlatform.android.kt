package com.valoser.futacha.shared.media.analysis

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.valoser.futacha.shared.network.MainThreadSafeResponseCloseInterceptor
import okhttp3.CookieJar
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createModelDownloadClient(): HttpClient = com.valoser.futacha.shared.download.createAssetDownloadClient()

internal actual fun modelStoreDirectory(platformContext: Any?): Path =
    requireNotNull(platformContext as? Context).applicationContext.noBackupFilesDir.absolutePath.toPath().resolve("analysis_models")
