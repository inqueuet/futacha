package com.valoser.futacha.shared.version

import io.ktor.client.HttpClient

actual fun createVersionChecker(httpClient: HttpClient): VersionChecker {
    return object : VersionChecker {
        override fun getCurrentVersion(): String = System.getProperty("futacha.desktop.version", "11.1")
        override suspend fun checkForUpdate(): UpdateInfo? = null
    }
}
