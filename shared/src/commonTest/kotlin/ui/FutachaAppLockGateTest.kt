package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FutachaAppLockGateTest {
    private val loading = "loading"
    private val error = "error"

    private fun gate(hash: String?, unlocked: Boolean) =
        resolveFutachaAppLockGate(hash, unlocked, loadingSentinel = loading, errorSentinel = error)

    @Test
    fun appLockGate_failsClosedOnReadError() {
        assertEquals(FutachaAppLockGate.Loading, gate(loading, unlocked = true))
        assertEquals(FutachaAppLockGate.Error, gate(error, unlocked = true))
        assertEquals(FutachaAppLockGate.Error, gate(error, unlocked = false))
        assertEquals(FutachaAppLockGate.Locked, gate("hash", unlocked = false))
        assertEquals(FutachaAppLockGate.Unlocked, gate("hash", unlocked = true))
        assertEquals(FutachaAppLockGate.Unlocked, gate(null, unlocked = false))
    }

    @Test
    fun platformAiCommandQueue_keepsCommandsUntilConsumed() {
        val first = FutachaAiCommand(action = FutachaAiAction.RefreshHistory, source = "a")
        val second = FutachaAiCommand(action = FutachaAiAction.RefreshHistory, source = "b")
        var queue = enqueuePlatformAiCommand(emptyList(), first)
        queue = enqueuePlatformAiCommand(queue, second)
        assertSame(first, queue.first())
        // Consuming something other than the head leaves the queue untouched.
        assertEquals(queue, consumePlatformAiCommand(queue, second))
        queue = consumePlatformAiCommand(queue, first)
        assertSame(second, queue.single())
        assertEquals(emptyList(), consumePlatformAiCommand(queue, second))
    }

    @Test
    fun platformAiCommandQueue_keepsEqualIdlessDeliveries() {
        val first = FutachaAiCommand(action = FutachaAiAction.RefreshHistory, source = "a")
        val duplicate = first.copy()
        val other = FutachaAiCommand(action = FutachaAiAction.RefreshHistory, source = "b")
        val queue = listOf(first, duplicate, other)
        assertEquals(listOf(duplicate, other), consumePlatformAiCommand(queue, first))
        assertEquals(AiCommandEffectKey(first), AiCommandEffectKey(first))
        kotlin.test.assertNotEquals(AiCommandEffectKey(first), AiCommandEffectKey(duplicate))
    }

    @Test
    fun platformAiCommandQueue_isBoundedWithoutDroppingTheHead() {
        val commands = List(5) { FutachaAiCommand(action = FutachaAiAction.RefreshHistory, source = "s$it") }
        val queue = commands.fold(emptyList<FutachaAiCommand>()) { acc, command ->
            enqueuePlatformAiCommand(acc, command, maxSize = 3)
        }
        assertEquals(3, queue.size)
        assertSame(commands[0], queue[0])
        assertSame(commands[3], queue[1])
        assertSame(commands[4], queue[2])
    }

    @Test
    fun historySelection_reportsUnregisteredBoard() {
        var unregistered: String? = null
        var navigation = FutachaNavigationState()
        val callbacks = buildFutachaNavigationCallbacks(
            currentBoards = { emptyList<BoardSummary>() },
            currentNavigationState = { navigation },
            setNavigationState = { navigation = it },
            onUnregisteredBoard = { unregistered = it }
        )
        callbacks.onHistoryEntrySelected(
            ThreadHistoryEntry(
                threadId = "1",
                boardId = "unknown",
                title = "t",
                titleImageUrl = "",
                boardName = "未登録板",
                boardUrl = "https://example.invalid/x/res/1.htm",
                lastVisitedEpochMillis = 0L,
                replyCount = 0
            )
        )
        assertEquals("未登録板", unregistered)
        assertNull(navigation.selectedThreadId)
        assertEquals(
            "「未登録板」はふたちゃに登録されていないため開けません。板一覧に追加するか、としあき(仮)モードで開いてください。",
            buildFutachaUnregisteredBoardMessage("未登録板")
        )
    }
}
