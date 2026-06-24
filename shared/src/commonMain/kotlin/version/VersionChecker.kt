package com.valoser.futacha.shared.version

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

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
private const val MAX_GITHUB_RELEASE_RESPONSE_BYTES = 256 * 1024
private const val GITHUB_RELEASE_READ_BUFFER_BYTES = 8 * 1024
private const val GITHUB_RELEASE_MAX_ZERO_READ_RETRIES = 5
private const val GITHUB_RELEASE_ZERO_READ_BACKOFF_MILLIS = 50L
private const val GITHUB_RELEASE_RESPONSE_TIMEOUT_MILLIS = 10_000L
private const val GITHUB_RELEASE_READ_IDLE_TIMEOUT_MILLIS = 10_000L
private const val MAX_RELEASE_NOTES_DISPLAY_CHARS = 4_000
private val markdownLinkRegex = Regex("""!?\[([^\]]*)\]\([^)]+\)""")
private val markdownAutolinkRegex = Regex("""<https?://[^>]+>""")
private val markdownBareUrlRegex = Regex("""https?://\S+""")
private val markdownHeadingRegex = Regex("""^\s{0,3}#{1,6}\s*""")
private val markdownListMarkerRegex = Regex("""^\s{0,3}([-*+])\s+""")
private val markdownOrderedListMarkerRegex = Regex("""^\s{0,3}\d+[.)]\s+""")
private val markdownQuoteRegex = Regex("""^\s{0,3}>\s?""")
private val markdownHrRegex = Regex("""^\s{0,3}([-*_]\s*){3,}$""")
private val markdownHtmlTagRegex = Regex("""<[^>]+>""")
private val markdownEmphasisRegex = Regex("""[*_~`]+""")

/**
 * バージョン文字列を比較
 * @return latestVersion > currentVersion の場合 true
 */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val current = parseSemVer(currentVersion) ?: return false
    val latest = parseSemVer(latestVersion) ?: return false
    return compareSemVer(latest, current) > 0
}

internal fun buildUpdateMessage(
    current: String,
    latest: String,
    releaseName: String?,
    releaseBody: String?
): String {
    return buildString {
        append("新しいバージョンが利用可能です\n\n")
        append("現在: v$current\n")
        append("最新: v$latest")
        if (!releaseName.isNullOrBlank() && releaseName != latest) {
            append("\n\n")
            append(sanitizeGitHubReleaseTextForDisplay(releaseName))
        }
        val releaseNotes = sanitizeGitHubReleaseTextForDisplay(releaseBody)
        if (releaseNotes.isNotBlank()) {
            append("\n\n")
            append(releaseNotes)
        }
    }
}

internal fun sanitizeGitHubReleaseTextForDisplay(value: String?): String {
    val text = value.orEmpty()
    if (text.isBlank()) return ""

    val cleanedLines = mutableListOf<String>()
    var inCodeBlock = false
    for (rawLine in text.lines()) {
        val trimmedLine = rawLine.trim()
        if (trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")) {
            inCodeBlock = !inCodeBlock
            continue
        }
        if (!inCodeBlock && markdownHrRegex.matches(trimmedLine)) {
            continue
        }

        val line = if (inCodeBlock) {
            trimmedLine
        } else {
            sanitizeGitHubReleaseMarkdownLine(rawLine)
        }
        cleanedLines += line
    }

    val compacted = cleanedLines
        .joinToString("\n")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    return if (compacted.length <= MAX_RELEASE_NOTES_DISPLAY_CHARS) {
        compacted
    } else {
        compacted.take(MAX_RELEASE_NOTES_DISPLAY_CHARS).trimEnd() + "\n..."
    }
}

private fun sanitizeGitHubReleaseMarkdownLine(line: String): String {
    val isUnorderedList = markdownListMarkerRegex.containsMatchIn(line)
    val isOrderedList = markdownOrderedListMarkerRegex.containsMatchIn(line)
    val withoutBlockMarkers = line
        .replace(markdownQuoteRegex, "")
        .replace(markdownHeadingRegex, "")
        .replace(markdownOrderedListMarkerRegex, "")
        .replace(markdownListMarkerRegex, "")
    val withoutLinks = withoutBlockMarkers
        .replace(markdownLinkRegex) { match ->
            match.groupValues.getOrNull(1).orEmpty()
        }
        .replace(markdownAutolinkRegex, "")
        .replace(markdownBareUrlRegex, "")
    val withoutInlineMarkers = withoutLinks
        .replace(markdownHtmlTagRegex, "")
        .replace(markdownEmphasisRegex, "")
        .trim()

    return when {
        withoutInlineMarkers.isBlank() -> ""
        isUnorderedList || isOrderedList -> "・$withoutInlineMarkers"
        else -> withoutInlineMarkers
    }
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
        val response = httpClient.get(url)
        val body = readGitHubReleaseResponseBody(response)
        withContext(AppDispatchers.parsing) {
            json.decodeFromString<GitHubRelease>(body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(TAG, "Failed to fetch latest version from GitHub: ${e.message}")
        null
    }
}

private suspend fun readGitHubReleaseResponseBody(response: HttpResponse): String {
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_GITHUB_RELEASE_RESPONSE_BYTES) {
        throw IllegalStateException("GitHub release response is too large")
    }

    val bytes = withContext(AppDispatchers.io) {
        withTimeout(GITHUB_RELEASE_RESPONSE_TIMEOUT_MILLIS) {
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(GITHUB_RELEASE_READ_BUFFER_BYTES)
            var output = ByteArray(GITHUB_RELEASE_READ_BUFFER_BYTES)
            var totalBytes = 0
            var zeroReadCount = 0
            var readLoopCount = 0L
            var fullyConsumed = false
            try {
                while (true) {
                    coroutineContext.ensureActive()
                    val read = withTimeoutOrNull(GITHUB_RELEASE_READ_IDLE_TIMEOUT_MILLIS) {
                        channel.readAvailable(buffer, 0, buffer.size)
                    } ?: throw IllegalStateException("GitHub release response read stalled")
                    if (read == -1) {
                        fullyConsumed = true
                        break
                    }
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= GITHUB_RELEASE_MAX_ZERO_READ_RETRIES) {
                            throw IllegalStateException("GitHub release response read stalled")
                        }
                        delay(GITHUB_RELEASE_ZERO_READ_BACKOFF_MILLIS)
                        continue
                    }

                    zeroReadCount = 0
                    val requiredSize = totalBytes + read
                    if (requiredSize > MAX_GITHUB_RELEASE_RESPONSE_BYTES) {
                        throw IllegalStateException("GitHub release response is too large")
                    }
                    if (requiredSize > output.size) {
                        var nextSize = output.size
                        while (nextSize < requiredSize) {
                            nextSize = (nextSize * 2).coerceAtMost(MAX_GITHUB_RELEASE_RESPONSE_BYTES)
                            if (nextSize == output.size) break
                        }
                        if (nextSize < requiredSize) {
                            throw IllegalStateException("GitHub release response buffer expansion failed")
                        }
                        output = output.copyOf(nextSize)
                    }
                    buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = read)
                    totalBytes = requiredSize
                    readLoopCount += 1
                    if (readLoopCount % 32L == 0L) {
                        yield()
                    }
                }
                if (totalBytes == output.size) output else output.copyOf(totalBytes)
            } finally {
                if (!fullyConsumed) {
                    runCatching { channel.cancel() }
                }
            }
        }
    }
    return bytes.decodeToString()
}

/**
 * プラットフォーム固有のVersionCheckerを作成
 */
expect fun createVersionChecker(httpClient: HttpClient): VersionChecker
