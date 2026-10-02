package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.AiAvailability

internal const val ALPHA_AI_COMMAND_ENABLED = true
internal const val ALPHA_AI_POST_FILTER_ENABLED = true

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
        if (aiAvailability.externalModeration) "OpenAI Moderationで選択カテゴリと閾値に基づいて候補を判定します。自動折りたたみは詳細設定で変更できます。同文再投稿と本文内の反復は端末側でも判定します。引用があるレスはコピペ判定から除外します。OpenAI単独ではスレの文脈を判定しません。端末内AIとの併用では文脈も参考にします。"
        else "表示中と前後8件を、本文量に応じて最大8件ずつ端末内で判定します。スレ題・先頭投稿・直前の会話を参考にし、引用部分は除外します。不明なレスは表示を維持します。判定結果は画面内にキャッシュし、本文・参考の会話・設定が変わると再判定します。"
    } else {
        aiAvailability.unavailableReason ?: "誤判定対策を含めた判定モデル接続後に有効化されます。"
    }
}

internal fun aiLocalProcessingDescription(providerLabel: String): String {
    return "$providerLabel はスレ本文を端末内で処理します。要約・荒らし候補や攻撃的内容の判定のために本文を外部サーバーへ送信しません。"
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
