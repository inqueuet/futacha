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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.searchInqueuetArchiveThreads
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private enum class CompatArchiveSearchMode { OR, AND }
private val compatArchiveWhitespaceRegex = Regex("\\s+")
private val compatArchiveServerRegex = Regex("[a-z0-9-]+")
private val compatArchiveBoardRegex = Regex("[a-z0-9_-]+")
private val compatArchiveThreadIdRegex = Regex("[0-9]+")

internal fun parseCompatArchiveSearchHistory(raw: String?): List<String> =
    raw.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).distinct().take(20).toList()

internal fun serializeCompatArchiveSearchHistory(values: List<String>): String =
    values.map(String::trim).filter(String::isNotBlank).distinct().take(20).joinToString("\n")

internal fun filterCompatArchiveSearchItems(
    items: List<ArchiveSearchItem>,
    query: String,
    mode: String
): List<ArchiveSearchItem> {
    val terms = query.trim().split(compatArchiveWhitespaceRegex).filter { it.isNotBlank() }
    if (terms.isEmpty()) return items
    fun matches(item: ArchiveSearchItem, term: String): Boolean {
        return item.title.orEmpty().contains(term, ignoreCase = true) ||
            item.threadId.contains(term, ignoreCase = true)
    }
    return items.filter { item ->
        if (mode == CompatArchiveSearchMode.AND.name) terms.all { matches(item, it) }
        else terms.any { matches(item, it) }
    }
}

internal fun mergeCompatArchiveSearchItems(
    remote: List<ArchiveSearchItem>,
    localHistory: List<CompatHistoryEntry>,
    scope: ArchiveSearchScope?
): List<ArchiveSearchItem> {
    val local = localHistory.mapNotNull { entry ->
        val entryScope = extractArchiveSearchScope(entry.originalUrl) ?: return@mapNotNull null
        if (scope != null && entryScope != scope) return@mapNotNull null
        ArchiveSearchItem(
            threadId = entry.threadNo,
            server = entryScope.server,
            board = entryScope.board,
            title = entry.title,
            htmlUrl = entry.originalUrl,
            thumbUrl = entry.thumbnailUrl,
            replyCount = entry.replyCount,
            status = "端末履歴"
        )
    }
    val merged = ArrayList<ArchiveSearchItem>(remote.size + local.size)
    val seen = mutableSetOf<String>()
    (remote + local).forEach { item ->
        val key = "${item.server.lowercase()}/${item.board.lowercase()}/${item.threadId}"
        if (seen.add(key)) merged += item
    }
    return merged
}

/** Converts a trusted archive search result back to the original Futaba thread URL. */
internal fun buildCompatArchiveSourceThreadUrl(item: ArchiveSearchItem): String? {
    val server = item.server.trim().lowercase()
    val board = item.board.trim().trim('/').lowercase()
    val threadId = item.threadId.trim()
    if (!server.matches(compatArchiveServerRegex)) return null
    if (!board.matches(compatArchiveBoardRegex)) return null
    if (!threadId.matches(compatArchiveThreadIdRegex)) return null
    return "https://$server.2chan.net/$board/res/$threadId.htm"
}

@Composable
internal fun CompatArchiveSearchDialog(
    httpClient: HttpClient?,
    archiveScope: ArchiveSearchScope?,
    archiveBaseUrl: String? = null,
    localHistory: List<CompatHistoryEntry> = emptyList(),
    initialSearchHistory: List<String> = emptyList(),
    noticeHidden: Boolean = true,
    onSearchHistoryChanged: (List<String>) -> Unit = {},
    onNoticeHidden: () -> Unit = {},
    onDismiss: () -> Unit,
    onSelected: (ArchiveSearchItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val archiveSearchJson = remember { Json { ignoreUnknownKeys = true } }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by remember { mutableStateOf(CompatArchiveSearchMode.OR) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fetchedResults by remember { mutableStateOf<List<ArchiveSearchItem>>(emptyList()) }
    var searchHistory by remember { mutableStateOf(initialSearchHistory.distinct().take(20)) }
    var queryFocused by remember { mutableStateOf(false) }
    var noticeVisible by remember { mutableStateOf(!noticeHidden) }
    var hideNoticeNextTime by rememberSaveable { mutableStateOf(false) }

    val results = filterCompatArchiveSearchItems(fetchedResults, query, mode.name)

    fun search() {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            error = "検索語を入力してください"
            fetchedResults = emptyList()
            return
        }
        val client = httpClient
        if (client == null && localHistory.isEmpty()) {
            error = "過去ログ検索を初期化できませんでした"
            return
        }
        loading = true
        error = null
        coroutineScope.launch {
            try {
                val fetched = if (client == null) emptyList() else searchInqueuetArchiveThreads(
                    httpClient = client,
                    archiveSearchJson = archiveSearchJson,
                    query = normalized,
                    scope = archiveScope,
                    archiveBaseUrl = archiveBaseUrl
                )
                val merged = mergeCompatArchiveSearchItems(fetched, localHistory, archiveScope)
                fetchedResults = merged
                searchHistory = listOf(normalized) + searchHistory.filterNot { it.equals(normalized, true) }.take(19)
                onSearchHistoryChanged(searchHistory)
                if (filterCompatArchiveSearchItems(merged, normalized, mode.name).isEmpty()) {
                    error = "一致する過去スレが見つかりません"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                error = failure.message ?: "過去ログ検索に失敗しました"
                fetchedResults = mergeCompatArchiveSearchItems(emptyList(), localHistory, archiveScope)
            } finally {
                loading = false
            }
        }
    }

    if (noticeVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("過去ログ検索の注意") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "対応板は限られています。",
                        "保存時刻や反映時間は保証されません。",
                        "見つからない場合は未保存の可能性があります。",
                        "端末に残る履歴も検索結果へ統合します。"
                    ).forEach { Text("・$it") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hideNoticeNextTime,
                            onCheckedChange = { hideNoticeNextTime = it }
                        )
                        Text("次回から表示しない")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hideNoticeNextTime) onNoticeHidden()
                    noticeVisible = false
                }) { Text("検索へ進む") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("過去スレ検索") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it.take(200); error = null },
                    singleLine = true,
                    label = { Text("スレタイまたはスレNo.") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; error = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = "入力を消去")
                            }
                        }
                    },
                    supportingText = {
                        if (queryFocused && query.isNotBlank() && searchHistory.any { it.startsWith(query.trim(), true) }) {
                            Text("候補をタップすると検索します")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { queryFocused = it.isFocused },
                    enabled = !loading
                )
                if (queryFocused && query.isNotBlank()) {
                    val suggestions = searchHistory.filter { it.startsWith(query.trim(), true) }.take(8)
                    if (suggestions.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                            items(suggestions, key = { "suggestion-$it" }) { suggestion ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { query = suggestion; search() },
                                        modifier = Modifier.weight(1f),
                                        enabled = !loading
                                    ) { Text(suggestion, modifier = Modifier.fillMaxWidth()) }
                                    IconButton(
                                        onClick = {
                                            searchHistory = searchHistory.filterNot { it == suggestion }
                                            onSearchHistoryChanged(searchHistory)
                                        },
                                        enabled = !loading
                                    ) { Icon(Icons.Filled.Clear, contentDescription = "履歴から削除") }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("検索条件")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = mode == CompatArchiveSearchMode.OR,
                            onClick = { mode = CompatArchiveSearchMode.OR },
                            enabled = !loading
                        )
                        Text("OR")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = mode == CompatArchiveSearchMode.AND,
                            onClick = { mode = CompatArchiveSearchMode.AND },
                            enabled = !loading
                        )
                        Text("AND")
                    }
                    TextButton(onClick = ::search, enabled = !loading) { Text("検索") }
                }
                when {
                    loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        Text("検索中…")
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    results.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(results, key = { index, item -> "${item.server}/${item.board}/${item.threadId}:$index" }) { _, item ->
                            TextButton(
                                onClick = { onSelected(item) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(item.title.orEmpty().ifBlank { "No.${item.threadId}" })
                                    Text(
                                        "${item.server}/${item.board} No.${item.threadId} (${item.replyCount}レス)",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
