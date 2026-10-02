package com.valoser.futacha.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** C4-3: the marker of a Wear OS "open on phone" link. */
class FutachaAiWatchRelayLinkTest {
    @Test
    fun watchLinkIsRecognizedOnlyForOpenActionsFromLinks() {
        val raw = buildFutachaAiDeepLink(
            FutachaAiAction.OpenThread,
            mapOf(
                "boardId" to "b",
                "threadId" to "1",
                "commandId" to "wear-1-1",
                FUTACHA_AI_WATCH_RELAY_PARAMETER to FUTACHA_AI_WATCH_RELAY_WEAR_OS
            )
        )
        val link = parseFutachaAiDeepLink(raw, source = "platform")!!
        assertTrue(link.isWatchRelayLink())
        assertEquals("wear-1-1", link.parameters["commandId"])
        // The marker grants nothing by itself: the link stays a link.
        assertTrue(link.isFromExternalLink())
        assertFalse(link.copy(source = "android-app-functions").isWatchRelayLink())
        assertFalse(link.copy(action = FutachaAiAction.AddBoard).isWatchRelayLink())
        assertFalse(link.copy(parameters = link.parameters - FUTACHA_AI_WATCH_RELAY_PARAMETER).isWatchRelayLink())
    }

    @Test
    fun confirmationMessageListsValuesOnOneLineAndShortensLongOnes() {
        val long = "x".repeat(500)
        val message = buildFutachaAiConfirmationMessage(
            FutachaAiCommand(FutachaAiAction.AddWatchWord, mapOf("word" to "a\nb"), source = "ios")
        )
        assertTrue(message.contains("監視ワード: 「a b」"), message)
        assertTrue(message.contains("外部のリンク"), message)
        assertEquals("「${"x".repeat(200)}…」", long.toConfirmationValue())
    }
}
