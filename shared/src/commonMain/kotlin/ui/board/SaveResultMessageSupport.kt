package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SaveStatus

internal fun savedThreadCompletionSummary(saved: SavedThread): String = buildString {
    append(if (saved.status == SaveStatus.PARTIAL || saved.isHtmlMissing || saved.incompleteMediaCount > 0) {
        "一部を保存できませんでした"
    } else "スレッドを保存しました")
    if (saved.isHtmlMissing) append("\nHTMLを保存できませんでした。本文データは保存されています。")
    if (saved.incompleteMediaCount > 0) append("\n${saved.incompleteMediaCount}件の画像・動画を保存できませんでした。")
    if (saved.status == SaveStatus.PARTIAL && !saved.isHtmlMissing && saved.incompleteMediaCount == 0) {
        append("\n本文の一部が省略されています。")
    }
}
