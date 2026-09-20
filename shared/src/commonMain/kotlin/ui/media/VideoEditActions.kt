package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.*

internal val LocalLaunchDeviceVideoEditor = staticCompositionLocalOf<(() -> Unit)?> { null }
private class ActiveVideoEditor(val source: VideoEditSource, val finished: CompletableDeferred<Unit>)

@Composable
internal fun DeviceVideoEditingHost(fileSystem: FileSystem?, stateStore: AppStateStore, content: @Composable () -> Unit) {
    val gate = LocalMediaFeatureGate.current
    val enabled = LocalMediaFeatureSettings.current.videoEditorEnabled
    var active by remember { mutableStateOf<ActiveVideoEditor?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun close() { active?.finished?.complete(Unit); active = null }
    val picker = rememberDeviceVideoPicker(onSelected = { source ->
        val editor = ActiveVideoEditor(source, CompletableDeferred())
        active = editor
        try { editor.finished.await() } finally { if (active === editor) active = null }
    }, onBusy = { busy = it }, onError = { error = it })
    LaunchedEffect(enabled) { if (!enabled) { close(); error = null } }
    DisposableEffect(Unit) { onDispose { active?.finished?.complete(Unit) } }
    val launch: (() -> Unit)? = if (enabled && fileSystem != null && gate != null) {
        { if (!busy && active == null) gate.permit(MediaFeature.VIDEO_EDITOR)?.let { picker.launch(VideoPickRequest(fileSystem, gate, it)) } }
    } else null
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLaunchDeviceVideoEditor provides launch, content = content)
        if (busy && enabled) Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding(), tonalElevation = 6.dp) {
            Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text("動画を読み込み中…", Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = picker.cancel) { Text("キャンセル") }
            }
        }
    }
    if (enabled && fileSystem != null) active?.let { editor ->
        key(editor) { VideoEditorDialog(editor.source, fileSystem, stateStore, ::close) }
    }
    if (enabled) error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("動画を開けませんでした") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("閉じる") } }) }
}

@Composable
internal fun DeviceVideoEditorMenuItem(onDismissMenu: () -> Unit) {
    val launch = LocalLaunchDeviceVideoEditor.current ?: return
    DropdownMenuItem(text = { Text("動画編集") }, onClick = { onDismissMenu(); launch() }, modifier = Modifier.testTag("device-video-editor-menu"))
}

@Composable
internal fun DeviceVideoEditorSettings() {
    val settings = LocalMediaFeatureSettings.current
    val update = LocalMediaFeatureUpdater.current ?: return
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().testTag("video-editor-settings-toggle").toggleable(settings.videoEditorEnabled,
            enabled = !saving, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { enabled -> scope.launch {
                saving = true; error = false
                try { update { it.copy(videoEditorEnabled = enabled) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { saving = false }
            } }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("動画編集を有効にする", Modifier.weight(1f))
            Switch(settings.videoEditorEnabled, onCheckedChange = null, enabled = !saving)
        }
        Text("端末内の動画を選び、時間と範囲を指定してモザイクや黒塗りを加えます。画像編集・プロンプト表示とは独立した機能です。", style = MaterialTheme.typography.bodySmall)
        LocalLaunchDeviceVideoEditor.current?.let { launch -> TextButton(onClick = launch) { Text("動画を選んで編集") } }
        if (error) Text("設定を保存できませんでした", color = MaterialTheme.colorScheme.error)
    }
}
