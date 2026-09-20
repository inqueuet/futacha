package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.ui.image.LocalMediaFeatureGate
import com.valoser.futacha.shared.util.rememberUrlLauncher
import kotlinx.coroutines.*
import kotlin.math.roundToInt

internal val LocalAnalysisModelStore = staticCompositionLocalOf<ModelStore?> { null }

@Composable
internal fun AnalysisModelDialog(feature: MediaFeature, onDismiss: () -> Unit) {
    val store = LocalAnalysisModelStore.current ?: return
    val gate = LocalMediaFeatureGate.current ?: return
    val permit = remember(store, gate, feature) { gate.permit(feature) } ?: return
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<Set<AnalysisModel>>(emptySet()) }
    var checking by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf<AnalysisModel?>(null) }
    var importing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var cancelling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val openUrl = rememberUrlLauncher()
    val picker = rememberModelImportPicker(onBusy = { value ->
        importing = value
        if (!value) { running = null; cancelling = false }
    }, onImported = { model -> installed = installed + model }, onError = { error = it })
    val busy = running != null || importing || checking
    LaunchedEffect(store, permit) {
        try {
            for (spec in AnalysisModels.all) if (store.verified(spec.id, permit) != null) installed = installed + spec.id
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "導入済みモデルを確認できませんでした" }
        finally { checking = false }
    }
    fun cancel() { cancelling = true; if (importing) picker.cancel() else job?.cancel() }
    AlertDialog(onDismissRequest = { if (checking) onDismiss() else if (busy) cancel() else onDismiss() }, title = { Text("自動編集用モデル") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("analysis-model-dialog"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("導入したモデルで端末内の画像・動画を解析します。画像や動画は送信しません。モデルなしでも手動編集は使えます。")
                if (checking) Text("導入済みモデルを確認しています…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("analysis-model-error")) }
                for (spec in AnalysisModels.all) {
                    val progress by store.progress(spec.id).collectAsState()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(spec.title, style = MaterialTheme.typography.titleSmall)
                        Text("${spec.license}・約${(spec.distribution.bytes / 1_000_000f).roundToInt()}MB", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openUrl(spec.page) }, enabled = !busy) { Text("配布元・ライセンスを確認") }
                        Text(if (spec.id in installed) "導入済み（検証済み）" else "未導入", modifier = Modifier.testTag("analysis-model-state-${spec.id.name}"))
                        if (running == spec.id) {
                            Text(if (cancelling) "取り消しています…" else when (progress?.stage) {
                                ModelInstallStage.VERIFYING -> "内容を検証しています…"
                                ModelInstallStage.IMPORTING -> "モデルを取り込んでいます…"
                                ModelInstallStage.DOWNLOADING -> "ダウンロードしています…"
                                null -> if (importing) "モデルを選択してください" else "準備しています…"
                            })
                            val state = progress
                            if (state != null && state.totalBytes > 0) LinearProgressIndicator(
                                progress = { (state.bytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy && spec.id !in installed, modifier = Modifier.testTag("analysis-model-download-${spec.id.name}"), onClick = {
                                running = spec.id; error = null; cancelling = false
                                job = scope.launch {
                                    try {
                                        store.download(spec.id, permit)
                                        ensureActive()
                                        if (gate.isCurrent(permit)) installed = installed + spec.id
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = failure.message ?: "モデルを導入できませんでした" }
                                    finally { running = null; cancelling = false }
                                }
                            }) { Text("ダウンロードして導入") }
                            TextButton(enabled = !busy, modifier = Modifier.testTag("analysis-model-import-${spec.id.name}"), onClick = {
                                running = spec.id; error = null; cancelling = false
                                picker.launch(ModelImportRequest(spec.id, store, gate, permit))
                            }) { Text("端末から取込") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }, confirmButton = { TextButton(onClick = { if (busy) cancel() else onDismiss() }, enabled = !checking && !cancelling) {
            Text(if (busy) "取消" else "閉じる")
        } })
}

@Composable
internal fun DetectionOptionsDialog(settings: DetectionSettings, onSettingsChanged: (DetectionSettings) -> Unit, onModels: () -> Unit, onDismiss: () -> Unit, onDetect: (DetectionSettings) -> Unit, video: Boolean = false) {
    val prefix = if (video) "video" else "image"
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (video) "動画の自動検出" else "画像の自動検出") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("$prefix-detection-options")) {
            Text(if (video) "性器の候補を各コマで検出し、追尾します。見逃しや誤検出があるため、解析後に区間全体を確認し、必要な枠を修正してください。"
                else "性器の候補を検出します。見逃しや誤検出があるため、解析後に画像全体を確認し、必要な枠を修正してください。")
            for (model in DetectorModel.entries) {
                AnalysisCheckbox(AnalysisModels.all.first { it.id == model.artifact }.title, model in settings.models) { selected ->
                    onSettingsChanged(settings.copy(models = if (selected) settings.models + model else settings.models - model))
                }
            }
            TextButton(onClick = onModels, modifier = Modifier.testTag("$prefix-detection-models")) { Text("モデルの導入・確認") }
            Text("検出しきい値：${(settings.threshold * 100).roundToInt()}％（低いほど候補が増えます）")
            Slider(settings.threshold, { onSettingsChanged(settings.copy(threshold = it)) }, valueRange = .05f.. .5f)
            Text("枠の余白：${(settings.margin * 100).roundToInt()}％")
            Slider(settings.margin, { onSettingsChanged(settings.copy(margin = it)) }, valueRange = 0f.. .75f)
            AnalysisCheckbox("顔も検出する（実写モデル）", settings.faces) { onSettingsChanged(settings.copy(faces = it)) }
            AnalysisCheckbox("画像を分割して小さな候補も探す", settings.tiles) { onSettingsChanged(settings.copy(tiles = it)) }
            AnalysisCheckbox("輪郭に沿って配置する", settings.contours) { onSettingsChanged(settings.copy(contours = it)) }
            if (settings.contours) Text("輪郭用の2モデルが必要です。抽出できない候補は枠全体を残します。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = settings.models.isNotEmpty(), modifier = Modifier.testTag("$prefix-detection-start"), onClick = { onDetect(settings.validated()) }) { Text("解析を開始") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("戻る") } })
}

@Composable
private fun AnalysisCheckbox(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = selected, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
    }
}
