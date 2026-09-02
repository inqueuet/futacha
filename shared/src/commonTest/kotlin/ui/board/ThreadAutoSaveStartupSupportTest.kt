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
