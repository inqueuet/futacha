package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome
import com.valoser.futacha.shared.ai.FutachaAiConfirmationRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutachaAiCommandCoalescingSupportTest {
    @Test
    fun shouldStartAiBridgeCommand_coalescesRunningHistoryRefresh() {
        val refresh = FutachaAiCommand(FutachaAiAction.RefreshHistory)
        val openBoard = FutachaAiCommand(FutachaAiAction.OpenBoardList)

        assertTrue(shouldStartAiBridgeCommand(refresh, isHistoryRefreshCommandRunning = false))
        assertFalse(shouldStartAiBridgeCommand(refresh, isHistoryRefreshCommandRunning = true))
        assertTrue(shouldStartAiBridgeCommand(openBoard, isHistoryRefreshCommandRunning = true))
    }

    @Test
    fun resolvePendingAiScreenCommand_keepsExistingPendingCommand() {
        val existing = FutachaAiCommand(FutachaAiAction.SearchCatalog)
        val incoming = FutachaAiCommand(FutachaAiAction.OpenGallery)

        assertSame(existing, resolvePendingAiScreenCommand(existing, incoming))
        assertSame(incoming, resolvePendingAiScreenCommand(null, incoming))
    }

    @Test
    fun shouldReplacePendingAiConfirmation_rejectsStackedConfirmation() {
        val command = FutachaAiCommand(FutachaAiAction.SaveCurrentThread)
        val request = FutachaAiConfirmationRequest(
            command = command,
            title = "確認",
            message = "保存しますか"
        )
        val outcome = FutachaAiCommandOutcome.NeedsConfirmation(request)

        assertTrue(shouldReplacePendingAiConfirmation(current = null, outcome = outcome))
        assertFalse(shouldReplacePendingAiConfirmation(current = request, outcome = outcome))
        assertFalse(
            shouldReplacePendingAiConfirmation(
                current = null,
                outcome = FutachaAiCommandOutcome.Completed("ok")
            )
        )
    }
}
