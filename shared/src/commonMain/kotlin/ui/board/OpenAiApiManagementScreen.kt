package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal fun openAiKeyStatus(info: AiApiKeyInfo, usage: OpenAiKeyUsage?, activeId: String?, now: Long): String = when {
    !info.enabled -> "無効"
    !usage?.blockedReason.isNullOrEmpty() -> usage!!.blockedReason
    (usage?.requiredWait(now, 1) ?: 0) > 0 -> "待機中：残り${openAiWaitText(now + usage!!.requiredWait(now, 1), now)}"
    activeId == info.id -> "現在の使用先"
    else -> "切り替え先として利用可能"
}

@Composable
internal fun OpenAiApiManagementScreen(store: AiConnectionStore, onDismiss: () -> Unit) {
    val state by store.state.collectAsState()
    val usage by store.usage.state.collectAsState()
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlLauncher()
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiApiKeyInfo?>(null) }
    var deleting by remember { mutableStateOf<AiApiKeyInfo?>(null) }
    LaunchedEffect(store) {
        store.load()
        while (true) { delay(1_000); now = Clock.System.now().toEpochMilliseconds() }
    }
    fun change(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "API設定を変更できませんでした。" }
            finally { busy = false }
        }
    }
    FutachaAppLockAwareWindow { Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).testTag("openai-api-management")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API管理", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("api-management-close")) { Text("閉じる") }
                }
                LazyColumn(Modifier.weight(1f).testTag("api-management-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("OpenAI APIを最大${MAX_OPENAI_KEYS}件登録できます。上から順に使い、利用制限（429）・残量不足・認証エラーが起きたら、次に利用できるAPIへ自動で切り替えます。成功している間は同じAPIを使います。",
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("同じ組織・プロジェクトのキーは利用上限を共有することがあります。キーを増やしても上限が増えるとは限りません。待機指定とこの端末全体の送信量制限は守ります。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("待機中のAPIは時間が来るまで使いません。すべて使えない場合は停止し、未判定のレスは表示したままにします。最後の有効なAPIを削除・無効化すると、判定先を端末内AIへ戻します。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.apiKeys.isEmpty()) item { Text("APIは未登録です。", color = MaterialTheme.colorScheme.onSurface) }
                    itemsIndexed(state.apiKeys, key = { _, key -> key.id }) { index, key ->
                        val keyUsage = usage.keys[key.id]
                        OutlinedCard(Modifier.fillMaxWidth().testTag("api-entry-${key.id}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${index + 1}. ${key.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text(key.maskedKey, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(key.enabled, onCheckedChange = { change { store.setApiKeyEnabled(key.id, it) } },
                                        enabled = !busy, modifier = Modifier.testTag("api-enabled-${key.id}"))
                                }
                                Text(openAiKeyStatus(key, keyUsage, usage.activeKeyId, now), color = MaterialTheme.colorScheme.onSurface)
                                Text("最後の応答：${keyUsage?.lastStatus?.let { "HTTP $it" } ?: "未取得"} / 送信試行：${keyUsage?.attempts ?: 0}回 / 成功：${keyUsage?.successes ?: 0}回 / 429：${keyUsage?.rateLimits ?: 0}回",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("保存済みの推定トークン量：${keyUsage?.estimatedTokens ?: 0}（実消費量・課金額ではありません）",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row {
                                    TextButton(onClick = { editing = key; showEditor = true }, enabled = !busy,
                                        modifier = Modifier.testTag("api-edit-${key.id}")) { Text("編集") }
                                    TextButton(onClick = { change { store.moveApiKey(key.id, -1) } }, enabled = !busy && index > 0,
                                        modifier = Modifier.testTag("api-up-${key.id}")) { Text("上へ") }
                                    TextButton(onClick = { change { store.moveApiKey(key.id, 1) } }, enabled = !busy && index < state.apiKeys.lastIndex,
                                        modifier = Modifier.testTag("api-down-${key.id}")) { Text("下へ") }
                                    TextButton(onClick = { deleting = key }, enabled = !busy,
                                        modifier = Modifier.testTag("api-delete-${key.id}")) { Text("削除") }
                                }
                                if (!keyUsage?.blockedReason.isNullOrEmpty()) TextButton(
                                    onClick = { change { store.retryApiKey(key.id) } }, enabled = !busy
                                ) { Text("キー・利用枠の確認後に再試行を許可") }
                            }
                        }
                    }
                    item {
                        Text("APIキーは端末の安全な保存領域へ保存します。登録済みのキー全文は表示せず、バックアップや投稿本文へ含めません。ここで削除してもOpenAI側のキーは失効しません。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openUrl("https://platform.openai.com/api-keys") }) { Text("OpenAI公式サイトでキーを発行・管理") }
                        TextButton(onClick = { openUrl("https://platform.openai.com/settings/organization/limits") }) { Text("OpenAIで利用上限を確認") }
                    }
                }
                (message ?: state.storageError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.storageError != null) Text("設定を読み込めない間はAPIを追加・変更できません。AI設定画面で再読み込みするか、設定を初期化してください。",
                    color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { editing = null; showEditor = true },
                    enabled = !busy && state.loaded && state.storageError == null && state.supportsSecureStorage && state.apiKeys.size < MAX_OPENAI_KEYS,
                    modifier = Modifier.fillMaxWidth().testTag("api-add")) { Text("APIを追加") }
            }
        }
    } }
    if (showEditor) ApiKeyEditor(store, editing) { showEditor = false; editing = null }
    deleting?.let { key -> FutachaAppLockAwareWindow {
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("APIを削除") },
            text = { Text("「${key.name}」をこのアプリから削除します。OpenAI側のキーは失効しません。") },
            confirmButton = { TextButton(onClick = { deleting = null; change { store.deleteApiKey(key.id) } }, modifier = Modifier.testTag("api-delete-confirm")) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } })
    } }
}

@Composable
private fun ApiKeyEditor(store: AiConnectionStore, entry: AiApiKeyInfo?, onDismiss: () -> Unit) {
    // Deliberately not rememberSaveable: never put a draft secret into saved UI state.
    var name by remember(entry?.id) { mutableStateOf(entry?.name.orEmpty()) }
    var key by remember(entry?.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (entry == null) "APIを追加" else "APIを編集") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("名前（例：個人用）") }, singleLine = true,
                enabled = !busy, modifier = Modifier.testTag("api-name"))
            OutlinedTextField(key, { key = it.take(1024) }, label = { Text(if (entry == null) "APIキー" else "変更時だけAPIキーを入力") },
                singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.testTag("openai-api-key"))
            if (entry != null) Text("キー欄を空欄にすると登録済みのキーを維持します。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && (entry != null || key.isNotBlank()),
            modifier = Modifier.testTag("api-editor-save"), onClick = {
                scope.launch {
                    busy = true
                    try { store.saveApiKey(entry?.id, name, key.takeIf { it.isNotBlank() }); key = ""; onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "APIを保存できませんでした。" }
                    finally { busy = false }
                }
            }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") } }) }
}
