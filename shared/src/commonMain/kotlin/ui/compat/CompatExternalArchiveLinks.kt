package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val COMPAT_EXTERNAL_ARCHIVE_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private val compatMayBoardUrlRegex = Regex("^https?://may\\.2chan\\.net/b/", RegexOption.IGNORE_CASE)
private val compatImgBoardUrlRegex = Regex("^https?://img\\.2chan\\.net/b/", RegexOption.IGNORE_CASE)
private val compatMayHostRegex = Regex("^https?://may\\.2chan\\.net", RegexOption.IGNORE_CASE)
private val compatFutapoThreadRegex = Regex(
    "^https?://(may|img)\\.2chan\\.net/b/res/([0-9]+)\\.htm(?:[?#].*)?$",
    RegexOption.IGNORE_CASE
)

/** URL builders reproduced from the reference APK's external archive actions. */
internal fun buildCompatFtbucketUrl(threadUrl: String): String =
    "https://dev2.ftbucket.info/scdev2/scrapshot.php?rooturl=${threadUrl.encodeURLParameter()}"

internal fun buildCompatForestUrl(threadUrl: String): String? {
    if (!compatMayBoardUrlRegex.containsMatchIn(threadUrl)) {
        return null
    }
    return threadUrl.replaceFirst(
        compatMayHostRegex,
        "http://futabaforest.net"
    )
}

internal fun buildCompatFutapoUrl(threadUrl: String): String? {
    val match = compatFutapoThreadRegex.find(threadUrl) ?: return null
    val board = "${match.groupValues[1].lowercase()}_b"
    return "https://kako.futakuro.com/futa/$board/${match.groupValues[2]}/"
}

/** Registers a thread in つまんね。 and returns its board landing page. */
internal suspend fun registerCompatTsumanne(
    client: HttpClient,
    threadUrl: String,
    title: String
): Result<String> = runSuspendCatchingPreservingCancellation {
    val baseUrl = when {
        compatMayBoardUrlRegex.containsMatchIn(threadUrl) ->
            "https://tsumanne.net/my/"
        compatImgBoardUrlRegex.containsMatchIn(threadUrl) ->
            "https://tsumanne.net/si/"
        else -> error("つまんね。ではmayかimg以外のスレは登録できません")
    }

    // The reference app first checks the JSON index. A direct registration
    // POST is not idempotent on tsumanne.net and can create duplicates.
    val lookupResponse = client.get("${baseUrl}indexes.php") {
        // D.Server.Cache.Json.Display in sample/1.apk resolves to "w".
        parameter("w", threadUrl)
        parameter("sbmt", "URL")
        parameter("format", "json")
        headers[HttpHeaders.Referrer] = baseUrl
    }
    val lookupBody = readBoundedHttpResponseText(
        lookupResponse,
        COMPAT_EXTERNAL_ARCHIVE_RESPONSE_MAX_BYTES
    )
    check(lookupResponse.status.isSuccess()) {
        "つまんね。の登録確認に失敗しました（HTTP ${lookupResponse.status.value}）"
    }
    val lookupJson = runCatching { Json.parseToJsonElement(lookupBody).jsonObject }
        .getOrElse { error("つまんね。の応答を解釈できませんでした") }
    when (lookupJson["success"]?.jsonPrimitive?.booleanOrNull) {
        true -> {
            val path = lookupJson["path"]?.jsonPrimitive?.contentOrNull
                ?: lookupJson["logs"]?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("path")
                    ?.jsonPrimitive
                    ?.contentOrNull
            if (!path.isNullOrBlank()) {
                return@runSuspendCatchingPreservingCancellation "https://tsumanne.net/${path.trimStart('/')}"
            }
            // The current service reports an unregistered URL as
            // success:true with an empty logs array; continue to POST.
        }
        false -> Unit // Not registered yet; continue with the reference POST.
        null -> error("つまんね。の応答を解釈できませんでした")
    }

    val response = client.submitForm(
        url = "${baseUrl}input.php",
        formParameters = Parameters.build {
            append("url", threadUrl)
            append("category", title.lineSequence().firstOrNull()?.trim().orEmpty().take(50))
            append("sbmt", "追加")
        }
    ) {
        headers[HttpHeaders.Referrer] = baseUrl
    }
    check(response.status.isSuccess()) { "スレ登録失敗（HTTP ${response.status.value}）" }
    val responseBody = readBoundedHttpResponseText(
        response,
        COMPAT_EXTERNAL_ARCHIVE_RESPONSE_MAX_BYTES
    )
    check(
        !responseBody.contains("<b>Warning</b>", ignoreCase = true) &&
            !responseBody.contains("Undefined array key", ignoreCase = true)
    ) { "つまんね。への登録に失敗しました" }
    baseUrl
}
