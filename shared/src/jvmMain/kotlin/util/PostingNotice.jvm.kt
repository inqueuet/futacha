package com.valoser.futacha.shared.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JOptionPane

suspend actual fun confirmPostingNotice(): Boolean = withContext(Dispatchers.Main) {
    JOptionPane.showConfirmDialog(null, "投稿内容は掲示板に公開されます。内容を確認してから送信してください。", "投稿について", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
}
