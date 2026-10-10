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
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.service.CatalogWatchAlertRefresher
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun WatchDiagnosticsDialog(store: CompatibilityStore, repository: BoardRepository?, modernStore: AppStateStore? = null, onDismiss: () -> Unit) {
    val preferences by store.preferences.collectAsState(emptyMap())
    val boards by store.boards.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, title = { Text("監視の確認状況") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("検出と通知は別の処理です。下の件数は検出結果です。通知が届かない場合は巡回管理の端末通知設定も確認してください。")
            if (modernStore == null) {
                Text("対象板: " + boards.filter { compatWatchWordsForBoard(preferences, it.key).isNotEmpty() }.joinToString { it.name }.ifEmpty { "なし" })
                Text("有効ワード: " + compatWatchRules(preferences).filter { it.enabled }.joinToString { it.word }.ifEmpty { "なし" })
            } else {
                val modernBoards by modernStore.boards.collectAsState(emptyList())
                val words by modernStore.watchWords.collectAsState(emptyList())
                val boardWords by modernStore.boardWatchWords.collectAsState(emptyMap())
                Text("登録板: " + modernBoards.joinToString { it.name })
                Text("共通ワード: " + words.joinToString().ifEmpty { "なし" })
                Text("板別ワード: " + boardWords.values.flatten().joinToString().ifEmpty { "共通を使用" })
            }
            listOf("監視ワード" to WATCH_RUN_CATALOG_KEY, "キーワード巡回" to WATCH_RUN_CRAWL_KEY).forEach { (label, key) ->
                HorizontalDivider()
                Text(label, style = MaterialTheme.typography.titleSmall)
                val run = decodeWatchRunDiagnostics(preferences[key])
                if (run == null) Text("実行記録なし") else {
                    Text("最終確認: " + kotlin.time.Instant.fromEpochMilliseconds(run.checkedAt).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).toString().replace('T', ' '))
                    Text("${run.outcome}・一致 ${run.matchCount}件")
                    Text("対象: " + run.targets.joinToString().ifEmpty { "なし" })
                    Text("ワード: " + run.words.joinToString().ifEmpty { "なし" })
                    run.failures.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    run.matchedTitles.forEach { Text(it) }
                }
            }
            message?.let { Text(it) }
            TextButton(enabled = !busy && repository != null, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (modernStore != null) {
                            val result = CatalogWatchAlertRefresher(modernStore, repository!!, diagnosticsStore = store).refresh()
                            message = "検出 ${result.matches.size}件／取得失敗 ${result.failures.size}件（通知は送信しません）"
                        } else {
                            val result = refreshCompatTabsInBackground(store, repository!!, checkUpdates = false, checkExistence = false, checkWatchWords = true)
                            message = "新たに保存 ${result.newWatchMatches.size}件／失敗 ${result.failures}件"
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { message = error.message ?: "確認に失敗しました" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "確認中…" else "今すぐ確認") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
}
