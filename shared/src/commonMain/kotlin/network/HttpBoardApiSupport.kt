package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear
import kotlin.text.RegexOption

private val THREAD_ID_REGEX = """res/(\d+)\.html?""".toRegex(RegexOption.IGNORE_CASE)
private val RES_QUERY_ID_REGEX = """\bres=(\d+)\b""".toRegex(RegexOption.IGNORE_CASE)
private val SUCCESS_KEYWORDS = listOf("書き込みました", "書き込みました。", "書き込みが完了", "書きこみました")
private val ERROR_KEYWORDS = listOf(
    "エラー",
    "error",
    "cookie",
    "クッキー",
    "荒らし",
    "規制",
    "制限",
    "拒否",
    "連続投稿",
    "大きすぎ",
    "書き込みできません",
    "書き込みができません",
    "書き込めません",
    "書けません",
    "投稿できません",
    "投稿ができません",
    "時間を置いて",
    "待",
    "あと",
    "秒",
    "分後",
    "日後"
)
private val COOKIE_RECOVERY_KEYWORDS = listOf("cookie", "クッキー", "posttime", "ptmt", "cxyl")
private val COOKIE_ENABLE_RETRY_KEYWORDS = listOf(
    "cookieを有効にしてもう一度",
    "cookie を有効にしてもう一度",
    "cookieを有効にし",
    "cookie を有効にし",
    "クッキーを有効にしてもう一度",
    "クッキーを有効にし"
)
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
    "ip規制",
    "ip制限",
    "書き込みできません",
    "書き込みができません",
    "書き込めません",
    "書けません",
    "投稿できません",
    "投稿ができません",
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
private val POSTING_WAIT_CONTEXT_KEYWORDS = listOf(
    "あと",
    "後",
    "待",
    "時間",
    "秒",
    "分",
    "日",
    "hour",
    "minute",
    "second",
    "wait",
    "retry"
)
private val POSTING_WAIT_HOUR_MINUTE_SECOND_REGEX =
    Regex("""(\d{1,3})\s*時間(?:\s*(\d{1,2})\s*分)?(?:\s*(\d{1,2})\s*秒)?""")
private val POSTING_WAIT_MINUTE_SECOND_REGEX =
    Regex("""(\d{1,4})\s*分(?:\s*(\d{1,2})\s*秒)?""")
private val POSTING_WAIT_DAY_REGEX =
    Regex("""(\d{1,3})\s*日(?:後|ほど|程度|くらい|ぐらい)?""")
private val POSTING_WAIT_SECOND_REGEX =
    Regex("""(\d{1,7})\s*秒(?:後|ほど|程度|くらい|ぐらい)?""")
private val POSTING_WAIT_ENGLISH_HOUR_REGEX =
    Regex("""(\d{1,3})\s*(?:hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
private val POSTING_WAIT_ENGLISH_MINUTE_REGEX =
    Regex("""(\d{1,4})\s*(?:minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
private val POSTING_WAIT_ENGLISH_SECOND_REGEX =
    Regex("""(\d{1,7})\s*(?:seconds?|secs?|s)\b""", RegexOption.IGNORE_CASE)
private val JSON_STATUS_REGEX = """"status"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
private val JSON_MESSAGE_REGEX = """"(error|reason|message)"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
private val JSON_JUMPTO_REGEX = """"jumpto"\s*:\s*(\d+)""".toRegex()
private val JSON_THISNO_REGEX = """"thisno"\s*:\s*(\d+)""".toRegex()
private val HUMAN_READABLE_WHITESPACE_REGEX = Regex("""\s+""")
private const val CHRENC_NEARBY_SCAN_WINDOW = 4096
private const val CHRENC_FALLBACK_SCAN_MAX_BYTES = 512 * 1024
private const val INPUT_TAG_SCAN_LIMIT = 768
private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]+);""")
private const val COOKIE_RESET_RECOVERY_GUIDANCE =
    "今回の投稿試行で投稿用 Cookie が保存された可能性があります。Cookie を保持したままもう一度投稿してください。残り秒数が表示された場合は、その時間まで待ってください"
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
    return extractHttpBoardApiHumanReadableErrorLine(normalized)
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
    return httpBoardApiHumanReadableLines(normalized)
        .firstOrNull()
        ?.take(120)
        ?: body.take(120)
}

private fun httpBoardApiHumanReadableLines(body: String): Sequence<String> {
    val text = if (body.indexOf('<') >= 0 || body.indexOf('&') >= 0) {
        HtmlEntityDecoder.decode(
            stripHtmlTagsLinear(
                replaceHtmlBreakTags(
                    value = body,
                    lineBreakReplacement = " ",
                    paragraphReplacement = "\n"
                )
            )
        )
    } else {
        body
    }
    return text
        .replace('\u00a0', ' ')
        .replace('\u3000', ' ')
        .lineSequence()
        .map { it.trim().replace(HUMAN_READABLE_WHITESPACE_REGEX, " ") }
        .filter { it.isNotEmpty() }
}

private fun extractHttpBoardApiHumanReadableErrorLine(body: String): String? {
    val lines = httpBoardApiHumanReadableLines(body).toList()
    val errorIndex = lines.indexOfFirst { line ->
        ERROR_KEYWORDS.any { keyword ->
            line.contains(keyword, ignoreCase = true)
        }
    }
    if (errorIndex == -1) return null

    val firstLine = lines[errorIndex]
    val tailLines = lines
        .drop(errorIndex + 1)
        .take(4)
        .filter { line ->
            ERROR_KEYWORDS.any { keyword -> line.contains(keyword, ignoreCase = true) } ||
                extractHttpBoardApiPostingWaitSeconds(line) != null
        }
    if (tailLines.isEmpty()) {
        return firstLine
    }
    return (listOf(firstLine) + tailLines)
        .joinToString(" ")
        .take(300)
}

internal fun requiresHttpBoardApiCookieResetRecovery(detail: String): Boolean {
    return classifyHttpBoardApiPostingFailure(detail) == HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
}

internal fun extractHttpBoardApiPostingWaitSeconds(detail: String): Long? {
    val normalized = detail
        .trim()
        .replace('\u3000', ' ')
        .lowercase()
    if (normalized.isEmpty()) return null
    if (!POSTING_WAIT_CONTEXT_KEYWORDS.any { normalized.contains(it) }) return null

    return listOf(
        POSTING_WAIT_HOUR_MINUTE_SECOND_REGEX.find(normalized)?.let { match ->
            val hours = match.groupValues.getOrNull(1).orEmpty().toLongOrNull() ?: 0L
            val minutes = match.groupValues.getOrNull(2).orEmpty().toLongOrNull() ?: 0L
            val seconds = match.groupValues.getOrNull(3).orEmpty().toLongOrNull() ?: 0L
            hours * 3600L + minutes * 60L + seconds
        },
        POSTING_WAIT_MINUTE_SECOND_REGEX.find(normalized)?.let { match ->
            val minutes = match.groupValues.getOrNull(1).orEmpty().toLongOrNull() ?: 0L
            val seconds = match.groupValues.getOrNull(2).orEmpty().toLongOrNull() ?: 0L
            minutes * 60L + seconds
        },
        POSTING_WAIT_DAY_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it * 86_400L },
        POSTING_WAIT_SECOND_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull(),
        POSTING_WAIT_ENGLISH_HOUR_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it * 3600L },
        POSTING_WAIT_ENGLISH_MINUTE_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it * 60L },
        POSTING_WAIT_ENGLISH_SECOND_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
    ).firstOrNull { it != null && it > 0L }
}

internal fun formatHttpBoardApiPostingWaitLabel(seconds: Long): String {
    val clampedSeconds = seconds.coerceAtLeast(1L)
    val days = clampedSeconds / 86_400L
    val hours = (clampedSeconds % 86_400L) / 3600L
    val minutes = (clampedSeconds % 3600L) / 60L
    val restSeconds = clampedSeconds % 60L
    return when {
        days > 0L && hours > 0L -> "${days}日${hours}時間"
        days > 0L -> "${days}日"
        hours > 0L && minutes > 0L -> "${hours}時間${minutes}分"
        hours > 0L -> "${hours}時間"
        minutes > 0L && restSeconds > 0L -> "${minutes}分${restSeconds}秒"
        minutes > 0L -> "${minutes}分"
        else -> "${restSeconds}秒"
    }
}

internal fun classifyHttpBoardApiPostingFailure(detail: String): HttpBoardApiPostingFailureKind {
    val normalized = detail
        .trim()
        .replace('\u3000', ' ')
        .lowercase()
    if (normalized.isEmpty()) {
        return HttpBoardApiPostingFailureKind.UNKNOWN
    }
    if (normalized.contains("posttime") || normalized.contains("ptmt")) {
        return HttpBoardApiPostingFailureKind.COOKIE_RESET_REQUIRED
    }
    if (COOKIE_ENABLE_RETRY_KEYWORDS.any { normalized.contains(it) }) {
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
    extractHttpBoardApiPostingWaitSeconds(detail)?.let { waitSeconds ->
        val waitLabel = formatHttpBoardApiPostingWaitLabel(waitSeconds)
        return "今回の投稿試行で投稿用 Cookie が保存された可能性があります。Cookie を保持したまま約${waitLabel}後に再試行してください"
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
            extractHttpBoardApiPostingWaitSeconds(detail)?.let { waitSeconds ->
                return "約${formatHttpBoardApiPostingWaitLabel(waitSeconds)}後に再試行してください"
            }
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
            extractHttpBoardApiPostingWaitSeconds(detail)?.let { waitSeconds ->
                return "約${formatHttpBoardApiPostingWaitLabel(waitSeconds)}後に再試行してください"
            }
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
        HttpBoardApiPostingFailureKind.UNKNOWN -> {
            extractHttpBoardApiPostingWaitSeconds(detail)?.let { waitSeconds ->
                return "約${formatHttpBoardApiPostingWaitLabel(waitSeconds)}後に再試行してください"
            }
            null
        }
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
    return parseHttpBoardApiInputValue(
        html = html,
        inputName = "chrenc",
        nearbyScanWindow = CHRENC_NEARBY_SCAN_WINDOW,
        fallbackScanMaxBytes = CHRENC_FALLBACK_SCAN_MAX_BYTES
    )
}

internal fun parseHttpBoardApiInputValue(
    html: String,
    inputName: String,
    nearbyScanWindow: Int = CHRENC_NEARBY_SCAN_WINDOW,
    fallbackScanMaxBytes: Int = CHRENC_FALLBACK_SCAN_MAX_BYTES
): String? {
    if (html.isBlank() || inputName.isBlank()) return null
    val nameIndex = html.indexOf(inputName, ignoreCase = true)
    val scanTarget = if (nameIndex >= 0) {
        val start = (nameIndex - 1024).coerceAtLeast(0)
        val end = (nameIndex + nearbyScanWindow).coerceAtMost(html.length)
        html.substring(start, end)
    } else {
        html.take(fallbackScanMaxBytes)
    }
    var searchStart = 0
    while (searchStart < scanTarget.length) {
        val tagStart = scanTarget.indexOf("<input", startIndex = searchStart, ignoreCase = true)
        if (tagStart == -1) return null
        val tagEnd = findHttpBoardApiInputTagEnd(scanTarget, tagStart)
        if (tagEnd == -1) {
            searchStart = tagStart + 1
            continue
        }
        val tag = scanTarget.substring(tagStart, tagEnd + 1)
        val name = extractHttpBoardApiTagAttribute(tag, "name")
        if (name.equals(inputName, ignoreCase = true)) {
            val rawValue = extractHttpBoardApiTagAttribute(tag, "value")?.trim().orEmpty()
            if (rawValue.isEmpty()) return null
            return decodeHttpBoardApiNumericEntities(rawValue)
        }
        searchStart = tagEnd + 1
    }
    return null
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

private fun findHttpBoardApiInputTagEnd(value: String, startIndex: Int): Int {
    val limit = minOf(value.length, startIndex + INPUT_TAG_SCAN_LIMIT + 1)
    var index = startIndex + 1
    var quote: Char? = null
    while (index < limit) {
        val char = value[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else {
            when (char) {
                '"', '\'' -> quote = char
                '>' -> return index
                '<' -> return -1
            }
        }
        index += 1
    }
    return -1
}

private fun extractHttpBoardApiTagAttribute(tag: String, attributeName: String): String? {
    var searchStart = 0
    while (searchStart < tag.length) {
        val attrIndex = tag.indexOf(attributeName, startIndex = searchStart, ignoreCase = true)
        if (attrIndex == -1) return null
        val before = tag.getOrNull(attrIndex - 1)
        val after = tag.getOrNull(attrIndex + attributeName.length)
        val hasNameBoundaryBefore = before == null || before.isWhitespace() || before == '<' || before == '/'
        val hasNameBoundaryAfter = after == null || after.isWhitespace() || after == '=' || after == '>'
        if (!hasNameBoundaryBefore || !hasNameBoundaryAfter) {
            searchStart = attrIndex + attributeName.length
            continue
        }
        var index = attrIndex + attributeName.length
        while (index < tag.length && tag[index].isWhitespace()) index += 1
        if (tag.getOrNull(index) != '=') {
            searchStart = index
            continue
        }
        index += 1
        while (index < tag.length && tag[index].isWhitespace()) index += 1
        val quote = tag.getOrNull(index)
        return if (quote == '"' || quote == '\'') {
            val valueStart = index + 1
            val valueEnd = tag.indexOf(quote, startIndex = valueStart)
            if (valueEnd == -1) null else tag.substring(valueStart, valueEnd)
        } else {
            val valueStart = index
            while (index < tag.length && !tag[index].isWhitespace() && tag[index] != '>') index += 1
            tag.substring(valueStart, index)
        }
    }
    return null
}
