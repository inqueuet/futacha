package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An あいもげ error, kept as the cause of a [NetworkException] (which has no subclasses): the screens read
 * [NetworkException.message], [NetworkException.statusCode] and [NetworkException.serverBody] as for Futaba; only the
 * あいもげ code reads [code] and [retryAfterSeconds].
 */
class AimogeApiException(
    val code: String?,
    val status: Int,
    val serverMessage: String?,
    val retryAfterSeconds: Long? = null
) : Exception(code ?: "HTTP $status")

/** The code and message of an error body in any of the three shapes, or nulls (HTML, empty, unreadable). */
internal data class AimogeErrorBody(val code: String?, val message: String?)

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseAimogeErrorBody(body: String?): AimogeErrorBody {
    val text = body?.trim().orEmpty()
    if (!text.startsWith("{")) return AimogeErrorBody(null, null)
    val obj = runCatching { errorJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return AimogeErrorBody(null, null)
    fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    // 1. {"error":{"code","message"}}
    (obj["error"] as? JsonObject)?.let { return AimogeErrorBody(it.str("code"), it.str("message")) }
    // 2. {"success":false,"error":"…","code":"…"}
    val outerCode = obj.str("code")
    val errorText = (obj["error"] as? JsonPrimitive)?.contentOrNull
    if (outerCode != null || errorText != null) return AimogeErrorBody(outerCode, errorText ?: obj.str("message"))
    // 3. {"success":false,"message":"Not Found"}
    return AimogeErrorBody(null, obj.str("message"))
}

// The Web client's fixed texts (`su`, `cu`, `lu`, `uu`, `du`, `fu` in ui-core).
private val fixedMessages = mapOf(
    "VALIDATION_ERROR" to "入力内容を確認してください。投稿本文は4000文字以内で入力してください",
    "INVALID_REQUEST" to "リクエスト内容を確認してください",
    "SPAM_REJECTED" to "スパム判定により投稿を受け付けられませんでした。内容を変更してお試しください",
    "ROLE_FORBIDDEN" to "この操作には参加期間が足りません。参加期間を満たしてからお試しください",
    "ACCOUNT_REQUIRED" to "8文字以上の削除キーで書き込み、アカウントに紐づけてください。削除キーを変更した場合は、書き込み時の削除キーに戻してください",
    "ACCOUNT_TOO_NEW" to "初書き込みから30分経過しないと、そうだね・delはできません"
)
private const val CHECK_INPUT = "入力内容を確認してください"
private const val NOT_ACCEPTED = "投稿を受け付けられませんでした"
private const val THREAD_NG = "このスレッドのNGワードに一致するため投稿できません"
private const val NSFW_SERVER_DEFAULT = "画像が現在のR18設定では投稿できません。画像を変更するか、スレッドのR18設定を確認してください"
private const val NSFW_FIXED = "この画像は現在のR18設定では投稿できません。画像を変更してお試しください"
private const val NSFW_SUFFIX = "画像を変更してお試しください"
const val AIMOGE_UPDATE_REQUIRED_MESSAGE = "あいもげの新しい形に合わせるため、アプリの更新が要ります"
private val genericEnglish = setOf("Bad Request", "Unprocessable Entity", "Forbidden", "Not Found", "Internal Server Error", "Service Unavailable")
private val httpOnly = Regex("^HTTP \\d+$")

/** The Web client's `hu`: the server's text when it says something real, else null. */
private fun usableServerText(message: String?, default: String): String? {
    val m = message?.trim().orEmpty()
    if (m.isEmpty() || m == default || m in genericEnglish || httpOnly.matches(m)) return null
    return m
}

/** The text to show for an あいもげ error, chosen as the Web client's `_u` does; null when there is nothing to add. */
fun aimogeErrorText(code: String?, status: Int, serverMessage: String?): String? {
    when (code) {
        "UNSUPPORTED_API_VERSION" -> return AIMOGE_UPDATE_REQUIRED_MESSAGE
        "THREAD_NG_REJECTED" -> return usableServerText(serverMessage, THREAD_NG) ?: THREAD_NG
        "SPAM_REJECTED" -> return usableServerText(serverMessage, NOT_ACCEPTED) ?: fixedMessages.getValue("SPAM_REJECTED")
        "NSFW_REJECTED" -> {
            val server = usableServerText(serverMessage, NSFW_SERVER_DEFAULT)
            return if (server != null) server + (if (server.endsWith("。")) "" else "。") + NSFW_SUFFIX else NSFW_FIXED
        }
    }
    code?.let { fixedMessages[it] }?.let { return it }
    if (status == 400) return CHECK_INPUT
    val m = serverMessage?.trim().orEmpty()
    if (m.isEmpty() || httpOnly.matches(m) || m in genericEnglish) return null
    return m
}

/** The Futaba-style lead the screens already show before the server's text. */
internal fun aimogeStatusLead(status: Int): String = when {
    status == 404 -> "スレッドが見つかりません (404)"
    status == 429 -> "あいもげが混み合っています。しばらく待ってからお試しください (429)"
    status in 500..599 -> "あいもげのサーバーが応答しません ($status)"
    else -> "あいもげのエラー ($status)"
}

/** The [NetworkException] for an error answer, with an [AimogeApiException] as its cause. */
fun aimogeNetworkException(status: Int, body: String?, retryAfterSeconds: Long? = null): NetworkException {
    val parsed = parseAimogeErrorBody(body)
    val text = aimogeErrorText(parsed.code, status, parsed.message)
    return NetworkException(
        message = text ?: aimogeStatusLead(status),
        statusCode = status,
        cause = AimogeApiException(parsed.code, status, parsed.message, retryAfterSeconds),
        serverBody = text
    )
}

/** The あいもげ details of [error] when it came from the あいもげ client, else null. */
fun Throwable.aimogeApiException(): AimogeApiException? = (this as? NetworkException)?.cause as? AimogeApiException

internal fun parseRetryAfterSeconds(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

@Suppress("unused")
private fun JsonObject.debugCode(): String? = this["code"]?.jsonPrimitive?.contentOrNull
