package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger
import kotlin.text.RegexOption

private val THREAD_ID_REGEX = """res/(\d+)\.html?""".toRegex(RegexOption.IGNORE_CASE)
private val RES_QUERY_ID_REGEX = """\bres=(\d+)\b""".toRegex(RegexOption.IGNORE_CASE)
private val SUCCESS_KEYWORDS = listOf("書き込みました", "書き込みました。", "書き込みが完了", "書きこみました")
private val ERROR_KEYWORDS = listOf("エラー", "error", "荒らし", "規制", "拒否", "連続投稿", "大きすぎ", "時間を置いて")
private val COOKIE_RECOVERY_KEYWORDS = listOf("cookie", "クッキー", "posttime", "cxyl")
private val COOKIE_RECOVERY_RESET_KEYWORDS = listOf(
    "削除",
    "リセット",
    "再取得",
    "再発行",
    "再生成",
    "作り直",
    "作成",
    "生成",
    "無効",
    "失効",
    "有効期限",
    "期限切れ",
    "期限が切れ",
    "expired"
)
private val COOKIE_RECOVERY_IP_KEYWORDS = listOf(
    "書き込み可能なip",
    "書き込めるip",
    "投稿可能なip",
    "送信元ip",
    "ipアドレス",
    "ipから",
    "writable ip"
)
private val POSTING_TEMPORARY_RESTRICTION_KEYWORDS = listOf(
    "時間を置いて",
    "しばらく",
    "少し待",
    "待って",
    "短時間",
    "連続投稿",
    "混雑",
    "too fast",
    "slow down"
)
private val POSTING_IP_RESTRICTION_KEYWORDS = listOf(
    "規制中",
    "アクセス規制",
    "書き込みできません",
    "書き込めません",
    "拒否",
    "禁止",
    "ブロック",
    "block",
    "ban",
    "proxy",
    "vpn",
    "串",
    "ホスト",
    "ip",
    "焼かれ",
    "この環境からは",
    "この回線からは"
)
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
private const val COOKIE_RESET_RECOVERY_GUIDANCE =
    "Cookie を削除し、ふたばちゃんねるに書き込み可能なIPからブラウザ等で一度書き込んで、新しい Cookie を生成してください"
private const val TEMPORARY_RESTRICTION_GUIDANCE =
    "時間を置いてから再試行してください"
private const val IP_RESTRICTION_GUIDANCE =
    "IP規制の可能性があります。時間を置くか、別の書き込み可能な回線を試してください"

internal enum class HttpBoardApiPostingFailureKind {
    UNKNOWN,
    COOKIE_RESET_REQUIRED,
    TEMPORARY_RESTRICTION,
    IP_RESTRICTION
}

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

internal fun requiresHttpBoardApiCookieResetRecovery(detail: String): Boolean {
    return classifyHttpBoardApiPostingFailure(detail) == HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
}

internal fun classifyHttpBoardApiPostingFailure(detail: String): HttpBoardApiPostingFailureKind {
    val normalized = detail
        .trim()
        .replace('\u3000', ' ')
        .lowercase()
    if (normalized.isEmpty()) {
        return HttpBoardApiPostingFailureKind.UNKNOWN
    }
    if (normalized.contains("posttime") || normalized.contains("cxyl")) {
        return HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
    }
    val hasCookieKeyword = COOKIE_RECOVERY_KEYWORDS.any { normalized.contains(it) }
    if (!hasCookieKeyword) {
        if (
            COOKIE_RECOVERY_IP_KEYWORDS.any { normalized.contains(it) } &&
            (normalized.contains("期限") || normalized.contains("cookie"))
        ) {
            return HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
        }
    } else {
        val hasResetKeyword = COOKIE_RECOVERY_RESET_KEYWORDS.any { normalized.contains(it) }
        val hasIpKeyword = COOKIE_RECOVERY_IP_KEYWORDS.any { normalized.contains(it) }
        if (hasResetKeyword || hasIpKeyword) {
            return HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
        }
    }

    val hasTemporaryRestrictionKeyword =
        POSTING_TEMPORARY_RESTRICTION_KEYWORDS.any { normalized.contains(it) }
    val hasIpRestrictionKeyword =
        POSTING_IP_RESTRICTION_KEYWORDS.any { normalized.contains(it) }

    return when {
        hasIpRestrictionKeyword && hasTemporaryRestrictionKeyword -> HttpBoardApiPostingFailureKind.IP_RESTRICTION
        hasIpRestrictionKeyword -> HttpBoardApiPostingFailureKind.IP_RESTRICTION
        hasTemporaryRestrictionKeyword -> HttpBoardApiPostingFailureKind.TEMPORARY_RESTRICTION
        else -> HttpBoardApiPostingFailureKind.UNKNOWN
    }
}

internal fun buildHttpBoardApiCookieResetRecoveryGuidance(detail: String): String? {
    if (classifyHttpBoardApiPostingFailure(detail) != HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED) {
        return null
    }
    val normalized = detail
        .trim()
        .replace('\u3000', ' ')
        .lowercase()
    val alreadyGuided =
        (normalized.contains("cookie") || normalized.contains("クッキー")) &&
            COOKIE_RECOVERY_IP_KEYWORDS.any { normalized.contains(it) } &&
            COOKIE_RECOVERY_RESET_KEYWORDS.any { normalized.contains(it) }
    return if (alreadyGuided) {
        null
    } else {
        COOKIE_RESET_RECOVERY_GUIDANCE
    }
}

internal fun buildHttpBoardApiPostingFailureGuidance(detail: String): String? {
    return when (classifyHttpBoardApiPostingFailure(detail)) {
        HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED -> buildHttpBoardApiCookieResetRecoveryGuidance(detail)
        HttpBoardApiPostingFailureKind.TEMPORARY_RESTRICTION -> {
            val normalized = detail.trim().replace('\u3000', ' ').lowercase()
            if (POSTING_TEMPORARY_RESTRICTION_KEYWORDS.any { normalized.contains(it) }) {
                if (
                    normalized.contains("時間を置いて") ||
                    normalized.contains("しばらく") ||
                    normalized.contains("待って")
                ) {
                    null
                } else {
                    TEMPORARY_RESTRICTION_GUIDANCE
                }
            } else {
                null
            }
        }
        HttpBoardApiPostingFailureKind.IP_RESTRICTION -> {
            val normalized = detail.trim().replace('\u3000', ' ').lowercase()
            if (
                normalized.contains("ip規制") ||
                normalized.contains("別の回線") ||
                normalized.contains("書き込み可能なip")
            ) {
                null
            } else {
                IP_RESTRICTION_GUIDANCE
            }
        }
        HttpBoardApiPostingFailureKind.UNKNOWN -> null
    }
}

internal fun buildHttpBoardApiPostingFailureMessage(prefix: String, detail: String): String {
    val trimmed = detail.trim()
    if (trimmed.isEmpty()) {
        return prefix
    }
    val guidance = buildHttpBoardApiPostingFailureGuidance(trimmed)
    return if (guidance == null) {
        "$prefix: $trimmed"
    } else {
        "$prefix: $trimmed $guidance"
    }
}

internal fun logHttpBoardApiPostingFailureClassification(
    logTag: String,
    operation: String,
    detail: String
) {
    val trimmed = detail.trim()
    if (trimmed.isEmpty()) {
        Logger.w(logTag, "Posting failure classification for $operation: empty detail")
        return
    }
    val kind = classifyHttpBoardApiPostingFailure(trimmed)
    val message = "Posting failure classified for $operation as $kind: ${trimmed.take(200)}"
    when (kind) {
        HttpBoardApiPostingFailureKind.UNKNOWN -> Logger.w(logTag, message)
        else -> Logger.i(logTag, message)
    }
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
