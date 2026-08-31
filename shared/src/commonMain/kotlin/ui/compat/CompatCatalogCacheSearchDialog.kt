package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.normalizeCompatSearchText
import com.valoser.futacha.shared.network.readBoundedHttpResponseText
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

private const val LEGACY_COMPAT_CATALOG_CACHE_SEARCH_URL =
    "https://futabaapp.xsrv.jp/api/search/"
private const val LEGACY_COMPAT_CACHE_USER_AGENT =
    "Apache-HttpClient/4.4.1 (java 1.4)"
internal const val COMPAT_CACHE_SEARCH_HISTORY_KEY =
    "compat.catalog.cacheSearchKeywordHistory"
private const val COMPAT_CACHE_SEARCH_HISTORY_MAX_STORED = 50
private const val COMPAT_CACHE_SEARCH_HISTORY_MAX_SUGGESTIONS = 10
private const val COMPAT_CACHE_SEARCH_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private val compatCatalogCacheThreadIdRegex = Regex("/res/(\\d+)\\.html?")
private val compatCatalogCacheWhitespaceRegex = Regex("\\s+")

internal enum class CompatCatalogCacheSearchMode { OR, AND }

internal fun compatCatalogCacheSearchModeLabel(mode: CompatCatalogCacheSearchMode): String = when (mode) {
    CompatCatalogCacheSearchMode.OR -> "OR検索"
    CompatCatalogCacheSearchMode.AND -> "AND検索"
}

/** The original APK's server-side catalog-cache search, kept separate from archive search. */
@Composable
internal fun CompatCatalogCacheSearchDialog(
    httpClient: HttpClient?,
    store: CompatibilityStore,
    boardKey: String,
    boardUrl: String,
    localHistory: List<CompatHistoryEntry> = emptyList(),
    initialSearchHistory: List<String> = emptyList(),
    onSearchHistoryChanged: (List<String>) -> Unit = {},
    onDismiss: () -> Unit,
    onOpenThread: (CatalogItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(CompatCatalogCacheSearchMode.OR) }
    var baseResults by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var cachedBodyTextByThreadId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var searchedQuery by remember { mutableStateOf("") }
    var searchHistory by remember(initialSearchHistory) {
        mutableStateOf(normalizeCompatCacheSearchHistory(initialSearchHistory.joinToString("\n")))
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch(keyword: String = query) {
        val normalized = cleanCompatCacheSearchKeyword(keyword)
        if (normalized.isBlank()) {
            error = "検索語を入力してください"
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val localBodies = loadCompatCacheSearchBodyText(store, localHistory, boardKey)
                val remoteResult = httpClient?.let {
                    searchLegacyCompatCatalogCache(it, boardUrl, normalized)
                }
                val fetched = remoteResult?.getOrDefault(emptyList()).orEmpty()
                cachedBodyTextByThreadId = localBodies
                baseResults = mergeCompatCacheSearchResults(
                    remoteResults = fetched,
                    localHistory = localHistory,
                    boardKey = boardKey,
                    query = normalized,
                    bodyTextByThreadId = localBodies
                )
                results = filterLegacyCompatCatalogCache(
                    baseResults,
                    normalized,
                    mode,
                    supplementalTextById = localBodies
                )
                searchedQuery = normalized
                searchHistory = rememberCompatCacheSearchKeyword(searchHistory, normalized)
                onSearchHistoryChanged(searchHistory)
                if (results.isEmpty()) {
                    error = remoteResult?.exceptionOrNull()?.message
                        ?: if (httpClient == null) "通信機能を初期化できませんでした" else null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                results = emptyList()
                error = failure.message ?: "キャッシュ検索に失敗しました"
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("キャッシュ検索") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query,
                        onValueChange = { query = it.take(200) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("スレッド名・キーワード") },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "検索語を消去")
                                }
                            }
                        }
                    )
                    TextButton(enabled = !loading, onClick = { runSearch() }) { Text("検索") }
                }
                val suggestions = compatCacheSearchSuggestions(searchHistory, query)
                if (suggestions.isNotEmpty() && !loading) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    ) {
                        itemsIndexed(suggestions, key = { index, suggestion -> "suggestion:$suggestion:$index" }) { _, suggestion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        query = suggestion
                                        runSearch(suggestion)
                                    },
                                    modifier = Modifier.weight(1f).padding(vertical = 0.dp)
                                ) {
                                    Text(suggestion, modifier = Modifier.fillMaxWidth(), maxLines = 1)
                                }
                                IconButton(
                                    onClick = {
                                        searchHistory = searchHistory.filterNot { it == suggestion }
                                        onSearchHistoryChanged(searchHistory)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "$suggestion を検索履歴から削除")
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == CompatCatalogCacheSearchMode.OR,
                        onClick = {
                            mode = CompatCatalogCacheSearchMode.OR
                            results = filterLegacyCompatCatalogCache(
                                baseResults, searchedQuery, mode, cachedBodyTextByThreadId
                            )
                        }
                    )
                    Text(compatCatalogCacheSearchModeLabel(CompatCatalogCacheSearchMode.OR))
                    RadioButton(
                        selected = mode == CompatCatalogCacheSearchMode.AND,
                        onClick = {
                            mode = CompatCatalogCacheSearchMode.AND
                            results = filterLegacyCompatCatalogCache(
                                baseResults, searchedQuery, mode, cachedBodyTextByThreadId
                            )
                        }
                    )
                    Text(compatCatalogCacheSearchModeLabel(CompatCatalogCacheSearchMode.AND))
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                error?.let { Text(it) }
                if (!loading && error == null && searchedQuery.isNotBlank() && results.isEmpty()) {
                    Text("該当するキャッシュはありません")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    itemsIndexed(results, key = { index, item -> "${item.threadUrl}:$index" }) { _, item ->
                        TextButton(
                            onClick = { onDismiss(); onOpenThread(item) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.title.orEmpty().ifBlank { "No.${item.id}" }, maxLines = 2)
                                Text("No.${item.id} / ${item.replyCount}レス", maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

/**
 * The reference APK searches its local thread database as well as the remote
 * cache service, then applies AND/OR to that stable combined base list.
 */
internal fun mergeCompatCacheSearchResults(
    remoteResults: List<CatalogItem>,
    localHistory: List<CompatHistoryEntry>,
    boardKey: String,
    query: String,
    bodyTextByThreadId: Map<String, String> = emptyMap()
): List<CatalogItem> {
    val terms = cleanCompatCacheSearchKeyword(query)
        .split(compatCatalogCacheWhitespaceRegex)
        .filter(String::isNotBlank)
    val localResults = localHistory.asSequence()
        .filter { it.boardKey == boardKey }
        .filter { entry ->
            terms.isEmpty() || terms.any { term ->
                normalizeCompatSearchText(entry.title).contains(normalizeCompatSearchText(term)) ||
                    entry.threadNo.contains(term) ||
                    normalizeCompatSearchText(bodyTextByThreadId[entry.threadNo].orEmpty())
                        .contains(normalizeCompatSearchText(term))
            }
        }
        .map { entry ->
            CatalogItem(
                id = entry.threadNo,
                threadUrl = entry.originalUrl,
                title = entry.title,
                thumbnailUrl = entry.thumbnailUrl,
                fullImageUrl = entry.thumbnailUrl,
                replyCount = entry.replyCount
            )
        }
    return (remoteResults.asSequence() + localResults)
        .distinctBy(CatalogItem::id)
        .toList()
}

internal suspend fun searchLegacyCompatCatalogCache(
    httpClient: HttpClient,
    boardUrl: String,
    query: String
): Result<List<CatalogItem>> = runSuspendCatchingPreservingCancellation {
    val board = Url(boardUrl.trim())
    val boardParameter = buildString {
        // The reference APK deliberately sends the board parameter as HTTP,
        // even when the board was opened through HTTPS.
        append("http://").append(board.host)
        if (board.port != board.protocol.defaultPort) append(":").append(board.port)
        append(board.encodedPath.trimEnd('/')).append('/')
    }
    val response = httpClient.get(LEGACY_COMPAT_CATALOG_CACHE_SEARCH_URL) {
        parameter("server", boardParameter)
        parameter("keyword", query)
        parameter("device", LEGACY_COMPAT_CACHE_USER_AGENT)
        headers { append(HttpHeaders.UserAgent, LEGACY_COMPAT_CACHE_USER_AGENT) }
    }
    val body = readBoundedHttpResponseText(response, COMPAT_CACHE_SEARCH_RESPONSE_MAX_BYTES)
    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
    val root = Json.parseToJsonElement(body).jsonObject
    root.stringValue("E")?.takeIf { it.isNotBlank() }?.let { error(it) }
    root["l"]?.jsonArray.orEmpty().mapNotNull { element ->
        val item = element.jsonObject
        val url = item.stringValue("u")?.replace("http://", "https://") ?: return@mapNotNull null
        val id = compatCatalogCacheThreadIdRegex.find(url)?.groupValues?.getOrNull(1)
            ?: item.stringValue("i")?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        // The legacy API calls this field `c` (catalog thumbnail).  `b` is
        // not present in the sample APK's response schema and would make a
        // perfectly valid cache result look image-less.
        val thumb = item.stringValue("c")?.replace("http://", "https://")
        CatalogItem(
            id = id,
            threadUrl = url,
            title = item.stringValue("t"),
            thumbnailUrl = thumb,
            fullImageUrl = thumb,
            thumbnailWidth = item.stringValue("e")?.toIntOrNull(),
            thumbnailHeight = item.stringValue("f")?.toIntOrNull(),
            replyCount = item.stringValue("C")?.toIntOrNull() ?: 0
        )
    }.distinctBy(CatalogItem::threadUrl)
}

internal fun filterLegacyCompatCatalogCache(
    items: List<CatalogItem>,
    query: String,
    mode: CompatCatalogCacheSearchMode,
    supplementalTextById: Map<String, String> = emptyMap()
): List<CatalogItem> {
    val terms = query.trim().split(compatCatalogCacheWhitespaceRegex).filter(String::isNotBlank)
    if (terms.isEmpty()) return items
    fun matches(item: CatalogItem, term: String): Boolean =
        normalizeCompatSearchText(item.title.orEmpty()).contains(normalizeCompatSearchText(term)) ||
            item.id.contains(term) ||
            normalizeCompatSearchText(supplementalTextById[item.id].orEmpty())
                .contains(normalizeCompatSearchText(term))
    return items.filter { item ->
        if (mode == CompatCatalogCacheSearchMode.AND) terms.all { matches(item, it) }
        else terms.any { matches(item, it) }
    }
}

internal fun compatCacheSearchBodyText(snapshot: CompatThreadSnapshot): String =
    snapshot.posts.joinToString("\n") { post ->
        listOfNotNull(post.subject, post.author, post.messageHtml).joinToString(" ")
    }

private suspend fun loadCompatCacheSearchBodyText(
    store: CompatibilityStore,
    localHistory: List<CompatHistoryEntry>,
    boardKey: String
): Map<String, String> = coroutineScope {
    val semaphore = Semaphore(4)
    localHistory.asSequence()
        .filter { it.boardKey == boardKey }
        .distinctBy(CompatHistoryEntry::canonicalUrl)
        .map { entry ->
            async {
                val snapshot = semaphore.withPermit {
                    runSuspendCatchingPreservingCancellation {
                        store.loadThreadSnapshotByCanonicalUrl(entry.canonicalUrl)
                    }.getOrNull()
                }
                entry.threadNo to snapshot?.let(::compatCacheSearchBodyText)
            }
        }
        .toList()
        .awaitAll()
        .mapNotNull { (threadNo, body) -> body?.let { threadNo to it } }
        .toMap()
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

/** Exact normalization used by CatalogCacheSearchKeywordHistory in the reference APK. */
internal fun cleanCompatCacheSearchKeyword(keyword: String?): String =
    keyword.orEmpty()
        .replace('\u3000', ' ')
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()

internal fun normalizeCompatCacheSearchHistory(stored: String): List<String> = buildList {
    stored.split('\n').forEach { raw ->
        val value = cleanCompatCacheSearchKeyword(raw)
        if (value.isNotBlank() && value !in this && size < COMPAT_CACHE_SEARCH_HISTORY_MAX_STORED) {
            add(value)
        }
    }
}

/** Newest first, de-duplicated, and capped at the same 50 entries as the APK. */
internal fun rememberCompatCacheSearchKeyword(stored: List<String>, keyword: String?): List<String> {
    val value = cleanCompatCacheSearchKeyword(keyword)
    if (value.isBlank()) return stored.distinct().take(COMPAT_CACHE_SEARCH_HISTORY_MAX_STORED)
    return buildList {
        add(value)
        stored.forEach { old ->
            val normalized = cleanCompatCacheSearchKeyword(old)
            if (normalized.isNotBlank() && normalized != value && normalized !in this && size < COMPAT_CACHE_SEARCH_HISTORY_MAX_STORED) {
                add(normalized)
            }
        }
    }
}

internal fun compatCacheSearchSuggestions(stored: List<String>, query: String?): List<String> {
    val normalizedQuery = cleanCompatCacheSearchKeyword(query).lowercase()
    return stored.asSequence()
        .map(::cleanCompatCacheSearchKeyword)
        .filter(String::isNotBlank)
        .distinct()
        .filter { normalizedQuery.isBlank() || it.lowercase().contains(normalizedQuery) }
        .take(COMPAT_CACHE_SEARCH_HISTORY_MAX_SUGGESTIONS)
        .toList()
}
