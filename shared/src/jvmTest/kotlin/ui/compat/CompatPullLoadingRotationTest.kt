package com.valoser.futacha.shared.ui.compat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatPullLoadingRotationTest {
    private class Harness(val clock: BroadcastFrameClock) {
        private var frameTime = 0L

        suspend fun pump(times: Int = 20) {
            repeat(times) {
                delay(5)
                Snapshot.sendApplyNotifications()
                frameTime += 16_000_000L
                clock.sendFrame(frameTime)
            }
        }

        /** Whether anything still waits for the next vsync once the frame work settled. */
        suspend fun requestsFrames(): Boolean {
            delay(20)
            return clock.hasAwaiters
        }
    }

    private fun withHeadlessComposition(
        content: @Composable () -> Unit,
        block: suspend CoroutineScope.(Harness) -> Unit
    ) = runBlocking<Unit> {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        try {
            composition.setContent(content)
            block(Harness(clock))
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun loadingRotationRequestsFramesOnlyWhileRunning() {
        var running by mutableStateOf(false)
        val rotations = mutableListOf<Float>()
        withHeadlessComposition(
            content = {
                val rotation by rememberCompatPullLoadingRotation(running)
                // Read in composition (as graphicsLayer would) to observe changes.
                val current = rotation
                SideEffect { rotations += current }
            }
        ) { harness ->
            harness.pump()
            assertFalse(harness.requestsFrames(), "idle pull header must not request frames")
            assertEquals(0f, rotations.last())

            running = true
            withTimeout(5_000) {
                while (rotations.last() == 0f) harness.pump(1)
            }
            assertTrue(harness.requestsFrames(), "loading rotation should animate while running")

            running = false
            harness.pump()
            assertFalse(harness.requestsFrames(), "stopped rotation must release the frame clock")
            assertEquals(0f, rotations.last())
        }
    }

    /** Control: the previous always-composed InfiniteTransition kept the clock busy while idle. */
    @Test
    fun alwaysOnInfiniteTransitionRequestsFramesWhileIdle() {
        withHeadlessComposition(
            content = {
                val transition = rememberInfiniteTransition(label = "control")
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 720f,
                    animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
                    label = "control-rotation"
                )
            }
        ) { harness ->
            harness.pump()
            assertTrue(harness.requestsFrames())
        }
    }
}
