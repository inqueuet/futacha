package com.valoser.futacha.shared.ui.futaber

/**
 * Whether a left-edge swipe may open the board list. It may not while anything else is on top of the thread: the swipe
 * would open the list under a writing screen or a sheet, and the next Back would then close the wrong thing.
 */
internal fun futaberDrawerGestureAllowed(
    drawerOpen: Boolean,
    managePanelOpen: Boolean,
    settingsOpen: Boolean,
    writing: Boolean,
    tabSheetOpen: Boolean,
    dialogOpen: Boolean
): Boolean = !(drawerOpen || managePanelOpen || settingsOpen || writing || tabSheetOpen || dialogOpen)

/** What Back does on a thread screen: close the board list when it is open (it is on top), else leave the thread. */
internal enum class FutaberThreadBack { CloseDrawer, LeaveThread }

internal fun futaberThreadBack(drawerOpen: Boolean): FutaberThreadBack =
    if (drawerOpen) FutaberThreadBack.CloseDrawer else FutaberThreadBack.LeaveThread

/** The subject and the text a write screen opens with. */
internal data class FutaberPostStart(val subject: String, val comment: String)

/**
 * What the write screen starts from: the text still in the screen when the same target is already being written (a
 * quote-mode round trip keeps the target, and the 400 ms that saves the draft may not have run yet), else the saved draft.
 */
internal fun futaberPostStart(
    draft: FutaberDraft?,
    target: FutaberPostTarget,
    currentTarget: FutaberPostTarget?,
    currentSubject: String,
    currentComment: String
): FutaberPostStart =
    if (currentTarget != null && currentTarget.draftKey == target.draftKey) FutaberPostStart(currentSubject, currentComment)
    else FutaberPostStart(draft?.subject.orEmpty(), draft?.comment.orEmpty())

/** How long a notice stays before it goes away by itself. */
internal const val FUTABER_NOTICE_MILLIS = 4_000L

/** Told when the screen was rebuilt while a file was attached: the file's bytes are not kept across that. */
internal const val FUTABER_ATTACHMENT_LOST_NOTICE = "画面が作り直されたため、添付したファイルが外れました。もう一度選んでください"

/** Told when a post went out but this device could not note it (the post itself is not undone). */
internal const val FUTABER_POST_NOTE_FAILED_NOTICE = "書き込みは送信されましたが、端末に記録できませんでした"

/** Whether the "file got lost" notice is due: the flag said a file was attached, and a post is being written without one. */
internal fun futaberAttachmentLostNoticeDue(wasAttached: Boolean, writing: Boolean, hasAttachment: Boolean): Boolean =
    wasAttached && writing && !hasAttachment

/** Told when a history row was deleted but its auto-saved copy of the thread could not be removed. */
internal const val FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE = "履歴は削除しましたが、自動保存した本文を削除できませんでした"
