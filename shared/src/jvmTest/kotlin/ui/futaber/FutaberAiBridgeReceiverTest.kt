package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.PLATFORM_AI_COMMAND_QUEUE_MAX
import com.valoser.futacha.shared.ui.consumePlatformAiCommand
import com.valoser.futacha.shared.ui.enqueuePlatformAiCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * X10-0: this mode takes AI commands off the Android bridge and hands each to its screen (B8: the screen runs or answers
 * it), so they do not pile up (128 fit) and replay after a mode switch; and the iOS queue head is consumed so further
 * commands are not refused.
 */
class FutaberAiBridgeReceiverTest {
    @BeforeTest fun setUp() { FutachaAiCommandBridge.drainBufferedCommandsForTest() }
    @AfterTest fun tearDown() { FutachaAiCommandBridge.drainBufferedCommandsForTest() }

    /** The screen of the mode (B8 runs the commands there); here it only notes what it was handed. */
    private fun inboxNoting(notices: MutableList<String>) = FutaberAiInbox().apply { runner = { notices += "ran:${it.action.id}" } }

    private fun unlockedLock() = FutachaAppLockHolder().apply {
        openSessionWithoutLock()
        setContentVisible(true)
    }

    @Test
    fun everyCommandIsTakenOffTheBridgeAndAnsweredSoTheBridgeNeverFills() = runBlocking {
        val notices = Collections.synchronizedList(mutableListOf<String>())
        val receiver = launch(Dispatchers.Default) { futaberReceiveAiBridgeCommands(inboxNoting(notices), unlockedLock()) { notices += it } }
        try {
            // Far more than the bridge holds: with nobody receiving, the 129th would have been refused.
            var accepted = 0
            repeat(500) {
                // The receiver runs beside this loop; a refused command is retried once it had room (it must get room).
                var tries = 0
                while (!FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.RefreshCatalog))) {
                    check(++tries < 10_000) { "the bridge stayed full: nobody takes the commands" }
                    kotlinx.coroutines.yield()
                }
                accepted++
            }
            assertEquals(500, accepted)
            withTimeout(5_000) { while (notices.size < 500) kotlinx.coroutines.delay(5) }
            assertTrue(notices.all { it == "ran:${FutachaAiAction.RefreshCatalog.id}" })
        } finally {
            receiver.cancelAndJoin()
        }
    }

    @Test
    fun withoutAReceiverTheBridgeRefusesAfter128WhichIsTheDefectThisFixes() {
        repeat(128) { assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.RefreshCatalog))) }
        assertFalse(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.RefreshCatalog)))
    }

    @Test
    fun aLockedAppLeavesTheCommandsInTheBridgeUntilTheUnlock() = runBlocking {
        val lock = FutachaAppLockHolder().apply { setContentVisible(true) } // the session is still locked
        val notices = Collections.synchronizedList(mutableListOf<String>())
        val receiver = launch(Dispatchers.Default) { futaberReceiveAiBridgeCommands(inboxNoting(notices), lock) { notices += it } }
        try {
            assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.OpenBoardList)))
            kotlinx.coroutines.delay(200)
            assertTrue(notices.isEmpty(), "nothing is answered while the lock screen is up")
            lock.unlockSession()
            withTimeout(5_000) { while (notices.isEmpty()) kotlinx.coroutines.delay(5) }
            assertEquals(1, notices.size)
        } finally {
            receiver.cancelAndJoin()
        }
    }

    @Test
    fun aCommandTooOldForItsSenderIsDroppedWithoutANotice() = runBlocking {
        val notices = mutableListOf<String>()
        // Enqueued with a limit of 0 ms: by the time it is received it is older than that, and the bridge skips it.
        assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.OpenBoardList), 0L))
        kotlinx.coroutines.delay(20)
        // The receiver finds nothing (the expired command was skipped) and waits until the timeout ends it.
        val finished = withTimeoutOrNull(300) { futaberReceiveAiBridgeCommands(inboxNoting(notices), unlockedLock()) { notices += it } }
        assertEquals(null, finished)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun theHeadOfTheIosQueueIsConsumedSoTheQueueDoesNotJam() {
        // What the iOS root does with what the sink consumes: the head leaves, the next one is handed over.
        var queue = emptyList<FutachaAiCommand>()
        repeat(PLATFORM_AI_COMMAND_QUEUE_MAX) { queue = enqueuePlatformAiCommand(queue, FutachaAiCommand(FutachaAiAction.RefreshCatalog)) }
        assertEquals(PLATFORM_AI_COMMAND_QUEUE_MAX, queue.size)
        var answered = 0
        while (queue.isNotEmpty()) {
            val head = queue.first()
            answered++ // the sink answers the head, then consumes it
            queue = consumePlatformAiCommand(queue, head)
        }
        assertEquals(PLATFORM_AI_COMMAND_QUEUE_MAX, answered)
        assertTrue(queue.isEmpty(), "the queue is empty again: the root takes the next command from the bridge")
    }
}
