package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.BoardUrlResolver

internal fun nextCatalogRequestGeneration(currentGeneration: Long): Long {
    return currentGeneration + 1L
}

internal fun shouldApplyCatalogRequestResult(
    isActive: Boolean,
    currentGeneration: Long,
    requestGeneration: Long
): Boolean {
    return isActive && currentGeneration == requestGeneration
}

internal fun shouldFinalizeCatalogRefresh(
    isSameRunningJob: Boolean,
    currentGeneration: Long,
    requestGeneration: Long
): Boolean {
    return isSameRunningJob && currentGeneration == requestGeneration
}

internal fun buildCatalogRefreshFailureMessage(): String {
    return "更新に失敗しました"
}

internal fun canSubmitCreateThread(title: String, comment: String): Boolean {
    return title.isNotBlank() || comment.isNotBlank()
}

internal fun buildCreateThreadBoardMissingMessage(): String {
    return "板が選択されていません"
}

internal fun buildCreateThreadSuccessMessage(threadId: String?): String {
    return if (threadId.isNullOrBlank()) {
        "スレッドを作成しました。カタログ更新で確認してください"
    } else {
        "スレッドを作成しました (ID: $threadId)"
    }
}

internal fun buildCreateThreadFailureMessage(error: Throwable): String {
    return "スレッド作成に失敗しました: ${error.message ?: "不明なエラー"}"
}

internal fun buildCatalogExternalAppUrl(boardUrl: String, mode: CatalogMode): String {
    val resolvedMode = if (mode == CatalogMode.WatchWords) {
        CatalogMode.Catalog
    } else {
        mode
    }
    return BoardUrlResolver.resolveCatalogUrl(boardUrl, resolvedMode)
}

internal fun buildCatalogLoadErrorMessage(error: Throwable): String {
    val message = error.message
    return when {
        message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
        message?.contains("404") == true -> "板が見つかりません (404)"
        message?.contains("500") == true -> "サーバーエラー (500)"
        message?.contains("HTTP error") == true -> "ネットワークエラー: $message"
        message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
        else -> "カタログを読み込めませんでした: ${message ?: "不明なエラー"}"
    }
}
