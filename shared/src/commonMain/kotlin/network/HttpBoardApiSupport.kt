package com.valoser.futacha.shared.network

import kotlin.text.RegexOption

private val THREAD_ID_REGEX = """res/(\d+)\.html?""".toRegex(RegexOption.IGNORE_CASE)
private val RES_QUERY_ID_REGEX = """\bres=(\d+)\b""".toRegex(RegexOption.IGNORE_CASE)
private val SUCCESS_KEYWORDS = listOf("書き込みました", "書き込みました。", "書き込みが完了", "書きこみました")
private val ERROR_KEYWORDS = listOf("エラー", "error", "荒らし", "規制", "拒否", "連続投稿", "大きすぎ", "時間を置いて")
private val JSON_STATUS_REGEX = """"status"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
private val JSON_MESSAGE_REGEX = """"(error|reason|message)"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
private val JSON_JUMPTO_REGEX = """"jumpto"\s*:\s*(\d+)""".toRegex()
private val JSON_THISNO_REGEX = """"thisno"\s*:\s*(\d+)""".toRegex()
private val CHRENC_INPUT_REGEX =
    Regex("""<input\s+[^>]{0,200}?name\s*=\s*["']chrenc["'][^>]{0,200}?>""", RegexOption.IGNORE_CASE)
private val VALUE_ATTR_REGEX =
    Regex("""value\s*=\s*["']([^"']{0,500})["']""", RegexOption.IGNORE_CASE)
private const val CHRENC_NEARBY_SCAN_WINDOW = 4096
private const val CHRENC_FALLBACK_SCAN_MAX_BYTES = 512 * 1024
private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]+);""")

internal fun isSuccessfulHttpBoardApiPostResponse(body: String): Boolean {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return false
    if (looksLikeHttpBoardApiJson(trimmed) && isHttpBoardApiJsonStatusOk(trimmed)) {
        return true
    }
    if (containsHttpBoardApiThreadId(trimmed)) {
        return true
    }
    return SUCCESS_KEYWORDS.any { keyword -> trimmed.contains(keyword) }
}

internal fun isSuccessfulHttpBoardApiSaidaneResponse(body: String): Boolean {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return false
    if (looksLikeHttpBoardApiJson(trimmed) && isHttpBoardApiJsonStatusOk(trimmed)) {
        return true
    }
    return trimmed.all(Char::isDigit)
}

internal fun extractHttpBoardApiServerError(body: String): String? {
    val normalized = body.replace("\r\n", "\n")
    if (looksLikeHttpBoardApiJson(normalized)) {
        if (isHttpBoardApiJsonStatusOk(normalized)) {
            return null
        }
        val message = JSON_MESSAGE_REGEX.find(normalized)?.groupValues?.getOrNull(2)
        if (message != null) {
            return message
        }
        val status = JSON_STATUS_REGEX.find(normalized)?.groupValues?.getOrNull(1)
        return status?.let { "status=$it" }
    }
    return normalized.lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotEmpty() && ERROR_KEYWORDS.any { keyword ->
                line.contains(keyword, ignoreCase = true)
            }
        }
}

internal fun summarizeHttpBoardApiResponse(body: String): String {
    val normalized = body.replace("\r\n", "\n")
    if (looksLikeHttpBoardApiJson(normalized)) {
        val status = JSON_STATUS_REGEX.find(normalized)?.groupValues?.getOrNull(1)
        val message = JSON_MESSAGE_REGEX.find(normalized)?.groupValues?.getOrNull(2)
        return buildString {
            append("status=${status ?: "unknown"}")
            if (!message.isNullOrBlank()) {
                append(", message=")
                append(message)
            }
        }
    }
    return normalized.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(120)
        ?: body.take(120)
}

internal fun tryParseHttpBoardApiThreadIdFromJson(body: String): String? {
    if (!looksLikeHttpBoardApiJson(body) || !isHttpBoardApiJsonStatusOk(body)) {
        return null
    }
    val jumpto = JSON_JUMPTO_REGEX.find(body)?.groupValues?.getOrNull(1)
    if (!jumpto.isNullOrBlank()) {
        return jumpto
    }
    val thisNo = JSON_THISNO_REGEX.find(body)?.groupValues?.getOrNull(1)
    if (!thisNo.isNullOrBlank()) {
        return thisNo
    }
    return null
}

internal fun tryExtractHttpBoardApiThreadId(body: String): String? {
    THREAD_ID_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
    RES_QUERY_ID_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

internal fun tryExtractHttpBoardApiThisNo(body: String): String? {
    val match = JSON_THISNO_REGEX.find(body)
    return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
}

internal fun parseHttpBoardApiChrencValue(html: String): String? {
    if (html.isBlank()) return null
    val chrencIndex = html.indexOf("chrenc", ignoreCase = true)
    val scanTarget = if (chrencIndex >= 0) {
        val start = (chrencIndex - 1024).coerceAtLeast(0)
        val end = (chrencIndex + CHRENC_NEARBY_SCAN_WINDOW).coerceAtMost(html.length)
        html.substring(start, end)
    } else {
        html.take(CHRENC_FALLBACK_SCAN_MAX_BYTES)
    }

    val input = CHRENC_INPUT_REGEX.find(scanTarget)?.value ?: return null
    val match = VALUE_ATTR_REGEX.find(input) ?: return null
    val rawValue = match.groupValues.getOrNull(1)?.trim().orEmpty()
    if (rawValue.isEmpty()) return null
    return decodeHttpBoardApiNumericEntities(rawValue)
}

internal fun decodeHttpBoardApiNumericEntities(value: String): String {
    if (!value.contains("&#")) return value
    return NUMERIC_ENTITY_REGEX.replace(value) { match ->
        val payload = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = when {
            payload.startsWith("x") || payload.startsWith("X") -> payload.drop(1).toIntOrNull(16)
            else -> payload.toIntOrNull(10)
        }
        codePoint?.let {
            when {
                it in 0x0000..0xFFFF -> {
                    if (it in 0xD800..0xDFFF) {
                        return@replace match.value
                    }
                    it.toChar().toString()
                }
                it in 0x10000..0x10FFFF -> {
                    val adjusted = it - 0x10000
                    val highSurrogate = 0xD800 + (adjusted shr 10)
                    val lowSurrogate = 0xDC00 + (adjusted and 0x3FF)
                    buildString {
                        append(highSurrogate.toChar())
                        append(lowSurrogate.toChar())
                    }
                }
                else -> match.value
            }
        } ?: match.value
    }
}

internal fun looksLikeHttpBoardApiJson(body: String): Boolean {
    val firstNonWhitespace = body.firstOrNull { !it.isWhitespace() }
    return firstNonWhitespace == '{' || firstNonWhitespace == '['
}

internal fun isHttpBoardApiJsonStatusOk(body: String): Boolean {
    val status = JSON_STATUS_REGEX.find(body)?.groupValues?.getOrNull(1)?.lowercase()
    return status == "ok" || status == "success"
}

private fun containsHttpBoardApiThreadId(body: String): Boolean {
    return THREAD_ID_REGEX.containsMatchIn(body) || RES_QUERY_ID_REGEX.containsMatchIn(body)
}
