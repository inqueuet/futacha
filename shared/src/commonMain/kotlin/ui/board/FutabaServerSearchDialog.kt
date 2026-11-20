package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.FutabaServerSearchResult
import com.valoser.futacha.shared.network.searchFutabaPosts
import com.valoser.futacha.shared.network.aimogePostSearchSlug
import com.valoser.futacha.shared.network.aimoge.AimogePostSearchRow
import com.valoser.futacha.shared.network.aimoge.searchAimogePostRowsWithOwnClient
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

/** Replaced in tests; the real one reads あいもげ's `/search` with its own Cookie-less client. */
internal var aimogePostSearcher: suspend (slug: String, query: String) -> List<AimogePostSearchRow> =
    { slug, query -> searchAimogePostRowsWithOwnClient(slug, query) }

@Composable
internal fun FutabaServerSearchDialog(client: HttpClient, boardUrl: String, onDismiss: () -> Unit, onOpen: (CatalogItem) -> Unit) {
    val jumpState = LocalThreadSearchJumpState.current
    val aimogeSlug = remember(boardUrl) { aimogePostSearchSlug(boardUrl) }
    val maxChars = if (aimogeSlug != null) com.valoser.futacha.shared.network.aimoge.AIMOGE_SEARCH_QUERY_MAX_CHARS else 200
    var query by remember(boardUrl) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FutabaServerSearchResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun search() {
        if (loading || query.isBlank()) return
        loading = true; error = null; results = null
        scope.launch {
            try {
                results = aimogeSlug?.let { slug ->
                    kotlinx.coroutines.withTimeout(20_000) { aimogePostSearcher(slug, query) }.map { row ->
                        FutabaServerSearchResult(
                            CatalogItem(id = row.threadId, title = row.text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty().take(100),
                                threadUrl = row.threadUrl, thumbnailUrl = row.thumbnailUrl, fullImageUrl = null, replyCount = 0),
                            row.seq?.toString().orEmpty(), row.text, ""
                        )
                    }
                } ?: searchFutabaPosts(client, boardUrl, query)
            }
            catch (_: TimeoutCancellationException) { error = "検索が時間内に完了しませんでした" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "検索できませんでした。しばらくしてからやり直してください" }
            finally { loading = false }
        }
    }
    FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(if (aimogeSlug != null) "あいもげのレス検索" else "ふたばのレス検索") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (aimogeSlug != null) "この板の今あるスレのレス本文を検索します（最大100件）" else "この板のレス本文を検索します")
                OutlinedTextField(query, { query = it.take(maxChars) }, label = { Text("検索語") }, singleLine = true,
                    enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("futaba-server-search-query"))
                TextButton(onClick = ::search, enabled = query.isNotBlank() && !loading,
                    modifier = Modifier.testTag("futaba-server-search-submit")) { Text("検索") }
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let { Text(it) }
                results?.let { rows ->
                    Text(if (rows.isEmpty()) "該当するレスはありません" else "${rows.size}件")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("futaba-server-search-results")) {
                        itemsIndexed(rows, key = { i, row -> "${row.postId}:$i" }) { _, row ->
                            val canJump = jumpState != null && row.postId.isNotBlank()
                            val action = if (canJump) "このレスへ移動" else "スレを開く"
                            Column(Modifier.fillMaxWidth().clickable(onClickLabel = action) {
                                jumpState?.select(row.thread.threadUrl, row.postId)
                                onOpen(row.thread)
                            }.padding(vertical = 10.dp)) {
                                Text(if (aimogeSlug != null) (if (row.postId.isEmpty()) row.thread.title.orEmpty() else "No.${row.postId}") else "No.${row.postId}（スレ No.${row.thread.id}）", style = MaterialTheme.typography.labelLarge)
                                if (row.date.isNotBlank()) Text(row.date, style = MaterialTheme.typography.labelSmall)
                                Text(row.text, maxLines = 6)
                                Text(action, style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    }
}
