package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.futaber.FutaberAttachment
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** 書き込み先：返信（スレッド）かスレ立て（板）。 */
internal sealed interface FtutterPostTarget {
    val boardId: String

    data class Reply(override val boardId: String, val threadId: String) : FtutterPostTarget
    data class Create(override val boardId: String) : FtutterPostTarget
}

/**
 * 下書き（原作 KakikomiData：題名・名前・メール・コメント。削除キーは含まない）。原作は添付のパスも持つが、
 * ここでは添付の中身を保存しない（閉じると外れる）。
 */
@Serializable
internal data class FtutterDraft(
    val title: String = "",
    val name: String = "",
    val mail: String = "",
    val comment: String = ""
) {
    val isEmpty: Boolean get() = title.isEmpty() && name.isEmpty() && mail.isEmpty() && comment.isEmpty()
}

private val draftJson = Json { ignoreUnknownKeys = true }
private val draftsSerializer = MapSerializer(String.serializer(), FtutterDraft.serializer())

/** 下書きの鍵：返信はスレッドのURL、スレ立ては板のURL（原作はスレッドのデータ・板のデータに持つ）。 */
internal fun ftutterDraftKey(target: FtutterPostTarget, boardUrl: String): String = when (target) {
    is FtutterPostTarget.Reply -> "res:" + ftutterNgBoardKey(boardUrl) + "res/" + target.threadId + ".htm"
    is FtutterPostTarget.Create -> "board:" + ftutterNgBoardKey(boardUrl)
}

internal fun parseFtutterDrafts(raw: String?): Map<String, FtutterDraft> =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { draftJson.decodeFromString(draftsSerializer, it) }.getOrNull() }.orEmpty()

/** 下書きを書き換える。空の下書きは消す。新しいものを前に、多すぎる時は古いものから落とす。 */
internal fun ftutterDraftsWith(drafts: Map<String, FtutterDraft>, key: String, draft: FtutterDraft?): String {
    val next = LinkedHashMap<String, FtutterDraft>()
    if (draft != null && !draft.isEmpty) next[key] = draft
    drafts.forEach { (k, v) -> if (k != key && next.size < FTUTTER_DRAFT_LIMIT) next[k] = v }
    return draftJson.encodeToString(draftsSerializer, next)
}

internal const val FTUTTER_DRAFT_LIMIT = 100

/**
 * 開いた時のコメント：下書きのコメント、（末尾が改行でなければ改行）、引用文（原作どおり）。
 */
internal fun ftutterInitialComment(draftComment: String, quote: String?): String {
    if (quote.isNullOrEmpty()) return draftComment
    if (draftComment.isEmpty()) return quote
    return draftComment + (if (draftComment.endsWith("\n")) "" else "\n") + quote
}

/** 本文返信：全行の先頭に`>`を付ける（原作は引用行もそのまま`>>…`にする）。 */
internal fun ftutterQuoteLines(lines: List<String>): String = lines.joinToString("\n") { ">$it" } + "\n"

/** 削除キーは英数字だけで8文字まで（原作 DeleteKeyNotifier.set）。 */
internal fun ftutterNormalizeDeleteKey(raw: String): String = raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(8)

/** 初回に作る削除キー：英数字から「W」を除いた61種で8文字（原作）。 */
internal fun ftutterGenerateDeleteKey(random: Random = Random.Default): String {
    val alphabet = (('0'..'9') + ('a'..'z') + ('A'..'Z')).filter { it != 'W' }
    return (1..8).map { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
}

/** 送る内容。 */
internal data class FtutterPostInput(val draft: FtutterDraft, val image: ImageData?, val deleteKey: String)

/**
 * 書き込みのダイアログ（原作 KakikomiDialog）。スレッドが後ろに見える。閉じる時に下書きを保存し、送信に成功すると消す。
 * 送信に失敗した時は、原作のように閉じず、理由を出して内容を残す。
 */
@Composable
internal fun FtutterPostDialog(
    target: FtutterPostTarget,
    boardUrl: String,
    boardName: String,
    initialDraft: FtutterDraft,
    quote: String?,
    deleteKey: String,
    confirmBeforePost: Boolean,
    capabilities: BoardPostingCapabilities,
    pickerPreference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    deviceInfo: () -> String,
    uploadUps: (suspend (ImageData, String) -> Result<String>)?,
    onDeleteKeyChange: (String) -> Unit,
    onSaveDraft: (FtutterDraft) -> Unit,
    onSend: suspend (FtutterPostInput) -> Result<String?>,
    onSent: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val scope = rememberCoroutineScope()
    val isReply = target is FtutterPostTarget.Reply
    // img/b では題名・名前の欄が無く、img/b の返信には添付が無い（原作。公式のフォームと同じ）。
    val imgB = ftutterIsImgB(boardUrl)
    var title by remember { mutableStateOf(initialDraft.title) }
    var name by remember { mutableStateOf(initialDraft.name) }
    var mail by remember { mutableStateOf(initialDraft.mail) }
    var comment by remember { mutableStateOf(ftutterInitialComment(initialDraft.comment, quote)) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var deleteKeyOpen by remember { mutableStateOf(false) }
    var upsBusy by remember { mutableStateOf(false) }
    val attachment = remember { FutaberAttachment(scope) }
    fun draft() = FtutterDraft(title, name, mail, comment)
    fun close() {
        if (sending) return
        onSaveDraft(draft())
        onDismiss()
    }
    val pick = com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher(
        preference = pickerPreference,
        maxBytes = com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onSelectionError = { attachment.error = it },
        onImageSelected = { attachment.offer(it, capabilities, isReply) }
    )
    val pickUps = com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher(
        preference = pickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
        onSelectionError = { error = it },
        onImageSelected = { picked ->
            val upload = uploadUps ?: return@rememberAttachmentPickerLauncher
            if (!com.valoser.futacha.shared.ui.compat.isCompatUpsUploadSizeAllowed(picked.bytes.size)) {
                error = "サイズが大きすぎます！"
                return@rememberAttachmentPickerLauncher
            }
            upsBusy = true
            scope.launch {
                upload(picked, deleteKey)
                    .onSuccess { file -> comment = com.valoser.futacha.shared.ui.compat.appendCompatPostText(comment, file) }
                    .onFailure { error = it.message ?: "アップロードできませんでした" }
                upsBusy = false
            }
        }
    )
    fun send() {
        if (sending) return
        com.valoser.futacha.shared.ui.board.validateThreadReplyForm(deleteKey, comment)?.let {
            error = it
            return
        }
        sending = true
        error = null
        scope.launch {
            val result = try {
                onSend(FtutterPostInput(draft(), attachment.image, deleteKey))
            } catch (cancelled: CancellationException) {
                sending = false
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            sending = false
            result.onSuccess { posted ->
                onSaveDraft(FtutterDraft())
                onSent(posted)
            }.onFailure { error = it.message ?: "書き込みに失敗しました" }
        }
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = ::close,
        containerColor = colors.popUp,
        modifier = Modifier.testTag("ftutter-post-dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isReply) "返信" else "スレ立て（$boardName）", color = colors.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("ftutter-post-menu")) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー", tint = colors.text)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("クリア") }, onClick = { menu = false; clearOpen = true },
                            modifier = Modifier.testTag("ftutter-post-clear"))
                        DropdownMenuItem(text = { Text("削除キー") }, onClick = { menu = false; deleteKeyOpen = true })
                        DropdownMenuItem(text = { Text("端末情報") }, onClick = {
                            menu = false
                            comment = com.valoser.futacha.shared.ui.compat.appendCompatPostText(comment, deviceInfo())
                        })
                    }
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!imgB) {
                    OutlinedTextField(title, { title = it }, label = { Text("題名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-post-title"))
                    OutlinedTextField(name, { name = it }, label = { Text("おなまえ") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-post-name"))
                }
                OutlinedTextField(mail, { mail = it }, label = { Text("E-mail") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-post-mail"))
                OutlinedTextField(comment, { comment = it }, label = { Text("コメント") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("ftutter-post-comment"))
                if (!(imgB && isReply)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = pick, modifier = Modifier.testTag("ftutter-post-attach")) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = "添付", tint = colors.text)
                        }
                        Text(
                            attachment.image?.fileName ?: if (attachment.processing) "準備中…" else "添付なし",
                            color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (attachment.image != null) {
                            IconButton(onClick = { attachment.clear() }, modifier = Modifier.testTag("ftutter-post-detach")) {
                                Icon(Icons.Rounded.Close, contentDescription = "添付を外す", tint = colors.text)
                            }
                        }
                        if (uploadUps != null) {
                            TextButton(onClick = pickUps, enabled = !upsBusy, modifier = Modifier.testTag("ftutter-post-ups")) {
                                Text(if (upsBusy) "アップロード中…" else "あぷ小")
                            }
                        }
                    }
                }
                (attachment.error ?: error)?.let { Text(it, color = colors.deleted, modifier = Modifier.testTag("ftutter-post-error")) }
                attachment.pendingCompression?.let { pending ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("容量を超えています。圧縮しますか？", color = colors.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = { attachment.compress(pending, capabilities) }) { Text("圧縮") }
                        TextButton(onClick = { attachment.cancelCompression() }) { Text("やめる") }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sending) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                }
                TextButton(
                    onClick = { if (confirmBeforePost) confirmOpen = true else send() },
                    enabled = !sending && !attachment.processing,
                    modifier = Modifier.testTag("ftutter-post-send")
                ) { Text("書き込む") }
            }
        },
        dismissButton = { TextButton(onClick = ::close, enabled = !sending) { Text("閉じる") } }
    ) }
    if (confirmOpen) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("この内容で書き込みますか？") },
            confirmButton = { TextButton(onClick = { confirmOpen = false; send() }, modifier = Modifier.testTag("ftutter-post-confirm")) { Text("OK") } },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("キャンセル") } }
        ) }
    }
    if (clearOpen) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("書き込み内容をクリアしますか？") },
            confirmButton = {
                TextButton(onClick = {
                    clearOpen = false
                    title = ""; name = ""; mail = ""; comment = ""
                    attachment.clear()
                }, modifier = Modifier.testTag("ftutter-post-clear-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("キャンセル") } }
        ) }
    }
    if (deleteKeyOpen) {
        FtutterDeleteKeyDialog(deleteKey, onDismiss = { deleteKeyOpen = false }) { key ->
            deleteKeyOpen = false
            onDeleteKeyChange(key)
        }
    }
}

/** 削除キーの入力（英数字だけ、8文字まで。空は断る）。 */
@Composable
internal fun FtutterDeleteKeyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除キー") },
        text = {
            Column {
                OutlinedTextField(value, { value = ftutterNormalizeDeleteKey(it); error = null }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-delete-key"))
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isEmpty()) error = "削除キーが空です！" else onSave(value) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}

/** 記事削除（原作 DeleteResuDialog）：削除キー（伏せ字、初期値は保存の削除キー）と「画像だけ」。 */
@Composable
internal fun FtutterDeletePostDialog(postNo: String, deleteKey: String, onDismiss: () -> Unit, onDelete: (key: String, imageOnly: Boolean) -> Unit) {
    var key by remember { mutableStateOf(deleteKey) }
    var imageOnly by remember { mutableStateOf(false) }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ftutter-delete-post-dialog"),
        title = { Text("記事削除") },
        text = {
            Column {
                Text("No.$postNo")
                OutlinedTextField(key, { key = ftutterNormalizeDeleteKey(it) }, label = { Text("削除キー") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(Modifier.clickable { imageOnly = !imageOnly }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = imageOnly, onCheckedChange = { imageOnly = it })
                    Spacer(Modifier.width(4.dp))
                    Text("画像だけ")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(key, imageOnly) }, enabled = key.isNotEmpty()) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
}
