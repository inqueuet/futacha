package version

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * バージョン更新チェック機能
 */
interface VersionChecker {
    /**
     * 現在のアプリバージョンを取得
     */
    fun getCurrentVersion(): String

    /**
     * アプリ更新をチェック
     * @return 更新がある場合は UpdateInfo、ない場合は null
     */
    suspend fun checkForUpdate(): UpdateInfo?
}

/**
 * 更新情報
 */
@Serializable
data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val message: String
)

/**
 * GitHub Releases APIのレスポンス
 */
@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null
)

/**
 * バージョン文字列を比較
 * @return latestVersion > currentVersion の場合 true
 */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    // "v1.0.0" のような prefix を削除
    val current = currentVersion.removePrefix("v")
    val latest = latestVersion.removePrefix("v")

    val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

    val maxLength = maxOf(currentParts.size, latestParts.size)

    for (i in 0 until maxLength) {
        val currentPart = currentParts.getOrNull(i) ?: 0
        val latestPart = latestParts.getOrNull(i) ?: 0

        when {
            latestPart > currentPart -> return true
            latestPart < currentPart -> return false
        }
    }

    return false
}

/**
 * GitHub Releases APIから最新バージョンを取得
 */
suspend fun fetchLatestVersionFromGitHub(
    httpClient: HttpClient,
    owner: String,
    repo: String
): GitHubRelease? {
    return try {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val response = httpClient.get(url).bodyAsText()
        Json { ignoreUnknownKeys = true }.decodeFromString<GitHubRelease>(response)
    } catch (e: Exception) {
        println("Failed to fetch latest version from GitHub: ${e.message}")
        null
    }
}

/**
 * プラットフォーム固有のVersionCheckerを作成
 */
expect fun createVersionChecker(httpClient: HttpClient): VersionChecker
