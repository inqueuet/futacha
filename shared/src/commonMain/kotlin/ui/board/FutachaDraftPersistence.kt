package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.ui.compat.deleteCompatPostAttachment
import com.valoser.futacha.shared.ui.compat.loadCompatPostAttachment
import com.valoser.futacha.shared.ui.compat.persistCompatPostAttachment
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

/*
 * ふたちゃの「下書きを端末に保存」（機能そろえ バッチ4、承認済み：設定のスイッチ・既定OFF）。
 *
 * 返信・スレ立てのフォームは、これまで画面の状態（remember／rememberSaveable）だけに置かれていて、スレッドや
 * カタログを離れると消えた。スイッチがONの時だけ、としあき(仮)と同じ共有ストアの下書き（返信は compatTabKey、
 * スレ立ては compatBoardKey）と、非公開ファイルの添付（persistCompatPostAttachment）へ書く。
 *
 * - 書くのは、そのキーのタブ（返信）・板（スレ立て）が共有ストアにある時だけ。Androidの下書きの表はタブ・板への
 *   外部キーを持ち、無いと書けない。無い間（タブを作る前、静的表示、公式でない板）は従来どおり画面の中だけ。
 * - 「空」は、題名・本文・添付がどれも無いこと。自動で入る削除キーや、送信後に残る名前・メールだけなら空とみなし、
 *   開いただけで書かない。空になったら（送信成功・クリア）記録と添付ファイルを消す。
 * - 削除キーは下書きに書かない（データ契約 §4.3 の安全面）。としあき(仮)が書いた削除キーはそのまま残す。
 * - 読み込みに失敗したら、その画面の間は消さない（空で上書きしない）。
 * - 書き込みは1つの列で順に行う。デバウンスは1.5秒（iOSの共有ストアは状態全体を書くため）で、フォームを閉じた時と
 *   画面を離れた時はすぐ書く。
 * - 同じスレッド・板の下書きは、としあき(仮)の書き込み画面にも出る（同じ記録のため）。
 */

private const val FUTACHA_DRAFT_TAG = "FutachaDraft"
internal const val FUTACHA_DRAFT_DEBOUNCE_MILLIS = 1_500L
internal const val FUTACHA_DRAFT_LOAD_FAILED_NOTICE = "下書きを読み込めませんでした"
internal const val FUTACHA_DRAFT_ATTACHMENT_REPLACED_NOTICE = "保存していた添付は、新しく選んだ添付に置き換えました"
internal const val FUTACHA_DRAFT_ATTACHMENT_LOST_NOTICE = "下書きの添付ファイルを読み込めなかったため、添付が外れました"

/** The fields of a reply or a new-thread form that the draft keeps (the delete key is not kept). */
internal data class FutachaDraftFields(
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val comment: String = "",
    val image: ImageData? = null
)

/** Only a subject, a comment or an attachment makes a draft; a name, a mail or an autofilled delete key alone does not. */
internal fun FutachaDraftFields.hasDraftContent(): Boolean =
    subject.isNotBlank() || comment.isNotBlank() || image != null

/** The stored draft with what was typed before it was read: the typed text after the stored one, empty fields filled. */
internal fun mergeFutachaDraftFields(stored: FutachaDraftFields, typed: FutachaDraftFields): FutachaDraftFields {
    // Typed text that already starts with the stored one is taken as is (re-audit L31: a form restored after the process ended holds the
    // stored text plus what was typed, and joining them doubled it, "abc" and "abcd" -> "abc\nabcd").
    fun join(a: String, b: String) = when {
        a.isBlank() -> b
        b.isBlank() || b == a -> a
        b.startsWith(a) -> b
        else -> a.trimEnd('\n') + "\n" + b
    }
    return FutachaDraftFields(
        name = typed.name.ifEmpty { stored.name },
        email = typed.email.ifEmpty { stored.email },
        subject = typed.subject.ifBlank { stored.subject },
        comment = join(stored.comment, typed.comment),
        image = typed.image ?: stored.image
    )
}

/** Where a draft is kept: a thread tab's reply draft, or a board's new-thread draft. */
internal sealed interface FutachaDraftOwner {
    val key: String
    data class Reply(override val key: String) : FutachaDraftOwner
    data class Build(override val key: String) : FutachaDraftOwner
}

/**
 * What one screen knows about its stored draft: the attachment file the record points at, and the picture it holds
 * (null = not known, e.g. a file としあき(仮) wrote that could not be read). Written only by the tasks of [FutachaDraftWriter].
 */
internal class FutachaDraftSession {
    var locator: String? = null
    var image: ImageData? = null
    /** The delete key another mode wrote into the record; kept as it was. */
    var storedDeleteKey: String = ""
    /** False after a failed read: nothing is deleted for this screen then. */
    var allowDelete: Boolean = true
    /** Main thread only: whether the form has been compared with the record. */
    var loaded: Boolean = false
    /** What was last handed to the writer; cleared by the writer when that could not be written for want of a tab / board. */
    @kotlin.concurrent.Volatile
    var lastEnqueued: FutachaDraftFields? = null
}

internal data class FutachaDraftRestore(val fields: FutachaDraftFields, val attachmentLost: Boolean)

/** [NoOwner]: the tab / board is not in the store yet, so the form stays in memory only (and is tried again later). */
internal enum class FutachaDraftWrite { Saved, Deleted, Skipped, NoOwner }

private data class StoredFutachaDraft(
    val name: String, val email: String, val subject: String, val comment: String,
    val attachmentUri: String?, val attachmentIsHandwriting: Boolean, val deleteKey: String
)

private suspend fun readStoredDraft(store: CompatibilityStore, owner: FutachaDraftOwner): StoredFutachaDraft? = when (owner) {
    is FutachaDraftOwner.Reply -> store.loadDraft(owner.key)?.let {
        StoredFutachaDraft(it.name, it.email, it.subject, it.comment, it.attachmentUri, it.attachmentIsHandwriting, it.deleteKey)
    }
    is FutachaDraftOwner.Build -> store.loadBuildDraft(owner.key)?.let {
        StoredFutachaDraft(it.name, it.email, it.subject, it.comment, it.attachmentUri, it.attachmentIsHandwriting, it.deleteKey)
    }
}

/** Reads the stored draft (and its attachment) into [session]; null when nothing worth restoring is stored. */
internal suspend fun loadFutachaDraft(
    store: CompatibilityStore,
    fileSystem: FileSystem?,
    owner: FutachaDraftOwner,
    session: FutachaDraftSession
): FutachaDraftRestore? {
    val stored = readStoredDraft(store, owner) ?: return null
    session.locator = stored.attachmentUri
    session.storedDeleteKey = stored.deleteKey
    val image = stored.attachmentUri?.let { locator ->
        fileSystem?.let { fs -> loadCompatPostAttachment(fs, locator).getOrNull()?.copy(isHandwriting = stored.attachmentIsHandwriting) }
    }
    session.image = image
    val fields = FutachaDraftFields(stored.name, stored.email, stored.subject, stored.comment, image)
    val lost = stored.attachmentUri != null && image == null
    return if (fields.hasDraftContent() || lost) FutachaDraftRestore(fields, lost) else null
}

private suspend fun ownerExists(store: CompatibilityStore, owner: FutachaDraftOwner): Boolean = when (owner) {
    is FutachaDraftOwner.Reply -> store.tabs.first().any { it.key == owner.key }
    is FutachaDraftOwner.Build -> store.boards.first().any { it.key == owner.key }
}

private suspend fun deleteAttachmentFile(fileSystem: FileSystem?, locator: String?) {
    if (fileSystem == null || locator == null) return
    deleteCompatPostAttachment(fileSystem, locator, deleteContainer = true).onFailure {
        Logger.e(FUTACHA_DRAFT_TAG, "Failed to delete a draft attachment", it)
    }
}

/**
 * Writes [fields] as the draft of [owner]: nothing when the tab / board is not in the store (the form stays in memory
 * only), the record and its file are deleted when the fields are empty, else the record is saved with the attachment
 * written once per picture. The old file goes after the record no longer points at it.
 */
internal suspend fun persistFutachaDraft(
    store: CompatibilityStore,
    fileSystem: FileSystem?,
    owner: FutachaDraftOwner,
    fields: FutachaDraftFields,
    session: FutachaDraftSession,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): FutachaDraftWrite {
    if (!ownerExists(store, owner)) return FutachaDraftWrite.NoOwner
    if (!fields.hasDraftContent()) {
        if (!session.allowDelete) return FutachaDraftWrite.Skipped
        when (owner) {
            is FutachaDraftOwner.Reply -> store.deleteDraft(owner.key)
            is FutachaDraftOwner.Build -> store.deleteBuildDraft(owner.key)
        }
        val old = session.locator
        session.locator = null
        session.image = null
        deleteAttachmentFile(fileSystem, old)
        return FutachaDraftWrite.Deleted
    }
    // The delete key as stored now (としあき(仮) writes it): a screen whose read failed knew none and wrote it back empty
    // (audit L31). Unreadable now too: nothing is written, so it is not lost.
    val deleteKey = try {
        readStoredDraft(store, owner)?.deleteKey ?: session.storedDeleteKey
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        if (!session.allowDelete) return FutachaDraftWrite.Skipped
        session.storedDeleteKey
    }
    session.storedDeleteKey = deleteKey
    val image = fields.image
    var written: String? = null
    val locator = when {
        image == null -> null
        image === session.image && session.locator != null -> session.locator
        fileSystem != null -> persistCompatPostAttachment(fileSystem, owner.key, image).getOrThrow().also { written = it }
        else -> null
    }
    try {
        when (owner) {
            is FutachaDraftOwner.Reply -> store.saveDraft(
                CompatReplyDraft(
                    tabKey = owner.key, name = fields.name, email = fields.email, subject = fields.subject,
                    comment = fields.comment, attachmentUri = locator,
                    attachmentIsHandwriting = locator != null && image?.isHandwriting == true,
                    deleteKey = deleteKey, updatedAtEpochMillis = nowEpochMillis
                )
            )
            is FutachaDraftOwner.Build -> store.saveBuildDraft(
                CompatBuildDraft(
                    boardKey = owner.key, name = fields.name, email = fields.email, subject = fields.subject,
                    comment = fields.comment, attachmentUri = locator,
                    attachmentIsHandwriting = locator != null && image?.isHandwriting == true,
                    deleteKey = deleteKey, updatedAtEpochMillis = nowEpochMillis
                )
            )
        }
    } catch (failure: Throwable) {
        deleteAttachmentFile(fileSystem, written)
        throw failure
    }
    val old = session.locator
    session.locator = locator
    session.image = if (locator != null) image else null
    if (old != null && old != locator) deleteAttachmentFile(fileSystem, old)
    return FutachaDraftWrite.Saved
}

/**
 * One queue for every draft read and write, so a write handed over when a screen closes finishes before the screen
 * opened next reads the same record, and two writes of one draft never cross. The queue outlives the screens.
 */
internal object FutachaDraftWriter {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val tasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (task in tasks) {
                try {
                    task()
                } catch (cancelled: CancellationException) {
                    Logger.e(FUTACHA_DRAFT_TAG, "A draft task was cancelled", cancelled)
                } catch (failure: Throwable) {
                    Logger.e(FUTACHA_DRAFT_TAG, "A draft task failed", failure)
                }
            }
        }
    }

    fun enqueue(task: suspend () -> Unit) {
        tasks.trySend(task)
    }

    suspend fun <T> run(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        enqueue {
            try {
                result.complete(block())
            } catch (failure: Throwable) {
                result.completeExceptionally(failure)
            }
        }
        return result.await()
    }
}

/** How the persistence reads and fills one form. */
internal class FutachaDraftFormBinding(
    val read: () -> FutachaDraftFields,
    val isOpen: () -> Boolean,
    /** Fills the form with a restored draft (the delete key is left as it is). */
    val restore: (FutachaDraftFields) -> Unit,
    /**
     * True while the form's text is being sent (audit M19). The stored draft is removed when sending starts and the text is
     * not written while it is in flight, so leaving the screen or the app ending mid-send does not keep the sent text as a
     * draft (it used to come back on the next opening, inviting a second post). If the send fails the text is written again.
     */
    val isSubmitting: () -> Boolean = { false }
)

/** The reply form of the thread screen. A restored draft keeps a name / mail already in the form. */
internal fun futachaReplyDraftFormBinding(
    binding: ThreadReplyDialogStateBinding,
    isSubmitting: () -> Boolean = { false }
): FutachaDraftFormBinding =
    FutachaDraftFormBinding(
        isSubmitting = isSubmitting,
        read = {
            val draft = binding.currentState().draft
            FutachaDraftFields(draft.name, draft.email, draft.subject, draft.comment, draft.imageData)
        },
        isOpen = { binding.currentState().isVisible },
        restore = { stored ->
            val state = binding.currentState()
            binding.setState(
                state.copy(
                    draft = state.draft.copy(
                        name = state.draft.name.ifEmpty { stored.name },
                        email = state.draft.email.ifEmpty { stored.email },
                        subject = stored.subject,
                        comment = stored.comment,
                        imageData = stored.image
                    )
                )
            )
        }
    )

/** The new-thread form of the catalog (its draft and its picture are kept apart by the screen). */
internal fun futachaCreateThreadDraftFormBinding(
    currentDraft: () -> CreateThreadDraft,
    currentImage: () -> ImageData?,
    setDraft: (CreateThreadDraft) -> Unit,
    setImage: (ImageData?) -> Unit,
    isOpen: () -> Boolean,
    isSubmitting: () -> Boolean = { false }
): FutachaDraftFormBinding = FutachaDraftFormBinding(
    isSubmitting = isSubmitting,
    read = {
        val draft = currentDraft()
        FutachaDraftFields(draft.name, draft.email, draft.title, draft.comment, currentImage())
    },
    isOpen = isOpen,
    restore = { stored ->
        val draft = currentDraft()
        setDraft(
            draft.copy(
                name = draft.name.ifEmpty { stored.name },
                email = draft.email.ifEmpty { stored.email },
                title = stored.subject,
                comment = stored.comment
            )
        )
        setImage(stored.image)
    }
)

/**
 * Keeps one form's draft in the shared store while [enabled]: restores it when the screen opens (only into an empty
 * form), writes it 1.5 s after the last edit, at once when the form closes, and when the screen leaves.
 */
@Composable
internal fun FutachaDraftPersistenceEffect(
    store: CompatibilityStore,
    fileSystem: FileSystem?,
    owner: FutachaDraftOwner,
    enabled: Boolean,
    form: FutachaDraftFormBinding,
    onMessage: (String) -> Unit
) {
    // The form is read through the binding made with this owner: when the owner changes (another board's catalog), the
    // binding of the new composition reads the new board's form, which must not be written as the old board's draft.
    val formOfOwner = remember(owner, store) { form }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestMessage by rememberUpdatedState(onMessage)
    val session = remember(owner, store) { FutachaDraftSession() }
    fun enqueueWrite(fields: FutachaDraftFields) {
        if (!session.loaded || fields == session.lastEnqueued) return
        session.lastEnqueued = fields
        FutachaDraftWriter.enqueue {
            try {
                if (persistFutachaDraft(store, fileSystem, owner, fields, session) == FutachaDraftWrite.NoOwner &&
                    session.lastEnqueued == fields
                ) {
                    // Typed before the screen made the tab: the next edit or leaving the screen tries again.
                    session.lastEnqueued = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e(FUTACHA_DRAFT_TAG, "Failed to keep the draft", failure)
                latestMessage("下書きを保存できませんでした")
            }
        }
    }
    LaunchedEffect(owner, store, enabled) {
        session.loaded = false
        if (!enabled) return@LaunchedEffect
        val restored = try {
            FutachaDraftWriter.run { loadFutachaDraft(store, fileSystem, owner, session) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e(FUTACHA_DRAFT_TAG, "Failed to read the draft", failure)
            session.allowDelete = false
            latestMessage(FUTACHA_DRAFT_LOAD_FAILED_NOTICE)
            null
        }
        val current = formOfOwner.read()
        if (restored != null && current.hasDraftContent()) {
            // Typed while the stored draft was still being read (audit L31: the stored text was then overwritten 1.5 s
            // later). Both are kept: what was typed goes after the stored text, an empty field takes the stored value.
            formOfOwner.restore(mergeFutachaDraftFields(restored.fields, current))
            session.lastEnqueued = null
            // Told here too (re-audit L31): the stored picture could not be read, or the one chosen since replaced it.
            when {
                restored.attachmentLost -> latestMessage(FUTACHA_DRAFT_ATTACHMENT_LOST_NOTICE)
                restored.fields.image != null && current.image != null -> latestMessage(FUTACHA_DRAFT_ATTACHMENT_REPLACED_NOTICE)
            }
        } else if (restored != null) {
            formOfOwner.restore(restored.fields)
            if (restored.attachmentLost) latestMessage(FUTACHA_DRAFT_ATTACHMENT_LOST_NOTICE)
            session.lastEnqueued = formOfOwner.read()
        } else if (!current.hasDraftContent()) {
            // Nothing stored and nothing typed: opening the screen writes nothing.
            session.lastEnqueued = current
        }
        session.loaded = true
        var wasOpen = formOfOwner.isOpen()
        snapshotFlow { Triple(formOfOwner.read(), formOfOwner.isOpen(), formOfOwner.isSubmitting()) }.collectLatest { (fields, open, submitting) ->
            val closed = wasOpen && !open
            wasOpen = open
            if (submitting) {
                // Being sent: the stored draft goes now (audit M19); a failed send writes the text again when it ends.
                enqueueWrite(FutachaDraftFields())
                return@collectLatest
            }
            if (!closed) delay(FUTACHA_DRAFT_DEBOUNCE_MILLIS)
            enqueueWrite(fields)
        }
    }
    DisposableEffect(owner, store) {
        onDispose {
            if (latestEnabled && session.loaded && !formOfOwner.isSubmitting()) enqueueWrite(formOfOwner.read())
            session.loaded = false
        }
    }
}
