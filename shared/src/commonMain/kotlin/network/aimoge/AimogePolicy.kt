package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.util.readResponseBody
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** `GET /client-policy`: what the board takes now (the server checks again when a post is sent). */
data class AimogeClientPolicy(
    val allowedMimeTypes: Set<String>,
    val maxFileSizeBytes: Long,
    val maxBodyLength: Int,
    val threadCreationCooldownSeconds: Long,
    val replyCreationCooldownSeconds: Long,
    val threadReplyCreationCooldownSeconds: Long
)

/** `GET /thread-limits/{slug}`: how long a new thread may live. */
data class AimogeThreadLimits(
    val daysInput: Boolean,
    val durationHours: Double,
    val minimumMinutes: Int,
    val allowR18ThreadCreation: Boolean
)

private val policyJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

internal fun parseAimogeClientPolicy(obj: JsonObject): AimogeClientPolicy? {
    val mimes = (obj["allowedMimeTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() } ?: return null
    return AimogeClientPolicy(
        allowedMimeTypes = mimes.toSet(),
        maxFileSizeBytes = obj.long("maxFileSizeBytes")?.takeIf { it > 0 } ?: return null,
        maxBodyLength = obj.long("maxBodyLength")?.takeIf { it > 0 }?.toInt() ?: return null,
        threadCreationCooldownSeconds = obj.long("threadCreationCooldownSeconds")?.coerceAtLeast(0) ?: 0,
        replyCreationCooldownSeconds = obj.long("replyCreationCooldownSeconds")?.coerceAtLeast(0) ?: 0,
        threadReplyCreationCooldownSeconds = obj.long("threadReplyCreationCooldownSeconds")?.coerceAtLeast(0) ?: 0
    )
}

internal fun parseAimogeThreadLimits(obj: JsonObject): AimogeThreadLimits? = AimogeThreadLimits(
    daysInput = (obj["inputMode"] as? JsonPrimitive)?.contentOrNull == "days_hours_minutes",
    durationHours = (obj["durationHours"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 && it.isFinite() } ?: return null,
    minimumMinutes = obj.long("minimumMinutes")?.takeIf { it > 0 }?.toInt() ?: return null,
    allowR18ThreadCreation = (obj["allowR18ThreadCreation"] as? JsonPrimitive)?.booleanOrNull ?: false
)

/** Reads the two non-secret settings; null on any failure (the server still checks every post). */
class AimogePolicyApi(private val client: HttpClient) {
    suspend fun clientPolicy(boardSlug: String): AimogeClientPolicy? =
        get("$AIMOGE_API_BASE/client-policy", boardSlug)?.let(::parseAimogeClientPolicy)

    suspend fun threadLimits(boardSlug: String): AimogeThreadLimits? =
        get("$AIMOGE_API_BASE/thread-limits/${boardSlug.encodeURLPathPart()}", boardSlug)?.let(::parseAimogeThreadLimits)

    private suspend fun get(url: String, boardSlug: String): JsonObject? = try {
        readResponseBody { client.prepareGet(url) {
            header("X-Api-Version", AIMOGE_API_VERSION)
            header(HttpHeaders.Accept, "application/json")
            header("X-Board-Key", boardSlug)
        }.execute { response ->
            if (!response.status.isSuccess()) return@execute null
            val text = readAimogeControlBody(response)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { policyJson.parseToJsonElement(text).jsonObject }
        } }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** Why the post would be refused by [policy], before anything is sent; null when it may go. */
fun aimogePolicyRefusal(
    policy: AimogeClientPolicy,
    body: String,
    attachment: ByteArray?,
    waitSeconds: Long
): String? {
    if (waitSeconds > 0) return aimogeWaitMessage(waitSeconds)
    if (body.trim().length > policy.maxBodyLength) return "本文が長すぎます（${policy.maxBodyLength}字まで）"
    if (attachment != null) {
        val mime = aimogeAttachmentMime(attachment)
        if (mime == null || (policy.allowedMimeTypes.isNotEmpty() && mime !in policy.allowedMimeTypes)) {
            return "この形式のファイルはこの板に送れません（送れる形式：${aimogeMimeLabels(policy.allowedMimeTypes)}）"
        }
        if (attachment.size > policy.maxFileSizeBytes) return "ファイルが大きすぎます（${aimogeSizeLabel(policy.maxFileSizeBytes)}まで）"
    }
    return null
}

fun aimogeWaitMessage(seconds: Long): String = "あいもげの決まりで、あと${seconds}秒待ってから送ってください"

/** The cooldown left: [cooldownSeconds] after [lastAtMillis] (null = never sent). */
fun aimogeWaitSeconds(lastAtMillis: Long?, cooldownSeconds: Long, nowMillis: Long): Long {
    if (lastAtMillis == null || cooldownSeconds <= 0) return 0
    val left = lastAtMillis + cooldownSeconds * 1000 - nowMillis
    return if (left <= 0) 0 else (left + 999) / 1000
}

fun aimogeMimeLabels(mimes: Set<String>): String =
    mimes.map { it.substringAfter('/').uppercase().replace("JPEG", "JPG") }.sorted().joinToString("・").ifEmpty { "なし" }

fun aimogeSizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${(bytes * 10 / (1024 * 1024)) / 10.0}MB".replace(".0MB", "MB")
    bytes >= 1024 -> "${bytes / 1024}KB"
    else -> "${bytes}B"
}

/** One choice of a thread's lifetime; [wire] is the `duration` field (empty = the current limit). */
data class AimogeDurationChoice(val label: String, val wire: String)

/** The choices offered for [limits]: the longest first, then shorter steps, down to the minimum. */
fun aimogeDurationChoices(limits: AimogeThreadLimits): List<AimogeDurationChoice> {
    val maxMinutes = (limits.durationHours * 60).toLong()
    val steps = listOf(30L, 60L, 3 * 60L, 6 * 60L, 12 * 60L, 24 * 60L, 3 * 24 * 60L)
        .filter { it in limits.minimumMinutes.toLong() until maxMinutes }
    val minimum = limits.minimumMinutes.toLong().takeIf { it < maxMinutes && it !in steps }
    return listOf(AimogeDurationChoice("いちばん長く（${aimogeDurationLabel(maxMinutes)}）", "")) +
        (steps + listOfNotNull(minimum)).sortedDescending().map { AimogeDurationChoice(aimogeDurationLabel(it), aimogeDurationWire(it, limits.daysInput)) }
}

fun aimogeDurationLabel(minutes: Long): String {
    val d = minutes / (24 * 60); val h = minutes % (24 * 60) / 60; val m = minutes % 60
    return buildString {
        if (d > 0) append("${d}日")
        if (h > 0) append("${h}時間")
        if (m > 0 || isEmpty()) append("${m}分")
    }
}

/** `H:M`, or `D:H:M` when the board asks for days. */
fun aimogeDurationWire(minutes: Long, daysInput: Boolean): String =
    if (daysInput) "${minutes / (24 * 60)}:${minutes % (24 * 60) / 60}:${minutes % 60}" else "${minutes / 60}:${minutes % 60}"
