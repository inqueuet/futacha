package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Round 3 A-7: the preview cache update walked every post on each recomposition of the thread screen.
class CompatAiPreviewPublishTest {
    private var frameTime = 0L

    private suspend fun pump(clock: BroadcastFrameClock, frames: Int = 3) {
        repeat(frames) {
            delay(5)
            Snapshot.sendApplyNotifications()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
        }
    }

    private fun withComposition(content: @Composable () -> Unit, block: suspend CoroutineScope.(BroadcastFrameClock) -> Unit) =
        runBlocking<Unit> {
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
                block(clock)
            } finally {
                composition.dispose()
                recomposer.close()
                job.cancelAndJoin()
            }
        }

    @Test
    fun previewIsPublishedWhenItsInputsChangeNotOnEveryRecomposition() {
        val cache = CompatAiPreviewCache()
        val snapshot = CompatThreadSnapshot("thread", 1, 1, posts = (1..3).map {
            CompatPostSnapshot(it - 1, "$it", timestamp = "", messageHtml = "本文$it")
        })
        val hiddenFirst = CompatThreadAiState(moderationEnabled = true, hiddenPostNos = setOf("1"),
            results = listOf(PostModerationResult("1", true), PostModerationResult("2", false)))
        var resolved by mutableStateOf(hiddenFirst)
        var unrelated by mutableStateOf(0)
        var compositions = 0
        withComposition(content = {
            compositions++
            check(unrelated >= 0) // Reads the unrelated state, like a scrolling thread screen.
            // An equal copy keeps the call from being skipped, as for the non-skippable host.
            PublishCompatAiPreview(cache, "DEVICE:BOTH:1", snapshot.copy(), resolved)
        }) { clock ->
            pump(clock)
            assertEquals(1, cache.updateCount)
            assertEquals(setOf("1"), cache.hidden(snapshot))
            repeat(5) {
                unrelated++
                pump(clock)
            }
            assertTrue(compositions >= 6, "the host recomposed $compositions times")
            assertEquals(1, cache.updateCount, "recompositions with the same inputs must not walk the posts again")
            // A new judgement is still published: post 1 kept, post 2 hidden.
            resolved = CompatThreadAiState(moderationEnabled = true, hiddenPostNos = setOf("2"),
                results = listOf(PostModerationResult("1", false), PostModerationResult("2", true)))
            pump(clock)
            assertEquals(2, cache.updateCount)
            assertEquals(setOf("2"), cache.hidden(snapshot))
        }
    }
}
