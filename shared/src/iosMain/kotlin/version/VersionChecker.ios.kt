package com.valoser.futacha.shared.version

import io.ktor.client.HttpClient
import platform.Foundation.NSBundle

/**
 * iOS版VersionChecker実装
 */
class IosVersionChecker(
    private val httpClient: HttpClient
) : VersionChecker {

    override fun getCurrentVersion(): String {
        return try {
            val bundle = NSBundle.mainBundle
            val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            version ?: "1.0.0"
        } catch (e: Exception) {
            println("Failed to get iOS version: ${e.message}")
            "1.0.0"
        }
    }

    override suspend fun checkForUpdate(): UpdateInfo? {
        val currentVersion = getCurrentVersion()

        // GitHub Releases APIから最新バージョンを取得
        val release = fetchLatestVersionFromGitHub(
            httpClient = httpClient,
            owner = "inqueuet",
            repo = "futacha"
        ) ?: return null

        val latestVersion = release.tag_name.removePrefix("v")

        // バージョン比較
        if (!isNewerVersion(currentVersion, latestVersion)) {
            return null
        }

        // 更新メッセージを生成
        val message = buildUpdateMessage(currentVersion, latestVersion, release.name)

        return UpdateInfo(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            message = message
        )
    }

    private fun buildUpdateMessage(current: String, latest: String, releaseName: String?): String {
        return buildString {
            append("新しいバージョンが利用可能です\n\n")
            append("現在: v$current\n")
            append("最新: v$latest")
            if (!releaseName.isNullOrBlank() && releaseName != latest) {
                append("\n\n$releaseName")
            }
        }
    }
}

actual fun createVersionChecker(httpClient: HttpClient): VersionChecker {
    return IosVersionChecker(httpClient)
}
