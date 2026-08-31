package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.AiAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPreferenceSupportTest {
    @Test
    fun isThreadSummaryFeatureEnabledRequiresUserToggleAndCapability() {
        assertTrue(
            isThreadSummaryFeatureEnabled(
                preferences(
                    summaryEnabled = true,
                    availability = availability(summary = true)
                )
            )
        )
        assertFalse(
            isThreadSummaryFeatureEnabled(
                preferences(
                    summaryEnabled = false,
                    availability = availability(summary = true)
                )
            )
        )
        assertFalse(
            isThreadSummaryFeatureEnabled(
                preferences(
                    summaryEnabled = true,
                    availability = availability(summary = false)
                )
            )
        )
    }

    @Test
    fun isAiPostFilterFeatureEnabledRequiresUserToggleAndCapability() {
        assertTrue(
            isAiPostFilterFeatureEnabled(
                preferences(
                    postFilterEnabled = true,
                    availability = availability(postModeration = true)
                )
            )
        )
        assertFalse(
            isAiPostFilterFeatureEnabled(
                preferences(
                    postFilterEnabled = false,
                    availability = availability(postModeration = true)
                )
            )
        )
        assertFalse(
            isAiPostFilterFeatureEnabled(
                preferences(
                    postFilterEnabled = true,
                    availability = availability(postModeration = false)
                )
            )
        )
    }

    @Test
    fun aiFeaturesStayDisabledWhenDeviceAiIsUnavailable() {
        val unavailable = AiAvailability(
            isAvailable = false,
            supportsThreadSummary = true,
            supportsPostModeration = true
        )

        assertFalse(
            isThreadSummaryFeatureEnabled(
                preferences(summaryEnabled = true, availability = unavailable)
            )
        )
        assertFalse(
            isAiPostFilterFeatureEnabled(
                preferences(postFilterEnabled = true, availability = unavailable)
            )
        )
    }

    @Test
    fun aiSettingDescriptionsUseUnavailableReasonWhenFeatureIsDisabled() {
        val unavailable = AiAvailability(
            isAvailable = false,
            unavailableReason = "モデルを準備中です。",
            supportsThreadSummary = false,
            supportsPostModeration = false
        )

        assertEquals("モデルを準備中です。", threadSummarySettingDescription(unavailable))
        assertEquals("モデルを準備中です。", aiPostFilterSettingDescription(unavailable))
    }

    @Test
    fun aiSettingDescriptionsUseFeatureSpecificTextWhenFeatureIsAvailable() {
        val available = availability(summary = true, postModeration = true)

        assertEquals(
            "スレ本文の一番上に要約欄を表示します。",
            threadSummarySettingDescription(available)
        )
        assertEquals(
            "対応端末では画面から有効化できます。AI判定で荒らし候補や攻撃的なレスを折りたたみます。",
            aiPostFilterSettingDescription(available)
        )
    }

    @Test
    fun aiLocalProcessingDescriptionMentionsProviderAndNoExternalUpload() {
        val description = aiLocalProcessingDescription("Gemini Nano")

        assertTrue(description.contains("Gemini Nano"))
        assertTrue(description.contains("端末内"))
        assertTrue(description.contains("外部サーバーへ送信しません"))
    }

    @Test
    fun aiCommandSettingDescriptionMentionsTransportStateAndConfirmPolicy() {
        val description = aiCommandSettingDescription(
            aiAvailability = AiAvailability(
                isAvailable = true,
                providerLabel = "Apple Intelligence"
            ),
            isAiCommandEnabled = true
        )

        assertTrue(description.contains("App Intents"))
        assertTrue(description.contains("App Functions"))
        assertTrue(description.contains("deep link"))
        assertTrue(description.contains("現在はON"))
        assertTrue(description.contains("端末AIの要約・判定とは別"))
        assertTrue(description.contains("実行前に確認"))
        assertFalse(description.contains("50 件"))
        assertFalse(description.contains("10 件"))
    }

    @Test
    fun aiCommandSettingDescriptionReflectsDisabledState() {
        val description = aiCommandSettingDescription(
            aiAvailability = AiAvailability(
                isAvailable = false,
                providerLabel = "Android App Functions"
            ),
            isAiCommandEnabled = false
        )

        assertTrue(description.contains("App Functions"))
        assertTrue(description.contains("現在はOFF"))
    }

    private fun preferences(
        summaryEnabled: Boolean = false,
        postFilterEnabled: Boolean = false,
        availability: AiAvailability
    ): ScreenPreferencesState {
        return ScreenPreferencesState(
            appVersion = "test",
            isThreadSummaryModeEnabled = summaryEnabled,
            isAiPostFilterEnabled = postFilterEnabled,
            aiAvailability = availability
        )
    }

    private fun availability(
        summary: Boolean = false,
        postModeration: Boolean = false
    ): AiAvailability {
        return AiAvailability(
            isAvailable = true,
            supportsThreadSummary = summary,
            supportsPostModeration = postModeration
        )
    }
}
