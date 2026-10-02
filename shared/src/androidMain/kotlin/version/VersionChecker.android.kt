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
        private const val PLAY_STORE_INSTALLER = "com.android.vending"
        private const val PREFS_NAME = "github_update_prompt"
        private const val KEY_OFFERED_VERSION = "offered_version"
        private const val KEY_OFFERED_AT = "offered_at_millis"
        private const val REOFFER_INTERVAL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }

    private fun installerPackageName(): String? = runCatching {
        val packageManager = context.packageManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

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
        // Play installs are updated through Google Play In-App Updates; the GitHub
        // release prompt would duplicate it (and point to a build Play cannot replace).
        if (installerPackageName() == PLAY_STORE_INSTALLER) return null

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

        // Offer one release at most once a week instead of on every launch.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!shouldOfferGitHubUpdate(
                offeredVersion = prefs.getString(KEY_OFFERED_VERSION, null),
                offeredAtMillis = prefs.getLong(KEY_OFFERED_AT, 0L),
                latestVersion = latestVersion,
                nowMillis = now,
                reofferIntervalMillis = REOFFER_INTERVAL_MILLIS
            )
        ) {
            return null
        }

        // 更新メッセージを生成
        val message = buildUpdateMessage(currentVersion, latestVersion, release.name, release.body)

        return UpdateInfo(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            message = message
        )
    }

    override fun onUpdateShown(info: UpdateInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_OFFERED_VERSION, info.latestVersion)
            .putLong(KEY_OFFERED_AT, System.currentTimeMillis())
            .apply()
    }
}

internal fun shouldOfferGitHubUpdate(
    offeredVersion: String?,
    offeredAtMillis: Long,
    latestVersion: String,
    nowMillis: Long,
    reofferIntervalMillis: Long
): Boolean {
    if (offeredVersion != latestVersion) return true
    if (nowMillis < offeredAtMillis) return true
    return nowMillis - offeredAtMillis >= reofferIntervalMillis
}

@Volatile
private var versionCheckerAppContext: Context? = null

/**
 * Android では Context が必要なため、この関数は使用できません。
 * 代わりに [createVersionChecker(Context, HttpClient)] を使用してください。
 *
 * 互換性のため、例外は投げず安全なフォールバックを返します。
 */
actual fun createVersionChecker(httpClient: HttpClient): VersionChecker {
    val context = versionCheckerAppContext
    if (context != null) {
        return AndroidVersionChecker(context, httpClient)
    }
    Logger.w(
        "AndroidVersionChecker",
        "createVersionChecker(httpClient) was called without Context on Android. " +
            "Falling back to a no-op checker."
    )
    return object : VersionChecker {
        override fun getCurrentVersion(): String = "1.0"
        override suspend fun checkForUpdate(): UpdateInfo? = null
    }
}

/**
 * Android用のVersionChecker作成関数
 */
fun createVersionChecker(context: Context, httpClient: HttpClient): VersionChecker {
    return AndroidVersionChecker(context, httpClient)
}

fun initializeVersionCheckerContext(context: Context) {
    versionCheckerAppContext = context.applicationContext
}
