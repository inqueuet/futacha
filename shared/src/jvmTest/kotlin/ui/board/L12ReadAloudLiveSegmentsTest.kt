package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import kotlin.test.*

/** Product Compose dependency capture -> real bindings -> real runner, with a silent gated speaker. */
class L12ReadAloudLiveSegmentsTest {
    private fun segment(index: Int, id: String, body: String = id) = ReadAloudSegment(index, id, body)

    @Test fun aRunningSessionSkipsNewlyHiddenPostsAndReadsNewRepliesFromTheCurrentComposition() = withSession { harness, pump ->
        val latest = listOf(segment(0, "p0"), segment(1, "p2"), segment(2, "p3"))
        harness.segments = latest
        pump { harness.observed === latest }
        harness.resume.complete(Unit)
        pump { harness.runtime.job == null }
        assertEquals(listOf("p0", "p2", "p3"), harness.spoken)
        assertEquals(listOf(0, 1, 2), harness.scrolled)
        assertEquals(ReadAloudStatus.Idle, harness.runtime.status)
    }

    @Test fun earlierRevealsAndChangedPostPositionsDoNotRepeatTheSpokenPostOrReadBackwards() = withSession { harness, pump ->
        val latest = listOf(segment(0, "revealed-a"), segment(1, "revealed-b"), segment(2, "p0", "updated p0"),
            segment(3, "p2", "updated p2"), segment(4, "p3"))
        harness.segments = latest
        pump { harness.observed === latest }
        harness.resume.complete(Unit)
        pump { harness.runtime.job == null }
        assertEquals(listOf("p0", "updated p2", "p3"), harness.spoken)
        assertEquals(listOf(0, 3, 4), harness.scrolled)
        assertEquals(listOf("p0" to 0, "p2" to 3, "p3" to 4), harness.starts)
    }

    @Test fun remappingUsesPostIdentityWhenTheTextAndListPositionChange() {
        val old = listOf(segment(0, "p0"), segment(1, "p1"), segment(2, "p2"))
        val latest = listOf(segment(7, "p0", "edited"), segment(8, "p2", "edited next"), segment(9, "p3"))
        assertEquals(1, nextReadAloudIndexAfter(old, 0, latest))
        assertEquals(1, nextReadAloudIndexAfter(old, 1, latest)) // Hidden p1 resumes at the same old next post.
    }

    private class Harness {
        var segments by mutableStateOf(listOf(ReadAloudSegment(0, "p0", "p0"), ReadAloudSegment(1, "p1", "p1"), ReadAloudSegment(2, "p2", "p2")))
        var observed: List<ReadAloudSegment> = emptyList()
        lateinit var dependencies: ThreadScreenReadAloudDependencies
        var runtime = ThreadReadAloudRuntimeState(null, ReadAloudStatus.Idle, 0, false)
        val speaking = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val spoken = mutableListOf<String>()
        val scrolled = mutableListOf<Int>()
        val starts = mutableListOf<Pair<String, Int>>()
    }

    private fun withSession(block: suspend CoroutineScope.(Harness, suspend (() -> Boolean) -> Unit) -> Unit) = runBlocking {
        val harness = Harness()
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val recomposition = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        var frame = 0L
        val pump: suspend (() -> Boolean) -> Unit = { condition ->
            withTimeout(5_000) {
                while (!condition()) {
                    delay(5)
                    Snapshot.sendApplyNotifications()
                    frame += 16_000_000
                    clock.sendFrame(frame)
                }
            }
        }
        try {
            composition.setContent {
                harness.dependencies = threadScreenReadAloudDependencies(harness.segments)
                harness.observed = harness.segments
            }
            val bindings = buildThreadScreenReadAloudBindings(this,
                ThreadScreenReadAloudStateBindings({ harness.runtime }, {
                    harness.runtime = it
                    (it.status as? ReadAloudStatus.Speaking)?.segment?.let { segment ->
                        val start = segment.postId to segment.postIndex
                        if (harness.starts.lastOrNull() != start) harness.starts += start
                    }
                }), ThreadScreenReadAloudCallbacks({}, {}, { harness.scrolled += it }, {
                    if (harness.spoken.isEmpty()) {
                        harness.speaking.complete(Unit)
                        harness.resume.await()
                    }
                    harness.spoken += it
                }, {}), harness.dependencies)
            bindings.startReadAloud()
            withTimeout(5_000) { harness.speaking.await() }
            block(harness, pump)
        } finally {
            harness.runtime.job?.cancelAndJoin()
            composition.dispose()
            recomposer.close()
            recomposition.cancelAndJoin()
        }
    }
}
