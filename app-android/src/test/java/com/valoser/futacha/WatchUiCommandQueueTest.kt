package com.valoser.futacha

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C4-1: a watch command reaches the app only while a MainActivity is started.
 * The app's collector also runs in the background, so a command handed over at
 * once opened the thread there and started reading when the app was opened,
 * however much later.
 */
class WatchUiCommandQueueTest {
    private val open = FutachaAiCommand(FutachaAiAction.OpenThread, mapOf("threadId" to "1"), "wear-os")
    private val read = FutachaAiCommand(FutachaAiAction.StartThreadReadAloud, mapOf("threadId" to "1"), "wear-os")

    private fun elapsed(): Long = System.nanoTime() / 1_000_000L

    @Test
    fun commandIsHandedOverAtOnceWhileAnActivityIsStarted() = runBlocking {
        val bridge = mutableListOf<Pair<FutachaAiCommand, Long>>()
        val queue = WatchUiCommandQueue(this, MutableStateFlow(1), { c, age -> bridge += c to age; true }, ::elapsed, maxAgeMillis = 60_000L, warn = {})
        assertTrue(queue.submit(open))
        assertEquals(listOf(open to 60_000L), bridge)
    }

    @Test
    fun commandsWaitInOrderUntilAnActivityStarts() = runBlocking {
        val started = MutableStateFlow(0)
        val bridge = mutableListOf<FutachaAiCommand>()
        val queue = WatchUiCommandQueue(this, started, { c, _ -> bridge += c; true }, ::elapsed, maxAgeMillis = 60_000L, warn = {})
        queue.submit(open)
        queue.submit(read)
        delay(50)
        assertTrue("handed over while no activity is started", bridge.isEmpty())
        started.value = 1
        withTimeout(5_000L) { while (bridge.size < 2) delay(10) }
        assertEquals(listOf(open, read), bridge)
    }

    @Test
    fun commandIsDroppedWhenNoActivityStartsInTime() = runBlocking {
        val started = MutableStateFlow(0)
        val bridge = mutableListOf<FutachaAiCommand>()
        val queue = WatchUiCommandQueue(this, started, { c, _ -> bridge += c; true }, ::elapsed, maxAgeMillis = 100L, warn = {})
        queue.submit(read)
        delay(400)
        started.value = 1
        delay(100)
        assertTrue("an expired command reached the app", bridge.isEmpty())
        // A later command is handed over normally.
        assertTrue(queue.submit(open))
        assertEquals(listOf(open), bridge)
    }

    @Test
    fun nullActivityCountKeepsTheImmediateHandover() = runBlocking {
        val bridge = mutableListOf<FutachaAiCommand>()
        val queue = WatchUiCommandQueue(this, null, { c, _ -> bridge += c; true }, ::elapsed, warn = {})
        queue.submit(read)
        assertEquals(listOf(read), bridge)
    }
}
