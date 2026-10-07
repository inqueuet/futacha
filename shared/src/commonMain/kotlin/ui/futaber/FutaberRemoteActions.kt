package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow

/**
 * The actions of the post sheet that send something to the board. The original app lists them
 * after the quote and copy entries: そうだね, delete my post, and report it to the board's staff.
 */
internal enum class FutaberRemoteAction(val id: String, val sheetLabel: String) {
    Saidane("saidane", "そうだね"),
    DeleteOwn("delete-own", "レス削除"),
    Report("report", "不適切な投稿を通報");

    /** What is asked before sending; null for the one that sends at once. */
    val confirmTitle: String?
        get() = when (this) {
            Saidane -> null
            DeleteOwn -> "このレスを削除しますか？"
            Report -> "不適切な投稿として通報しますか？"
        }

    val confirmBody: String
        get() = when (this) {
            Saidane -> ""
            DeleteOwn -> "書き込み設定の削除キーで、自分のレスを削除します。削除キーが違うと削除できません。"
            Report -> "掲示板の管理側へ削除依頼（DEL）を送ります。"
        }

    val confirmLabel: String
        get() = when (this) {
            Saidane -> "そうだね"
            DeleteOwn -> "削除する"
            Report -> "通報する"
        }

    val successMessage: String
        get() = when (this) {
            Saidane -> "そうだねを送信しました"
            DeleteOwn -> "レスを削除しました"
            Report -> "削除依頼を送信しました"
        }

    val failurePrefix: String
        get() = when (this) {
            Saidane -> "そうだねを送信できませんでした"
            DeleteOwn -> "レスを削除できませんでした"
            Report -> "削除依頼を送信できませんでした"
        }
}

/** Why an action cannot run now, or null. A saved copy sends nothing; deleting needs the delete key. */
internal fun futaberRemoteActionBlockedReason(action: FutaberRemoteAction, offline: Boolean, deleteKey: String): String? = when {
    offline -> "保存したコピーでは使えません"
    action == FutaberRemoteAction.DeleteOwn && deleteKey.isBlank() -> "設定の「書き込み設定」で削除キーを入れてください"
    else -> null
}

@Composable
internal fun FutaberRemoteActionDialog(
    action: FutaberRemoteAction,
    postNumber: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFutaberColors.current
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text(action.confirmTitle.orEmpty()) },
            text = { Text("No.$postNumber\n${action.confirmBody}") },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag("futaber-remote-confirm")) {
                    Text(action.confirmLabel, color = colors.accent)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.link) } }
        )
    }
}
