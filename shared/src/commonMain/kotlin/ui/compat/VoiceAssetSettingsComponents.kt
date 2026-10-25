package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.valoser.futacha.shared.ui.media.mediaEditorDialogProperties
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.audio.voicevox.*

@Composable
internal fun VoiceAssetSettings(runtime: SpeechRuntime?, settings: SpeechSettings,
    onEngine: (String) -> Unit, onStop: () -> Unit,
    // Saves the engine for a deletion; it runs in the runtime, after this screen may be gone (L10).
    commitEngine: suspend (String) -> Unit = { onEngine(it) }) {
    Text("読み上げの音声", style = MaterialTheme.typography.titleMedium)
    Row {
        RadioButton(settings.engine == SpeechEngineKind.SYSTEM, onClick = { onStop(); onEngine("system") })
        TextButton(onClick = { onStop(); onEngine("system") }) { Text("端末の音声") }
    }
    if (runtime == null || !runtime.platform.available) {
        Text(runtime?.platform?.unavailableReason ?: "この環境ではずんだもんを利用できません")
        return
    }
    val state by runtime.assets.state.collectAsState()
    // The deletion and its result belong to the runtime, so they are shown again after returning (L10).
    val deletion by runtime.deletion.collectAsState()
    val removing = deletion.removing
    var confirmDelete by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    LaunchedEffect(runtime) { runtime.assets.refresh() }
    Row {
        RadioButton(settings.engine == SpeechEngineKind.VOICEVOX_ZUNDAMON,
            enabled = state.ready && !removing, onClick = { onStop(); onEngine("voicevox_zundamon") })
        TextButton(enabled = state.ready && !removing,
            onClick = { onStop(); onEngine("voicevox_zundamon") }, modifier = Modifier.testTag("speech-zundamon-select")) {
            Text("ずんだもん（ノーマル）")
        }
    }
    Text("VOICEVOX:ずんだもん")
    Text("初回に約82MBをダウンロードし、端末に約166MB保存します。準備には約256MiBの空き容量が必要です。取得後はオフラインで読み上げます。読み上げる文章は送信しません。")
    Text("取得はこの画面を離れても続きます。アプリ終了などで中断した場合は、ここから再開・削除できます。")
    if (state.busy) {
        LinearProgressIndicator(progress = { (state.bytes.toFloat() / VoiceAssetManifest.DOWNLOAD_BYTES).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(when (state.stage) {
            VoiceAssetStage.EXTRACTING -> "辞書を準備しています"
            VoiceAssetStage.VERIFYING -> "音声データを確認しています"
            else -> "ダウンロード中：${state.bytes / 1_000_000} / 82MB"
        })
        TextButton(onClick = { runtime.cancelVoiceAssetDownload() }) { Text("中断") }
    } else if (state.ready) {
        Text("音声データ：準備済み")
    } else {
        state.message?.let { Text(it) }
        TextButton(enabled = state.stage != VoiceAssetStage.CHECKING && !removing,
            modifier = Modifier.testTag("speech-zundamon-download"), onClick = {
                runtime.clearVoiceDeletionError()
                runtime.downloadVoiceAssets()
            }) { Text(if (state.stage == VoiceAssetStage.PAUSED) "ダウンロードを再開" else "音声データをダウンロード") }
    }
    if (!state.busy && state.stage != VoiceAssetStage.CHECKING && state.stage != VoiceAssetStage.MISSING) {
        TextButton(enabled = !removing, onClick = { confirmDelete = true }, modifier = Modifier.testTag("speech-zundamon-delete")) {
            Text(if (removing) "削除中…" else "音声データを削除")
        }
    }
    // The settings of the four modes place licenses differently (ふたちゃ has no 「ライセンス」 row), so the
    // notice opens the license screen itself (L9).
    TextButton(onClick = { licenses = true }, modifier = Modifier.testTag("speech-zundamon-license")) {
        Text("利用条件・著作権表示（ライセンス）を開く")
    }
    deletion.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (confirmDelete) FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("ずんだもんの音声データを削除") },
        text = { Text("読み上げを停止して、取得した音声データを削除します。再び使うにはダウンロードが必要です。") },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false; onStop()
            runtime.deleteVoiceAssets { commitEngine("system") }
        }) { Text("削除") } }) }
    if (licenses) FutachaAppLockAwareWindow { Dialog(onDismissRequest = { licenses = false }, properties = mediaEditorDialogProperties()) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            CompatLicenseScreen(onBack = { licenses = false }, initialAssetId = "voicevox-credit")
        }
    } }
}

@Composable
internal fun VoicePitchSlider(saved: Float, onSave: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    Text("ずんだもんの声の高さ：${(value * 100).toInt()}")
    Slider(value, { value = it }, valueRange = -0.15f..0.15f, steps = 29,
        onValueChangeFinished = { onSave(value) }, modifier = Modifier.testTag("speech-zundamon-pitch"))
    TextButton(onClick = { value = 0f; onSave(0f) }) { Text("声の高さを標準に戻す") }
}
