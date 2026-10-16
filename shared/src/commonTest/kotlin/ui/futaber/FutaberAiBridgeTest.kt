package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** Re-audit L6: on Android the next AI command is taken while a long one is still running, in order. */
class FutaberAiBridgeTest {
    @Test
    fun aLongCommandDoesNotHoldUpTheNext() = runBlocking<Unit> {
        val appLock = FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }
        val inbox = FutaberAiInbox()
        val started = mutableListOf<FutachaAiAction>()
        val secondStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        inbox.runner = { command ->
            started += command.action
            if (started.size == 1) gate.await() else secondStarted.complete(Unit)
        }
        val receiving = launch { futaberReceiveAiBridgeCommands(inbox, appLock) {} }
        FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.OpenBoardList, source = "test"))
        FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.RefreshCatalog, source = "test"))
        // The first is still running (the gate is shut) when the second starts.
        withTimeout(5_000) { secondStarted.await() }
        assertEquals(listOf(FutachaAiAction.OpenBoardList, FutachaAiAction.RefreshCatalog), started)
        gate.complete(Unit)
        receiving.cancelAndJoin()
    }
}
