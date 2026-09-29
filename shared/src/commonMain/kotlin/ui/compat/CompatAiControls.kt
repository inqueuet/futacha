package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.AiAvailability
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.OpenAiConnectionControls
import com.valoser.futacha.shared.ui.board.rememberSelectedAiService
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CompatAiSettingsControls(stateStore: AppStateStore?) {
    val summary = stateStore?.isThreadSummaryModeEnabled?.collectAsState(false)?.value ?: false
    val moderation = stateStore?.isAiPostFilterEnabled?.collectAsState(false)?.value ?: false
    val service = rememberSelectedAiService(LocalPlatformContext.current)
    var availability by remember(service) { mutableStateOf(AiAvailability(false, "AIを確認中です。")) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(service) {
        availability = try { withContext(AppDispatchers.io) { service.getAvailability() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { AiAvailability(false, "AIの利用状況を確認できませんでした。") }
    }
    fun save(block: suspend (AppStateStore) -> Unit) {
        val store = stateStore ?: return
        scope.launch {
            try { block(store); error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "設定を保存できませんでした。" }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI設定はふたちゃモードと共通です。初期状態では要約・荒らし非表示ともにOFFです。",
            color = MaterialTheme.colorScheme.onSurface)
        OpenAiConnectionControls(ngStyleHiding = true)
        Row(Modifier.fillMaxWidth()) {
            Text("スレ要約（端末AIのみ）", Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            Switch(summary, { enabled -> save { it.setThreadSummaryModeEnabled(enabled) } },
                enabled = stateStore != null && (summary || availability.supportsThreadSummary),
                modifier = Modifier.testTag("compat-ai-summary-enabled"))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("荒らし非表示", Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            Switch(moderation, { enabled -> save { it.setAiPostFilterEnabled(enabled) } },
                enabled = stateStore != null && (moderation || availability.supportsPostModeration),
                modifier = Modifier.testTag("compat-ai-moderation-enabled"))
        }
        Text("判定対象のレスをNGと同じように一覧から非表示にします。NG表示切替で一時的に再表示でき、NG抽出でも確認できます。手動NGには登録しません。",
            color = MaterialTheme.colorScheme.onSurface)
        availability.unavailableReason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun CompatThreadAiPanel(state: CompatThreadAiState, onRetry: () -> Unit) {
    if (!state.summaryEnabled) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).testTag("compat-thread-ai-panel")) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(if (expanded) "スレ要約・閉じる" else "スレ要約・開く", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (expanded) {
                if (state.running && state.summary == null && state.summaryError == null) Text("スレ要約を処理中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.summary?.let { summary ->
                    Text("スレ要約 · ${summary.providerLabel}", style = MaterialTheme.typography.labelMedium)
                    Text(summary.headline)
                    summary.bullets.forEach { Text("・$it") }
                }
                state.summaryError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.summaryError != null) TextButton(onRetry, enabled = !state.running) {
                    Text("再試行", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
