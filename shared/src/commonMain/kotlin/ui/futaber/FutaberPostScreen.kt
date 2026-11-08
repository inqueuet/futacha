package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.util.ToolbarOverflowHints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.board.isVideoAttachmentName
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.StableTextInputState
import com.valoser.futacha.shared.ui.compat.appendCompatPostText
import com.valoser.futacha.shared.ui.compat.compatPostDeviceInfo
import com.valoser.futacha.shared.ui.compat.compatPostLineCount
import com.valoser.futacha.shared.ui.compat.compatPostShiftJisByteCount
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the write screen hands to the sender. */
internal data class FutaberPostInput(val subject: String, val comment: String, val image: com.valoser.futacha.shared.util.ImageData? = null)

/**
 * Full-screen writing as the original's (L-B8, docs/futaber-evolution-look-2026-10-31.md §2.8): × and the title in blue,
 * the text with the 「N行 / NB」 counter at its bottom right, the tools カメラ／ｶﾅ／用紙／(紙飛行機)／×／歯車 and this app's
 * after them, scrolling sideways beside the fixed 「送信」 frame. The camera opens the black menu (M37), × clears and
 * its long press brings back what was cleared (M42), the gear opens the full-screen 書き込み設定 (S16). Pulling the
 * text down at its top goes into quote mode (G12), a swipe to the right closes the screen (G29; the draft is kept).
 * A failed send keeps everything typed and says why; the text is also kept as a draft while typing.
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
    onClose: () -> Unit,
    /** The board's address, for the shared "板名の誤投稿確認" ([destinationConfirm]; off by default in every mode). */
    boardUrl: String = "",
    destinationConfirm: Boolean = false,
    /** The posting aids (drawing, あぷ小, voice input, …; FutaberParityB2.kt). The default offers only the drawing where it is needed. */
    tools: FutaberPostTools = FutaberPostTools(),
    /** The "ペーストボード" (the paper icon, as in the original); null where nothing can be kept on this device. */
    pasteboard: FutaberPasteboardLink? = null,
    /** F17: 設定→スレッド「従来の動き」 brings back a new thread's subject field (the original's has none). */
    legacySubjectField: Boolean = false,
    /** M42: what × cleared last; held by the app so it outlives quote mode, forgotten with the writing. */
    cleared: MutableState<FutaberClearedText?> = remember { mutableStateOf(null) },
    /** C43: 書き込み設定「画像の品質」 changed. */
    onImageQualityChange: (FutaberImageQuality) -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    val commentIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(comment, onCommentChange)
    val subjectIme = com.valoser.futacha.shared.ui.board.rememberStableTextInputState(subject, { onSubjectChange(it.futaberTakeChars(FUTABER_DRAFT_MAX_SUBJECT_CHARS)) })
    // The screen opens ready to type, the cursor in the text and the keyboard up, as the original's does.
    val commentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Once the field is laid out (a request before that is not attached and throws).
        androidx.compose.runtime.withFrameNanos { }
        runCatching { commentFocus.requestFocus() }
    }
    val scope = rememberCoroutineScope()
    // Not saved with the screen: after a re-creation or process death no post is in flight any more, and a saved `true`
    // would leave the screen locked behind its "sending" cover.
    var sending by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    var pasteboardOpen by rememberSaveable { mutableStateOf(false) }
    var restoreAnchor by remember { mutableStateOf<FutaberAnchor?>(null) }
    // Where × is now: the open bubble follows it (the keyboard's slide moves the tools).
    var clearAnchor by remember { mutableStateOf<FutaberAnchor?>(null) }
    val toolsState = rememberFutaberPostToolsState()
    // The error of the last failed send (the Cookie guidance reads it); not saved with the screen.
    var lastFailure by remember { mutableStateOf<Throwable?>(null) }
    val isCreate = target is FutaberPostTarget.CreateThread
    // F17: the subject field only with the old behaviour switched on, or while a kept draft still has a subject (it is
    // not hidden and sent unseen).
    val subjectShown = isCreate && (legacySubjectField || subject.isNotEmpty())

    // While a post is on its way the screen stays; afterwards Back closes it (the draft is kept).
    // On Android the first Back with the keyboard up only puts the keyboard away, as elsewhere: the screen now opens with
    // it up, and a back callback registered after the keyboard's would otherwise take that Back and close the screen.
    // The keyboard is read at the press, so its slide does not compose the screen again.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val imeInsets = androidx.compose.foundation.layout.WindowInsets.ime
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Closed once: the right swipe and iOS's edge gesture may both end the same touch.
    var closed by remember { mutableStateOf(false) }
    val close: () -> Unit = { if (!closed && !sending) { closed = true; onClose() } }
    PlatformBackHandler(enabled = true) {
        when {
            sending -> Unit
            com.valoser.futacha.shared.util.isAndroid() && imeInsets.getBottom(density) > 0 -> keyboard?.hide()
            else -> close()
        }
    }
    // F28: back from the camera's menu, a picker, the pasteboard or the settings, the text takes the keyboard again.
    val refocus: () -> Unit = {
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { commentFocus.requestFocus() }
            keyboard?.show()
        }
    }

    // The byte count is a Shift_JIS round trip of the whole text: counted once per edit off the main thread, as としあき(仮)
    // does (re-audit H2: it ran in composition at every key, which made typing stutter on iOS). The first value is counted
    // at once so the label never shows nothing.
    var byteCount by remember { mutableStateOf(compatPostShiftJisByteCount(comment)) }
    LaunchedEffect(comment) {
        byteCount = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.textAnnotation) {
            compatPostShiftJisByteCount(comment)
        }
    }
    // F18: an empty text is 「0行 / 0B」, as the original counts it (the shared count keeps 1 for the other modes).
    val counts = futaberPostCounts(compatPostLineCount(comment, emptyIsOneLine = false), byteCount)
    val validation = com.valoser.futacha.shared.ui.board.validateThreadReplyForm(deleteKey, comment)
    // A post that looks meant for another board (img's "としあき", may's empty 「」) asks first, even without the confirmation.
    val destinationWarning = futaberPostDestinationWarning(boardUrl, comment, destinationConfirm)
    val isReply = target is FutaberPostTarget.Reply
    val attachedImage = attachment.image
    // C43: the attachment draws pictures again as JPEG under 「JPG圧縮」.
    SideEffect { attachment.quality = settings.imageQuality }
    val pickImage = com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher(
        preference = pickerPreference,
        maxBytes = com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onSelectionError = { attachment.error = it; refocus() },
        onImageSelected = { attachment.offer(it, capabilities, isReply); refocus() }
    )
    val pickPhoto = rememberFutaberPhotoPicker(
        maxBytes = com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES,
        onPicked = { attachment.offer(it, capabilities, isReply) },
        onFinished = refocus,
        onError = { attachment.error = it }
    )
    val pasteImage = com.valoser.futacha.shared.util.rememberClipboardImagePaste(
        onImage = { attachment.offer(it, capabilities, isReply) },
        onError = { attachment.error = it }
    )

    fun send() {
        // A second tap (the confirmation's button, the send button) while a post is on its way sends nothing more.
        if (sending) return
        errorMessage = null
        lastFailure = null
        sending = true
        scope.launch {
            val result = try {
                // A subject the screen does not show is not sent (F17).
                onSend(FutaberPostInput(if (subjectShown) subject.trim() else "", comment, attachment.image))
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
                lastFailure = error
            }
        }
    }
    val overlayOpen = settingsOpen || pasteboardOpen || toolsState.cameraMenu != null || restoreAnchor != null || confirmOpen
    val latestSelecting by rememberUpdatedState(!commentIme.value.selection.collapsed)

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
            // G29: a swipe to the right from the middle or the left edge closes the writing (the draft is kept). Not on the
            // tools, which scroll sideways themselves, nor while text is selected or a post is on its way.
            Column(
                Modifier.weight(1f).fillMaxWidth().then(
                    rememberFutaberSwipeModifier(enabled = !sending && !overlayOpen) { swipe ->
                        if (futaberPostSwipeCloses(swipe, sending, latestSelecting)) close()
                    }
                )
            ) {
                FutaberPostHeader(target, boardAddress, onClose = close, onEnterQuoteMode = onEnterQuoteMode, sending = sending)
                HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                if (subjectShown) {
                    FutaberPostSubjectField(subjectIme)
                    HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                }
                FutaberPostBody(
                    state = commentIme,
                    enabled = !sending,
                    focus = commentFocus,
                    counter = futaberPostCounterText(counts.lines, counts.bytes),
                    onPullIntoQuote = onEnterQuoteMode,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                attachedImage?.let { picked -> FutaberPostAttachmentRow(picked, enabled = !sending, onRemove = { attachment.clear() }) }
                if (attachment.processing) {
                    Text("画像を処理しています…", color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                }
                (attachment.error ?: errorMessage)?.let { message ->
                    Text(
                        text = message,
                        color = colors.onAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().background(colors.accent)
                            .clickable(onClickLabel = "閉じる") { errorMessage = null; lastFailure = null; attachment.clearError() }
                            .padding(8.dp).testTag("futaber-post-error")
                    )
                }
                if (errorMessage != null) FutaberCookieGuidanceRow(toolsState, tools, lastFailure)
            }
            HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
            FutaberPostToolbar(
                tools = futaberPostTools(canQuote = onEnterQuoteMode != null),
                sending = sending,
                sendStyle = futaberPostSendStyle(comment, attachedImage != null, busy = sending || attachment.processing),
                // Drawn as the other tools even while the text is empty, as the original's (a press then changes nothing).
                kanaEnabled = !sending,
                pasteboardAvailable = pasteboard != null,
                pasteBusy = pasteImage.busy,
                onTool = { id, anchor ->
                    when (id) {
                        "camera" -> toolsState.cameraMenu = anchor
                        "kana" -> onCommentChange(futaberToHalfWidthKana(comment))
                        "paper" -> pasteboardOpen = true
                        "clear" -> {
                            cleared.value = futaberRememberCleared(subject, comment, cleared.value)
                            onCommentChange(""); onSubjectChange(""); errorMessage = null; attachment.clear()
                        }
                        "settings" -> settingsOpen = true
                        "paste" -> pasteImage.paste()
                        "quote-mode" -> onEnterQuoteMode?.invoke()
                    }
                },
                onClearLongPress = { anchor -> restoreAnchor = anchor },
                onAnchor = { id, anchor ->
                    when (id) {
                        "camera" -> toolsState.cameraAnchor = anchor
                        "clear" -> clearAnchor = anchor
                    }
                },
                onSend = {
                    if (validation != null) {
                        errorMessage = if (validation.contains("削除キー")) "$validation（下の歯車、書き込み設定で入力できます）" else validation
                    }
                    else if (settings.confirmBeforeSend || destinationWarning != null) confirmOpen = true
                    else send()
                }
            )
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
        FutaberPostToolsHost(
            state = toolsState,
            tools = tools,
            handwritingOnly = isReply && !capabilities.replyAttachmentsAllowed,
            comment = comment,
            onCommentChange = onCommentChange,
            deleteKey = deleteKey,
            onPickFile = pickImage,
            onAttach = { attachment.offer(it, capabilities, isReply) },
            boardUrl = boardUrl,
            onPickPhoto = pickPhoto
        )
        restoreAnchor?.let { opened ->
            val anchor = clearAnchor ?: opened
            // M42: the original's black bubble over × (`P/out-post/90-04`); the keyboard stays.
            val remembered = cleared.value
            FutaberCalloutMenu(
                anchor = anchor,
                items = listOf(
                    FutaberCalloutItem("restore-cleared", FUTABER_RESTORE_CLEARED_LABEL, enabled = remembered != null) {
                        restoreAnchor = null
                        if (remembered != null) {
                            val (back, next) = futaberRestoreCleared(subject, comment, remembered)
                            cleared.value = next
                            onCommentChange(back.comment)
                            onSubjectChange(back.subject)
                        }
                    }
                ),
                onDismiss = { restoreAnchor = null }
            )
        }
        val board = pasteboard
        if (pasteboardOpen && board != null) {
            FutaberPasteboardSheet(
                link = board,
                canKeepText = comment.isNotBlank(),
                canKeepImage = attachedImage != null && !attachedImage.fileName.isVideoAttachmentName(),
                onKeepText = { folder -> board.onAddText(comment, folder) },
                onKeepImage = { folder -> attachedImage?.let { board.onAddImage(it, folder) } },
                onPick = { item ->
                    pasteboardOpen = false
                    refocus()
                    if (item.isImage) scope.launch {
                        // The board's rules (size, kind, img's replies without pictures) apply as for any picked file.
                        board.loadImage(item)
                            .onSuccess { attachment.offer(it, capabilities, isReply) }
                            .onFailure { attachment.error = FUTABER_PASTEBOARD_IMAGE_LOST }
                    } else onCommentChange(futaberInsertPaste(comment, item.text))
                },
                onClose = { pasteboardOpen = false; refocus() }
            )
        }
        if (settingsOpen) {
            FutaberPostSettingsScreen(
                settings = settings,
                deleteKey = deleteKey,
                onSettingsChange = onSettingsChange,
                onDeleteKeyChange = onDeleteKeyChange,
                onImageQualityChange = onImageQualityChange,
                onDone = { settingsOpen = false; refocus() },
                // C44: from the writing screen only; the device line goes into the text (today's 機種情報) and the page closes.
                onDeviceInfo = {
                    onCommentChange(appendCompatPostText(comment, futaberDeviceInfoText(compatPostDeviceInfo(tools.appVersion))))
                    settingsOpen = false
                    refocus()
                }
            )
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
                text = {
                    Text(
                        listOfNotNull(destinationWarning, futaberPostCounterText(counts.lines, counts.bytes)).joinToString("\n\n"),
                        modifier = Modifier.testTag("futaber-post-confirm-text")
                    )
                },
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
}

/** × and the title (V17: both in the blue); a reply shows the thread's title, a new thread the action and the board (V37). */
@Composable
private fun FutaberPostHeader(
    target: FutaberPostTarget,
    boardAddress: String,
    onClose: () -> Unit,
    onEnterQuoteMode: (() -> Unit)?,
    sending: Boolean
) {
    val colors = LocalFutaberColors.current
    val latestQuote by rememberUpdatedState(onEnterQuoteMode)
    Row(
        Modifier.fillMaxWidth().height(FUTABER_TOP_BAR_HEIGHT_DP.dp)
            .then(
                if (onEnterQuoteMode == null) Modifier else Modifier.pointerInput(Unit) {
                    // Dragging the screen down puts the keyboard away and shows the thread to quote from.
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { _, amount ->
                            dragged += amount
                            if (dragged > FUTABER_QUOTE_DRAG_THRESHOLD_PX) {
                                dragged = Float.NEGATIVE_INFINITY
                                latestQuote?.invoke()
                            }
                        }
                    )
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (!sending) onClose() }, modifier = Modifier.testTag("futaber-post-close")) {
            FutaberIcon(Icons.Outlined.Close, contentDescription = "書き込みを閉じる（下書きは残ります）", tint = colors.iosBlue)
        }
        // Two centred lines like the original: what is being written, then where.
        Column(
            Modifier.weight(1f).testTag("futaber-post-title"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (target) {
                // A reply shows only the thread's title; a new thread names the action and the board.
                is FutaberPostTarget.Reply -> Text(
                    target.threadTitle, color = colors.iosBlue, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                is FutaberPostTarget.CreateThread -> {
                    Text("スレッド作成", color = colors.iosBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(boardAddress, color = colors.iosBlue, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Balances the close button so the title stays centred.
        Spacer(Modifier.size(48.dp))
    }
}

/** The old subject field of a new thread (F17: behind 「従来の動き」). Plain, no placeholder (V41). */
@Composable
private fun FutaberPostSubjectField(state: StableTextInputState) {
    val colors = LocalFutaberColors.current
    BasicTextField(
        value = state.value,
        onValueChange = state.onValueChange,
        visualTransformation = com.valoser.futacha.shared.ui.board.imeCompositionHighlight(state.value.composition),
        singleLine = true,
        textStyle = TextStyle(color = colors.body, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = LocalFutaberCustomFont.current),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "題名" }.testTag("futaber-post-subject")
    )
}

/**
 * The text (V41: plain, no placeholder; V40: a sentence starts with a capital) with the counter over its bottom right
 * (F18), scrolled by its own column so it is known when it is at its top: a drag started there whose downward part the
 * text cannot scroll pulls it into quote mode (G12). A long text scrolls as before; a selection is left alone.
 */
@Composable
private fun FutaberPostBody(
    state: StableTextInputState,
    enabled: Boolean,
    focus: FocusRequester,
    counter: String,
    onPullIntoQuote: (() -> Unit)?,
    modifier: Modifier
) {
    val colors = LocalFutaberColors.current
    val scroll = rememberScrollState()
    val pull = remember { FutaberQuotePull(FUTABER_QUOTE_DRAG_THRESHOLD_PX) }
    val latestPull by rememberUpdatedState(onPullIntoQuote)
    val latestSelecting by rememberUpdatedState(!state.value.selection.collapsed)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f) pull.onScrolledBack()
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && pull.onLeftover(available.y)) latestPull?.invoke()
                return Offset.Zero
            }
        }
    }
    BoxWithConstraints(
        modifier
            .then(if (onPullIntoQuote == null) Modifier else Modifier.nestedScroll(connection).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pull.onDown(atTop = scroll.value == 0, selecting = latestSelecting)
                    var dx = 0f
                    var dy = 0f
                    // Watched, not taken: the text and its scroll get the same touch.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // The lift may carry the last part of a quick drag: it is counted before the loop ends.
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (pull.onFingerMoved(dx, dy, atTop = scroll.value == 0)) latestPull?.invoke()
                        if (!change.pressed) break
                    }
                }
            })
            .testTag("futaber-post-body")
    ) {
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            BasicTextField(
                value = state.value,
                onValueChange = state.onValueChange,
                visualTransformation = com.valoser.futacha.shared.ui.board.imeCompositionHighlight(state.value.composition),
                enabled = enabled,
                textStyle = TextStyle(color = colors.body, fontSize = 16.sp, fontFamily = LocalFutaberCustomFont.current),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = viewport)
                    // The counter sits over the last lines' right end: they keep clear of it.
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 30.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = "本文" }.testTag("futaber-post-comment")
            )
        }
        Text(
            counter,
            color = colors.meta,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp).testTag("futaber-post-counts")
        )
    }
}

@Composable
private fun FutaberPostAttachmentRow(picked: com.valoser.futacha.shared.util.ImageData, enabled: Boolean, onRemove: () -> Unit) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("futaber-post-attachment"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (picked.fileName.isVideoAttachmentName()) {
            Box(Modifier.size(56.dp).background(colors.fill), contentAlignment = Alignment.Center) {
                Text("動画", color = colors.body, fontSize = 12.sp)
            }
        } else {
            AsyncImage(
                model = picked.bytes,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "添付する画像のプレビュー",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).background(colors.fill)
            )
        }
        Text(
            "${picked.fileName}（${maxOf(1, picked.bytes.size / 1024)}KB）",
            color = colors.body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("futaber-post-attachment-name")
        )
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("futaber-post-attachment-remove")) {
            FutaberIcon(Icons.Outlined.Close, contentDescription = "添付を外す", tint = colors.icon)
        }
    }
}

/**
 * The tool row (M36): the original's six places at 44 each from 4 (the paper plane's place kept empty), then this
 * app's tools, scrolling sideways; 「送信」 the 90×30 frame fixed on the right (V17: a frame while there is nothing to
 * send, the blue fill when there is). [onTool] gets the id and the button's top middle (for the camera's menu).
 */
@Composable
private fun FutaberPostToolbar(
    tools: List<FutaberPostToolSlot>,
    sending: Boolean,
    sendStyle: FutaberPostSendStyle,
    kanaEnabled: Boolean,
    pasteboardAvailable: Boolean,
    pasteBusy: Boolean,
    onTool: (id: String, anchor: FutaberAnchor) -> Unit,
    onClearLongPress: (FutaberAnchor) -> Unit,
    onAnchor: (id: String, anchor: FutaberAnchor) -> Unit,
    onSend: () -> Unit
) {
    val colors = LocalFutaberColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.bar).heightIn(min = FUTABER_BOTTOM_BAR_HEIGHT_DP.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val toolsScroll = rememberScrollState()
        // The tools take what 「送信」 leaves: on a narrow screen or with large text they scroll sideways instead of
        // squeezing it; this app's tools after the original's six are reached by scrolling, as the original's list does.
        Box(Modifier.weight(1f)) {
            Row(Modifier.horizontalScroll(toolsScroll).testTag("futaber-post-tools"), verticalAlignment = Alignment.CenterVertically) {
                tools.forEach { tool ->
                    androidx.compose.runtime.key(tool.id) {
                        FutaberPostToolButton(tool, sending, kanaEnabled, pasteboardAvailable, pasteBusy, onTool, onClearLongPress, onAnchor)
                    }
                }
            }
            ToolbarOverflowHints(toolsScroll)
        }
        Spacer(Modifier.width(8.dp))
        val filled = sendStyle == FutaberPostSendStyle.Filled
        // The 44 high touch target around the 30 high frame.
        Box(
            Modifier.heightIn(min = 44.dp)
                .clickable(enabled = filled, onClickLabel = "送信", role = Role.Button, onClick = onSend)
                .semantics { contentDescription = "送信" }
                .testTag("futaber-post-send"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(FUTABER_POST_SEND_WIDTH_DP.dp, FUTABER_POST_SEND_HEIGHT_DP.dp).clip(FutaberShapes.button)
                    .background(if (filled) colors.iosBlue else colors.bar)
                    .border(1.dp, colors.iosBlue, FutaberShapes.button)
                    .testTag("futaber-post-send-frame"),
                contentAlignment = Alignment.Center
            ) {
                Text("送信", color = if (filled) colors.onIosBlue else colors.iosBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** One tool of the row (the paper plane's place is an empty 44). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FutaberPostToolButton(
    tool: FutaberPostToolSlot,
    sending: Boolean,
    kanaEnabled: Boolean,
    pasteboardAvailable: Boolean,
    pasteBusy: Boolean,
    onTool: (id: String, anchor: FutaberAnchor) -> Unit,
    onClearLongPress: (FutaberAnchor) -> Unit,
    onAnchor: (id: String, anchor: FutaberAnchor) -> Unit
) {
    val colors = LocalFutaberColors.current
    val haptic = rememberFutaberLongPressHaptic()
    var anchor by remember { mutableStateOf(FutaberAnchor(0f, 0f, 0f)) }
    val place = Modifier.size(FUTABER_POST_TOOL_DP.dp).onGloballyPositioned {
        val b = it.boundsInRoot()
        anchor = FutaberAnchor(b.left + b.width / 2f, b.top, b.bottom)
        onAnchor(tool.id, anchor)
    }
    if (tool.empty) {
        Spacer(place.testTag(tool.tag))
        return
    }
    val enabled = !sending && when (tool.id) {
        "kana" -> kanaEnabled
        "paper" -> pasteboardAvailable
        "paste" -> !pasteBusy
        else -> true
    }
    val label = when (tool.id) {
        "camera" -> "画像・動画を添付"
        "kana" -> "本文を半角カナにする"
        "paper" -> "ペーストボード"
        "clear" -> "入力をクリア"
        "settings" -> "書き込み設定"
        "paste" -> "画像を貼り付け"
        else -> "本文を見ながら引用する"
    }
    Box(
        place.clip(FutaberShapes.button)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onLongClickLabel = if (tool.id == "clear") FUTABER_RESTORE_CLEARED_LABEL else null,
                onLongClick = if (tool.id == "clear") { { haptic(); onClearLongPress(anchor) } } else null,
                onClick = { onTool(tool.id, anchor) }
            )
            .semantics { contentDescription = label }
            .testTag(tool.tag),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (enabled) colors.icon else colors.iconDisabled
        when (tool.id) {
            "camera" -> FutaberIcon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = tint)
            "kana" -> Text(FUTABER_POST_KANA_LABEL, color = tint, fontSize = 18.sp)
            "paper" -> FutaberIcon(Icons.Outlined.Description, contentDescription = null, tint = tint)
            "clear" -> FutaberIcon(Icons.Outlined.Close, contentDescription = null, tint = tint)
            "settings" -> FutaberIcon(Icons.Outlined.Settings, contentDescription = null, tint = tint)
            "paste" -> FutaberIcon(Icons.Outlined.ContentPaste, contentDescription = null, tint = tint)
            else -> FutaberIcon(Icons.Outlined.FormatQuote, contentDescription = null, tint = tint)
        }
    }
}
