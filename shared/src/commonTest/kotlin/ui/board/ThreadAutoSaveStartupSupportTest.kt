package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadAutoSaveStartupSupportTest {
    @Test
    fun initialAutoSaveWaitsForContentFramesAndSettleWindow() = runBlocking {
        val events = mutableListOf<String>()

        awaitThreadAutoSaveStartupWindow(
            previousTimestampMillis = 0L,
            awaitFrame = { events += "frame" },
            pause = { delayMillis -> events += "pause:$delayMillis" }
        )

        assertEquals(
            listOf(
                "frame",
                "frame",
                "pause:$THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS"
            ),
            events
        )
    }

    @Test
    fun restartedInitialWindowWaitsOnlyForTheRemainder() = runBlocking {
        val events = mutableListOf<String>()

        // A page change 12 s into the window (e.g. automatic scrolling refresh)
        // must not postpone the first save by a fresh 30 s.
        awaitThreadAutoSaveStartupWindow(
            previousTimestampMillis = 0L,
            windowStartedAtMillis = 1_000L,
            nowMillis = { 13_000L },
            awaitFrame = { events += "frame" },
            pause = { delayMillis -> events += "pause:$delayMillis" }
        )
        assertEquals(
            listOf("frame", "frame", "pause:${THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS - 12_000L}"),
            events
        )

        events.clear()
        awaitThreadAutoSaveStartupWindow(
            previousTimestampMillis = 0L,
            windowStartedAtMillis = 1_000L,
            nowMillis = { 1_000L + THREAD_AUTO_SAVE_INITIAL_SETTLE_DELAY_MS + 1L },
            awaitFrame = { events += "frame" },
            pause = { delayMillis -> events += "pause:$delayMillis" }
        )
        assertEquals(listOf("frame", "frame"), events, "An elapsed window saves without waiting again")
    }

    @Test
    fun intervalAutoSaveStartsWithoutRepeatingInitialWindow() = runBlocking {
        val events = mutableListOf<String>()

        awaitThreadAutoSaveStartupWindow(
            previousTimestampMillis = 123_456L,
            awaitFrame = { events += "frame" },
            pause = { delayMillis -> events += "pause:$delayMillis" }
        )

        assertEquals(emptyList(), events)
    }
}
