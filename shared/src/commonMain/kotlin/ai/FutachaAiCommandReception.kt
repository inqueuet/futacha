package com.valoser.futacha.shared.ai

data class FutachaAiCommandReception(
    val actionId: String,
    val actionLabel: String,
    val risk: FutachaAiCommandRisk,
    val status: String,
    val message: String,
    val requiresConfirmation: Boolean
)

fun describeFutachaAiCommandReception(actionId: String?): FutachaAiCommandReception? {
    val action = FutachaAiAction.fromId(actionId) ?: return null
    val requiresConfirmation = action.risk == FutachaAiCommandRisk.Confirm
    return FutachaAiCommandReception(
        actionId = action.id,
        actionLabel = action.label,
        risk = action.risk,
        status = if (requiresConfirmation) "needs_confirmation" else "accepted",
        message = if (requiresConfirmation) {
            "「${action.label}」を受け付けました。${action.confirmationReason()}、アプリ内で確認してから実行します。"
        } else {
            "「${action.label}」を受け付けました。"
        },
        requiresConfirmation = requiresConfirmation
    )
}

internal fun FutachaAiAction.confirmationReason(): String {
    return when (this) {
        FutachaAiAction.SaveCurrentThread,
        FutachaAiAction.SaveThread -> "スレ保存に関係するため"

        FutachaAiAction.DeleteHistoryEntry,
        FutachaAiAction.ClearHistory,
        FutachaAiAction.DeleteSavedThread,
        FutachaAiAction.ClearSavedThreads,
        FutachaAiAction.DeleteBoard -> "削除に関係するため"

        FutachaAiAction.DraftReply,
        FutachaAiAction.DraftThread -> "投稿下書きを作成するため"

        FutachaAiAction.AddBoard -> "板リストを変更するため"

        else -> "データ変更に関係するため"
    }
}
