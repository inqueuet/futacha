package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C-6: a refresh or an AI hide rebuilds the read-aloud segments. The position
 * must neither fall back to the first post while they are rebuilt nor drift to
 * another post when segments before it are added or dropped.
 */
class ThreadReadAloudIndexRemapTest {
    private fun segment(postIndex: Int, id: String = "p$postIndex") =
        ReadAloudSegment(postIndex = postIndex, postId = id, body = "body $id")

    private fun post(id: String) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = "本文 $id",
        imageUrl = null,
        thumbnailUrl = null
    )

    @Test
    fun idleIndexFollowsItsPostWhenEarlierPostsAreHidden() {
        val previous = (0 until 6).map { segment(it) }
        // AI hides p1 and p2: the idle index 4 (p4) must stay on p4.
        val segments = previous.filterNot { it.postId == "p1" || it.postId == "p2" }
        assertEquals(2, resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Idle, 4))
    }

    @Test
    fun idleIndexFollowsItsPostWhenEarlierPostsAreRevealed() {
        val previous = listOf(segment(0), segment(3), segment(4))
        val segments = (0 until 5).map { segment(it) }
        assertEquals(3, resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Idle, 1))
    }

    @Test
    fun hiddenAnchorContinuesAtTheNextPostThatIsStillRead() {
        val previous = (0 until 5).map { segment(it) }
        // p1 and p2 are hidden while the position is on p2: continue at p3.
        val segments = previous.filterNot { it.postId == "p1" || it.postId == "p2" }
        assertEquals(1, resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Idle, 2))
        val paused = ReadAloudStatus.Paused(previous[2])
        assertEquals(1, resolveThreadReadAloudIndexRemap(previous, segments, paused, 2))
    }

    @Test
    fun pausedAndSpeakingIndicesFollowTheirSegment() {
        val previous = (0 until 5).map { segment(it) }
        val segments = previous.drop(2)
        assertEquals(1, resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Paused(previous[3]), 3))
        // A running session still reports indices of the list it captured.
        assertEquals(2, resolveThreadReadAloudIndexRemap(segments, segments, ReadAloudStatus.Speaking(previous[4]), 4))
    }

    @Test
    fun unchangedOrAppendedSegmentsKeepTheIndex() {
        val previous = (0 until 3).map { segment(it) }
        assertNull(resolveThreadReadAloudIndexRemap(previous, previous, ReadAloudStatus.Idle, 2))
        assertNull(resolveThreadReadAloudIndexRemap(previous, previous + segment(3), ReadAloudStatus.Idle, 2))
        assertNull(resolveThreadReadAloudIndexRemap(null, previous, ReadAloudStatus.Idle, 1))
        // Duplicated posts: staying on the current duplicate is not a change.
        val duplicated = listOf(segment(0), segment(1, "dup"), segment(2, "dup"))
        assertNull(resolveThreadReadAloudIndexRemap(duplicated, duplicated, ReadAloudStatus.Speaking(duplicated[2]), 2))
    }

    @Test
    fun emptySegmentsStillClampToTheStart() {
        val previous = (0 until 3).map { segment(it) }
        assertEquals(0, resolveThreadReadAloudIndexRemap(previous, emptyList(), ReadAloudStatus.Idle, 2))
    }

    @Test
    fun finishedIndexStaysAfterTheLastPostStillRead() {
        val previous = (0 until 6).map { segment(it) }
        // Finished (index == size); p5 and p4 are hidden: stay after p3.
        val segments = previous.filterNot { it.postId == "p4" || it.postId == "p5" }
        assertEquals(4, resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Idle, 6))
        // p1 hidden too: p3 is now at index 2, so the position is 3.
        val fewer = segments.filterNot { it.postId == "p1" }
        assertEquals(3, resolveThreadReadAloudIndexRemap(previous, fewer, ReadAloudStatus.Idle, 6))
        // Nothing of the previous list is read any more: keep the index (clamped).
        assertEquals(1, resolveThreadReadAloudIndexRemap(previous, listOf(segment(9)), ReadAloudStatus.Idle, 6))
    }

    @Test
    fun finishedIndexRemapIsLinearInTheSegmentCount() {
        // T4-3: when no previous post is read any more, every previous post
        // was compared with every segment (n²; 1.6 billion comparisons here,
        // several seconds). A post-id index keeps it to milliseconds.
        val count = 40_000
        val previous = (0 until count).map { segment(it) }
        val segments = (0 until count).map { segment(it, "n$it") }
        val started = System.nanoTime()
        assertNull(resolveThreadReadAloudIndexRemap(previous, segments, ReadAloudStatus.Idle, count))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMillis < 1_500, "remap took $elapsedMillis ms")
    }

    @Test
    fun closingTheIdleControlsKeepsThePositionOnTheSamePost() = withComposition { clock, harness ->
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 6 }
        harness.index = 4
        pump(clock)
        // U4-4: closed controls stop preparing the segments (an empty list).
        harness.controlsVisible = false
        pumpUntil(clock) { harness.segments.isEmpty() }
        pump(clock)
        assertEquals(4, harness.index, "closing the controls must not move the position")
        // Reopened unchanged: still on p4.
        harness.controlsVisible = true
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 6 }
        pump(clock)
        assertEquals("p4", harness.segments[harness.index].postId)
        // Closed again while two earlier posts get hidden, then reopened.
        harness.controlsVisible = false
        pumpUntil(clock) { harness.segments.isEmpty() }
        harness.layout = harness.layout.copy(collapsedPostIds = setOf("p1", "p2"))
        pump(clock)
        harness.controlsVisible = true
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 4 }
        pump(clock)
        assertEquals("p4", harness.segments[harness.index].postId)
        assertEquals(0, harness.indexResetsToStart, "the position must never fall back to the start")
    }

    @Test
    fun rebuildKeepsSegmentsAndThePositionOnTheSamePost() = withComposition { clock, harness ->
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 6 }
        harness.index = 4
        pump(clock)
        val sizesDuringRebuild = harness.observedSizes.size
        // A refresh that appends posts, then an AI hide of two earlier posts.
        harness.layout = harness.layout.copy(posts = harness.layout.posts + post("p6"))
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 7 }
        assertEquals(4, harness.index, "an appended post must not move the position")
        harness.layout = harness.layout.copy(collapsedPostIds = setOf("p1", "p2"))
        pumpUntil(clock) { harness.segmentsReady && harness.segments.size == 5 }
        pump(clock)
        val observedAfterReady = harness.observedSizes.drop(sizesDuringRebuild)
        assertTrue(0 !in observedAfterReady, "segments must not be empty while rebuilt: $observedAfterReady")
        assertEquals("p4", harness.segments[harness.index].postId)
        assertEquals(0, harness.indexResetsToStart, "the position must never fall back to the start")
    }

    private class Harness(posts: List<Post>) {
        var layout by mutableStateOf(ThreadDisplayedPostsLayout(posts = posts))
        var index by mutableStateOf(0)
        var controlsVisible by mutableStateOf(true)
        var segments: List<ReadAloudSegment> = emptyList()
        var segmentsReady = false
        val observedSizes = mutableListOf<Int>()
        var indexResetsToStart = 0
    }

    private var frameTime = 0L

    private suspend fun pump(clock: BroadcastFrameClock, frames: Int = 5) {
        repeat(frames) {
            delay(5)
            Snapshot.sendApplyNotifications()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
        }
    }

    private suspend fun pumpUntil(clock: BroadcastFrameClock, condition: () -> Boolean) {
        repeat(400) {
            if (condition()) return
            pump(clock, frames = 1)
        }
        error("condition not reached")
    }

    private fun withComposition(
        block: suspend CoroutineScope.(BroadcastFrameClock, Harness) -> Unit
    ) = runBlocking<Unit> {
        val posts = (0 until 6).map { post("p$it") }
        val harness = Harness(posts)
        val page = ThreadPage(threadId = "1", boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = posts)
        val lazyListState = LazyListState()
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
            composition.setContent { Content(harness, page, lazyListState) }
            block(clock, harness)
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancelAndJoin()
        }
    }

    @Composable
    private fun Content(harness: Harness, page: ThreadPage, lazyListState: LazyListState) {
        val runtime = rememberThreadScreenDerivedRuntimeState(
            currentState = ThreadUiState.Success(page),
            initialReplyCount = null,
            threadTitle = null,
            isReadAloudControlsVisible = harness.controlsVisible,
            readAloudStatus = ReadAloudStatus.Idle,
            lazyListState = lazyListState,
            isSearchActive = false,
            searchQuery = "",
            displayedPostsLayout = harness.layout
        )
        harness.segments = runtime.readAloudSegments
        harness.segmentsReady = runtime.readAloudSegmentsReady
        harness.observedSizes += runtime.readAloudSegments.size
        ThreadReadAloudIndexEffect(
            segments = runtime.readAloudSegments,
            currentStatus = { ReadAloudStatus.Idle },
            currentIndex = { harness.index },
            onCurrentReadAloudIndexChanged = {
                if (it == 0 && harness.index != 0) harness.indexResetsToStart++
                harness.index = it
            }
        )
    }
}
