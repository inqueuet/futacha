package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** The site's own read-only search (the same mode=search endpoint as its search form), not a post. */
internal fun futabaServerSearchBase(boardUrl: String): String? = canonicalizeBoardUrl(boardUrl)?.trimEnd('/')

internal fun futabaServerSearchUrl(boardUrl: String, query: String): String {
    val base = requireNotNull(futabaServerSearchBase(boardUrl)) { "ふたばの板を選んでください" }
    val word = query.trim()
    require(word.isNotEmpty() && word.length <= 200) { "検索語は1〜200字で入力してください" }
    val encoded = TextEncoding.encodeToShiftJis(word).joinToString("") { "%" + (it.toInt() and 255).toString(16).padStart(2, '0') }
    return "$base/futaba.php?mode=search&keyword=$encoded"
}

internal data class FutabaServerSearchResult(val thread: CatalogItem, val postId: String, val text: String, val date: String)

internal suspend fun searchFutabaPosts(client: HttpClient, boardUrl: String, query: String): List<FutabaServerSearchResult> = withTimeout(20_000) {
    val url = withContext(AppDispatchers.parsing) { futabaServerSearchUrl(boardUrl, query) }
    client.prepareGet(url) {
        headers.append(HttpHeaders.Referrer, requireNotNull(futabaServerSearchBase(boardUrl)) + "/futaba.htm")
    }.execute { response ->
        check(response.status.isSuccess()) { "検索できませんでした（HTTP ${response.status.value}）" }
        val html = readBoundedHttpResponseText(response, 2 * 1024 * 1024)
        withContext(AppDispatchers.parsing) { parseFutabaServerSearch(html, boardUrl) }
    }
}

/** Extract only JSON, never execute the page's scripts or follow response-provided links. */
internal fun parseFutabaServerSearch(html: String, boardUrl: String): List<FutabaServerSearchResult> {
    val base = requireNotNull(futabaServerSearchBase(boardUrl))
    val assignment = Regex("""\bvar\s+ret\s*=\s*""").find(html) ?: error("検索結果を読み取れませんでした")
    val start = assignment.range.last + 1
    check(html.getOrNull(start) == '{') { "検索結果を読み取れませんでした" }
    var depth = 0
    var quoted = false
    var escaped = false
    var end = -1
    for (i in start until html.length) {
        val ch = html[i]
        if (quoted) {
            if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
        } else when (ch) {
            '"' -> quoted = true
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) { end = i + 1; break } }
        }
    }
    check(end > start) { "検索結果を読み取れませんでした" }
    val json = Json.parseToJsonElement(html.substring(start, end)).jsonObject
    val entries = json["res"]
    if (entries == null || entries == JsonNull || entries is JsonArray && entries.isEmpty()) {
        check(html.contains("searchResult")) { "検索結果を読み取れませんでした" }
        return emptyList()
    }
    val rows = entries as? JsonObject ?: error("検索結果を読み取れませんでした")
    return rows.entries.asSequence().filter { it.key.toLongOrNull()?.let { n -> n > 0 } == true }.take(500).mapNotNull { (no, value) ->
        val row = value as? JsonObject ?: return@mapNotNull null
        val threadId = (row["resto"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }?.toString() ?: no
        val text = messageHtmlToPlainText((row["com"] as? JsonPrimitive)?.contentOrNull.orEmpty()).take(4000)
        FutabaServerSearchResult(
            CatalogItem(id = threadId, title = text.take(100), threadUrl = "$base/res/$threadId.htm", thumbnailUrl = null,
                fullImageUrl = null, replyCount = 0),
            no, text, messageHtmlToPlainText((row["now"] as? JsonPrimitive)?.contentOrNull.orEmpty()).take(100)
        )
    }.toList()
}
