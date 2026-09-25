package com.valoser.futacha.shared.compat

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatAutoScrollLoopTest {
    @Test
    fun autoScrollNeitherScrollsNorReloadsWhileHostIsBackgrounded() = runBlocking<Unit> {
        val foreground = MutableStateFlow(false)
        var canScrollForward = true
        var scrolls = 0
        var reloads = 0
        val job = launch {
            runCompatAutoScroll(
                isAutoScrolling = { true },
                awaitForeground = { foreground.first { it } },
                canScrollForward = { canScrollForward },
                isDead = { false },
                stepDelayMillis = 1L,
                scrollStep = { scrolls++ },
                reload = { reloads++ },
                stopDead = {},
                reloadWaitMillis = 20L
            )
        }
        try {
            delay(100)
            assertEquals(0, scrolls, "backgrounded host must not scroll")

            foreground.value = true
            withTimeout(5_000) { while (scrolls < 3) delay(1) }

            foreground.value = false
            delay(20)
            val pausedScrolls = scrolls
            delay(100)
            assertEquals(pausedScrolls, scrolls, "scrolling resumes only in the foreground")

            // Reaching the bottom while paused does not start the 12 s reload
            // cycle until the host is visible again.
            canScrollForward = false
            delay(100)
            assertEquals(0, reloads, "backgrounded host must not reload")
            foreground.value = true
            withTimeout(5_000) { while (reloads == 0) delay(1) }
            assertTrue(reloads >= 1)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun deadThreadAtBottomStopsAutoScroll() = runBlocking<Unit> {
        var scrolling = true
        var stopped = 0
        withTimeout(5_000) {
            runCompatAutoScroll(
                isAutoScrolling = { scrolling },
                awaitForeground = {},
                canScrollForward = { false },
                isDead = { true },
                stepDelayMillis = 1L,
                scrollStep = {},
                reload = { error("dead thread must not reload") },
                stopDead = {
                    scrolling = false
                    stopped++
                }
            )
        }
        assertEquals(1, stopped)
    }
}
