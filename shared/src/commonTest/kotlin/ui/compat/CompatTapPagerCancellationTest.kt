package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompatTapPagerCancellationTest {
    @Test fun dragDiscardsRemainingDistanceButAnotherTapAccumulatesIt() = runTest {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000)
            }
        }
        withContext(clock) {
            var moved = 0f
            val state = ScrollableState { moved += it; it }
            val pager = CompatTapPager()
            pager.page(this, state, 100f)
            testScheduler.advanceTimeBy(80)
            testScheduler.runCurrent()
            assertTrue(moved > 0 && moved < 100)
            state.scroll(MutatePriority.UserInput) {}
            testScheduler.runCurrent()
            assertEquals(0f, pager.remaining)
            moved = 0f
            pager.page(this, state, 100f)
            testScheduler.advanceTimeBy(80)
            testScheduler.runCurrent()
            pager.page(this, state, 100f)
            testScheduler.advanceUntilIdle()
            assertEquals(200f, moved, 0.01f)
            assertEquals(0f, pager.remaining)
        }
    }
}
