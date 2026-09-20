@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.media.analysis

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*

internal actual fun createModelDownloadClient(): HttpClient = HttpClient(Darwin) {
    configureModelDownloads()
    engine {
        configureSession {
            HTTPCookieStorage = null
            HTTPShouldSetCookies = false
            URLCredentialStorage = null
            URLCache = null
            requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
        }
    }
}

internal actual fun modelStoreDirectory(platformContext: Any?): Path {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
        ?: error("モデルの保存先を開けません")
    val path = "$base/futacha/analysis_models".toPath()
    FileSystem.SYSTEM.createDirectories(path)
    check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
        "モデルのバックアップ除外を設定できません"
    }
    return path
}
