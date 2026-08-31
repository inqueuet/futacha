package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.stableCompatHash
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlin.time.Clock

private const val COMPAT_UPS_MAX_BYTES = 3_000_000
private const val COMPAT_UPS_ENDPOINT = "https://dec.2chan.net/up2/up.php"
private const val COMPAT_UPS_INDEX = "https://dec.2chan.net/up2/up.htm"
private const val COMPAT_UPS_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private val compatUpsErrorRegex = Regex("<font[^>]*color=red[^>]*><b>(.*?)<br><br>", RegexOption.IGNORE_CASE)
private val compatUpsHtmlTagRegex = Regex("<[^>]+>")
private val compatUpsUploadedFileRegex = Regex(
    "(?:href|src)\\s*=\\s*['\"][^'\"]*/(fu\\d{5,10}\\.[0-9a-z]{2,4})['\"]",
    RegexOption.IGNORE_CASE
)
private val compatUpsUnsafeFileNameRegex = Regex("[^A-Za-z0-9._-]")

internal suspend fun uploadCompatUps(
    client: HttpClient,
    attachment: ImageData,
    comment: String,
    deleteKey: String,
    appVersion: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    publishDelayMillis: Long = 1_000L
): Result<String> = runSuspendCatchingPreservingCancellation {
    require(attachment.bytes.isNotEmpty()) { "ファイルの準備に失敗しました" }
    require(attachment.bytes.size <= COMPAT_UPS_MAX_BYTES) { "ファイルサイズ超過です\n3000KBまで" }
    require(deleteKey.isNotBlank()) { "削除キーがありません" }
    val token = stableCompatHash("${attachment.fileName}:$nowEpochMillis")
    val response = client.submitFormWithBinaryData(
        url = COMPAT_UPS_ENDPOINT,
        formData = formData {
            append("mode", "reg")
            append("com", "${comment.take(1000)} $token$appVersion")
            append("pass", deleteKey)
            append(
                "up",
                attachment.bytes,
                Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"up\"; filename=\"${safeUpsFileName(attachment.fileName)}\"")
                    append(HttpHeaders.ContentType, compatUpsContentType(attachment.fileName).toString())
                }
            )
        }
    ) {
        headers[HttpHeaders.CacheControl] = "no-cache"
        headers[HttpHeaders.Pragma] = "no-cache"
        headers[HttpHeaders.Referrer] = "https://dec.2chan.net/up2/"
    }
    val responseBody = readBoundedHttpResponseText(response, COMPAT_UPS_RESPONSE_MAX_BYTES)
    if (!response.status.isSuccess()) error("Http ${response.status.value} Error")
    val errorMessage = compatUpsErrorRegex
        .find(responseBody.replace('\n', ' '))?.groupValues?.getOrNull(1)
    if (!errorMessage.isNullOrBlank()) {
        error(
            "アップローダーからエラーが返されました\n" +
                errorMessage.replace(compatUpsHtmlTagRegex, "").trim()
        )
    }

    // The old client waits briefly for up.php to publish the row, then finds the
    // generated fuXXXXX.ext name in up.htm. Keep that observable behavior instead
    // of inventing a URL from the local filename.
    delay(publishDelayMillis)
    val indexResponse = client.get(COMPAT_UPS_INDEX)
    val indexBody = readBoundedHttpResponseText(indexResponse, COMPAT_UPS_RESPONSE_MAX_BYTES)
    if (!indexResponse.status.isSuccess()) {
        error("トップページが取得できませんでした\nHTTP status ${indexResponse.status.value} error")
    }
    findCompatUpsUploadedFileName(indexBody, token)
        ?: error("アップロードしたファイルが見つかりません")
}

internal fun isCompatUpsUploadSizeAllowed(size: Int): Boolean = size in 1..COMPAT_UPS_MAX_BYTES

/**
 * Finds the generated filename from the same table row as the stable upload
 * token.  The token is in the comment column, after the filename column; a
 * plain substringAfter(token) scan therefore returned the next row's file
 * whenever the list was rendered newest-first (the reported one-file offset).
 */
internal fun findCompatUpsUploadedFileName(indexBody: String, token: String): String? {
    if (token.isBlank()) return null
    val tokenIndex = indexBody.indexOf(token)
    if (tokenIndex < 0) return null
    val rowStart = indexBody.lastIndexOf("<tr", startIndex = tokenIndex, ignoreCase = true)
    val rowEnd = indexBody.indexOf("</tr>", startIndex = tokenIndex, ignoreCase = true)
    if (rowStart < 0 || rowEnd <= tokenIndex) return null
    val row = indexBody.substring(rowStart, rowEnd)
    return compatUpsUploadedFileRegex.find(row)?.groupValues?.getOrNull(1)
}

private fun safeUpsFileName(fileName: String): String =
    fileName.substringAfterLast('/').substringAfterLast('\\')
        .replace(compatUpsUnsafeFileNameRegex, "_")
        .ifBlank { "upload.bin" }

private fun compatUpsContentType(fileName: String): ContentType = when (
    fileName.substringAfterLast('.', "").lowercase()
) {
    "jpg", "jpeg" -> ContentType.Image.JPEG
    "png" -> ContentType.Image.PNG
    "gif" -> ContentType.Image.GIF
    "webp" -> ContentType.parse("image/webp")
    "webm" -> ContentType.parse("video/webm")
    "mp4" -> ContentType.Video.MP4
    else -> ContentType.Application.OctetStream
}
