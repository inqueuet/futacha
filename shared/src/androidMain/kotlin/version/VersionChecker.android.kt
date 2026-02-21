package com.valoser.futacha.shared.version

import android.content.Context
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient

/**
 * Android版VersionChecker実装
 */
class AndroidVersionChecker(
    private val context: Context,
    private val httpClient: HttpClient
) : VersionChecker {
    companion object {
        private const val TAG = "AndroidVersionChecker"
    }

    override fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get version name: ${e.message}")
            "1.0"
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

/**
 * Android では Context が必要なため、この関数は使用できません。
 * 代わりに [createVersionChecker(Context, HttpClient)] を使用してください。
 *
 * @throws UnsupportedOperationException 常にスローされます
 */
actual fun createVersionChecker(httpClient: HttpClient): VersionChecker {
    throw UnsupportedOperationException(
        "Android では createVersionChecker(context, httpClient) を使用してください。" +
        "Context が必要なため、共通コードからは直接呼び出せません。"
    )
}

/**
 * Android用のVersionChecker作成関数
 */
fun createVersionChecker(context: Context, httpClient: HttpClient): VersionChecker {
    return AndroidVersionChecker(context, httpClient)
}
