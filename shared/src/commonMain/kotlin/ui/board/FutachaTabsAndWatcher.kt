package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.valoser.futacha.shared.util.isAndroid
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
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
    val colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = { tabsOpen = true }, colors = colors, modifier = Modifier.weight(1f)) { Text("タブ一覧") }
        TextButton(onClick = { watcherOpen = true }, colors = colors, modifier = Modifier.weight(1f)) { Text("巡回") }
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
    // The running action; "キャンセル" stops it (audit L30: the check of every tab went on after cancelling and closed
    // the tabs it found later, with the buttons off meanwhile).
    var running by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        running = scope.launch {
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
    FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, title = { Text("タブ一覧 (${tabs.size})") }, text = {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(tabs, key = { it.key }) { tab ->
                    ListItem(
                        headlineContent = { Text(tab.title) },
                        supportingContent = { Column {
                            val status = threadStatusText(tab.confirmedStatus())
                            Text("${tab.boardName}・${tab.replyCount}レス${if (status.isEmpty()) "" else "・$status"}")
                            CatalogNewRepliesBadge(tab.unreadCount)
                        } },
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
                // Also offered by the host's snackbar for a tab closed elsewhere: once restored there, not here.
                val offeredUndo = undo?.takeUnless(features.closedTabsUndo::isRestored)
                TextButton(enabled = !busy && offeredUndo != null, modifier = Modifier.testTag("futacha-tabs-undo"), onClick = { perform {
                    offeredUndo?.let { features.closedTabsUndo.restore(features.store, it) }; undo = null
                } }) { Text("元に戻す") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
    selected?.let { tab -> FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = { running?.cancel(); running = null; selected = null }, title = { Text("タブを整理") }, text = {
            Column {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                        // Undone with this list's own "元に戻す"; the host's snackbar is not asked to offer it again.
                        undo = features.store.closeTabs(keys, Clock.System.now().toEpochMilliseconds())
                        selected = null
                    } }) { Text(label) }
                    if (action == CompatDrawerTabCloseAction.DEAD && repository != null) {
                        TextButton(enabled = !busy, modifier = Modifier.testTag("futacha-tabs-close-dead-checked"),
                            onClick = { perform {
                                val keys = futachaCheckedDeadTabCloseKeys({ features.store.tabs.first() }, protect) { url ->
                                    repository.probeThreadGone(url)
                                }
                                if (keys.isEmpty()) {
                                    message = "落ちたスレは見つかりませんでした"
                                } else {
                                    undo = features.store.closeTabs(keys, Clock.System.now().toEpochMilliseconds())
                                    message = "落ちたスレを${keys.size}件閉じました"
                                }
                                selected = null
                            } }) { Text("確認して落ちたスレを閉じる") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { running?.cancel(); running = null; selected = null }) { Text("キャンセル") } })
    } }
}

/**
 * 「確認して落ちたスレを閉じる」: like 「落ちたスレを閉じる」 but asks the server about every
 * tab first (としあき(仮)'s explicit check: up to 4 at once, 10 s each; a failed check
 * counts as alive). Favorites stay when [protectFavorites].
 */
internal suspend fun futachaCheckedDeadTabCloseKeys(
    currentTabs: suspend () -> List<CompatTab>,
    protectFavorites: Boolean,
    probeGone: suspend (String) -> Boolean
): Set<String> = checkCompatDeadTabCloseKeys(
    tabs = currentTabs(),
    protectFavorites = protectFavorites,
    currentTabs = currentTabs,
    probeGone = probeGone
)

/**
 * Second line of a crawl-result row: the word it matched, the board, the reply count and whether the
 * thread has gone. Spelled like としあき(仮) and ふたばー風 (the word in 「」, "落ち" for a dropped thread).
 */
internal fun futachaWatcherSubtitle(entry: CompatExternalWatcherEntry): String = buildString {
    entry.extractedKeyword?.trim()?.takeIf { it.isNotEmpty() }?.let { append("「").append(it).append("」") }
    append(entry.boardName.orEmpty())
    append("・").append(entry.replyCount).append("レス")
    if (!entry.active) append("・落ち")
}

internal const val FUTACHA_WATCHER_DELETE_ALL_CONFIRMATION =
    "巡回結果をすべて削除しますか？\nふたちゃ・としあき(仮)・ふたばー風で共有している結果が消えます。"

@Composable
private fun FutachaWatcherDialog(features: FutachaSharedFeatures, onOpenThread: (ThreadHistoryEntry) -> Unit, onDismiss: () -> Unit) {
    val watcher = rememberCompatExternalWatcher(features.store)
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(CompatExternalWatcherSnapshot()) }
    var message by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    // Read again only when the watcher's own values change, not on every write of any setting (re-audit L30).
    val watcherPreferences = remember(features.preferences) { features.preferences.filterKeys { it.startsWith("compat.watcher.") } }
    LaunchedEffect(watcher, refresh, watcherPreferences) {
        watcher.load().onSuccess { snapshot = it }.onFailure { message = it.message }
    }
    FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, title = { Text("巡回結果") }, text = {
        Column {
            snapshot.message?.let { Text(it) }
            message?.let { Text(it) }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(snapshot.entries, key = { it.key }) { entry ->
                    ListItem(headlineContent = { Text(entry.title) },
                        supportingContent = { Text(futachaWatcherSubtitle(entry)) },
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
            TextButton(onClick = { managing = true }) { Icon(Icons.Filled.Settings, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("巡回管理") }
            TextButton(onClick = { refresh++ }) { Text("保存済み結果を再読込") }
            TextButton(enabled = snapshot.entries.isNotEmpty(), onClick = { confirmDeleteAll = true }) { Text("すべて削除") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
    if (confirmDeleteAll) FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { confirmDeleteAll = false },
        title = { Text("巡回結果の削除") }, text = { Text(FUTACHA_WATCHER_DELETE_ALL_CONFIRMATION) },
        confirmButton = { TextButton(onClick = {
            confirmDeleteAll = false
            scope.launch { watcher.deleteAll().onFailure { message = it.message }; refresh++ }
        }) { Text("すべて削除する") } },
        dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("キャンセル") } }) }
    if (managing) FutachaAppLockAwareWindow { CompatWatcherManager(features.store, features.repository,
        onDismiss = { managing = false; refresh++ }, onResultsChanged = { refresh++ },
        onOpenExternal = if (isAndroid()) watcher::openManager else null,
        onOpenHelp = { managing = false; onDismiss(); features.openSettings("help") }) }
}
