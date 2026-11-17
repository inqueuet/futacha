package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.util.readResponseBody
import com.valoser.futacha.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CancellationException

const val AIMOGE_API_BASE = "https://api.nijiurachan.net"
/** The API version the app was written against; the server answers 400 UNSUPPORTED_API_VERSION when it is dropped. */
const val AIMOGE_API_VERSION = "2026-11-11"
private const val MAX_BODY_CHARS = 8 * 1024 * 1024
/** Japanese text takes up to 3 bytes a character in UTF-8. */
private const val MAX_BODY_BYTES = 24 * 1024 * 1024

/** The read requests of the あいもげ API. Every request goes to [AIMOGE_API_BASE] only. */
class AimogeApi(private val client: HttpClient) {
    /** Paths the tests and the repository can count. */
    suspend fun get(path: String, boardKey: String? = null): String {
        require(path.startsWith("/") && !path.startsWith("//")) { "path" }
        val url = AIMOGE_API_BASE + path
        val response = try {
            readResponseBody { client.get(url) {
                header("X-Api-Version", AIMOGE_API_VERSION)
                header(HttpHeaders.Accept, "application/json")
                boardKey?.let { header("X-Board-Key", it) }
            } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("あいもげに接続できませんでした", cause = e)
        }
        val status = response.status.value
        // Read with a limit while receiving: bodyAsText() took the whole answer into memory before the size check
        // (audit 2026-11-12 L2). JSON is UTF-8; the bytes are decoded off the main thread.
        val body = com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation {
            val bytes = com.valoser.futacha.shared.network.readBoundedHttpResponseBytes(response, MAX_BODY_BYTES)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { bytes.decodeToString() }
        }.getOrNull()
        if (status !in 200..299) {
            throw aimogeNetworkException(status, body, parseRetryAfterSeconds(response.headers[HttpHeaders.RetryAfter]))
        }
        if (body == null || body.length > MAX_BODY_CHARS) throw NetworkException("あいもげの応答を読めませんでした", statusCode = status)
        if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) {
            // A Cloudflare page or another HTML answer with 200.
            throw NetworkException("あいもげの応答を読めませんでした", statusCode = status, cause = AimogeApiException(null, status, null))
        }
        return body
    }

    suspend fun catalog(slug: String, sort: String, limit: Int = 300, page: Int = 1, seed: Long? = null): String =
        get("/catalog/${slug.encodeURLPathPart()}?sort=${sort.encodeURLQueryComponent()}&limit=${limit.coerceIn(1, 300)}" +
            (if (page > 1) "&page=$page" else "") + (seed?.let { "&seed=$it" } ?: ""), boardKey = slug)

    /** The guide asks for X-Board-Key on every request; [slug] is the board the thread is on. */
    suspend fun thread(threadId: String, slug: String? = null): String = get("/thread/${threadId.encodeURLPathPart()}", boardKey = slug)

    suspend fun threadState(threadId: String, after: Long, slug: String? = null): String =
        get("/threads/${threadId.encodeURLPathPart()}/state?after=$after", boardKey = slug)

    suspend fun boards(): String = get("/boards")

    /** The event calendar of every board (no sign-in): [from] and [to] are ISO instants, [slug] narrows to one board. */
    suspend fun events(from: String, to: String, slug: String? = null): String =
        get(
            "/events?from=${from.encodeURLQueryComponent()}&to=${to.encodeURLQueryComponent()}" +
                (slug?.let { "&b=${it.encodeURLQueryComponent()}" } ?: ""),
            boardKey = slug
        )

    suspend fun boardSummary(slug: String, ids: List<String>): String =
        get("/boards/${slug.encodeURLPathPart()}/summary?ids=${ids.joinToString(",") { it.encodeURLQueryComponent() }}", boardKey = slug)

    suspend fun clientPolicy(slug: String): String = get("/client-policy", boardKey = slug)

    suspend fun capabilities(): String = get("/capabilities")

    suspend fun threadLimits(slug: String): String = get("/thread-limits/${slug.encodeURLPathPart()}", boardKey = slug)

    suspend fun archiveSnapshot(slug: String, threadId: String): String =
        get("/archive/${slug.encodeURLPathPart()}/threads/${threadId.encodeURLPathPart()}/snapshot.json", boardKey = slug)
}

/** Control/write responses are small; bound bytes while receiving, before allocating JSON strings. */
internal suspend fun readAimogeControlBody(response: io.ktor.client.statement.HttpResponse): String {
    val bytes = com.valoser.futacha.shared.network.readBoundedHttpResponseBytes(response, 1024 * 1024)
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { bytes.decodeToString() }
}
