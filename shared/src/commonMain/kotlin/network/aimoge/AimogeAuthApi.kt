package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.compat.AIMOGE_HOST
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Registered with あいもげ (research ch.2 and ch.8). Not a secret: it is visible in the traffic anyway. */
const val AIMOGE_CALLBACK_SCHEME = "com.valoser.futacha"
const val AIMOGE_REDIRECT_URI = "$AIMOGE_CALLBACK_SCHEME://auth/callback"

/** Which of the three approval endpoints answered. */
enum class AimogeAuthEndpoint { START, EXCHANGE, REVOKE }

/**
 * A failure of an approval endpoint. The message is fixed text: it never holds the code, the verifier, the
 * credential, the approval page's address or the return address (they must not reach logs or crash reports).
 */
class AimogeAuthException(
    val endpoint: AimogeAuthEndpoint,
    /** The HTTP status, or null when the request did not get an answer. */
    val status: Int?,
    val code: String? = null
) : Exception("あいもげの承認の要求が失敗しました（${endpoint.name} ${status ?: "通信"}）")

data class AimogeAuthorizationStart(val id: String, val expiresAtMillis: Long, val authorizationUrl: String) {
    override fun toString(): String = "AimogeAuthorizationStart(id=$id)"
}

data class AimogeCredential(val credential: String, val credentialId: String, val expiresAtMillis: Long) {
    override fun toString(): String = "AimogeCredential(id=$credentialId)"
}

private val authJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** The three approval requests (`/app-authorizations`, `/app-credentials`, `/app-credentials/current`). */
class AimogeAuthApi(private val client: HttpClient) {
    suspend fun start(boardSlug: String, installationId: String, state: String, codeChallenge: String): AimogeAuthorizationStart {
        val body = buildJsonObject {
            put("redirectUri", AIMOGE_REDIRECT_URI)
            put("installationId", installationId)
            put("state", state)
            put("codeChallenge", codeChallenge)
            put("codeChallengeMethod", "S256")
        }
        val obj = send(AimogeAuthEndpoint.START, boardSlug) { url ->
            client.post(url) { common(boardSlug); contentType(ContentType.Application.Json); setBody(body.toString()) }
        } ?: throw AimogeAuthException(AimogeAuthEndpoint.START, 201)
        val id = obj.str("id")
        val expires = obj.str("expiresAt")?.let(::parseInstantMillis)
        val page = obj.str("authorizationUrl")
        if (id == null || expires == null || page == null || !isAimogeApprovalPage(page, boardSlug)) {
            throw AimogeAuthException(AimogeAuthEndpoint.START, 201)
        }
        return AimogeAuthorizationStart(id, expires, page)
    }

    suspend fun exchange(boardSlug: String, code: String, codeVerifier: String): AimogeCredential {
        val body = buildJsonObject {
            put("redirectUri", AIMOGE_REDIRECT_URI)
            put("code", code)
            put("codeVerifier", codeVerifier)
        }
        val obj = send(AimogeAuthEndpoint.EXCHANGE, boardSlug) { url ->
            client.post(url) { common(boardSlug); contentType(ContentType.Application.Json); setBody(body.toString()) }
        } ?: throw AimogeAuthException(AimogeAuthEndpoint.EXCHANGE, 201)
        val credential = obj.str("credential")
        val id = obj.str("credentialId")
        val expires = obj.str("expiresAt")?.let(::parseInstantMillis)
        if (credential.isNullOrBlank() || id == null || expires == null) throw AimogeAuthException(AimogeAuthEndpoint.EXCHANGE, 201)
        return AimogeCredential(credential, id, expires)
    }

    suspend fun revoke(boardSlug: String, credential: String) {
        send(AimogeAuthEndpoint.REVOKE, boardSlug) { url ->
            client.delete(url) { common(boardSlug); header("x-aimg-token", credential) }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.common(boardSlug: String) {
        header("X-Api-Version", AIMOGE_API_VERSION)
        header(HttpHeaders.Accept, "application/json")
        header("X-Board-Key", boardSlug)
    }

    private suspend fun send(
        endpoint: AimogeAuthEndpoint,
        boardSlug: String,
        request: suspend (String) -> HttpResponse
    ): JsonObject? {
        require(boardSlug.isNotBlank())
        val path = when (endpoint) {
            AimogeAuthEndpoint.START -> "/app-authorizations"
            AimogeAuthEndpoint.EXCHANGE -> "/app-credentials"
            AimogeAuthEndpoint.REVOKE -> "/app-credentials/current"
        }
        val response = try {
            request(AIMOGE_API_BASE + path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The transport's own text can name hosts and paths; it is dropped on purpose.
            throw AimogeAuthException(endpoint, null)
        }
        val status = response.status.value
        val text = com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation { response.bodyAsText() }.getOrNull()
        if (status !in 200..299) throw AimogeAuthException(endpoint, status, parseAimogeErrorBody(text).code)
        if (status == 204 || text.isNullOrBlank()) return null
        return runCatching { authJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw AimogeAuthException(endpoint, status)
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun parseInstantMillis(value: String): Long? = runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()

/**
 * The approval page is opened as the server returns it (official guide); only https on nijiurachan.net is checked,
 * so a change of its path does not stop approvals. Anything else is not opened.
 */
@Suppress("UNUSED_PARAMETER")
fun isAimogeApprovalPage(value: String, boardSlug: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name == "https" && url.host.equals(AIMOGE_HOST, ignoreCase = true) && url.port == 443 &&
        url.user == null && value.none { it.isWhitespace() || it.isISOControl() }
}

/** state and verifier come from the platform's secure random source (Uuid.random). */
@OptIn(ExperimentalUuidApi::class)
private fun secureRandomBytes(count: Int): ByteArray {
    val out = ByteArray(count)
    var filled = 0
    while (filled < count) {
        val bytes = Uuid.random().toByteArray()
        // Skip the version and variant bytes (6 and 8), which are fixed.
        for ((i, b) in bytes.withIndex()) {
            if (i == 6 || i == 8 || filled >= count) continue
            out[filled++] = b
        }
    }
    return out
}

private fun base64Url(bytes: ByteArray): String = bytes.toByteString().base64Url().trimEnd('=')

/** 43 characters of `[A-Za-z0-9_-]` (the OpenAPI pattern is 32..128). */
fun newAimogeState(): String = base64Url(secureRandomBytes(32))

/** 64 characters of `[A-Za-z0-9_-]` (RFC 7636 allows 43..128). */
fun newAimogeCodeVerifier(): String = base64Url(secureRandomBytes(48))

/** RFC 7636 S256: base64url(SHA-256(verifier)) without padding. */
fun aimogeCodeChallenge(verifier: String): String = verifier.encodeUtf8().sha256().base64Url().trimEnd('=')

@OptIn(ExperimentalUuidApi::class)
fun newAimogeInstallationId(): String = Uuid.random().toString()

/** The `state` and `code` of an approval return, or null when [url] is not our return address. */
data class AimogeAuthReturn(val state: String, val code: String) {
    override fun toString(): String = "AimogeAuthReturn(…)"
}

fun parseAimogeAuthReturn(url: String): AimogeAuthReturn? {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (!parsed.protocol.name.equals(AIMOGE_CALLBACK_SCHEME, true)) return null
    if (!url.startsWith("$AIMOGE_CALLBACK_SCHEME://auth/callback", ignoreCase = true)) return null
    val state = parsed.parameters["state"]?.takeIf { STATE_PATTERN.matches(it) } ?: return null
    val code = parsed.parameters["code"]?.takeIf { CODE_PATTERN.matches(it) } ?: return null
    return AimogeAuthReturn(state, code)
}

/** An approval return that says the person refused (`access_denied`) or closed (`cancelled`) the page. */
data class AimogeAuthErrorReturn(val state: String?, val cancelled: Boolean)

fun parseAimogeAuthErrorReturn(url: String): AimogeAuthErrorReturn? {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (!url.startsWith("$AIMOGE_CALLBACK_SCHEME://auth/callback", ignoreCase = true)) return null
    val error = parsed.parameters["error"] ?: return null
    val state = parsed.parameters["state"]?.takeIf { STATE_PATTERN.matches(it) }
    return when (error) {
        "access_denied" -> AimogeAuthErrorReturn(state, cancelled = false)
        "cancelled" -> AimogeAuthErrorReturn(state, cancelled = true)
        else -> null
    }
}

private val STATE_PATTERN = Regex("^[A-Za-z0-9_-]{32,128}$")
private val CODE_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

private const val DELETE_KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

/** A new random delete key of 16 letters and digits (J7: the suggested first value, never the Futaba key). */
fun newAimogeDeleteKey(): String {
    val out = StringBuilder()
    while (out.length < 16) {
        for (b in secureRandomBytes(32)) {
            val v = b.toInt() and 0xFF
            // 248 = 62 * 4: drop the tail so every letter is equally likely.
            if (v < 248 && out.length < 16) out.append(DELETE_KEY_ALPHABET[v % 62])
        }
    }
    return out.toString()
}
