package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.AiAvailability

internal const val ALPHA_AI_COMMAND_ENABLED = false
internal const val ALPHA_AI_POST_FILTER_ENABLED = false

internal fun isThreadSummaryFeatureEnabled(preferencesState: ScreenPreferencesState): Boolean {
    return preferencesState.isThreadSummaryModeEnabled &&
        isThreadSummaryFeatureAvailable(preferencesState.aiAvailability)
}

internal fun isAiPostFilterFeatureEnabled(preferencesState: ScreenPreferencesState): Boolean {
    return ALPHA_AI_POST_FILTER_ENABLED &&
        preferencesState.isAiPostFilterEnabled &&
        isAiPostFilterFeatureAvailable(preferencesState.aiAvailability)
}

internal fun isThreadSummaryFeatureAvailable(aiAvailability: AiAvailability): Boolean {
    return aiAvailability.isAvailable && aiAvailability.supportsThreadSummary
}

internal fun isAiPostFilterFeatureAvailable(aiAvailability: AiAvailability): Boolean {
    return aiAvailability.isAvailable && aiAvailability.supportsPostModeration
}

internal fun threadSummarySettingDescription(aiAvailability: AiAvailability): String {
    return if (isThreadSummaryFeatureAvailable(aiAvailability)) {
        "スレ本文の一番上に要約欄を表示します。"
    } else {
        aiAvailability.unavailableReason ?: "対応端末でのみ利用できます。"
    }
}

internal fun aiPostFilterSettingDescription(aiAvailability: AiAvailability): String {
    if (!ALPHA_AI_POST_FILTER_ENABLED) {
        return "アルファ版のため現在は画面上から有効化できません。"
    }
    return if (isAiPostFilterFeatureAvailable(aiAvailability)) {
        "AI判定で荒らし候補のレスを折りたたみます。"
    } else {
        aiAvailability.unavailableReason ?: "誤判定対策を含めた判定モデル接続後に有効化されます。"
    }
}

internal fun aiLocalProcessingDescription(providerLabel: String): String {
    return "$providerLabel はスレ本文を端末内で処理します。要約・荒らし候補判定のために本文を外部サーバーへ送信しません。"
}

@Suppress("UNUSED_PARAMETER")
internal fun aiCommandSettingDescription(
    aiAvailability: AiAvailability,
    isAiCommandEnabled: Boolean
): String {
    if (!ALPHA_AI_COMMAND_ENABLED) {
        return "アルファ版のため現在は画面上から有効化できません。"
    }
    val stateLabel = if (isAiCommandEnabled) "ON" else "OFF"
    return "App Intents / App Functions / deep link からアプリ操作を受け付けます。現在は${stateLabel}です。端末AIの要約・判定とは別の設定です。保存・投稿・削除に関係する操作は実行前に確認します。"
}
