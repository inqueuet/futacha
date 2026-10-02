package com.valoser.futacha.shared.ui.board

import kotlin.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop

class ThreadModerationViewportTest {
    @Test fun sendsOnlyVisiblePostsAndEightNeighborsAndWaitsForLayout() {
        val ids = (1..1000).map(Int::toString)
        assertTrue(moderationNearbyPostIds(ids, emptySet()).isEmpty())
        assertEquals((492..511).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("500", "501", "502", "503")))
        assertEquals((1..9).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("1")))
        assertEquals((992..1000).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("1000")))
    }

    @Test fun filteredAndReorderedListsUseIdentitiesAndIgnoreHeaderKeys() {
        val ids = listOf("90", "2", "73", "5", "22")
        assertEquals(setOf("2", "73", "5"), moderationNearbyPostIds(ids, setOf("73", "thread-summary"), radius = 1))
        assertEquals(setOf("90", "2", "5", "22"), moderationNearbyPostIds(ids, setOf("90", "22"), radius = 1))
    }
}

class ThreadModerationViewportWorkerTest {
    private fun post(id: String) = com.valoser.futacha.shared.model.Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "本文 $id", imageUrl = null, thumbnailUrl = null)

    private val posts = (1..40).map { post("$it") }

    private fun next(nearby: Set<String>?, attempted: Set<String>, done: Set<String>, size: Int = 4) =
        posts.filter { it.id !in done && it.id !in attempted && (nearby == null || it.id in nearby) }
            .take(size).takeIf { it.isNotEmpty() }?.let { com.valoser.futacha.shared.ai.PostModerationInput("t", it) }

    @Test fun viewportChangesQueueNewPostsWithoutCancellingTheBatchInFlight() = kotlinx.coroutines.runBlocking {
        val viewport = kotlinx.coroutines.flow.MutableStateFlow((1..4).map(Int::toString).toSet())
        val done = mutableSetOf<String>()
        val sent = mutableListOf<List<String>>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch {
            runViewportModeration(viewport, nextBatch = { nearby, attempted -> next(nearby, attempted, done) }) { input ->
                sent += input.posts.map { it.id }
                try {
                    if (sent.size == 1) gate.await()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled = true
                    throw e
                }
                done += input.posts.map { it.id }
                true
            }
        }
        kotlinx.coroutines.withTimeout(5_000) { while (sent.isEmpty()) kotlinx.coroutines.yield() }
        viewport.value = (3..8).map(Int::toString).toSet()
        kotlinx.coroutines.yield()
        viewport.value = (5..10).map(Int::toString).toSet()
        kotlinx.coroutines.yield()
        assertFalse(cancelled)
        gate.complete(Unit)
        kotlinx.coroutines.withTimeout(5_000) { while (sent.size < 3) kotlinx.coroutines.yield() }
        assertEquals(listOf(listOf("1", "2", "3", "4"), listOf("5", "6", "7", "8"), listOf("9", "10")), sent)
        job.cancelAndJoin()
        assertFalse(cancelled)
    }

    @Test fun startDelayRunsOnceAndFailurePausesUntilTheViewportMoves() = kotlinx.coroutines.runBlocking {
        val viewport = kotlinx.coroutines.flow.MutableStateFlow(setOf("1", "2"))
        val sent = mutableListOf<List<String>>()
        var fail = true
        val job = launch {
            runViewportModeration(viewport, startDelayMillis = 1_000, nextBatch = { nearby, attempted ->
                next(nearby, attempted, emptySet(), size = 1)
            }) { input ->
                sent += input.posts.map { it.id }
                !fail
            }
        }
        kotlinx.coroutines.withTimeout(5_000) { while (sent.isEmpty()) kotlinx.coroutines.yield() }
        kotlinx.coroutines.delay(100)
        // The failed batch paused the worker: "2" is not sent until the viewport changes.
        assertEquals(listOf(listOf("1")), sent)
        fail = false
        val before = kotlin.time.TimeSource.Monotonic.markNow()
        viewport.value = setOf("1", "2", "3")
        kotlinx.coroutines.withTimeout(5_000) { while (sent.size < 4) kotlinx.coroutines.yield() }
        assertTrue(before.elapsedNow().inWholeMilliseconds < 900, "start delay must not be repeated")
        assertEquals(listOf(listOf("1"), listOf("1"), listOf("2"), listOf("3")), sent)
        job.cancelAndJoin()
    }

    // Round 3 A-4: pressing 再試行 for an API key cleared its hold, but the worker paused by that
    // key's failure only continued after the user scrolled.
    @Test fun explicitResumeContinuesAPausedWorkerWithoutAViewportChange() = kotlinx.coroutines.runBlocking {
        val viewport = kotlinx.coroutines.flow.MutableStateFlow(setOf("1", "2"))
        val retries = kotlinx.coroutines.flow.MutableStateFlow(0)
        val sent = mutableListOf<List<String>>()
        val done = mutableSetOf<String>()
        var fail = true
        var viewportChanges = 0
        val job = launch {
            runViewportModeration(viewport, onViewportChanged = { viewportChanges++ },
                resume = retries.drop(1), nextBatch = { nearby, attempted ->
                    next(nearby, attempted, done, size = 1)
                }) { input ->
                sent += input.posts.map { it.id }
                if (!fail) done += input.posts.map { it.id }
                !fail
            }
        }
        kotlinx.coroutines.withTimeout(5_000) { while (sent.isEmpty()) kotlinx.coroutines.yield() }
        kotlinx.coroutines.delay(100)
        assertEquals(listOf(listOf("1")), sent, "a failure pauses the worker")
        fail = false
        retries.value = 1
        kotlinx.coroutines.withTimeout(5_000) { while (sent.size < 3) kotlinx.coroutines.yield() }
        assertEquals(listOf(listOf("1"), listOf("1"), listOf("2")), sent)
        assertEquals(2, viewportChanges, "errors are cleared as for a viewport change")
        job.cancelAndJoin()
    }

    @Test fun incompleteLocalDecisionIsNotRepeatedOnEverySmallViewportChange() = kotlinx.coroutines.runBlocking {
        val viewport = kotlinx.coroutines.flow.MutableStateFlow(setOf("1"))
        val sent = mutableListOf<List<String>>()
        val job = launch {
            runViewportModeration(viewport, retryDelayMillis = 200,
                nextBatch = { nearby, attempted -> next(nearby, attempted, emptySet(), 1) }) {
                sent += it.posts.map { p -> p.id }; true
            }
        }
        kotlinx.coroutines.withTimeout(5_000) { while (sent.isEmpty()) kotlinx.coroutines.yield() }
        viewport.value = setOf("1", "2")
        kotlinx.coroutines.delay(50)
        viewport.value = setOf("1", "2", "3")
        kotlinx.coroutines.delay(50)
        assertEquals(listOf(listOf("1")), sent)
        kotlinx.coroutines.withTimeout(5_000) { while (sent.size < 4) kotlinx.coroutines.yield() }
        assertEquals(listOf(listOf("1"), listOf("1"), listOf("2"), listOf("3")), sent)
        job.cancelAndJoin()
    }

    @Test fun completedViewportFlowEndsAfterPendingPostsAreSent() = kotlinx.coroutines.runBlocking {
        val sent = mutableListOf<String>()
        runViewportModeration(kotlinx.coroutines.flow.flowOf(null), nextBatch = { nearby, attempted ->
            next(nearby, attempted, emptySet(), size = 16)
        }) { input -> sent += input.posts.map { it.id }; true }
        assertEquals(posts.map { it.id }, sent)
    }

    // Round 3 A10: an undecided device answer is not re-inferred for as long as it stays on screen.
    @Test fun undecidedPostsStopAfterTheRetryBudgetWithGrowingDelays() = kotlinx.coroutines.runBlocking {
        val viewport = kotlinx.coroutines.flow.MutableStateFlow(setOf("1"))
        val budget = ModerationRetryBudget(maxAttempts = 3)
        val sent = mutableListOf<Pair<String, Long>>()
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val job = launch {
            runViewportModeration(viewport, retryDelayMillis = 100, retryBudget = budget,
                nextBatch = { nearby, attempted -> next(nearby, attempted, emptySet(), 1) }) { input ->
                input.posts.forEach { sent += it.id to started.elapsedNow().inWholeMilliseconds; budget.record(it.id) }
                true
            }
        }
        // Keep moving the viewport around the undecided post for well past the back-off.
        repeat(40) { index ->
            viewport.value = setOf("1") + if (index % 2 == 0) setOf("x$index") else emptySet()
            kotlinx.coroutines.delay(25)
        }
        job.cancelAndJoin()
        val post1 = sent.filter { it.first == "1" }.map { it.second }
        assertEquals(3, post1.size, "sent at most maxAttempts times: $sent")
        assertTrue(post1[1] - post1[0] >= 90, "first retry waits the base delay: $post1")
        assertTrue(post1[2] - post1[1] >= 190, "the delay doubles: $post1")
        assertFalse(budget.allows("1"))
    }

    @Test fun retryBudgetKeysByInputAndClearsOnRequest() {
        val budget = ModerationRetryBudget(maxAttempts = 2, maxEntries = 2)
        budget.record("a"); budget.record("a")
        assertFalse(budget.allows("a"))
        assertTrue(budget.allows("a-edited"))
        budget.record("b"); budget.record("c")
        assertTrue(budget.allows("a"), "bounded: the oldest entry is dropped")
        budget.record("c")
        budget.clear()
        assertTrue(budget.allows("c"))
    }
}
