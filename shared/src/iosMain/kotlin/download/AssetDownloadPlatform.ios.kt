@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*

internal actual fun createAssetDownloadClient(): HttpClient = HttpClient(Darwin) {
    configureAssetDownloads()
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
