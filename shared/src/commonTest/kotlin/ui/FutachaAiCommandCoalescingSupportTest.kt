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
    fun resolvePendingAiScreenCommand_newerCommandReplacesUnconsumedOne() {
        val existing = FutachaAiCommand(FutachaAiAction.SearchCatalog)
        val incoming = FutachaAiCommand(FutachaAiAction.OpenGallery)

        // An unconsumed command must not block later ones (H1).
        assertSame(incoming, resolvePendingAiScreenCommand(existing, incoming))
        assertSame(incoming, resolvePendingAiScreenCommand(null, incoming))
    }

    @Test
    fun isAiCommandForThread_matchesOnlyTheNamedThread() {
        val untargeted = FutachaAiCommand(FutachaAiAction.StartThreadReadAloud)
        val watch = FutachaAiCommand(
            FutachaAiAction.StartThreadReadAloud,
            parameters = mapOf("boardId" to "b", "boardUrl" to "https://may.2chan.net/b/", "threadId" to "123"),
            source = "watchos"
        )
        val byUrl = FutachaAiCommand(
            FutachaAiAction.SearchThread,
            parameters = mapOf("url" to "https://may.2chan.net/b/res/456.htm")
        )

        assertTrue(isAiCommandForThread(untargeted, boardId = "b", threadId = "999"))
        assertTrue(isAiCommandForThread(watch, boardId = "b", threadId = "123"))
        assertFalse(isAiCommandForThread(watch, boardId = "b", threadId = "999"))
        assertFalse(isAiCommandForThread(watch, boardId = "img", threadId = "123"))
        assertTrue(isAiCommandForThread(byUrl, boardId = "b", threadId = "456"))
        assertFalse(isAiCommandForThread(byUrl, boardId = "b", threadId = "123"))
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
