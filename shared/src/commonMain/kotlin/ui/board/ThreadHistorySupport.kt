package com.valoser.futacha.shared.ui.board

internal enum class ThreadHistoryRefreshAvailability {
    Busy,
    Ready
}

internal fun resolveThreadHistoryRefreshAvailability(
    isHistoryRefreshing: Boolean
): ThreadHistoryRefreshAvailability {
    return if (isHistoryRefreshing) {
        ThreadHistoryRefreshAvailability.Busy
    } else {
        ThreadHistoryRefreshAvailability.Ready
    }
}

internal fun buildThreadHistoryRefreshSuccessMessage(): String = "履歴を更新しました"

internal fun buildThreadHistoryRefreshAlreadyRunningMessage(): String = "履歴更新はすでに実行中です"

internal fun buildThreadHistoryRefreshFailureMessage(error: Throwable): String {
    return "履歴の更新に失敗しました: ${error.message ?: "不明なエラー"}"
}

internal fun buildThreadHistoryBatchDeleteMessage(): String = "履歴を一括削除しました"
