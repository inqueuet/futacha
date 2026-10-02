package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class HybridModerationServiceTest {
    private val input = PostModerationInput("t", (1..3).map {
        Post("$it", author = null, subject = null, timestamp = "", messageHtml = "本文$it", imageUrl = null, thumbnailUrl = null)
    }, "スレ題: テスト")
    private class Fake(private val external: Boolean = false) : OnDeviceAiService {
        override val isExternalService get() = external
        var available = true
        var fail = false
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var availabilityGate: CompletableDeferred<Unit>? = null
        var hide = setOf<String>()
        var omit = setOf<String>() // Answered without a decision (UNCERTAIN/omitted).
        var lastPostIds = listOf<String>()
        override suspend fun getAvailability(): AiAvailability {
            availabilityGate?.await()
            return AiAvailability(available, supportsPostModeration = available)
        }
        override suspend fun summarizeThread(input: ThreadSummaryInput) = Result.failure<ThreadSummary>(IllegalStateException("unused"))
        override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
            calls++
            lastPostIds = input.posts.map { it.id }
            gate?.await()
            return if (fail) Result.failure(IllegalStateException("429")) else Result.success(input.posts.filter { it.id !in omit }.map {
                PostModerationResult(it.id, it.id in hide, if (external) "OpenAI: 攻撃" else "反復妨害")
            })
        }
    }
    @Test fun cloudDecisionsArriveWhileLocalAvailabilityIsStillWaiting() = runBlocking {
        val localGate = CompletableDeferred<Unit>()
        val local = Fake().apply { availabilityGate = localGate }
        val cloud = Fake(true)
        val service = HybridModerationService(local, cloud)
        assertTrue(withTimeout(1_000) { service.getAvailability().supportsPostModeration })
        val cloudPublished = CompletableDeferred<Unit>()
        val job = async { service.classifyPosts(input) {
            if (cloud.calls > 0 && it.isNotEmpty()) cloudPublished.complete(Unit)
        }.getOrThrow() }
        try { withTimeout(1_000) { cloudPublished.await() } }
        finally { localGate.complete(Unit) }
        assertTrue(job.await().all { it.isComplete })
    }

    @Test fun localExplicitDecisionsAreReusedWhenBatchContextChanges() = runBlocking {
        val local = Fake()
        val service = HybridModerationService(local, Fake(true))
        service.classifyPosts(input).getOrThrow()
        service.classifyPosts(input.copy(contextText = "different neighbours")).getOrThrow()
        assertEquals(1, local.calls)
    }

    // Round 3 A-3: an undecided device answer was remembered by body only, so the post was never
    // judged on the device again, even after its context (title, first post, preceding posts) changed.
    @Test fun undecidedDevicePostIsJudgedAgainOnlyWhenItsContextChanges() = runBlocking {
        val local = Fake().apply { omit = setOf("2") }
        val service = HybridModerationService(local, Fake(true))
        assertTrue(service.classifyPosts(input).getOrThrow().all { it.isComplete })
        assertTrue(service.classifyPosts(input).getOrThrow().all { it.isComplete })
        assertEquals(1, local.calls, "the same input must not be re-inferred")
        local.omit = emptySet()
        local.hide = setOf("2")
        val results = service.classifyPosts(input.copy(contextText = "スレ題: テスト\n参考 No.1: 本文1")).getOrThrow()
        assertEquals(2, local.calls)
        assertEquals(listOf("2"), local.lastPostIds, "explicit decisions are still reused")
        assertTrue(results.single { it.postId == "2" }.shouldHide)
        assertTrue(results.all { it.isComplete })
    }

    @Test fun combinesIndependentCandidatesAndRetainsProviderReasons() = runBlocking {
        val local = Fake().apply { hide = setOf("1") }
        val cloud = Fake(true).apply { hide = setOf("2") }
        val results = HybridModerationService(local, cloud).classifyPosts(input).getOrThrow()
        assertEquals(setOf("1", "2"), results.filter { it.shouldHide }.map { it.postId }.toSet())
        assertTrue(results.all { it.isComplete })
        assertContains(results.first().reason.orEmpty(), "端末AI")
        assertContains(results[1].reason.orEmpty(), "OpenAI")
        assertFalse(results.last().shouldHide)
    }
    @Test fun publishesLocalBeforeSlowCloudAndCachesLocalAcross429Retry() = runBlocking {
        val local = Fake().apply { hide = setOf("2") }
        val cloud = Fake(true).apply { fail = true; gate = CompletableDeferred() }
        val service = HybridModerationService(local, cloud)
        val localPublished = CompletableDeferred<List<PostModerationResult>>()
        val job = async { service.classifyPosts(input) { if (it.any { r -> r.shouldHide }) localPublished.complete(it) } }
        val partial = withTimeout(2_000) { localPublished.await() }
        assertFalse(job.isCompleted)
        assertTrue(partial.none { it.isComplete })
        cloud.gate!!.complete(Unit)
        assertTrue(job.await().getOrThrow().any { it.postId == "2" && it.shouldHide })
        cloud.fail = false
        val complete = service.classifyPosts(input).getOrThrow()
        assertEquals(1, local.calls)
        assertEquals(2, cloud.calls)
        assertTrue(complete.all { it.isComplete })
    }
    @Test fun unavailableDeviceDoesNotDisableCloudOrLeaveDecisionsPending() = runBlocking {
        val local = Fake().apply { available = false }
        val cloud = Fake(true).apply { hide = setOf("2") }
        val service = HybridModerationService(local, cloud)
        assertTrue(service.getAvailability().supportsPostModeration)
        assertTrue(service.classifyPosts(input).getOrThrow().all { it.isComplete })
        assertEquals(0, local.calls)
        assertEquals(1, cloud.calls)
    }
    @Test fun localFailureStillAcceptsCloudAndDoesNotClaimBothComplete() = runBlocking {
        val local = Fake().apply { fail = true }
        val cloud = Fake(true).apply { hide = setOf("2") }
        val results = HybridModerationService(local, cloud).classifyPosts(input).getOrThrow()
        assertTrue(results.any { it.shouldHide })
        assertTrue(results.none { it.isComplete })
    }
    @Test fun cancellationDoesNotPublishLateResults() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val local = Fake().apply { this.gate = gate }
        val cloud = Fake(true).apply { this.gate = gate }
        var published = 0
        val job = launch { HybridModerationService(local, cloud).classifyPosts(input) { published++ } }
        yield()
        job.cancelAndJoin()
        gate.complete(Unit)
        yield()
        assertEquals(0, published)
    }
    @Test fun bothSelectionRequiresKeyAndSurvivesReload() = runBlocking {
        val storage = MemoryAiStorage()
        val store = AiConnectionStore(storage)
        store.load()
        assertFailsWith<IllegalArgumentException> { store.save(AiProvider.DEVICE, AiProvider.BOTH, DEFAULT_OPENAI_SUMMARY_MODEL) }
        store.save(AiProvider.DEVICE, AiProvider.BOTH, DEFAULT_OPENAI_SUMMARY_MODEL, "test-only-key")
        val reopened = AiConnectionStore(storage)
        reopened.load()
        assertEquals(AiProvider.BOTH, reopened.state.value.moderationProvider)
        assertEquals(AiProvider.DEVICE, reopened.state.value.summaryProvider)
    }

    /** Like the Android worker: one device request at a time, for summaries and judgements. */
    private class SingleQueueDevice(private val summaryMillis: Long, private val classifyMillis: Long) : OnDeviceAiService {
        private val queue = kotlinx.coroutines.sync.Mutex()
        val summaryStarted = CompletableDeferred<Unit>()
        override suspend fun getAvailability() = AiAvailability(true, supportsThreadSummary = true, supportsPostModeration = true)
        override suspend fun summarizeThread(input: ThreadSummaryInput) = queue.withLock {
            summaryStarted.complete(Unit)
            delay(summaryMillis)
            Result.success(ThreadSummary("要約", emptyList(), "端末"))
        }
        override suspend fun classifyPosts(input: PostModerationInput) = queue.withLock {
            delay(classifyMillis)
            Result.success(input.posts.map { PostModerationResult(it.id, false) })
        }
    }

    // Round 3 A5: waiting for a summary on the device AI must not consume the judgement deadline.
    @Test fun deviceDeadlineStartsAfterASummaryReleasesTheDeviceAi() = runBlocking {
        val local = SingleQueueDevice(summaryMillis = 600, classifyMillis = 300)
        val hybrid = HybridModerationService(local, Fake(true), localTimeoutMillis = 700)
        val routed = RoutedAiService(local, hybrid, "both")
        // As CompatThreadAiSession runs a summary: under the shared lock, deadline inside it.
        val summary = launch {
            routed.localInferenceLock.withLock { withTimeout(45_000) { routed.summarizeThread(ThreadSummaryInput("t", null, emptyList())) } }
        }
        local.summaryStarted.await()
        val results = routed.classifyPosts(input).getOrThrow()
        summary.join()
        assertEquals(3, results.size)
        assertTrue(results.all { it.isComplete }, "the device side must not time out while queued")
    }

    // Round 3 A-1: a device readiness check stuck behind a long request (model download) had no
    // deadline, so the whole judgement hit the callers' 150 s limit although OpenAI had answered.
    // Round 4 A4-1: this test used to require isComplete=true, i.e. a timed-out check was recorded
    // as "device AI unavailable" and the posts were never judged on the device. Like a busy queue,
    // the device side now stays pending and is judged once the device AI answers.
    @Test fun stuckDeviceReadinessCheckReturnsCloudDecisionsWithinTheWaitBudget() = runBlocking {
        val clock = kotlin.time.TestTimeSource()
        val gate = CompletableDeferred<Unit>()
        val local = Fake().apply { availabilityGate = gate } // never answers until released
        val cloud = Fake(true).apply { hide = setOf("2") }
        val service = HybridModerationService(local, cloud, timeSource = clock, localWaitMillis = 200)
        val results = withTimeout(5_000) { service.classifyPosts(input).getOrThrow() }
        assertEquals(setOf("2"), results.filter { it.shouldHide }.map { it.postId }.toSet())
        assertTrue(results.none { it.isComplete }, "the device side must be retried, not claimed done")
        // Within the re-check interval the next pass does not wait again and stays pending.
        val again = withTimeout(150) { service.classifyPosts(input).getOrThrow() }
        assertTrue(again.none { it.isComplete })
        assertEquals(0, local.calls)
        gate.complete(Unit)
        clock += 61.seconds
        assertTrue(service.classifyPosts(input).getOrThrow().all { it.isComplete })
        assertEquals(1, local.calls)
    }

    // Round 4 A4-2: the undecided memory was keyed with the whole batch context, so a post was
    // inferred again whenever the other posts sent with it changed.
    @Test fun undecidedDevicePostIsNotReinferredWhenOnlyItsBatchNeighboursChange() = runBlocking {
        val local = Fake().apply { omit = setOf("2") }
        val service = HybridModerationService(local, Fake(true))
        val context = LocalModerationContext("テスト", input.posts)
        withContext(ModerationPostContext(context)) {
            assertTrue(service.classifyPosts(input.copy(contextText = context.forPosts(input.posts))).getOrThrow().all { it.isComplete })
            val neighbours = input.posts.drop(1)
            val results = service.classifyPosts(PostModerationInput("t", neighbours, context.forPosts(neighbours))).getOrThrow()
            assertTrue(results.all { it.isComplete })
        }
        assertEquals(1, local.calls, "only the batch neighbours changed, not post 2's own context")
    }

    @Test fun undecidedDevicePostIsReinferredAtMostThreeTimesWithoutPerPostContext() = runBlocking {
        val local = Fake().apply { omit = setOf("1", "2", "3") }
        val service = HybridModerationService(local, Fake(true))
        repeat(5) { round ->
            assertTrue(service.classifyPosts(input.copy(contextText = "batch $round")).getOrThrow().all { it.isComplete })
        }
        assertEquals(3, local.calls)
    }

    @Test fun busyDeviceAiBeyondTheWaitBudgetLeavesDevicePendingButReturnsCloud() = runBlocking {
        val local = Fake()
        val cloud = Fake(true).apply { hide = setOf("1") }
        val service = HybridModerationService(local, cloud, localWaitMillis = 200)
        val release = CompletableDeferred<Unit>()
        val holder = launch { service.localInferenceLock.withLock { release.await() } } // e.g. a summary
        yield()
        val results = try { withTimeout(5_000) { service.classifyPosts(input).getOrThrow() } }
        finally { release.complete(Unit); holder.join() }
        assertTrue(results.any { it.postId == "1" && it.shouldHide })
        assertTrue(results.none { it.isComplete }, "the device side must be retried, not claimed done")
        assertEquals(0, local.calls)
        // Once the device AI is free, the next pass judges on the device with its full deadline.
        assertTrue(service.classifyPosts(input).getOrThrow().all { it.isComplete })
        assertEquals(1, local.calls)
    }
}
