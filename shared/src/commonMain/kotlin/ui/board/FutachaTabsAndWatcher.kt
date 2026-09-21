package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
internal fun FutachaDrawerTools(onOpenThread: (ThreadHistoryEntry) -> Unit) {
    val features = LocalFutachaSharedFeatures.current ?: return
    var tabsOpen by remember { mutableStateOf(false) }
    var watcherOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = { tabsOpen = true }, modifier = Modifier.weight(1f)) { Text("タブ一覧") }
        TextButton(onClick = { watcherOpen = true }, modifier = Modifier.weight(1f)) { Text("巡回") }
    }
    if (tabsOpen) FutachaTabsDialog(features, features.repository, onOpenThread, onDismiss = { tabsOpen = false })
    if (watcherOpen) FutachaWatcherDialog(features, onOpenThread, onDismiss = { watcherOpen = false })
}

@Composable
internal fun FutachaTabsDialog(
    features: FutachaSharedFeatures,
    repository: BoardRepository?,
    onOpenThread: (ThreadHistoryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val tabs by features.store.tabs.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<CompatTab?>(null) }
    var protect by remember { mutableStateOf(true) }
    var undo by remember { mutableStateOf<ClosedTabBatch?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "タブを更新できませんでした" }
            finally { busy = false }
        }
    }
    LaunchedEffect(features.store) {
        try { undo = features.store.loadPendingClosedTabs(Clock.System.now().toEpochMilliseconds()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = "閉じたタブの記録を読み込めませんでした" }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("タブ一覧 (${tabs.size})") }, text = {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(tabs, key = { it.key }) { tab ->
                    ListItem(
                        headlineContent = { Text(tab.title) },
                        supportingContent = { Text("${tab.boardName}・${tab.replyCount}レス${if (tab.unreadCount > 0) " (+${tab.unreadCount})" else ""}${if (tab.isDead) "・落ち" else ""}") },
                        leadingContent = { TextButton(enabled = !busy, onClick = { perform {
                            features.store.tabs.first().firstOrNull { it.key == tab.key }?.let {
                                features.store.updateTab(it.copy(favorite = !it.favorite))
                            }
                        } }) { Text(if (tab.favorite) "★" else "☆") } },
                        trailingContent = { TextButton(enabled = !busy, onClick = { selected = tab; protect = true }) { Text("整理") } },
                        modifier = Modifier.clickable { onDismiss(); onOpenThread(tab.toFutachaHistoryEntry()) }
                    )
                }
            }
            if (tabs.isEmpty()) Text("開いているスレッドはありません")
            Row {
                TextButton(enabled = !busy && repository != null, onClick = { perform {
                    val result = refreshCompatTabsInBackground(features.store, requireNotNull(repository), maxTabs = 100)
                    message = "更新${result.updatedTabs}件・落ち${result.deadTabs}件・失敗${result.failures}件"
                } }) { Text("全タブを確認") }
                TextButton(enabled = !busy && undo != null, onClick = { perform {
                    undo?.let { features.store.restoreClosedTabs(it) }; undo = null
                } }) { Text("元に戻す") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    selected?.let { tab ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("タブを整理") }, text = {
            Column {
                Row {
                    Checkbox(checked = protect, onCheckedChange = { protect = it })
                    Text("お気に入りを保護")
                }
                listOf(
                    "このスレを閉じる" to CompatDrawerTabCloseAction.SELECTED,
                    "これより下を閉じる" to CompatDrawerTabCloseAction.BELOW,
                    "他のスレを閉じる" to CompatDrawerTabCloseAction.OTHERS,
                    "落ちたスレを閉じる" to CompatDrawerTabCloseAction.DEAD,
                    "すべて閉じる" to CompatDrawerTabCloseAction.ALL
                ).forEach { (label, action) ->
                    TextButton(enabled = !busy, onClick = { perform {
                        val keys = compatDrawerTabCloseKeys(features.store.tabs.first(), tab.key, action, protect)
                        undo = features.store.closeTabs(keys, Clock.System.now().toEpochMilliseconds())
                        undo?.let(features.onTabsClosed)
                        selected = null
                    } }) { Text(label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("キャンセル") } })
    }
}

@Composable
private fun FutachaWatcherDialog(features: FutachaSharedFeatures, onOpenThread: (ThreadHistoryEntry) -> Unit, onDismiss: () -> Unit) {
    val watcher = rememberCompatExternalWatcher(features.store)
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(CompatExternalWatcherSnapshot()) }
    var message by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(watcher, refresh, features.preferences) {
        watcher.load().onSuccess { snapshot = it }.onFailure { message = it.message }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("巡回結果") }, text = {
        Column {
            snapshot.message?.let { Text(it) }
            message?.let { Text(it) }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(snapshot.entries, key = { it.key }) { entry ->
                    ListItem(headlineContent = { Text(entry.title) },
                        supportingContent = { Text("${entry.boardName.orEmpty()}・${entry.replyCount}レス") },
                        trailingContent = { TextButton(onClick = { scope.launch {
                            watcher.delete(entry.key).onFailure { message = it.message }; refresh++
                        } }) { Text("削除") } },
                        modifier = Modifier.clickable {
                            val parsed = canonicalizeThreadUrl(entry.threadUrl)
                            if (parsed != null) {
                                onDismiss()
                                onOpenThread(ThreadHistoryEntry(parsed.threadNo, compatBoardKey(parsed.canonicalBoardUrl),
                                    entry.title, entry.thumbnailUrl.orEmpty(), entry.boardName.orEmpty(), entry.threadUrl,
                                    entry.updatedAtEpochMillis, entry.replyCount))
                            }
                        })
                }
            }
            if (snapshot.entries.isEmpty()) Text("巡回管理からキーワードを登録できます")
            TextButton(onClick = { managing = true }) { Text("巡回管理") }
            TextButton(onClick = { refresh++ }) { Text("結果を更新") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    if (managing) CompatWatcherManager(features.store, features.repository,
        onDismiss = { managing = false; refresh++ }, onResultsChanged = { refresh++ }, onOpenExternal = watcher::openManager)
}
