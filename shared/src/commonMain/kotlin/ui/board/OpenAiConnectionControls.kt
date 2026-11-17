package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

@Composable
internal fun OpenAiConnectionControls(ngStyleHiding: Boolean = false, connectionStore: AiConnectionStore? = null) {
    val context = LocalPlatformContext.current
    val openUrl = rememberUrlLauncher()
    val store = connectionStore ?: remember(context) { getAiConnectionStore(context) }
    val state by store.state.collectAsState()
    LaunchedEffect(store) { store.load() }
    var moderation by remember(state.moderationProvider) { mutableStateOf(state.moderationProvider) }
    var threshold by remember(state.moderationThreshold) { mutableStateOf(state.moderationThreshold) }
    var autoHide by remember(state.moderationAutoHide) { mutableStateOf(state.moderationAutoHide) }
    var categories by remember(state.moderationCategories) { mutableStateOf(state.moderationCategories) }
    var showCategories by remember { mutableStateOf(false) }
    var showApiManagement by remember { mutableStateOf(false) }
    var consent by remember(state.moderationProvider) { mutableStateOf(state.moderationProvider != AiProvider.DEVICE) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val usesCloud = moderation != AiProvider.DEVICE
    // A failed load leaves no keys in memory: saving would erase the stored keys.
    val unreadable = state.storageError != null
    fun recover(action: suspend () -> Unit, done: String) {
        scope.launch {
            busy = true
            message = null
            try { action(); message = if (store.state.value.storageError == null) done else null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "AI接続設定を復旧できませんでした。" }
            finally { busy = false }
        }
    }
    fun save() {
        scope.launch {
            busy = true
            message = null
            try {
                store.save(
                    AiProvider.DEVICE,
                    moderation,
                    state.summaryModel,
                    moderationThreshold = threshold,
                    moderationAutoHide = autoHide,
                    moderationCategories = categories
                )
                message = "AI設定を保存しました。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "AI設定を保存できませんでした。" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("機能ごとのAI接続", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("スレ要約は端末内AIのみ利用できます。", Modifier.testTag("ai-summary-device-only"),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AiProviderPicker("moderation", "荒らし候補の判定", moderation, !busy && state.loaded) { moderation = it }
        OutlinedButton(onClick = { showApiManagement = true }, enabled = state.loaded && !unreadable && !busy,
            modifier = Modifier.fillMaxWidth().testTag("openai-api-management-open")) { Text("API管理（有効${state.apiKeys.count { it.enabled }} / 登録${state.apiKeys.size}件）") }
        Text("利用制限時は、API管理で登録した順に次の利用可能なAPIへ自動で切り替えます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (moderation == AiProvider.BOTH) Text("端末内AIとOpenAIを併用します。どちらかが明確な候補と判定すると折りたたみます。判定元は理由に表示します。見逃しを減らせる一方、誤判定が増える場合があります。一方が未対応・利用制限中でも他方は継続します。引用部分は両方のAI判定から除外します。", color = MaterialTheme.colorScheme.onSurface)
        if (usesCloud) {
        Text("同文再投稿・本文内の反復は端末側でもコピペ候補として判定します。引用を含むレスや短い相づちはこの反復判定の対象外です。端末内AIとの併用時は会話の文脈も参考にします。", color = MaterialTheme.colorScheme.onSurface)
        OpenAiUsageControls(store)
        Text("OpenAI 詳細設定", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("OpenAIのAPIキー", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text("APIキーはOpenAI公式サイト（platform.openai.com）で、ご自身のアカウントから発行してください。第三者から受け取ったキーは使用しないでください。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("この荒らし判定に使うModeration APIは現在、課金・有料プランの契約・APIクレジットの購入なしで利用できます。OpenAI側で用意するのはSecret Key（APIキー）の発行だけです。発行したキーをこの画面に登録してください。OpenAIのアカウント条件と利用制限は適用されます。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { openUrl("https://platform.openai.com/api-keys") }) {
            Text("OpenAI公式サイトでAPIキーを発行・管理")
        }
        Text("APIキーの取り扱いに注意してください。漏れると第三者による不正利用や、キーの権限によっては有料APIの利用料金が発生する恐れがあります。パスワードと同様に厳重に管理し、チャット・SNS・スクリーンショットで公開したり、他人に渡したりしないでください。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("漏えいが疑われる場合は、OpenAI公式サイトでキーを失効させ、再発行してください。このアプリからキーを削除するだけでは、OpenAI側のキーは失効しません。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Moderation API / $OPENAI_MODERATION_MODEL",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showCategories = true }, enabled = !busy) { Text("判定カテゴリを選択（${categories.size} / ${OPENAI_MODERATION_CATEGORIES.size}）") }
        Text(OPENAI_MODERATION_CATEGORIES.filterKeys { it in categories }.values.joinToString("、").ifEmpty { "カテゴリを1つ以上選択してください。" },
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("OpenAIの判定閾値: ${(threshold * 100).roundToInt()} / 100", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = threshold, onValueChange = { threshold = (it * 20).roundToInt() / 20f },
            valueRange = 0.05f..1f, steps = 18, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("openai-moderation-threshold"))
        Text("選択カテゴリのスコアのどれかが閾値以上なら候補になります。低いほど候補が増えます。スコアは荒らしである確率ではありません。APIが有害と判定していても、選択カテゴリのスコアが閾値未満なら隠しません。変更は保存後に反映します。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().testTag("openai-moderation-auto-hide")
            .toggleable(autoHide, enabled = !busy, role = Role.Switch, onValueChange = { autoHide = it })) {
            Text(if (ngStyleHiding) "判定候補をNGと同様に非表示にする" else "判定候補を自動で折りたたむ", Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = autoHide, onCheckedChange = null, enabled = !busy)
        }
        Text(if (ngStyleHiding) "OFFでは本文を隠しません。ONではNGと同様に一覧から非表示にします。NG表示切替で再表示し、NG抽出で確認できます。スレ主・自分のレスは対象外です。" else "OFFでは本文を隠しません。ONでも折りたたんだ本文は展開できます。スレ主・自分のレスは対象外です。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("荒らし判定に使用するOpenAIのModeration APIは現在無料ですが、利用制限があります。今後有料になる可能性があり、有料化の有無や時期は不明です。判定のために投稿本文を送信します。画像・動画は送信しません。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.testTag("ai-cloud-consent").toggleable(consent, enabled = !busy, role = Role.Checkbox, onValueChange = { consent = it })) {
                Checkbox(checked = consent, onCheckedChange = null, enabled = !busy)
                Text("荒らし候補の判定のため、投稿本文をOpenAIへ送信することに同意します。",
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            }
        if (!state.supportsSecureStorage) Text("この環境ではAPIキーの安全な保存に対応していません。", color = MaterialTheme.colorScheme.onSurface)
        }
        state.storageError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Text("読み込めない間は、登録済みのキーを消さないようAI設定の保存とAPIの追加を停止しています。",
                color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { recover({ store.load() }, "AI接続設定を読み込みました。") }, enabled = !busy,
                    modifier = Modifier.testTag("ai-settings-reload")) { Text("再読み込み") }
                OutlinedButton(onClick = { confirmReset = true }, enabled = !busy && state.supportsSecureStorage,
                    modifier = Modifier.testTag("ai-settings-reset")) { Text("設定を初期化") }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save() }, enabled = state.loaded && !unreadable && state.supportsSecureStorage && !busy && (!usesCloud || (consent && state.hasApiKey && categories.isNotEmpty()))) { Text("AI設定を保存") }
        }
    }
    if (confirmReset) FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("AI接続設定を初期化") },
        text = { Text("読み込めないAI接続設定を初期状態に戻します。登録済みのAPIキーはこのアプリから消えるため、再登録が必要です。OpenAI側のキーは失効しません。",
            color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = { confirmReset = false; recover({ store.resetUnreadableSettings() }, "AI接続設定を初期化しました。APIを登録し直してください。") },
            modifier = Modifier.testTag("ai-settings-reset-confirm")) { Text("初期化") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("キャンセル") } }
    ) }
    if (showApiManagement) OpenAiApiManagementScreen(store) { showApiManagement = false }
    if (showCategories && usesCloud) FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = { showCategories = false },
        title = { Text("判定カテゴリ") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                OPENAI_MODERATION_CATEGORIES.forEach { (id, label) ->
                    item(key = id) {
                        Row(Modifier.fillMaxWidth().testTag("openai-category-$id")
                            .toggleable(id in categories, role = Role.Checkbox, onValueChange = {
                                categories = if (it) categories + id else categories - id
                            })) {
                            Checkbox(checked = id in categories, onCheckedChange = null)
                            Text(label, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showCategories = false }) { Text("閉じる") } }
    ) }
}

@Composable
private fun AiProviderPicker(task: String, label: String, selected: AiProvider, enabled: Boolean, onSelect: (AiProvider) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Column {
            AiProvider.entries.forEach { provider ->
                Row(Modifier.fillMaxWidth().testTag("ai-$task-${provider.name}")
                    .selectable(selected == provider, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(provider) })) {
                    RadioButton(selected = selected == provider, onClick = null, enabled = enabled)
                    Text(when (provider) { AiProvider.DEVICE -> "端末内AI"; AiProvider.OPENAI -> "OpenAI"; AiProvider.BOTH -> "端末内AI ＋ OpenAI（併用）" },
                        Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
