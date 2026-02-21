package com.valoser.futacha.shared.version

import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

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

private val json = Json {
    ignoreUnknownKeys = true
}
private const val TAG = "VersionChecker"

/**
 * バージョン文字列を比較
 * @return latestVersion > currentVersion の場合 true
 */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val current = parseSemVer(currentVersion) ?: return false
    val latest = parseSemVer(latestVersion) ?: return false
    return compareSemVer(latest, current) > 0
}

private data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<PreRelease>
)

private data class PreRelease(
    val value: String,
    val isNumeric: Boolean
)

private fun parseSemVer(raw: String): SemVer? {
    val trimmed = raw.removePrefix("v").trim()
    if (trimmed.isEmpty()) return null

    val mainAndPre = trimmed.split("-", limit = 2)
    val mainPart = mainAndPre[0].substringBefore("+").trim()
    val mainSegments = mainPart.split(".")
    val major = mainSegments.getOrNull(0)?.toIntOrNull() ?: return null
    val minor = mainSegments.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = mainSegments.getOrNull(2)?.toIntOrNull() ?: 0

    val preRelease = if (mainAndPre.size > 1) {
        mainAndPre[1].substringBefore("+").split(".").filter { it.isNotBlank() }.map { token ->
            val numeric = token.all { it.isDigit() }
            PreRelease(token, numeric)
        }
    } else {
        emptyList()
    }

    return SemVer(major, minor, patch, preRelease)
}

private fun compareSemVer(left: SemVer, right: SemVer): Int {
    if (left.major != right.major) return left.major.compareTo(right.major)
    if (left.minor != right.minor) return left.minor.compareTo(right.minor)
    if (left.patch != right.patch) return left.patch.compareTo(right.patch)

    val leftPre = left.preRelease
    val rightPre = right.preRelease
    if (leftPre.isEmpty() && rightPre.isEmpty()) return 0
    if (leftPre.isEmpty()) return 1
    if (rightPre.isEmpty()) return -1

    val max = maxOf(leftPre.size, rightPre.size)
    for (i in 0 until max) {
        val l = leftPre.getOrNull(i) ?: return -1
        val r = rightPre.getOrNull(i) ?: return 1
        if (l.isNumeric && r.isNumeric) {
            val lNum = l.value.toIntOrNull() ?: 0
            val rNum = r.value.toIntOrNull() ?: 0
            if (lNum != rNum) return lNum.compareTo(rNum)
        } else if (l.isNumeric != r.isNumeric) {
            return if (l.isNumeric) -1 else 1
        } else {
            if (l.value != r.value) return l.value.compareTo(r.value)
        }
    }
    return 0
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
        json.decodeFromString<GitHubRelease>(response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(TAG, "Failed to fetch latest version from GitHub: ${e.message}")
        null
    }
}

/**
 * プラットフォーム固有のVersionCheckerを作成
 */
expect fun createVersionChecker(httpClient: HttpClient): VersionChecker
