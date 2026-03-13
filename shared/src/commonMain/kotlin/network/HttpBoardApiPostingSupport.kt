package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.util.sanitizeForShiftJis
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DEFAULT_UPLOAD_FILE_NAME = "upload.bin"
private const val SHIFT_JIS_TEXT_MIME = "text/plain; charset=Shift_JIS"
private const val UTF8_TEXT_MIME = "text/plain; charset=UTF-8"
private const val ASCII_TEXT_MIME = "text/plain; charset=US-ASCII"
private const val DEFAULT_SCREEN_SPEC = "1080x1920x24"
private const val DEFAULT_PTUA_VALUE = "1341647872"
private val WEBP_CONTENT_TYPE = ContentType.parse("image/webp")
private val WEBM_CONTENT_TYPE = ContentType.parse("video/webm")
private val BMP_CONTENT_TYPE = ContentType.parse("image/bmp")
private val MP4_CONTENT_TYPE = ContentType.parse("video/mp4")

internal data class HttpBoardApiPostingConfig(
    val encoding: HttpBoardApiPostEncoding,
    val chrencValue: String,
    val fromFallback: Boolean = false
)

internal fun resolveHttpBoardApiPostingConfig(
    chrencValue: String?,
    fallbackChrencValue: String
): HttpBoardApiPostingConfig {
    val resolvedChrencValue = chrencValue ?: fallbackChrencValue
    return HttpBoardApiPostingConfig(
        encoding = determineHttpBoardApiEncoding(resolvedChrencValue),
        chrencValue = resolvedChrencValue,
        fromFallback = chrencValue == null
    )
}

internal fun fallbackHttpBoardApiPostingConfig(
    fallbackChrencValue: String
): HttpBoardApiPostingConfig = resolveHttpBoardApiPostingConfig(
    chrencValue = null,
    fallbackChrencValue = fallbackChrencValue
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
    forceAjaxResponse: Boolean = false
) = formData {
    appendHttpBoardApiAsciiField("guid", "on")
    appendHttpBoardApiAsciiField("mode", "regist")
    appendHttpBoardApiAsciiField("MAX_FILE_SIZE", "8192000")
    appendHttpBoardApiTextField(logTag, "name", name, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "email", email, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "sub", subject, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "com", comment, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "pwd", password, postingConfig.encoding)
    appendHttpBoardApiTextField(logTag, "chrenc", postingConfig.chrencValue, postingConfig.encoding)
    appendHttpBoardApiAsciiField("js", "on")
    appendHttpBoardApiAsciiField("baseform", "")
    appendHttpBoardApiAsciiField("pthb", "")
    appendHttpBoardApiAsciiField("pthc", buildHttpBoardApiClientTimestampSeed())
    appendHttpBoardApiAsciiField("pthd", "")
    appendHttpBoardApiAsciiField("ptua", DEFAULT_PTUA_VALUE)
    appendHttpBoardApiAsciiField("scsz", DEFAULT_SCREEN_SPEC)
    appendHttpBoardApiAsciiField("hash", buildHttpBoardApiClientHash())
    threadId?.let {
        appendHttpBoardApiAsciiField("resto", it)
        appendHttpBoardApiAsciiField("responsemode", "ajax")
    } ?: run {
        if (forceAjaxResponse) {
            appendHttpBoardApiAsciiField("responsemode", "ajax")
        }
    }

    val attachImage = shouldAttachHttpBoardApiImage(imageFile, textOnly)
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
                    """form-data; name="upfile"; filename="$safeName""""
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
    } else {
        append("textonly", "on")
        append(
            "upfile",
            ByteArray(0),
            Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    """form-data; name="upfile"; filename="""
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
            append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
            append(HttpHeaders.ContentType, contentType)
        }
    )
}

private fun FormBuilder.appendHttpBoardApiAsciiField(name: String, value: String) {
    append(
        name,
        value,
        Headers.build {
            append(HttpHeaders.ContentDisposition, """form-data; name="$name"""")
            append(HttpHeaders.ContentType, ASCII_TEXT_MIME)
        }
    )
}

@OptIn(ExperimentalTime::class)
internal fun buildHttpBoardApiClientHash(
    currentEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    randomByteSupplier: () -> Int = { Random.nextInt(0, 256) }
): String {
    val randomHex = buildString(32) {
        repeat(16) {
            append(randomByteSupplier().toString(16).padStart(2, '0'))
        }
    }
    return "$currentEpochMillis-$randomHex"
}

@OptIn(ExperimentalTime::class)
internal fun buildHttpBoardApiClientTimestampSeed(
    currentEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): String = currentEpochMillis.toString()
