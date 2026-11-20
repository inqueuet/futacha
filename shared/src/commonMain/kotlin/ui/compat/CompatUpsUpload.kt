package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.readResponseBody
import com.valoser.futacha.shared.compat.stableCompatHash
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.sanitizeForShiftJis
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.prepareFormWithBinaryData
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
    val responseBody = readResponseBody { client.prepareFormWithBinaryData(
        url = COMPAT_UPS_ENDPOINT,
        formData = formData {
            append("mode", "reg")
            // up2 is a Shift_JIS page: a plain String part is sent as UTF-8
            // and the comment was garbled. Encode it like the board forms do.
            append(
                "com",
                compatUpsShiftJisCommentBytes("${comment.take(1000)} $token$appVersion"),
                Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"com\"")
                    append(HttpHeaders.ContentType, "text/plain; charset=Shift_JIS")
                }
            )
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
    }.execute { response ->
        if (!response.status.isSuccess()) error("Http ${response.status.value} Error")
        readBoundedHttpResponseText(response, COMPAT_UPS_RESPONSE_MAX_BYTES)
    } }
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
    val indexBody = readResponseBody {
        client.prepareGet(COMPAT_UPS_INDEX).execute { response ->
            if (!response.status.isSuccess()) {
                error("トップページが取得できませんでした\nHTTP status ${response.status.value} error")
            }
            readBoundedHttpResponseText(response, COMPAT_UPS_RESPONSE_MAX_BYTES)
        }
    }
    findCompatUpsUploadedFileName(indexBody, token)
        ?: error("アップロードしたファイルが見つかりません")
}

/** Shift_JIS bytes of the uploader comment; characters outside Shift_JIS become numeric references. */
internal fun compatUpsShiftJisCommentBytes(comment: String): ByteArray =
    TextEncoding.encodeToShiftJis(sanitizeForShiftJis(comment).sanitizedText)

internal fun isCompatUpsUploadSizeAllowed(size: Int): Boolean = size in 1..COMPAT_UPS_MAX_BYTES

/**
 * あぷ小 picker read limit (review 2026-11-11).  The pickers used the platform default (8,192,000 bytes), so a
 * phone photo over 8MB was refused while reading and the user saw only 「添付ファイルが読み込めません」 — on img
 * replies あぷ小 is the only way to attach.  Read as much as the normal posting picker, then shrink.
 */
internal const val COMPAT_UPS_PICKER_MAX_BYTES: Long = COMPAT_POST_PICKER_MAX_BYTES

private val COMPAT_UPS_SHRINKABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

/** Result of [prepareCompatUpsUpload]: the image to upload, and whether it was shrunk to fit 3000KB. */
internal data class CompatUpsPrepared(val image: ImageData, val shrunk: Boolean)

/**
 * Picture over the あぷ小 limit: shrink it (the normal posting compression, which also drops location metadata)
 * instead of refusing.  GIF, video and anything that still does not fit fail, and the caller shows its own
 * mode's size message.
 */
internal suspend fun prepareCompatUpsUpload(picked: ImageData): Result<CompatUpsPrepared> {
    if (isCompatUpsUploadSizeAllowed(picked.bytes.size)) return Result.success(CompatUpsPrepared(picked, shrunk = false))
    val extension = picked.fileName.substringAfterLast('.', "").lowercase()
    if (picked.bytes.isEmpty() || extension !in COMPAT_UPS_SHRINKABLE_EXTENSIONS) {
        return Result.failure(IllegalArgumentException("too large"))
    }
    // compressCompatPostImage keeps cancellation; the size check is plain (no runCatching that would swallow it).
    return compressCompatPostImage(picked, COMPAT_UPS_MAX_BYTES).fold(
        onSuccess = { shrunk ->
            if (isCompatUpsUploadSizeAllowed(shrunk.bytes.size)) Result.success(CompatUpsPrepared(shrunk, shrunk = true))
            else Result.failure(IllegalArgumentException("still too large"))
        },
        onFailure = { error -> if (error is kotlin.coroutines.cancellation.CancellationException) throw error else Result.failure(error) }
    )
}

internal const val COMPAT_UPS_SHRUNK_NOTICE = "あぷ小の3000KBに収まるよう画像を縮小しました"

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
