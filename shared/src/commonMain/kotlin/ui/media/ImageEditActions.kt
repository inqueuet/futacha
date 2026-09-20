package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.media.MediaFeature
import com.valoser.futacha.shared.media.edit.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.ui.board.requiresVisibleManualSaveDestination
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.isAndroid
import kotlinx.coroutines.*

internal data class ImageEditHost(val fileSystem: FileSystem, val stateStore: AppStateStore)
internal val LocalLaunchDeviceImageEditor = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Device picker and editor survive closing the menu that launched them. */
@Composable
internal fun DeviceImageEditingHost(fileSystem: FileSystem?, stateStore: AppStateStore, content: @Composable () -> Unit) {
    val gate = LocalMediaFeatureGate.current
    val enabled = LocalMediaFeatureSettings.current.imageEditorEnabled
    var pendingPermit by remember { mutableStateOf<com.valoser.futacha.shared.media.MediaFeaturePermit?>(null) }
    var picked by remember { mutableStateOf<ImageData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher(
        maxBytes = 32L * 1024 * 1024,
        onImageSelected = { image ->
            val permit = pendingPermit
            if (permit != null && gate?.isCurrent(permit) == true) picked = image
            pendingPermit = null
        },
        onSelectionError = { message ->
            if (pendingPermit?.let { gate?.isCurrent(it) } == true) error = message
            pendingPermit = null
        }
    )
    LaunchedEffect(enabled) { if (!enabled) { pendingPermit = null; picked = null; error = null } }
    val launch: (() -> Unit)? = if (enabled && fileSystem != null && gate != null) {
        {
            pendingPermit = gate.permit(MediaFeature.IMAGE_EDITOR)
            if (pendingPermit != null) picker()
        }
    } else null
    CompositionLocalProvider(LocalLaunchDeviceImageEditor provides launch) {
        DeviceVideoEditingHost(fileSystem, stateStore, content)
    }
    if (enabled && fileSystem != null) picked?.let { image ->
        ImageEditSaveDialog(ImageEditInput(image), ImageEditHost(fileSystem, stateStore), onDismiss = { picked = null })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("画像を開けませんでした") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("閉じる") } }) }
}

@Composable
internal fun DeviceImageEditorMenuItem(onDismissMenu: () -> Unit) {
    val launch = LocalLaunchDeviceImageEditor.current ?: return
    DropdownMenuItem(text = { Text("画像編集") }, onClick = { onDismissMenu(); launch() },
        modifier = Modifier.testTag("device-image-editor-menu"))
}

@Composable
internal fun DeviceImageEditorSettings() {
    val settings = LocalMediaFeatureSettings.current
    val update = LocalMediaFeatureUpdater.current ?: return
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().testTag("image-editor-settings-toggle")
            .toggleable(value = settings.imageEditorEnabled, enabled = !saving,
                role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { enabled ->
                    scope.launch {
                        saving = true; error = false
                        try { update { it.copy(imageEditorEnabled = enabled) } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                        finally { saving = false }
                    }
                }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("画像編集を有効にする", Modifier.weight(1f))
            Switch(settings.imageEditorEnabled, onCheckedChange = null, enabled = !saving)
        }
        Text("端末内の画像を選んで、モザイクや黒塗りを加えます。プロンプト表示とは独立した機能です。", style = MaterialTheme.typography.bodySmall)
        LocalLaunchDeviceImageEditor.current?.let { launch -> TextButton(onClick = launch) { Text("画像を選んで編集") } }
        if (error) Text("設定を保存できませんでした", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ImageEditSaveDialog(input: ImageEditInput, host: ImageEditHost, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val gate = LocalMediaFeatureGate.current ?: return
    val permit = remember(input) { gate.permit(MediaFeature.IMAGE_EDITOR) } ?: return
    val persisted by host.stateStore.manualSaveLocation.collectAsState(initial = null)
    var chosen by remember { mutableStateOf<SaveLocation?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    val location = chosen ?: persisted
    val picker = rememberDirectoryPickerLauncher(onDirectorySelected = { selected ->
        if (gate.isCurrent(permit)) {
            chosen = selected; locationError = null
            scope.launch {
                try { host.stateStore.setManualSaveLocation(selected) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { locationError = "保存先を設定に記録できませんでした。今回の保存には選んだ場所を使います。" }
            }
        }
    })
    var saved by remember { mutableStateOf<Pair<SaveLocation, String>?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val share = rememberCompatShareLauncher()
    if (saved == null) ImageEditorDialog(input, onDismiss, confirmLabel = "保存", onResult = { image ->
        val target = requireNotNull(location) { "保存先を選択してください" }
        val path = saveEditedImage(host.fileSystem, target, image) {
            check(gate.isCurrent(permit)) { "画像編集は無効になりました" }
        }
        saved = target to path
    },
        // Saving a visible result is the commit. Keep its confirmation independently of editor disposal.
        dismissAfterResult = false,
        exportEnabled = location != null && !requiresVisibleManualSaveDestination(isAndroid(), location),
        destination = {
            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (location == null || requiresVisibleManualSaveDestination(isAndroid(), location)) "保存先を選択してください" else "選択した保存先の edited_images に保存", modifier = Modifier.weight(1f))
                TextButton(onClick = picker) { Text("保存先") }
            }
        }
    )
    saved?.let { (target, path) ->
        AlertDialog(onDismissRequest = onDismiss, title = { Text("編集した画像を保存しました") },
            text = { Text(shareError ?: path) }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
            dismissButton = { TextButton(onClick = { scope.launch {
                try { share("", "image/jpeg", host.fileSystem.resolveSavedFile(target, path).getOrThrow()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { shareError = failure.message ?: "共有できませんでした" }
            } }) { Text("共有") } })
    }
}
