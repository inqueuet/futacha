package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.board.isVideoAttachmentName
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import com.valoser.futacha.shared.ui.compat.applyCompatMailPreset
import com.valoser.futacha.shared.ui.compat.compatPostMailPresets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.compatPostLineCount
import com.valoser.futacha.shared.ui.compat.compatPostShiftJisByteCount
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the write screen hands to the sender. */
internal data class FutaberPostInput(val subject: String, val comment: String, val image: com.valoser.futacha.shared.util.ImageData? = null)

private const val QUOTE_DRAG_THRESHOLD_PX = 120f

/**
 * Full-screen writing: a large text box with a line/byte counter, clear, settings (name, mail,
 * delete key, confirmation) and send. A failed send keeps everything typed and says why.
 * The text is also kept as a draft while typing and dropped once the post goes through.
 */
@Composable
internal fun FutaberPostScreen(
    target: FutaberPostTarget,
    boardAddress: String,
    subject: String,
    comment: String,
    onSubjectChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onEnterQuoteMode: (() -> Unit)?,
    attachment: FutaberAttachment,
    capabilities: com.valoser.futacha.shared.network.BoardPostingCapabilities,
    pickerPreference: com.valoser.futacha.shared.util.AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    settings: FutaberPostSettings,
    deleteKey: String,
    onSettingsChange: (name: String, email: String, confirmBeforeSend: Boolean) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    onSend: suspend (FutaberPostInput) -> Result<String?>,
    onSent: (String?) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val commentIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(comment, onCommentChange)
    val subjectIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(subject, { onSubjectChange(it.futaberTakeChars(FUTABER_DRAFT_MAX_SUBJECT_CHARS)) })
    val scope = rememberCoroutineScope()
    // Not saved with the screen: after a re-creation or process death no post is in flight any more, and a saved `true`
    // would leave the screen locked behind its "sending" cover.
    var sending by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    val isCreate = target is FutaberPostTarget.CreateThread

    // While a post is on its way the screen stays; afterwards Back closes it (the draft is kept).
    PlatformBackHandler(enabled = true) { if (!sending) onClose() }

    val counts = futaberPostCounts(compatPostLineCount(comment), compatPostShiftJisByteCount(comment))
    val validation = com.valoser.futacha.shared.ui.board.validateThreadReplyForm(deleteKey, comment)
    val isReply = target is FutaberPostTarget.Reply
    val attachedImage = attachment.image
    val pickImage = com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher(
        preference = pickerPreference,
        maxBytes = com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onSelectionError = { attachment.error = it },
        onImageSelected = { attachment.offer(it, capabilities, isReply) }
    )
    val pasteImage = com.valoser.futacha.shared.util.rememberClipboardImagePaste(
        onImage = { attachment.offer(it, capabilities, isReply) },
        onError = { attachment.error = it }
    )

    fun send() {
        // A second tap (the confirmation's button, the send button) while a post is on its way sends nothing more.
        if (sending) return
        errorMessage = null
        sending = true
        scope.launch {
            val result = try {
                onSend(FutaberPostInput(subject.trim(), comment, attachment.image))
            } catch (cancelled: CancellationException) {
                sending = false
                throw cancelled
            } catch (error: Throwable) {
                // The sender is expected to return a Result; anything it throws instead is shown, not left to crash.
                Result.failure<String?>(error)
            }
            sending = false
            result.onSuccess { id ->
                try {
                    onSent(id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // The post went through; only what follows it failed. What was typed is sent, so it goes, and the
                    // reason says so: a second send would post it twice.
                    onCommentChange("")
                    onSubjectChange("")
                    attachment.clear()
                    errorMessage = "投稿は送信されましたが、その後の処理に失敗しました。二重投稿を避けるため、スレッドを確認してください。"
                }
            }.onFailure { error ->
                errorMessage = error.message ?: "送信に失敗しました"
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.96f))
            // This screen lies over the catalog or the thread: a tap or a drag on its empty parts ends here and does not
            // reach what is behind it. (Controls and the text boxes take their own input first.)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("futaber-post-screen")
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            com.valoser.futacha.shared.ui.board.IncomingSharedAttachmentButton(enabled = !sending,
                onAttach = { attachment.offer(it, capabilities, isReply) })
            Row(
                Modifier.fillMaxWidth().height(FUTABER_TOP_BAR_HEIGHT_DP.dp)
                    .then(
                        if (onEnterQuoteMode == null) Modifier else Modifier.pointerInput(onEnterQuoteMode) {
                            // Dragging the screen down puts the keyboard away and shows the thread to quote from.
                            var dragged = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragged = 0f },
                                onVerticalDrag = { _, amount ->
                                    dragged += amount
                                    if (dragged > QUOTE_DRAG_THRESHOLD_PX) {
                                        dragged = Float.NEGATIVE_INFINITY
                                        onEnterQuoteMode()
                                    }
                                }
                            )
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!sending) onClose() }, modifier = Modifier.testTag("futaber-post-close")) {
                    FutaberIcon(Icons.Outlined.Close, contentDescription = "書き込みを閉じる（下書きは残ります）", tint = colors.icon)
                }
                // Two centred lines like the original: what is being written, then where.
                Column(
                    Modifier.weight(1f).testTag("futaber-post-title"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (target) {
                        // A reply shows only the thread's title; a new thread names the action and the board.
                        is FutaberPostTarget.Reply -> Text(
                            target.threadTitle, color = colors.action, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        is FutaberPostTarget.CreateThread -> {
                            Text("スレッド作成", color = colors.action, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(boardAddress, color = colors.action, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Balances the close button so the title stays centred.
                Spacer(Modifier.size(48.dp))
            }
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            if (isCreate) {
                BasicTextField(
                    value = subjectIme.value,
                    onValueChange = subjectIme.onValueChange,
                    visualTransformation = com.valoser.futacha.shared.ui.board.ImeCompositionHighlight(subjectIme.value.composition, colors.catalogGap),
                    singleLine = true,
                    textStyle = TextStyle(color = colors.body, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics { contentDescription = "題名" }.testTag("futaber-post-subject"),
                    decorationBox = { inner ->
                        if (subject.isEmpty()) Text("題名（省略できます）", color = colors.meta, fontSize = 16.sp)
                        inner()
                    }
                )
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BasicTextField(
                    value = commentIme.value,
                    onValueChange = commentIme.onValueChange,
                    visualTransformation = com.valoser.futacha.shared.ui.board.ImeCompositionHighlight(commentIme.value.composition, colors.catalogGap),
                    enabled = !sending,
                    textStyle = TextStyle(color = colors.body, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                        .semantics { contentDescription = "本文" }.testTag("futaber-post-comment"),
                    decorationBox = { inner ->
                        if (comment.isEmpty()) Text("本文", color = colors.meta, fontSize = 16.sp)
                        inner()
                    }
                )
            }
            attachedImage?.let { picked ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("futaber-post-attachment"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (picked.fileName.isVideoAttachmentName()) {
                        Box(Modifier.size(56.dp).background(colors.catalogGap), contentAlignment = Alignment.Center) {
                            Text("動画", color = colors.meta, fontSize = 12.sp)
                        }
                    } else {
                        AsyncImage(
                            model = picked.bytes,
                            imageLoader = LocalFutachaImageLoader.current,
                            contentDescription = "添付する画像のプレビュー",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).background(colors.catalogGap)
                        )
                    }
                    Text(
                        "${picked.fileName}（${maxOf(1, picked.bytes.size / 1024)}KB）",
                        color = colors.body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { attachment.clear() }, enabled = !sending, modifier = Modifier.testTag("futaber-post-attachment-remove")) {
                        FutaberIcon(Icons.Outlined.Close, contentDescription = "添付を外す", tint = colors.icon)
                    }
                }
            }
            if (attachment.processing) {
                Text("画像を処理しています…", color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Text(
                "${counts.lines}行 / ${counts.bytes}B",
                color = colors.meta,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("futaber-post-counts")
            )
            (attachment.error ?: errorMessage)?.let { message ->
                Text(
                    text = message,
                    color = colors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(colors.accent)
                        .clickable(onClickLabel = "閉じる") { errorMessage = null; attachment.clearError() }
                        .padding(8.dp).testTag("futaber-post-error")
                )
            }
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            Row(
                Modifier.fillMaxWidth().background(colors.bar).heightIn(min = FUTABER_BOTTOM_BAR_HEIGHT_DP.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // The tools take what the send button leaves (it is measured first, being the only fixed-size child):
                // on a narrow screen or with large text they scroll sideways instead of squeezing "送信" away.
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = pickImage, enabled = !sending, modifier = Modifier.testTag("futaber-post-attach")) {
                        FutaberIcon(Icons.Outlined.PhotoCamera, contentDescription = "画像・動画を添付", tint = colors.icon)
                    }
                    IconButton(
                        onClick = pasteImage.paste,
                        enabled = !sending && !pasteImage.busy,
                        modifier = Modifier.testTag("futaber-post-paste")
                    ) {
                        FutaberIcon(Icons.Outlined.ContentPaste, contentDescription = "画像を貼り付け", tint = colors.icon)
                    }
                    if (onEnterQuoteMode != null) {
                        IconButton(onClick = onEnterQuoteMode, enabled = !sending, modifier = Modifier.testTag("futaber-post-quote-mode")) {
                            FutaberIcon(Icons.Outlined.FormatQuote, contentDescription = "本文を見ながら引用する", tint = colors.icon)
                        }
                    }
                    IconButton(
                        onClick = { onCommentChange(""); onSubjectChange(""); errorMessage = null; attachment.clear() },
                        enabled = !sending && (comment.isNotEmpty() || subject.isNotEmpty() || attachedImage != null),
                        modifier = Modifier.testTag("futaber-post-clear")
                    ) {
                        FutaberIcon(Icons.Outlined.Close, contentDescription = "入力をクリア", tint = colors.icon)
                    }
                    IconButton(onClick = { settingsOpen = true }, enabled = !sending, modifier = Modifier.testTag("futaber-post-settings")) {
                        FutaberIcon(Icons.Outlined.Settings, contentDescription = "書き込み設定", tint = colors.icon)
                    }
                }
                val canSend = !sending && !attachment.processing
                Box(
                    Modifier.heightIn(min = 44.dp).clip(FutaberShapes.pill)
                        .background(if (canSend) colors.action else colors.catalogGap)
                        .border(1.dp, if (canSend) colors.action else colors.separator, FutaberShapes.pill)
                        .clickable(enabled = !sending && !attachment.processing, onClickLabel = "送信") {
                            if (validation != null) {
                                errorMessage = if (validation.contains("削除キー")) "$validation（右下の歯車、書き込み設定で入力できます）" else validation
                            }
                            else if (settings.confirmBeforeSend) confirmOpen = true
                            else send()
                        }
                        .padding(horizontal = 22.dp)
                        .testTag("futaber-post-send"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "送信",
                        color = if (canSend) colors.onAction else colors.meta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Box(Modifier.padding(end = 4.dp))
            }
        }
        if (sending) {
            // A post in flight cannot be edited or left; the reason is shown, not hidden.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("futaber-post-sending"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Text(
                        if (isCreate) "スレッドを立てています…" else "送信しています…",
                        color = colors.body,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    attachment.pendingCompression?.let { big ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = attachment::cancelCompression,
                containerColor = colors.bar,
                titleContentColor = colors.body,
                textContentColor = colors.body,
                title = { Text("画像を圧縮しますか？") },
                text = { Text("この板の添付上限を超えています。上限に収まるよう圧縮します。") },
                confirmButton = {
                    TextButton(onClick = { attachment.compress(big, capabilities) }) { Text("圧縮", color = colors.link) }
                },
                dismissButton = { TextButton(onClick = attachment::cancelCompression) { Text("キャンセル", color = colors.link) } }
            )
        }
    }
    if (confirmOpen) {
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { confirmOpen = false },
                containerColor = colors.bar,
                titleContentColor = colors.body,
                textContentColor = colors.body,
                title = { Text(if (isCreate) "スレッドを立てますか？" else "送信しますか？") },
                text = { Text("${counts.lines}行 / ${counts.bytes}B") },
                confirmButton = {
                    TextButton(
                        onClick = { confirmOpen = false; send() },
                        modifier = Modifier.testTag("futaber-post-confirm")
                    ) { Text("送信", color = colors.link) }
                },
                dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("キャンセル", color = colors.link) } }
            )
        }
    }
    if (settingsOpen) {
        FutaberPostSettingsDialog(
            settings = settings,
            deleteKey = deleteKey,
            onSettingsChange = onSettingsChange,
            onDeleteKeyChange = onDeleteKeyChange,
            onDismiss = { settingsOpen = false }
        )
    }
}

/**
 * Name, mail, delete key and the send confirmation. The fields are edited in local state and every change is passed
 * on at once (the value the stored settings hold comes back late, and typing into that round trip loses composing text
 * and moves the cursor). The settings are read again each time the dialog opens.
 */
@Composable
internal fun FutaberPostSettingsDialog(
    settings: FutaberPostSettings,
    deleteKey: String,
    onSettingsChange: (name: String, email: String, confirmBeforeSend: Boolean) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var name by remember { mutableStateOf(settings.name) }
    var email by remember { mutableStateOf(settings.email) }
    var key by remember { mutableStateOf(deleteKey) }
    var confirmBeforeSend by remember { mutableStateOf(settings.confirmBeforeSend) }
    val nameIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(name, {
        name = it.futaberTakeChars(60)
        onSettingsChange(name, email, confirmBeforeSend)
    })
    val emailIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(email, {
        email = it.futaberTakeChars(60)
        onSettingsChange(name, email, confirmBeforeSend)
    })
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("書き込み設定") },
            text = {
                // Scrolls when the dialog does not fit (a landscape screen, large text).
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameIme.value,
                        onValueChange = nameIme.onValueChange,
                        visualTransformation = com.valoser.futacha.shared.ui.board.ImeCompositionHighlight(nameIme.value.composition, colors.catalogGap),
                        label = { Text("名前") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("futaber-post-name")
                    )
                    OutlinedTextField(
                        value = emailIme.value,
                        onValueChange = emailIme.onValueChange,
                        visualTransformation = com.valoser.futacha.shared.ui.board.ImeCompositionHighlight(emailIme.value.composition, colors.catalogGap),
                        label = { Text("メール") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("futaber-post-email")
                    )
                    if (LocalFutaberDisplaySettings.current.extMailPresets) {
                        // The same buttons and values ふたちゃ and としあき(仮) offer for the mail field.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            compatPostMailPresets(true).forEach { preset ->
                                val applied = applyCompatMailPreset(email, preset, true)
                                TextButton(
                                    onClick = {
                                        email = applied.futaberTakeChars(60)
                                        onSettingsChange(name, email, confirmBeforeSend)
                                    },
                                    modifier = Modifier.testTag("futaber-post-mail-preset-$preset")
                                ) { Text(preset, color = if (email == applied) colors.accent else colors.link) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            key = it.futaberTakeChars(20)
                            onDeleteKeyChange(key)
                        },
                        label = { Text("削除キー") },
                        supportingText = { Text("自分のレスを後から削除するときに使います") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("futaber-post-deletekey")
                    )
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("送信前に確認する", color = colors.body, modifier = Modifier.weight(1f))
                        Switch(
                            checked = confirmBeforeSend,
                            onCheckedChange = {
                                confirmBeforeSend = it
                                onSettingsChange(name, email, confirmBeforeSend)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.action, checkedThumbColor = colors.onAction),
                            modifier = Modifier.testTag("futaber-post-confirm-switch")
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = colors.link) } }
        )
    }
}
