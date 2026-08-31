package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.TextEncoding
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext

/** The reference starts empty and asks the user to enter Futaba's top URL. */
internal const val COMPAT_DEFAULT_BOARD_MENU_URL = ""
internal const val COMPAT_LEGACY_DEFAULT_BOARD_MENU_URL = "https://www.2chan.net/bbsmenu.html"
internal const val COMPAT_REFERENCE_BOARD_UPDATE_URL = "https://www.2chan.net/"
internal const val COMPAT_BOARD_MENU_URL_KEY = "compat.boardUpdateUrl"
private const val COMPAT_BOARD_MENU_MAX_HTML_BYTES = 10 * 1024 * 1024
private const val COMPAT_BOARD_MENU_MAX_DISCOVERED_BOARDS = 512
private const val COMPAT_BOARD_MENU_MAX_TITLE_CHARS = 256
private const val COMPAT_BOARD_MENU_URL_MAX_CHARS = 8_192

private val COMPAT_BOARD_ANCHOR_REGEX = Regex(
    """<a\b[^>]*\bhref\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val COMPAT_HTML_TAG_REGEX = Regex("<[^>]*>")
private val COMPAT_WHITESPACE_REGEX = Regex("\\s+")

internal data class CompatBoardMenuUpdate(
    val addedOrUpdated: Int,
    val discovered: Int
)

internal fun normalizeCompatBoardUpdateUrl(rawValue: String): String =
    if (rawValue == "debug" || rawValue == "E&E") COMPAT_REFERENCE_BOARD_UPDATE_URL else rawValue

internal fun isCompatBoardUpdateUrlAccepted(value: String): Boolean = value.contains("www.2chan.net")

/** Parses the legacy bbsmenu format used by sample/1.apk. */
internal fun parseCompatBoardMenu(
    html: String,
    existingBoards: List<CompatBoard>
): List<CompatBoard> {
    val normalizedHtml = html
        .replace("\r", "")
        .replace("guro2-enter.html", "futaba.htm")
        .replace("51enter.htm", "futaba.htm")
        .replace("oe/futaba", "oe/")
        .replace("junbi/futaba", "junbi/")
    val priorityBoards = LinkedHashMap<String, String>()
    val regularBoards = LinkedHashMap<String, String>()
    val seenUrls = hashSetOf<String>()
    for (match in COMPAT_BOARD_ANCHOR_REGEX.findAll(normalizedHtml)) {
        if (seenUrls.size >= COMPAT_BOARD_MENU_MAX_DISCOVERED_BOARDS) break
        val rawHref = decodeCompatHtmlEntities(match.groupValues[1]).trim()
        if (rawHref.contains("ipv6", ignoreCase = true)) continue
        if (!rawHref.contains("futaba.htm", ignoreCase = true) &&
            !rawHref.contains("futaba.php", ignoreCase = true)
        ) continue
        val absoluteHref = when {
            rawHref.startsWith("//") -> "https:$rawHref"
            rawHref.startsWith("/") -> "https://www.2chan.net$rawHref"
            rawHref.startsWith("http://", ignoreCase = true) ||
                rawHref.startsWith("https://", ignoreCase = true) -> rawHref
            else -> continue
        }
        val canonical = canonicalizeBoardUrl(absoluteHref) ?: continue
        val title = decodeCompatHtmlEntities(match.groupValues[2])
            .replace(COMPAT_HTML_TAG_REGEX, " ")
            .replace(COMPAT_WHITESPACE_REGEX, " ")
            .trim()
            .ifBlank { canonical.substringAfter("//").trimEnd('/').substringAfterLast('/') }
            .take(COMPAT_BOARD_MENU_MAX_TITLE_CHARS)
        if (seenUrls.add(canonical)) {
            // BoardUpdateAsyncTask inserts every generic "二次元裏" row
            // immediately, queues the other rows, then inserts img/dat before
            // flushing that queue. Preserve that user-visible new-board order.
            if (title == "二次元裏") {
                priorityBoards[canonical] = title
            } else {
                regularBoards[canonical] = title
            }
        }
    }

    if (normalizedHtml.contains("www.2chan.net/hinan/futaba.htm") &&
        seenUrls.size < COMPAT_BOARD_MENU_MAX_DISCOVERED_BOARDS
    ) {
        if (seenUrls.add("https://www.2chan.net/hinan/")) {
            regularBoards["https://www.2chan.net/hinan/"] = "避難所"
        }
    }

    val discovered = LinkedHashMap<String, String>()
    fun appendBoard(canonical: String, title: String) {
        if (discovered.size < COMPAT_BOARD_MENU_MAX_DISCOVERED_BOARDS &&
            !discovered.containsKey(canonical)
        ) {
            discovered[canonical] = title
        }
    }
    priorityBoards.forEach { (canonical, title) -> appendBoard(canonical, title) }
    // These two boards are explicitly inserted before the queued ordinary
    // rows even when the public menu omits them.
    appendBoard("https://img.2chan.net/b/", "二次元裏img")
    appendBoard("https://dat.2chan.net/b/", "二次元裏dat")
    regularBoards.forEach { (canonical, title) -> appendBoard(canonical, title) }

    val existingByUrl = existingBoards.associateBy { it.canonicalUrl }
    var nextSortOrder = (existingBoards.maxOfOrNull(CompatBoard::sortOrder) ?: -1) + 1
    return discovered.entries.map { (canonical, title) ->
        val existing = existingByUrl[canonical]
        existing ?: CompatBoard(
            key = compatBoardKey(canonical),
            name = if (title == "二次元裏") {
                when (canonical) {
                    "https://dec.2chan.net/b/" -> "二次元裏dec"
                    "https://jun.2chan.net/jun/" -> "二次元裏jun"
                    "https://may.2chan.net/b/" -> "二次元裏may"
                    else -> title
                }
            } else {
                title
            },
            canonicalUrl = canonical,
            originalUrl = canonical,
            sortOrder = nextSortOrder++
        )
    }
}

private fun decodeCompatHtmlEntities(value: String): String = value
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&nbsp;", " ", ignoreCase = true)

internal suspend fun updateCompatBoardsFromMenu(
    httpClient: HttpClient,
    store: CompatibilityStore,
    existingBoards: List<CompatBoard>
): Result<CompatBoardMenuUpdate> = runSuspendCatchingPreservingCancellation {
    val boards = fetchDefaultCompatBoardsFromMenu(httpClient, existingBoards).getOrThrow()
    store.upsertBoards(boards)
    val existingUrls = existingBoards.mapTo(hashSetOf(), CompatBoard::canonicalUrl)
    CompatBoardMenuUpdate(
        addedOrUpdated = boards.count { board -> board.canonicalUrl !in existingUrls },
        discovered = boards.size
    )
}

internal suspend fun fetchDefaultCompatBoardsFromMenu(
    httpClient: HttpClient,
    existingBoards: List<CompatBoard>
): Result<List<CompatBoard>> = fetchCompatBoardsFromMenu(
    httpClient = httpClient,
    menuUrl = COMPAT_LEGACY_DEFAULT_BOARD_MENU_URL,
    existingBoards = existingBoards
)

internal suspend fun fetchCompatBoardsFromMenu(
    httpClient: HttpClient,
    menuUrl: String,
    existingBoards: List<CompatBoard>
): Result<List<CompatBoard>> = runSuspendCatchingPreservingCancellation {
    val boardMenu = withContext(AppDispatchers.io) {
        val response = httpClient.get(menuUrl) {
            headers {
                append(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                append(HttpHeaders.AcceptLanguage, "ja-JP,ja;q=0.9")
            }
        }
        check(response.status.isSuccess()) { "Http ${response.status.value} Error" }
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(contentLength == null || contentLength <= COMPAT_BOARD_MENU_MAX_HTML_BYTES) {
            "板一覧本文が大きすぎます"
        }
        val bytes = readBoundedHttpResponseBytes(response, COMPAT_BOARD_MENU_MAX_HTML_BYTES)
        val contentType = response.headers[HttpHeaders.ContentType]
        withContext(AppDispatchers.parsing) {
            TextEncoding.decodeToString(bytes, contentType)
        }
    }
    val boards = withContext(AppDispatchers.parsing) {
        parseCompatBoardMenu(boardMenu, existingBoards)
    }
    check(boards.isNotEmpty()) { "板一覧を解析できませんでした" }
    boards
}

@Composable
internal fun CompatBoardUpdateDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onExecute: (String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("板一覧の取得") },
        text = {
            Column {
                Text("ふたばちゃんねるアドレス")
                TextField(
                    value = url,
                    onValueChange = { url = it.take(COMPAT_BOARD_MENU_URL_MAX_CHARS) },
                    modifier = Modifier.fillMaxWidth().testTag("compat-board-update-url"),
                    singleLine = true,
                    placeholder = { Text("https://") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExecute(normalizeCompatBoardUpdateUrl(url))
                }
            ) { Text("更新する") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
