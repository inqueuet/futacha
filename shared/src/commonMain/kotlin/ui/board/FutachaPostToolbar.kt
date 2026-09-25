package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
internal fun FutachaPostToolbar(
    features: FutachaSharedFeatures,
    boardUrl: String,
    comment: String,
    onCommentChange: (String) -> Unit,
    password: String,
    onImageSelected: (ImageData) -> Unit,
    onChooseImage: () -> Unit,
    onChooseVideo: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean,
    attachmentPickerPreference: com.valoser.futacha.shared.util.AttachmentPickerPreference,
    preferredFileManagerPackage: String?
) {
    val scope = rememberCoroutineScope()
    val latestComment by rememberUpdatedState(comment)
    val master = remember { compatToolbarMaster(CompatToolbarSurface.POST) }
    var toolbar by remember { mutableStateOf(master.mapIndexed { index, item -> CompatToolbarItem(item.key, index, item.defaultActive) }) }
    var editing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendConfirmation by remember { mutableStateOf<String?>(null) }
    var upsAttachment by remember { mutableStateOf<ImageData?>(null) }
    var upsComment by remember { mutableStateOf("") }
    var upsDeleteKey by remember { mutableStateOf(password) }
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "操作に失敗しました" }
            finally { busy = false }
        }
    }
    LaunchedEffect(features.store, editing) {
        if (!editing) {
            try { toolbar = features.store.loadToolbar(CompatToolbarSurface.POST) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = "ツールバーを読み込めませんでした" }
        }
    }
    val speech = rememberCompatSpeechRecognizer(
        onResult = { onCommentChange(appendCompatPostText(latestComment, normalizeCompatSpeechResult(it))) },
        onError = { message = it }
    )
    val pickUps = rememberAttachmentPickerLauncher(
        preference = attachmentPickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onSelectionError = { message = it },
        onImageSelected = {
            if (!isCompatUpsUploadSizeAllowed(it.bytes.size)) message = "あぷ小は3000KBまでです"
            else {
                upsComment = ""
                upsDeleteKey = features.preferences.compatStoredPostDeleteKey().ifBlank { password }
                upsAttachment = it
            }
        }
    )
    fun runCommand(key: String) {
        overflow = false
        when (key) {
            "send" -> {
                if (!enabled || busy) return
                val warning = compatPostDestinationWarning(boardUrl, comment,
                    features.value("control", "controlPostDestinationConfirm", "板名の誤投稿確認") == "ON")
                if (warning != null || features.value("control", "controlPostConfirm", "送信時の確認") != "OFF") {
                    sendConfirmation = warning ?: "この内容を送信しますか？"
                } else onSubmit()
            }
            "attach" -> attachmentMenu = true
            "pallete" -> drawing = true
            "sio" -> pickUps()
            "voice_input" -> speech()
            "network_info" -> perform {
                val info = fetchCompatPostNetworkInfo(features.httpClient, "Futacha/${features.appVersion}")
                onCommentChange(appendCompatPostText(latestComment, info))
            }
            "model_info" -> onCommentChange(appendCompatPostText(comment,
                compatPostDeviceInfo(features.appVersion).replace("ふたば＠アプリ としあき(仮)", "ふたちゃ")))
            "reset" -> onClear()
            "discard" -> onDismiss()
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        remember(toolbar) { toolbar.filter { it.active }.sortedBy { it.position } }.forEach { item ->
            IconButton(enabled = !busy && (item.key != "send" || enabled), onClick = { runCommand(item.key) }) {
                Icon(compatToolbarIcon(item.key), master.firstOrNull { it.key == item.key }?.label ?: item.key)
            }
        }
        Box {
            IconButton(onClick = { overflow = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "投稿のその他") }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                remember(toolbar) { toolbar.filterNot { it.active }.sortedBy { it.position } }.forEach { item ->
                    DropdownMenuItem(text = { Text(master.firstOrNull { it.key == item.key }?.label ?: item.key) },
                        enabled = item.key != "send" || enabled, onClick = { runCommand(item.key) })
                }
                DropdownMenuItem(text = { Text("ツールバー編集") }, onClick = { overflow = false; editing = true })
                DropdownMenuItem(text = { Text("送信・操作の設定") }, onClick = { overflow = false; features.openSettings("control") })
            }
            DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                DropdownMenuItem(text = { Text("画像を選択") }, onClick = { attachmentMenu = false; onChooseImage() })
                DropdownMenuItem(text = { Text("動画を選択") }, onClick = { attachmentMenu = false; onChooseVideo() })
            }
        }
    }
    if (editing || drawing) {
        Dialog(onDismissRequest = { if (!drawing) editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                if (editing) CompatToolbarEditorScreen(CompatToolbarSurface.POST, features.store, onBack = { editing = false })
                else CompatPostDrawingScreen(onSaved = { image ->
                    onImageSelected(image)
                    drawing = false
                    features.fileSystem?.let { fs ->
                        perform {
                            persistCompatDrawingCopy(fs,
                                parseCompatSaveLocation(features.value("storage", "dummyDrawingDir", "手書きファイルの保存先")),
                                image, Clock.System.now().toEpochMilliseconds())?.getOrThrow()
                        }
                    }
                }, onBack = { drawing = false })
            }
        }
    }
    upsAttachment?.let { image ->
        CompatUpsUploadDialog(image.fileName, upsComment, upsDeleteKey,
            onCommentChange = { upsComment = it }, onDeleteKeyChange = { upsDeleteKey = it },
            onCancel = { upsAttachment = null }, onSubmit = {
                val client = features.httpClient
                if (client == null) message = "通信機能を利用できません"
                else perform {
                    val uploadComment = upsComment
                    val deleteKey = upsDeleteKey
                    upsAttachment = null
                    val fileName = uploadCompatUps(client, image, uploadComment, deleteKey, features.appVersion).getOrThrow()
                    onCommentChange(appendCompatPostText(latestComment, fileName))
                    message = "$fileName をアップロードしました"
                }
            })
    }
    sendConfirmation?.let { confirmation ->
        AlertDialog(onDismissRequest = { sendConfirmation = null }, title = { Text("投稿の確認") },
            text = { Text(confirmation) }, confirmButton = {
                TextButton(enabled = enabled, onClick = { sendConfirmation = null; onSubmit() }) { Text("送信する") }
            }, dismissButton = { TextButton(onClick = { sendConfirmation = null }) { Text("キャンセル") } })
    }
    if (busy) AlertDialog(onDismissRequest = {}, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("閉じる") } })
    }
}
