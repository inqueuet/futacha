package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val FUTAPO_SEARCH_URL = "https://kako.futakuro.com/futapo_parse_test.php"
private const val FUTABA_FOREST_BASE_URL = "https://futabaforest.net"
private const val THIRD_PARTY_SEARCH_REQUEST_TIMEOUT_MILLIS = 12_000L
private const val THIRD_PARTY_SEARCH_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private const val THIRD_PARTY_TITLE_MAX_CHARS = 512

// Futapo's own past-log screen asks this endpoint (kako.futakuro.com is its past-log database). Only the
// boards seen to answer are listed; any other board goes on to ふたばフォレスト or ends with no result.
private val FUTAPO_SEARCH_BOARDS = setOf("may/b", "img/b")
private val FUTABA_FOREST_SEARCH_BOARDS = setOf("may/b")

private val THIRD_PARTY_THREAD_ID_REGEX = Regex("""/res/(\d{1,20})\.html?""")
private val FOREST_ITEM_REGEX = Regex(
    """<a\s+href=['"]([^'"]*?/res/\d{1,20}\.html?)['"][^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val FOREST_THUMB_REGEX = Regex("""<img\b[^>]*\bsrc=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
private val FOREST_TITLE_REGEX = Regex("""<small>(.*?)</small>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val FOREST_REPLY_REGEX = Regex("""<font\b[^>]*>\s*(\d{1,9})\s*</font>""", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^>]+>")

/**
 * Past-log search on sites other than inqueuet, for when inqueuet has no hit (it keeps only threads with
 * 100 replies or more). Tries ふたポ and then ふたばフォレスト; the first one that has results answers.
 * Never throws except for cancellation: a site that is down or changed just contributes nothing.
 *
 * The result's [ArchiveSearchItem.htmlUrl] is the thread's own board URL, so opening it shows the live
 * thread or, once it has fallen, the same external-archive fallback every fallen thread gets.
 */
internal suspend fun searchThirdPartyArchiveThreads(
    httpClient: HttpClient,
    query: String,
    scope: ArchiveSearchScope?,
    limit: Int,
    includeAllSources: Boolean = false
): List<ArchiveSearchItem> {
    val normalized = query.trim()
    if (normalized.isEmpty() || scope == null) return emptyList()
    val boardKey = "${scope.server.lowercase()}/${scope.board.lowercase()}"
    val safeLimit = limit.coerceIn(1, 100)
    val combined = mutableListOf<ArchiveSearchItem>()
    if (boardKey in FUTAPO_SEARCH_BOARDS) {
        val items = runThirdPartySearch { searchFutapo(httpClient, normalized, scope, safeLimit) }
        if (!includeAllSources && items.isNotEmpty()) return items
        combined += items
    }
    if (boardKey in FUTABA_FOREST_SEARCH_BOARDS) {
        val items = runThirdPartySearch { searchFutabaForest(httpClient, normalized, scope, safeLimit) }
        if (!includeAllSources && items.isNotEmpty()) return items
        combined += items
    }
    return combined.distinctBy { "${it.server}/${it.board}/${it.threadId}" }.take(if (includeAllSources) 100 else safeLimit)
}

private suspend fun runThirdPartySearch(block: suspend () -> List<ArchiveSearchItem>): List<ArchiveSearchItem> =
    try {
        block()
    } catch (e: CancellationException) {
        // Only the caller's own cancellation ends the search; a timeout inside is just a site that did not answer.
        currentCoroutineContext().ensureActive()
        emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

private suspend fun fetchThirdPartyText(
    httpClient: HttpClient,
    url: String,
    configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit
): String? = withTimeoutOrNull(THIRD_PARTY_SEARCH_REQUEST_TIMEOUT_MILLIS) {
    httpClient.prepareGet(url) {
        // A site that does not answer must not hold the search for the client's own retries.
        attributes.put(HigherLayerRetryManaged, true)
        configure()
    }.execute { response ->
        // Unknown board or a site that changed: no result, not an error shown to the user.
        if (!response.status.isSuccess()) return@execute null
        readBoundedHttpResponseText(response, THIRD_PARTY_SEARCH_RESPONSE_MAX_BYTES)
    }
}

private suspend fun searchFutapo(
    httpClient: HttpClient,
    query: String,
    scope: ArchiveSearchScope,
    limit: Int
): List<ArchiveSearchItem> {
    val body = fetchThirdPartyText(httpClient, FUTAPO_SEARCH_URL) {
        parameter("s", "${scope.server.lowercase()}_${scope.board.lowercase()}")
        parameter("k", query)
        parameter("li", limit)
        parameter("sm", 0)
        parameter("so", 1)
        parameter("as", 0)
        parameter("st", 0)
    } ?: return emptyList()
    return withContext(AppDispatchers.parsing) { parseFutapoSearchResponse(body, scope, limit) }
}

/**
 * One result per line, fields separated by `<>`: thread URL, thumbnail URL, reply count, then (index 7)
 * the title, whose `>` quote marks arrive HTML-escaped.
 */
internal fun parseFutapoSearchResponse(body: String, scope: ArchiveSearchScope, limit: Int): List<ArchiveSearchItem> {
    val items = ArrayList<ArchiveSearchItem>()
    for (line in body.lineSequence()) {
        if (items.size >= limit) break
        val fields = line.split("<>")
        if (fields.size < 8) continue
        val threadId = THIRD_PARTY_THREAD_ID_REGEX.find(fields[0])?.groupValues?.getOrNull(1) ?: continue
        val thumb = fields[1].trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { if (it.startsWith("http://kako.futakuro.com/")) "https://" + it.removePrefix("http://") else it }
        items += ArchiveSearchItem(
            threadId = threadId,
            server = scope.server,
            board = scope.board,
            title = HtmlEntityDecoder.decode(fields[7]).trim().take(THIRD_PARTY_TITLE_MAX_CHARS).ifBlank { null },
            htmlUrl = "https://${scope.server}.2chan.net/${scope.board}/res/$threadId.htm",
            thumbUrl = thumb,
            replyCount = fields[2].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        )
    }
    return items.distinctBy { it.threadId }
}

private suspend fun searchFutabaForest(
    httpClient: HttpClient,
    query: String,
    scope: ArchiveSearchScope,
    limit: Int
): List<ArchiveSearchItem> {
    val body = fetchThirdPartyText(httpClient, "$FUTABA_FOREST_BASE_URL/index.htm") {
        parameter("sm", 1)
        parameter("words", query)
    } ?: return emptyList()
    return withContext(AppDispatchers.parsing) { parseFutabaForestSearchResponse(body, scope, limit) }
}

/** The result page is a table of `<a href='b/res/ID.htm'>` cells holding a thumbnail, a short title and the reply count. */
internal fun parseFutabaForestSearchResponse(html: String, scope: ArchiveSearchScope, limit: Int): List<ArchiveSearchItem> {
    val items = ArrayList<ArchiveSearchItem>()
    for (match in FOREST_ITEM_REGEX.findAll(html)) {
        if (items.size >= limit) break
        val href = match.groupValues[1]
        val threadId = THIRD_PARTY_THREAD_ID_REGEX.find(href)?.groupValues?.getOrNull(1) ?: continue
        val cell = match.groupValues[2]
        val title = FOREST_TITLE_REGEX.find(cell)?.groupValues?.getOrNull(1)
            ?.let { HtmlEntityDecoder.decode(it.replace(HTML_TAG_REGEX, "")).trim() }
            ?.take(THIRD_PARTY_TITLE_MAX_CHARS)?.ifBlank { null }
        val thumb = FOREST_THUMB_REGEX.find(cell)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                when {
                    it.startsWith("http://") || it.startsWith("https://") -> it
                    it.startsWith("/") -> "$FUTABA_FOREST_BASE_URL$it"
                    else -> "$FUTABA_FOREST_BASE_URL/$it"
                }
            }
        items += ArchiveSearchItem(
            threadId = threadId,
            server = scope.server,
            board = scope.board,
            title = title,
            htmlUrl = "https://${scope.server}.2chan.net/${scope.board}/res/$threadId.htm",
            thumbUrl = thumb,
            replyCount = FOREST_REPLY_REGEX.find(cell)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        )
    }
    return items.distinctBy { it.threadId }
}
