package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.sanitizeForShiftJis
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

private const val DEFAULT_UPLOAD_FILE_NAME = "upload.bin"
private const val SHIFT_JIS_TEXT_MIME = "text/plain; charset=Shift_JIS"
private const val UTF8_TEXT_MIME = "text/plain; charset=UTF-8"
private const val ASCII_TEXT_MIME = "text/plain; charset=US-ASCII"
private const val DEFAULT_MAX_FILE_SIZE_BYTES = 8_192_000L
private val WEBP_CONTENT_TYPE = ContentType.parse("image/webp")
private val WEBM_CONTENT_TYPE = ContentType.parse("video/webm")
private val BMP_CONTENT_TYPE = ContentType.parse("image/bmp")
private val MP4_CONTENT_TYPE = ContentType.parse("video/mp4")

internal data class HttpBoardApiPostingConfig(
    val encoding: HttpBoardApiPostEncoding,
    val chrencValue: String,
    val hashValue: String? = null,
    val ptuaValue: String? = null,
    val maxFileSizeBytes: Long? = null,
    val supportedExtensions: Set<String> = emptySet(),
    val cacheable: Boolean = true,
    val fromFallback: Boolean = false,
    val formFields: Set<String> = emptySet(),
    val environment: Map<String, String> = emptyMap()
)

internal fun resolveHttpBoardApiPostingConfig(
    chrencValue: String?,
    fallbackChrencValue: String,
    hashValue: String? = null,
    ptuaValue: String? = null,
    maxFileSizeBytes: Long? = null,
    supportedExtensions: Set<String> = emptySet(),
    cacheable: Boolean = true
): HttpBoardApiPostingConfig {
    val resolvedChrencValue = chrencValue ?: fallbackChrencValue
    return HttpBoardApiPostingConfig(
        encoding = determineHttpBoardApiEncoding(resolvedChrencValue),
        chrencValue = resolvedChrencValue,
        hashValue = hashValue?.takeIf { it.isNotBlank() },
        ptuaValue = ptuaValue?.takeIf { it.isNotBlank() },
        maxFileSizeBytes = maxFileSizeBytes?.takeIf { it > 0L },
        supportedExtensions = supportedExtensions.map { it.lowercase() }.toSet(),
        cacheable = cacheable,
        fromFallback = chrencValue == null
    )
}

internal fun fallbackHttpBoardApiPostingConfig(
    fallbackChrencValue: String
): HttpBoardApiPostingConfig = resolveHttpBoardApiPostingConfig(
    chrencValue = null,
    fallbackChrencValue = fallbackChrencValue,
    cacheable = false
)

internal fun buildHttpBoardApiPostFormData(
    logTag: String,
    threadId: String?,
    name: String,
    email: String,
    subject: String,
    comment: String,
    password: String,
    imageFile: ByteArray?,
    imageFileName: String?,
    textOnly: Boolean,
    postingConfig: HttpBoardApiPostingConfig,
    forceAjaxResponse: Boolean = false,
    handwriting: Boolean = false
) = formData {
    if (postingConfig.fromFallback || postingConfig.hashValue.isNullOrBlank()) {
        throw NetworkException("投稿フォームの必須情報が不足しているため送信していません")
    }
    fun hasField(name: String) = name in postingConfig.formFields
    val drawing = handwriting && imageFile != null && !textOnly
    if (drawing && !hasField("baseform")) throw NetworkException("この投稿フォームではお手書きを利用できません")
    if (!drawing && shouldAttachHttpBoardApiImage(imageFile, textOnly) && !hasField("upfile")) {
        throw NetworkException("この返信では通常の画像添付を利用できません。お手書きはお絵描きから選択してください")
    }
    if (shouldAttachHttpBoardApiImage(imageFile, textOnly) && imageFile!!.size > (postingConfig.maxFileSizeBytes ?: DEFAULT_MAX_FILE_SIZE_BYTES)) {
        throw NetworkException("添付ファイルが投稿先のサイズ上限を超えています")
    }
    if (!drawing && shouldAttachHttpBoardApiImage(imageFile, textOnly) && postingConfig.supportedExtensions.isNotEmpty()) {
        val extension = imageFileName.orEmpty().substringAfterLast('.', "").lowercase()
        if (extension !in postingConfig.supportedExtensions) throw NetworkException("この投稿先では指定された添付形式を利用できません")
    }
    if (drawing && !isHttpBoardApiHandwritingPng(imageFile!!)) throw NetworkException("お手書き画像は344×135のPNGで指定してください")
    if (hasField("guid")) appendHttpBoardApiAsciiField("guid", "on")
    appendHttpBoardApiAsciiField("mode", "regist")
    appendHttpBoardApiAsciiField(
        "MAX_FILE_SIZE",
        (postingConfig.maxFileSizeBytes ?: DEFAULT_MAX_FILE_SIZE_BYTES).toString()
    )
    if (hasField("name")) appendHttpBoardApiTextField(logTag, "name", name, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "email", email, postingConfig.encoding)
    if (hasField("sub")) appendHttpBoardApiTextField(logTag, "sub", subject, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "com", comment, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "pwd", password, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "chrenc", postingConfig.chrencValue, postingConfig.encoding)
    for (field in listOf("js", "pthb", "pthc", "pthd", "ptua", "scsz")) {
        if (hasField(field)) appendHttpBoardApiAsciiField(field, postingConfig.environment[field]
            ?: when(field) { "js" -> "off"; "ptua" -> postingConfig.ptuaValue.orEmpty(); else -> "" })
    }
    if (hasField("baseform")) appendHttpBoardApiAsciiField("baseform", if (drawing) kotlin.io.encoding.Base64.Default.encode(imageFile!!) else "")
    appendHttpBoardApiAsciiField("hash", postingConfig.hashValue!!)
    threadId?.let {
        appendHttpBoardApiAsciiField("resto", it)
        appendHttpBoardApiAsciiField("responsemode", "ajax")
    } ?: run {
        if (forceAjaxResponse) {
            appendHttpBoardApiAsciiField("responsemode", "ajax")
        }
    }

    val attachImage = !drawing && shouldAttachHttpBoardApiImage(imageFile, textOnly)
    if (attachImage) {
        val safeName = sanitizeHttpBoardApiUploadFileName(imageFileName, DEFAULT_UPLOAD_FILE_NAME)
        val fileData = imageFile ?: ByteArray(0)
        if (fileData.isEmpty()) {
            Logger.w(logTag, "imageFile is unexpectedly null or empty when attachImage is true")
        }
        append(
            "upfile",
            fileData,
            Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    """filename="$safeName""""
                )
                append(
                    HttpHeaders.ContentType,
                    guessHttpBoardApiMediaContentType(
                        fileName = safeName,
                        webpContentType = WEBP_CONTENT_TYPE,
                        webmContentType = WEBM_CONTENT_TYPE,
                        bmpContentType = BMP_CONTENT_TYPE,
                        mp4ContentType = MP4_CONTENT_TYPE
                    ).toString()
                )
            }
        )
    } else if (hasField("upfile")) {
        if (!drawing && hasField("textonly")) append("textonly", "on")
        append(
            "upfile",
            ByteArray(0),
            Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    "filename=\"\""
                )
                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            }
        )
    }
}

private fun FormBuilder.appendHttpBoardApiTextField(
    logTag: String,
    name: String,
    value: String,
    encoding: HttpBoardApiPostEncoding
) {
    val normalizedValue = when (encoding) {
        HttpBoardApiPostEncoding.SHIFT_JIS -> {
            val sanitized = sanitizeForShiftJis(value)
            if (sanitized.escapedCodePointCount > 0 || sanitized.removedCodePointCount > 0) {
                Logger.w(
                    logTag,
                    "Escaped ${sanitized.escapedCodePointCount} and removed ${sanitized.removedCodePointCount} unsupported Shift_JIS character(s) from '$name'"
                )
            }
            sanitized.sanitizedText
        }

        HttpBoardApiPostEncoding.UTF8 -> value
    }
    val (bytes, contentType) = when (encoding) {
        HttpBoardApiPostEncoding.SHIFT_JIS -> TextEncoding.encodeToShiftJis(normalizedValue) to SHIFT_JIS_TEXT_MIME
        HttpBoardApiPostEncoding.UTF8 -> normalizedValue.encodeToByteArray() to UTF8_TEXT_MIME
    }
    append(
        name,
        bytes,
        Headers.build {
            append(HttpHeaders.ContentType, contentType)
        }
    )
}

private fun FormBuilder.appendHttpBoardApiAsciiField(name: String, value: String) {
    append(
        name,
        value,
        Headers.build {
            append(HttpHeaders.ContentType, ASCII_TEXT_MIME)
        }
    )
}

internal fun isHttpBoardApiHandwritingPng(bytes: ByteArray): Boolean {
    if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) return false
    fun dimension(start: Int): Int = (start until start + 4).fold(0) { value, i -> (value shl 8) or (bytes[i].toInt() and 255) }
    return bytes.copyOfRange(12,16).decodeToString() == "IHDR" && dimension(16) == 344 && dimension(20) == 135
}
