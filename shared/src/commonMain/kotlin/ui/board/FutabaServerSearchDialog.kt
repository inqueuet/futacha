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
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FutabaServerSearchDialog(client: HttpClient, boardUrl: String, onDismiss: () -> Unit, onOpen: (CatalogItem) -> Unit) {
    var query by remember(boardUrl) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FutabaServerSearchResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun search() {
        if (loading || query.isBlank()) return
        loading = true; error = null; results = null
        scope.launch {
            try { results = searchFutabaPosts(client, boardUrl, query) }
            catch (_: TimeoutCancellationException) { error = "検索が時間内に完了しませんでした" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "検索できませんでした。しばらくしてからやり直してください" }
            finally { loading = false }
        }
    }
    FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("ふたばのレス検索") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("この板のレス本文を検索します")
                OutlinedTextField(query, { query = it.take(200) }, label = { Text("検索語") }, singleLine = true,
                    enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("futaba-server-search-query"))
                TextButton(onClick = ::search, enabled = query.isNotBlank() && !loading,
                    modifier = Modifier.testTag("futaba-server-search-submit")) { Text("検索") }
                if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let { Text(it) }
                results?.let { rows ->
                    Text(if (rows.isEmpty()) "該当するレスはありません" else "${rows.size}件")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("futaba-server-search-results")) {
                        itemsIndexed(rows, key = { i, row -> "${row.postId}:$i" }) { _, row ->
                            Column(Modifier.fillMaxWidth().clickable { onOpen(row.thread) }.padding(vertical = 10.dp)) {
                                Text("No.${row.postId}（スレ No.${row.thread.id}）", style = MaterialTheme.typography.labelLarge)
                                if (row.date.isNotBlank()) Text(row.date, style = MaterialTheme.typography.labelSmall)
                                Text(row.text, maxLines = 6)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    }
}
