package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
internal fun CompatWatcherManager(
    store: CompatibilityStore,
    repository: BoardRepository?,
    onDismiss: () -> Unit,
    onResultsChanged: () -> Unit,
    onOpenExternal: (() -> Result<Unit>)? = null,
    onOpenHelp: (() -> Unit)? = null
) {
    val preferences by store.preferences.collectAsState(emptyMap())
    val boards by store.boards.collectAsState(emptyList())
    val rules = remember(preferences) { compatWatchRules(preferences) }
    val scope = rememberCoroutineScope()
    val watcher = remember(store) { CompatWatcherRepository(store) }
    var word by remember { mutableStateOf("") }
    var boardKey by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var boardMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var actionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val openNotificationSettings = rememberCompatWatcherNotificationSettings()
    val requestNotificationPermission = rememberCompatWatcherNotificationPermission { granted ->
        scope.launch {
            try {
                store.savePreference(COMPAT_WATCH_NOTIFY_KEY, if (granted) "ON" else "OFF")
                message = if (granted) "通知を有効にしました" else "通知が許可されていません。端末のアプリ設定を確認してください。"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = error.message ?: "通知設定を保存できませんでした" }
        }
    }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionJob = scope.launch {
            try { action() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = error.message ?: "処理に失敗しました" }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { actionJob?.cancel(); onDismiss() },
        title = { Text("巡回管理") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("キーワードを含むカタログのタイトルを探します。結果はドロワーの「巡回」に保存され、開いたスレッドだけが閲覧履歴に入ります。")
                Text("標準はアプリ内巡回です。にじろぐのインストールや起動は不要です。自動巡回は画面を閉じてもOSの判断で実行されますが、省電力・強制停止などで遅延・停止します。")
                onOpenHelp?.let { openHelp -> TextButton(onClick = openHelp) { Text("履歴・巡回のヘルプ") } }
                Text("1. キーワードと板を選んで追加　2. 今すぐ巡回　3. ドロワーの巡回で結果を確認")
                Row {
                    Checkbox(preferences[COMPAT_WATCH_ENABLED_KEY] != "OFF", enabled = !busy,
                        onCheckedChange = { runAction { store.savePreference(COMPAT_WATCH_ENABLED_KEY, if (it) "ON" else "OFF") } })
                    Text("自動巡回")
                }
                Row {
                    Checkbox(preferences[COMPAT_WATCH_WIFI_KEY] == "ON", enabled = !busy,
                        onCheckedChange = { runAction { store.savePreference(COMPAT_WATCH_WIFI_KEY, if (it) "ON" else "OFF") } })
                    Text("自動巡回はWi-Fi接続時のみ")
                }
                TextField(word, { word = it.take(100) }, label = { Text("キーワード") }, singleLine = true)
                Row {
                    Checkbox(preferences[COMPAT_WATCH_NOTIFY_KEY] != "OFF", enabled = !busy,
                        onCheckedChange = { if (it) requestNotificationPermission() else runAction { store.savePreference(COMPAT_WATCH_NOTIFY_KEY, "OFF") } })
                    Text("バックグラウンド巡回の新着を通知")
                }
                TextButton(enabled = !busy, onClick = requestNotificationPermission) { Text("端末の通知を許可") }
                TextButton(onClick = { openNotificationSettings().onFailure { message = it.message } }) { Text("通知音・振動（端末の設定）") }
                Box {
                    TextButton(onClick = { boardMenu = true }) { Text(boards.firstOrNull { it.key == boardKey }?.name ?: "すべての板") }
                    DropdownMenu(boardMenu, { boardMenu = false }) {
                        DropdownMenuItem(text = { Text("すべての板") }, onClick = { boardKey = null; boardMenu = false })
                        boards.forEach { board ->
                            DropdownMenuItem(text = { Text(board.name) }, onClick = { boardKey = board.key; boardMenu = false })
                        }
                    }
                }
                Row {
                    TextButton(enabled = !busy && word.isNotBlank(), onClick = {
                        runAction {
                            val next = rules.toMutableList()
                            val index = editing
                            val rule = CompatWatchRule(word.trim(), boardKey, index?.let { rules.getOrNull(it)?.enabled } ?: true)
                            if (index != null && index in next.indices) next[index] = rule else next.add(rule)
                            watcher.saveRules(next)
                            word = ""; editing = null
                        }
                    }) { Text(if (editing == null) "追加" else "変更を保存") }
                    if (editing != null) TextButton(onClick = { editing = null; word = "" }) { Text("取消") }
                }
                rules.forEachIndexed { index, rule ->
                    HorizontalDivider()
                    Row {
                        Checkbox(rule.enabled, enabled = !busy, onCheckedChange = { enabled ->
                            runAction { watcher.saveRules(rules.toMutableList().also { it[index] = rule.copy(enabled = enabled) }) }
                        })
                        Column {
                            Text(rule.word)
                            Text(if (rule.boardKey == null) "すべての板" else boards.firstOrNull { it.key == rule.boardKey }?.name ?: "削除された板")
                        }
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = { editing = index; word = rule.word; boardKey = rule.boardKey }) { Text("編集") }
                        TextButton(enabled = !busy && index > 0, onClick = {
                            runAction { watcher.saveRules(rules.toMutableList().also { it.add(index - 1, it.removeAt(index)) }); editing = null }
                        }) { Text("↑") }
                        TextButton(enabled = !busy && index < rules.lastIndex, onClick = {
                            runAction { watcher.saveRules(rules.toMutableList().also { it.add(index + 1, it.removeAt(index)) }); editing = null }
                        }) { Text("↓") }
                        TextButton(enabled = !busy, onClick = { runAction { watcher.saveRules(rules.toMutableList().also { it.removeAt(index) }); editing = null } }) { Text("削除") }
                    }
                }
                TextButton(enabled = !busy && repository != null, onClick = {
                    runAction {
                        val result = refreshCompatTabsInBackground(store, repository!!,
                            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            checkUpdates = false, checkExistence = false, checkWatchWords = true)
                        message = "巡回完了：新着${result.newWatchMatches.size}件／失敗${result.failures}件"
                        onResultsChanged()
                    }
                }) { Text(if (busy) "処理中…" else "今すぐ巡回（現在の回線を使用）") }
                if (onOpenExternal != null) {
                    Row {
                        Checkbox(preferences[COMPAT_WATCH_EXTERNAL_KEY] == "ON", enabled = !busy, onCheckedChange = {
                            runAction { store.savePreference(COMPAT_WATCH_EXTERNAL_KEY, if (it) "ON" else "OFF"); onResultsChanged() }
                        })
                        Text("外部にじろぐの結果を表示（Android）")
                    }
                    Text("この画面の設定と「今すぐ巡回」はアプリ内巡回用です。外部の巡回は、にじろぐ側で設定・開始してください。")
                    TextButton(onClick = { onOpenExternal().onFailure { message = it.message } }) { Text("外部にじろぐを開く") }
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = { actionJob?.cancel(); onDismiss() }) { Text(if (busy) "中止して閉じる" else "閉じる") } }
    )
}
