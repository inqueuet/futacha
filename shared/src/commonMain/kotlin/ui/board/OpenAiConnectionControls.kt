package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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

@Composable
internal fun OpenAiConnectionControls(ngStyleHiding: Boolean = false) {
    val context = LocalPlatformContext.current
    val openUrl = rememberUrlLauncher()
    val store = remember(context) { getAiConnectionStore(context) }
    val state by store.state.collectAsState()
    LaunchedEffect(store) { store.load() }
    var moderation by remember(state.revision) { mutableStateOf(state.moderationProvider) }
    var threshold by remember(state.revision) { mutableStateOf(state.moderationThreshold) }
    var autoHide by remember(state.revision) { mutableStateOf(state.moderationAutoHide) }
    var categories by remember(state.revision) { mutableStateOf(state.moderationCategories) }
    var showCategories by remember { mutableStateOf(false) }
    // Never save the draft key in instance state, preferences, logs or analytics.
    var key by remember { mutableStateOf("") }
    var consent by remember(state.revision) { mutableStateOf(state.moderationProvider == AiProvider.OPENAI) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val usesCloud = moderation == AiProvider.OPENAI
    fun save(delete: Boolean = false) {
        scope.launch {
            busy = true
            message = null
            try {
                store.save(
                    AiProvider.DEVICE,
                    if (delete) AiProvider.DEVICE else moderation,
                    state.summaryModel,
                    if (delete) "" else key.takeIf { it.isNotBlank() },
                    moderationThreshold = threshold,
                    moderationAutoHide = autoHide,
                    moderationCategories = if (delete) state.moderationCategories else categories
                )
                key = ""
                message = if (delete) "APIキーを削除し、荒らし判定を端末内AIへ戻しました。" else "AI設定を保存しました。"
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
        if (usesCloud) {
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
        OutlinedTextField(
            value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth().testTag("openai-api-key"),
            label = { Text(if (state.hasApiKey) "登録済み（変更するときだけ入力）" else "APIキーを入力") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            enabled = state.loaded && state.supportsSecureStorage && !busy
        )
        Text("Moderation API / $OPENAI_MODERATION_MODEL",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showCategories = true }, enabled = !busy) { Text("判定カテゴリを選択（${categories.size} / ${OPENAI_MODERATION_CATEGORIES.size}）") }
        Text(OPENAI_MODERATION_CATEGORIES.filterKeys { it in categories }.values.joinToString("、").ifEmpty { "カテゴリを1つ以上選択してください。" },
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("OpenAIの判定閾値: ${(threshold * 100).roundToInt()} / 100", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = threshold, onValueChange = { threshold = (it * 20).roundToInt() / 20f },
            valueRange = 0.05f..1f, steps = 18, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("openai-moderation-threshold"))
        Text("選択カテゴリのスコアのどれかが閾値以上なら候補になります。低いほど候補が増えます。スコアは荒らしである確率ではありません。変更は保存後に反映します。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().testTag("openai-moderation-auto-hide")
            .toggleable(autoHide, enabled = !busy, role = Role.Switch, onValueChange = { autoHide = it })) {
            Text(if (ngStyleHiding) "閾値以上の候補をNGと同様に非表示にする" else "閾値以上の候補を自動で折りたたむ", Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = autoHide, onCheckedChange = null, enabled = !busy)
        }
        Text(if (ngStyleHiding) "OFFでは候補数だけを表示し、本文を隠しません。ONではNGと同様に一覧から非表示にします。NG表示切替で再表示し、NG抽出で確認できます。スレ主・自分のレスは対象外です。" else "OFFでは候補数だけを表示し、本文を隠しません。ONでも折りたたんだ本文は展開できます。スレ主・自分のレスは対象外です。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("荒らし判定に使用するOpenAIのModeration APIは現在無料ですが、利用制限があります。今後有料になる可能性があり、有料化の有無や時期は不明です。判定のために投稿本文を送信します。画像・動画は送信しません。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.testTag("ai-cloud-consent").toggleable(consent, enabled = !busy, role = Role.Checkbox, onValueChange = { consent = it })) {
                Checkbox(checked = consent, onCheckedChange = null, enabled = !busy)
                Text("荒らし候補の判定のため、投稿本文をOpenAIへ送信することに同意します。",
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            }
        if (!state.supportsSecureStorage) Text("この環境ではAPIキーの安全な保存に対応していません。", color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = { save(delete = true) }, enabled = state.hasApiKey && !busy) { Text("APIキーを削除") }
        }
        state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save() }, enabled = state.loaded && state.supportsSecureStorage && !busy && (!usesCloud || (consent && categories.isNotEmpty()))) { Text("AI設定を保存") }
        }
    }
    if (showCategories && usesCloud) AlertDialog(
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
    )
}

@Composable
private fun AiProviderPicker(task: String, label: String, selected: AiProvider, enabled: Boolean, onSelect: (AiProvider) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Row {
            AiProvider.entries.forEach { provider ->
                Row(Modifier.weight(1f).testTag("ai-$task-${provider.name}")
                    .selectable(selected == provider, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(provider) })) {
                    RadioButton(selected = selected == provider, onClick = null, enabled = enabled)
                    Text(if (provider == AiProvider.DEVICE) "端末内AI" else "OpenAI",
                        Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
