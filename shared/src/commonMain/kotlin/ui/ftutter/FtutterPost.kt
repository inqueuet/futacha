package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.futaber.FutaberAttachment
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    val comment: String = "",
    /** Recovery hint only; never opened automatically as an attachment. */
    val sourceAttachmentPath: String = ""
) {
    val isEmpty: Boolean get() = title.isEmpty() && name.isEmpty() && mail.isEmpty() && comment.isEmpty() && sourceAttachmentPath.isEmpty()
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

internal fun encodeFtutterDraftsForStore(drafts: Map<String, FtutterDraft>): String =
    draftJson.encodeToString(draftsSerializer, drafts)

/** 下書きを書き換える。空の下書きは消す。新しいものを前に、多すぎる時は古いものから落とす。 */
internal fun ftutterDraftsWith(drafts: Map<String, FtutterDraft>, key: String, draft: FtutterDraft?): String =
    ftutterDraftsUpdate(drafts, key, draft).encoded

/**
 * 下書きの保存値は1つの設定（上限2万字）なので、件数・1件の長さ・全体の大きさを決める（ふたばー風の下書きと同じ考え。
 * 2026-10-14の監査A9：以前は件数の制限しか無く、長い下書きが数件あると以後の保存も送信後の削除も黙って失敗した）。
 * - 件数は[FTUTTER_DRAFT_LIMIT]まで。全体は[FTUTTER_DRAFT_STORE_MAX_CHARS]まで（新しいものから残し、古いものを落とす）。
 * - 本文は[FTUTTER_DRAFT_MAX_COMMENT_CHARS]字、題名・名前・メールは[FTUTTER_DRAFT_MAX_FIELD_CHARS]字まで残す。
 *   切ったり古いものを落としたりした時は[FtutterDraftsUpdate]で知らせる（画面が伝える）。
 * - 消すだけ（空の下書き）なら、結果は今の保存値より大きくならないので、送信後の削除は必ず保存できる。
 */
internal data class FtutterDraftsUpdate(val encoded: String, val truncated: Boolean, val dropped: Int)

internal fun ftutterDraftsUpdate(drafts: Map<String, FtutterDraft>, key: String, draft: FtutterDraft?): FtutterDraftsUpdate {
    val next = LinkedHashMap<String, FtutterDraft>()
    var truncated = false
    if (draft != null && !draft.isEmpty) {
        val bounded = draft.boundedForStore()
        truncated = bounded != draft
        next[key] = bounded
    }
    drafts.forEach { (k, v) -> if (k != key) next[k] = v }
    val result = ftutterBoundDrafts(next)
    return FtutterDraftsUpdate(result.encoded, truncated || result.truncated, result.dropped)
}

/** [drafts]（新しい順）を件数と全体の大きさの決まりに合わせる（1件ずつの長さは保存する時に決める）。 */
internal fun ftutterBoundDrafts(drafts: Map<String, FtutterDraft>): FtutterDraftsUpdate {
    val entries = drafts.entries.map { it.key to it.value }.toMutableList()
    var truncated = false
    var dropped = 0
    while (entries.size > FTUTTER_DRAFT_LIMIT) { entries.removeAt(entries.lastIndex); dropped++ }
    fun encode() = draftJson.encodeToString(draftsSerializer, LinkedHashMap<String, FtutterDraft>().apply { entries.forEach { put(it.first, it.second) } })
    var encoded = encode()
    while (encoded.length > FTUTTER_DRAFT_STORE_MAX_CHARS && entries.size > 1) {
        entries.removeAt(entries.lastIndex)
        dropped++
        encoded = encode()
    }
    // 1件だけでも収まらない（書き出すと長くなる文字ばかりの本文）：本文を半分ずつ縮める。
    while (encoded.length > FTUTTER_DRAFT_STORE_MAX_CHARS && entries.isNotEmpty()) {
        val (k, v) = entries[0]
        entries[0] = k to if (v.comment.isNotEmpty()) v.copy(comment = v.comment.ftutterTakeChars(v.comment.length / 2))
            else v.copy(sourceAttachmentPath = v.sourceAttachmentPath.ftutterTakeChars(v.sourceAttachmentPath.length / 2))
        truncated = true
        encoded = encode()
    }
    return FtutterDraftsUpdate(encoded, truncated, dropped)
}

internal fun FtutterDraft.boundedForStore(): FtutterDraft = FtutterDraft(
    title = title.ftutterTakeChars(FTUTTER_DRAFT_MAX_FIELD_CHARS),
    name = name.ftutterTakeChars(FTUTTER_DRAFT_MAX_FIELD_CHARS),
    mail = mail.ftutterTakeChars(FTUTTER_DRAFT_MAX_FIELD_CHARS),
    comment = comment.ftutterTakeChars(FTUTTER_DRAFT_MAX_COMMENT_CHARS),
    sourceAttachmentPath = sourceAttachmentPath.ftutterTakeChars(4096)
)

/** 先頭から[limit]字（絵文字などの2つで1文字のものを半分に切らない）。 */
private fun String.ftutterTakeChars(limit: Int): String {
    if (limit <= 0) return ""
    if (length <= limit) return this
    return if (this[limit - 1].isHighSurrogate() && this[limit].isLowSurrogate()) substring(0, limit - 1) else substring(0, limit)
}

internal const val FTUTTER_DRAFT_LIMIT = 20
internal const val FTUTTER_DRAFT_MAX_COMMENT_CHARS = 4_000
internal const val FTUTTER_DRAFT_MAX_FIELD_CHARS = 200
internal const val FTUTTER_DRAFT_STORE_MAX_CHARS = 18_000

/** 下書きを残す時に切った・古いものを落とした時の知らせ。何も無ければ null。 */
internal fun ftutterDraftsNotice(update: FtutterDraftsUpdate): String? = when {
    update.truncated -> "下書きが長いため、本文は先頭の${FTUTTER_DRAFT_MAX_COMMENT_CHARS}字だけ残しました"
    update.dropped > 0 -> "下書きを残せる量を超えたため、古い下書きを${update.dropped}件消しました"
    else -> null
}

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
 * 書き込み画面に出す理由（監査M6）。添付の誤りと、送信の検査・失敗の理由を別々に出す（以前は添付の誤りが1つあると、
 * 削除キーが空・本文が空・送信の失敗の理由が隠れ、押しても何も起きないように見えた）。
 */
internal fun ftutterPostErrors(vararg errors: String?): List<String> =
    errors.filterNotNull().filter { it.isNotBlank() }.distinct()

internal fun ftutterSourceAttachmentNotice(fileName: String, canAttach: Boolean): String =
    if (canAttach) "取り込み元の添付: $fileName\nファイル本体はバックアップに含まれません。添付ボタンから選び直してください。"
    else "取り込み元の添付: $fileName\nこの板の返信には画像を添付できないため、取り込み元の添付は使われません。"

/** 書き込む・閉じるを押せるか（監査M7：「あぷ小」のアップロード中は、ファイル名が本文に入る前に送る・閉じて放置するのを防ぐ）。 */
internal fun ftutterPostCanSend(sending: Boolean, attachmentProcessing: Boolean, uploading: Boolean): Boolean =
    !sending && !attachmentProcessing && !uploading

internal fun ftutterPostCanClose(sending: Boolean, uploading: Boolean): Boolean = !sending && !uploading

/** 打ち終わってから下書きを残すまでの間（監査M5。ふたばー風の下書きと同じ）。 */
internal const val FTUTTER_DRAFT_AUTOSAVE_DELAY_MS = 400L

/**
 * 書き込みのダイアログ（原作 KakikomiDialog）。スレッドが後ろに見える。閉じる時に下書きを保存し、送信に成功すると消す。
 * 打っている間も[FTUTTER_DRAFT_AUTOSAVE_DELAY_MS]ごとに残す（監査M5：原作は閉じる時だけで、閉じずに画面から外れる・アプリが
 * 終わると長文が消えた）。
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
    /** 下書きを残す。[announce]が真の時（閉じた時）だけ、切った・古いものを落とした知らせを出す。 */
    onSaveDraft: (draft: FtutterDraft, announce: Boolean) -> Unit,
    onSend: suspend (FtutterPostInput) -> Result<String?>,
    onSent: (String?) -> Unit,
    onDismiss: () -> Unit,
    handwritingEnabled: Boolean = false,
    /** 手書きのコピーを保存する。失敗した時はその文を返し、書き込み画面の中に出す（L22）。 */
    onHandwritingSaved: suspend (ImageData) -> String? = { null },
    /** 書いている手書きを残す場所。アプリと一緒に消えた手書きを、次に開いた時に復元できる（L69）。null なら残さない。 */
    drawingRecoveryFileSystem: com.valoser.futacha.shared.util.FileSystem? = null
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
    var sourceAttachmentPath by remember { mutableStateOf(initialDraft.sourceAttachmentPath) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var closeConfirmOpen by remember { mutableStateOf(false) }
    var deleteKeyOpen by remember { mutableStateOf(false) }
    var drawingOpen by remember { mutableStateOf(false) }
    var handwritingError by remember { mutableStateOf<String?>(null) }
    // この画面で変えた削除キーは、保存が届く前でもすぐ使う（監査L11：変えてすぐ送ると古いキーで送っていた）。
    var deleteKeyNow by remember(deleteKey) { mutableStateOf(deleteKey) }
    var upsBusy by remember { mutableStateOf(false) }
    val attachment = remember { FutaberAttachment(scope) }
    // 送信に成功した後は、遅れて動く自動保存が送った本文を下書きへ戻さない。
    var sent by remember { mutableStateOf(false) }
    fun draft() = FtutterDraft(title, name, mail, comment, sourceAttachmentPath)
    LaunchedEffect(attachment.image) { if (attachment.image != null) sourceAttachmentPath = "" }
    fun saveAndClose() {
        if (!ftutterPostCanClose(sending, upsBusy)) return
        onSaveDraft(draft(), true)
        attachment.clear()
        onDismiss()
    }
    fun close() {
        if (!ftutterPostCanClose(sending, upsBusy)) return
        // 下書きにファイル本体は残らない。戻る・枠外・閉じるのどこからでも、失う前に確認する（監査M32）。
        // 選んだ直後の処理中や、圧縮の返答待ちのファイルも対象にする。
        if (attachment.image != null || attachment.processing || attachment.pendingCompression != null) {
            closeConfirmOpen = true
        } else {
            saveAndClose()
        }
    }
    LaunchedEffect(Unit) {
        var saved = draft()
        snapshotFlow { draft() }.collectLatest { current ->
            if (current == saved) return@collectLatest
            delay(FTUTTER_DRAFT_AUTOSAVE_DELAY_MS)
            if (sending || sent) return@collectLatest
            saved = current
            onSaveDraft(current, false)
        }
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
                upload(picked, deleteKeyNow)
                    .onSuccess { file -> comment = com.valoser.futacha.shared.ui.compat.appendCompatPostText(comment, file) }
                    .onFailure { error = it.message ?: "アップロードできませんでした" }
                upsBusy = false
            }
        }
    )
    fun validateForSend(): Boolean {
        com.valoser.futacha.shared.ui.board.validateThreadReplyForm(deleteKeyNow, comment)?.let {
            error = it
            return false
        }
        return true
    }
    fun send() {
        if (!ftutterPostCanSend(sending, attachment.processing, upsBusy) || !validateForSend()) return
        // 拒否された添付の誤りは、添付が付いていないので送信には関わらない。押したら消して、今回の理由を見せる。
        attachment.clearError()
        sending = true
        error = null
        scope.launch {
            val result = try {
                onSend(FtutterPostInput(draft(), attachment.image, deleteKeyNow))
            } catch (cancelled: CancellationException) {
                sending = false
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            sending = false
            result.onSuccess { posted ->
                sent = true
                onSaveDraft(FtutterDraft(), false)
                onSent(posted)
            }.onFailure { error = it.message ?: "書き込みに失敗しました" }
        }
    }
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = ::close,
        properties = ftutterInputDialogProperties(),
        containerColor = colors.popUp,
        modifier = Modifier.then(if (com.valoser.futacha.shared.util.isAndroid()) Modifier.imePadding() else Modifier)
            .testTag("ftutter-post-dialog"),
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
                        if (handwritingEnabled) DropdownMenuItem(
                            text = { Text("手書き") },
                            enabled = ftutterPostCanSend(sending, attachment.processing, upsBusy),
                            onClick = { menu = false; drawingOpen = true },
                            modifier = Modifier.testTag("ftutter-post-handwriting")
                        )
                    }
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!imgB) {
                    com.valoser.futacha.shared.ui.board.ImeOutlinedTextField(title, { title = it }, label = { Text("題名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-post-title"))
                    com.valoser.futacha.shared.ui.board.ImeOutlinedTextField(name, { name = it }, label = { Text("おなまえ") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-post-name"))
                }
                com.valoser.futacha.shared.ui.board.ImeOutlinedTextField(mail, { mail = it }, label = { Text("E-mail") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ftutter-post-mail"))
                com.valoser.futacha.shared.ui.board.ImeOutlinedTextField(comment, { comment = it }, label = { Text("コメント") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("ftutter-post-comment"))
                if (!(imgB && isReply) || attachment.image?.isHandwriting == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!(imgB && isReply)) IconButton(onClick = pick, modifier = Modifier.testTag("ftutter-post-attach")) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = "添付", tint = colors.text)
                        }
                        Text(
                            attachment.image?.fileName ?: if (attachment.processing) "準備中…" else "添付なし",
                            color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (attachment.image != null) {
                            IconButton(onClick = { attachment.clear(); handwritingError = null }, modifier = Modifier.testTag("ftutter-post-detach")) {
                                Icon(Icons.Rounded.Close, contentDescription = "添付を外す", tint = colors.text)
                            }
                        }
                        if (uploadUps != null && !(imgB && isReply)) {
                            TextButton(onClick = pickUps, enabled = !upsBusy, modifier = Modifier.testTag("ftutter-post-ups")) {
                                Text(if (upsBusy) "アップロード中…" else "あぷ小")
                            }
                        }
                    }
                }
                // 取り込み元の添付の案内は1つだけ。この板の返信のように添付できない時は、選び直せとは言わず消せるようにする（L15）。
                if (sourceAttachmentPath.isNotEmpty()) {
                    val canAttach = !(imgB && isReply)
                    Text(ftutterSourceAttachmentNotice(sourceAttachmentPath.substringAfterLast('/'), canAttach),
                        color = colors.text, modifier = Modifier.testTag("ftutter-draft-attachment-reselect"))
                    if (!canAttach) TextButton(onClick = { sourceAttachmentPath = "" },
                        modifier = Modifier.testTag("ftutter-draft-attachment-dismiss")) { Text("案内を消す") }
                }
                if (!(imgB && isReply)) {
                    // 他のアプリから共有された画像（監査M12：他の3モードと同じく、ここで添付するか破棄する。以前はこのモードに
                    // ボタンが無く、案内だけ出て添付できず、画像がメモリに残った）。
                    com.valoser.futacha.shared.ui.board.IncomingSharedAttachmentButton(enabled = !sending) {
                        attachment.offer(it, capabilities, isReply)
                    }
                }
                ftutterPostErrors(attachment.error, handwritingError, error).forEach {
                    Text(it, color = colors.deleted, modifier = Modifier.testTag("ftutter-post-error"))
                }
                if (attachment.processing || upsBusy) Text(
                    if (upsBusy) "アップロードが終わると書き込めます" else "添付ファイルの準備が終わると書き込めます",
                    color = colors.text, modifier = Modifier.testTag("ftutter-post-busy-reason"))
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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                if (deleteKeyNow.isBlank()) {
                    Text("投稿には削除キーの設定が必要です", color = colors.text, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().testTag("ftutter-post-key-required"))
                }
                // Keep the actions on one row so the editor still has room above a software keyboard.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (deleteKeyNow.isBlank()) {
                        TextButton(onClick = { deleteKeyOpen = true }, enabled = !sending,
                            modifier = Modifier.weight(1f).testTag("ftutter-post-set-delete-key")) { Text("削除キーを設定") }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (sending) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    }
                    TextButton(
                        onClick = {
                            if (validateForSend()) {
                                if (confirmBeforePost) confirmOpen = true else send()
                            }
                        },
                        enabled = ftutterPostCanSend(sending, attachment.processing, upsBusy),
                        modifier = Modifier.testTag("ftutter-post-send")
                    ) { Text("書き込む") }
                    TextButton(onClick = ::close, enabled = ftutterPostCanClose(sending, upsBusy)) { Text("閉じる") }
                }
            }
        }
    ) }
    if (drawingOpen) FutachaAppLockAwareWindow {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // The shared drawing toolbar uses white labels, including in light themes.
            val palette = ftutterMediaPalette(colors).let {
                if (it.chrome.luminance() <= 0.1833f) it else it.copy(chrome = Color(0xFF800000))
            }
            CompositionLocalProvider(com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides palette) {
                Surface(Modifier.fillMaxSize()) {
                    com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen(
                        onSaved = { image ->
                            attachment.offer(image, capabilities, isReply)
                            drawingOpen = false
                            handwritingError = null
                            // The copy's result comes later; the drawing screen does not wait for it (L39).
                            scope.launch { onHandwritingSaved(image)?.let { handwritingError = it } }
                        },
                        onBack = { drawingOpen = false },
                        // 参照ヘルプの「手書き」の節だけを出す（L70。としあき(仮)のヘルプ全体を開いていた）。
                        helpSectionId = com.valoser.futacha.shared.ui.compat.COMPAT_DRAWING_HELP_SECTION,
                        recoveryFileSystem = drawingRecoveryFileSystem
                    )
                }
            }
        }
    }
    if (confirmOpen) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("この内容で書き込みますか？") },
            confirmButton = { TextButton(onClick = { confirmOpen = false; send() }, modifier = Modifier.testTag("ftutter-post-confirm")) { Text("OK") } },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("キャンセル") } }
        ) }
    }
    if (closeConfirmOpen) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { closeConfirmOpen = false },
            title = { Text("添付ファイルを破棄して閉じますか？") },
            text = { Text("添付ファイルは下書きに残りません。文字の下書きは保存します。") },
            confirmButton = {
                TextButton(onClick = {
                    closeConfirmOpen = false
                    saveAndClose()
                }) { Text("破棄して閉じる") }
            },
            dismissButton = { TextButton(onClick = { closeConfirmOpen = false }) { Text("キャンセル") } }
        ) }
    }
    if (clearOpen) {
        FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("書き込み内容をクリアしますか？") },
            confirmButton = {
                TextButton(onClick = {
                    clearOpen = false
                    title = ""; name = ""; mail = ""; comment = ""; sourceAttachmentPath = ""
                    attachment.clear()
                }, modifier = Modifier.testTag("ftutter-post-clear-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("キャンセル") } }
        ) }
    }
    if (deleteKeyOpen) {
        FtutterDeleteKeyDialog(deleteKeyNow, onDismiss = { deleteKeyOpen = false }) { key ->
            deleteKeyOpen = false
            deleteKeyNow = key
            error = null
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
        properties = ftutterInputDialogProperties(),
        modifier = if (com.valoser.futacha.shared.util.isAndroid()) Modifier.imePadding() else Modifier,
        title = { Text("削除キー") },
        text = {
            Column {
                // 英数字のパスワード用キーボード（監査L10：日本語のキーボードの変換中の文字を捨てて文字が重なり、学習辞書にも残った）。
                OutlinedTextField(value, { value = ftutterNormalizeDeleteKey(it); error = null }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
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
