package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation

internal fun isThreadSaveLocationPermissionIssue(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return message.contains("cannot resolve tree uri") ||
        message.contains("write permission lost for tree uri") ||
        message.contains("please select the folder again") ||
        message.contains("invalid bookmark data") ||
        message.contains("failed to resolve bookmark") ||
        message.contains("bookmark url has no filesystem path")
}

internal fun requiresThreadManualSaveLocationSelection(
    isAndroidPlatform: Boolean,
    manualSaveLocation: SaveLocation?
): Boolean {
    return isAndroidPlatform &&
        manualSaveLocation !is SaveLocation.TreeUri &&
        manualSaveLocation !is SaveLocation.Path
}

internal fun buildThreadInitialLoadErrorMessage(error: Throwable, statusCode: Int?): String {
    val message = error.message
    return when {
        message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
        statusCode == 404 -> "スレッドが見つかりません (404)"
        statusCode == 410 -> "スレッドは削除済みです (410)"
        statusCode != null && statusCode >= 500 -> "サーバーエラー ($statusCode)"
        message?.contains("HTTP error") == true -> "ネットワークエラー: $message"
        message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
        else -> "スレッドを読み込めませんでした: ${message ?: "不明なエラー"}"
    }
}

internal fun buildThreadRefreshSuccessMessage(usedOffline: Boolean): String {
    return if (usedOffline) {
        "ネットワーク接続不可: ローカルコピーを表示しています"
    } else {
        "スレッドを更新しました"
    }
}

internal fun buildThreadRefreshFailureMessage(error: Throwable, statusCode: Int?): String {
    return when (statusCode) {
        404 -> "更新に失敗しました: スレッドが見つかりません (404)"
        410 -> "更新に失敗しました: スレッドは削除済みです (410)"
        else -> "更新に失敗しました: ${error.message ?: "不明なエラー"}"
    }
}

internal fun buildThreadSaveBusyMessage(): String = "保存処理を実行中です…"

internal fun buildThreadSaveLocationRequiredMessage(): String {
    return "保存先が未選択です。設定からフォルダを選択してください。"
}

internal fun buildThreadSaveUnavailableMessage(): String = "保存機能が利用できません"

internal fun buildThreadSaveNotReadyMessage(): String = "スレッドの読み込みが完了していません"

internal fun buildThreadSavePermissionLostMessage(): String {
    return "保存先の権限が失われました。フォルダを再選択してください。"
}

internal fun buildThreadSaveFailureMessage(error: Throwable): String {
    return "保存に失敗しました: ${error.message}"
}

internal fun buildThreadSaveUnexpectedErrorMessage(error: Throwable): String {
    return "エラーが発生しました: ${error.message}"
}

internal fun buildThreadActionFailureMessage(failurePrefix: String, error: Throwable): String {
    val detail = error.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    return "$failurePrefix$detail"
}

internal fun buildSelfSaidaneBlockedMessage(): String = "自分のレスにはそうだねできません"

internal fun buildMissingPosterIdMessage(): String = "IDが見つかりませんでした"

internal fun buildDeletePasswordRequiredMessage(): String = "削除キーを入力してください"

internal fun buildReplyCommentRequiredMessage(): String = "コメントを入力してください"

internal fun validateThreadDeletePassword(password: String): String? {
    return if (password.trim().isBlank()) {
        buildDeletePasswordRequiredMessage()
    } else {
        null
    }
}

internal fun validateThreadReplyForm(password: String, comment: String): String? {
    if (password.trim().isBlank()) {
        return buildDeletePasswordRequiredMessage()
    }
    if (comment.trim().isBlank()) {
        return buildReplyCommentRequiredMessage()
    }
    return null
}

internal fun buildReadAloudNoTargetMessage(): String = "読み上げ対象がありません"

internal fun buildReadAloudPausedMessage(): String = "読み上げを一時停止しました"

internal fun buildReadAloudCompletedMessage(): String = "読み上げを完了しました"

internal fun buildReadAloudStoppedMessage(): String = "読み上げを停止しました"

internal fun buildReadAloudFailureMessage(error: Throwable): String {
    return "読み上げ中にエラーが発生しました: ${error.message ?: "不明なエラー"}"
}

internal fun nextThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex + 1 >= matchCount) 0 else currentIndex + 1
}

internal fun previousThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex - 1 < 0) matchCount - 1 else currentIndex - 1
}
