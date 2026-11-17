package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CompatThreadAiSupportTest {
    private fun post(no: Int, body: String = "テスト本文 $no") = CompatPostSnapshot(
        position = no - 1, postNo = "$no", timestamp = "", messageHtml = body)
    private fun snapshot(posts: List<CompatPostSnapshot>, key: String = "board:123") = CompatThreadSnapshot(
        tabKey = key, revision = 1, fetchedAtEpochMillis = 0, posts = posts)
    private class FakeAi(override val externalModeration: Boolean = false) : OnDeviceAiService {
        val batches = mutableListOf<PostModerationInput>()
        val summaries = mutableListOf<ThreadSummaryInput>()
        var availabilityCalls = 0
        var summaryAvailable = true
        var moderationAvailable = true
        var fail = false
        var pause = false
        var hideOnly = false
        var availabilityFails = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getAvailability(): AiAvailability {
            availabilityCalls++
            if (availabilityFails) throw IllegalStateException("ipc")
            return AiAvailability(true, supportsThreadSummary = summaryAvailable, supportsPostModeration = moderationAvailable)
        }
        override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
            batches += input
            if (pause) awaitCancellation()
            gate?.await()
            return if (fail) Result.failure(IllegalStateException("failed")) else Result.success(
                input.posts.map { PostModerationResult(it.id, it.id.toInt() % 2 == 0) }.filter { !hideOnly || it.shouldHide })
        }
        override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
            summaries += input
            return Result.success(ThreadSummary(input.title.orEmpty(), listOf("要約本文"), "テスト端末AI"))
        }
    }

    @Test fun refreshAndVotesKeepAiEffectIdentityWhileBodyChangesRestartIt() {
        val source = snapshot(listOf(post(1)))
        val refreshed = source.copy(revision = 2, fetchedAtEpochMillis = 1000,
            expiresAtLabel = "soon", posts = source.posts.map { it.copy(saidaneLabel = "そうだねx3", referencedCount = 7) })
        assertEquals(buildCompatThreadAiSourceKey(source), buildCompatThreadAiSourceKey(refreshed))
        assertNotEquals(buildCompatThreadAiSourceKey(source), buildCompatThreadAiSourceKey(
            refreshed.copy(posts = listOf(post(1, "訂正された本文")))))
        assertNotEquals(buildCompatThreadAiSourceKey(source), buildCompatThreadAiSourceKey(
            source.copy(posts = source.posts + post(2))))
    }

    @Test fun offDoesNotCheckAvailabilityOrRunEitherModel() = runBlocking {
        val service = FakeAi()
        CompatThreadAiSession(service).analyze(snapshot(listOf(post(1))), "スレ", false, false) {
            fail("OFF must not publish an analysis")
        }
        assertEquals(0, service.availabilityCalls)
        assertTrue(service.batches.isEmpty())
        assertTrue(service.summaries.isEmpty())
    }

    @Test fun externalModerationBatchesPostsAndWorksWithoutLocalSummary() = runBlocking {
        val service = FakeAi(true).apply { summaryAvailable = false }
        var state = CompatThreadAiState()
        CompatThreadAiSession(service).analyze(snapshot((1..101).map(::post)), "スレ", true, true) { state = it }
        assertEquals(listOf(32, 32, 32, 5), service.batches.map { it.posts.size })
        assertEquals("101", service.batches.last().posts.last().id)
        assertEquals(101, state.results.size)
        assertNotNull(state.summaryError)
        assertFalse(state.running)
        assertTrue(service.summaries.isEmpty())
    }

    @Test fun viewportChangesOnlySendUncachedNearbyPostsAndKeepPreviousDecisions() = runBlocking {
        val service = FakeAi(true)
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..1000).map(::post))
        var state = CompatThreadAiState()
        session.analyze(source, "スレ", false, true, emptySet()) { state = it }
        assertTrue(service.batches.isEmpty())
        val first = (492..511).map(Int::toString).toSet()
        session.analyze(source, "スレ", false, true, first) { state = it }
        assertEquals(first, service.batches.single().posts.map { it.id }.toSet())
        val next = (502..521).map(Int::toString).toSet()
        session.analyze(source, "スレ", false, true, next) { state = it }
        assertEquals((512..521).map(Int::toString), service.batches.last().posts.map { it.id })
        assertEquals((492..521).map(Int::toString).toSet(), state.results.map { it.postId }.toSet())
        session.analyze(source, "スレ", false, true, first) { state = it }
        assertEquals(2, service.batches.size)
        assertTrue("492" in state.resolveVisibility(emptySet(), true).hiddenPostNos)
        val edited = source.copy(posts = source.posts.map { if (it.postNo == "500") post(500, "変更本文") else it })
        session.analyze(edited, "スレ", false, true, first) { state = it }
        assertEquals(listOf("500"), service.batches.last().posts.map { it.id })
        service.fail = true
        session.analyze(edited, "スレ", false, true, setOf("800")) { state = it }
        assertNotNull(state.moderationError)
        assertTrue("492" in state.resolveVisibility(emptySet(), true).hiddenPostNos)
        assertFalse("800" in state.resolveVisibility(emptySet(), true).hiddenPostNos)
    }

    @Test fun localCachesReuseUnchangedPostsAndInvalidateEditedBodyAndBoard() = runBlocking {
        val service = FakeAi()
        val session = CompatThreadAiSession(service)
        val initial = (1..10).map(::post)
        session.analyze(snapshot(initial), "スレ", true, true) {}
        assertEquals(listOf(8, 2), service.batches.map { it.posts.size })
        session.analyze(snapshot(initial).copy(revision = 2), "スレ", true, true) {}
        assertEquals(2, service.batches.size)
        assertEquals(1, service.summaries.size)
        val edited = initial.map { if (it.postNo == "3") it.copy(messageHtml = "編集された本文") else it } + post(11)
        session.analyze(snapshot(edited), "スレ", true, true) {}
        assertEquals(listOf("3", "4", "5", "11"), service.batches.last().posts.map { it.id })
        assertEquals(2, service.summaries.size)
        session.analyze(snapshot(initial, "other:123"), "別板", false, true) {}
        assertEquals(5, service.batches.size)
    }

    @Test fun localMissingDecisionsAreRetriedAndNeverCachedAsKeep() = runBlocking {
        val service = FakeAi().apply { hideOnly = true }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..8).map(::post))
        session.analyze(source, "スレ", false, true) {}
        var state = CompatThreadAiState()
        session.analyze(source.copy(revision = 2), "スレ", false, true) { state = it }
        assertEquals(2, service.batches.size)
        assertEquals(listOf("1", "3", "5", "7"), service.batches.last().posts.map { it.id })
        assertEquals(4, state.results.size)
        assertNotNull(state.moderationError)
        assertEquals(setOf("2", "4", "6", "8"), state.resolveVisibility(emptySet(), true).hiddenPostNos)
    }

    @Test fun localViewportCachesOnlyNearbyDecisionsAndRechecksChangedContext() = runBlocking {
        val service = FakeAi()
        val session = CompatThreadAiSession(service)
        val original = snapshot((1..160).map(::post))
        val nearby = (52..71).map(Int::toString).toSet()
        var state = CompatThreadAiState()
        session.analyze(original, "修理", false, true, nearby) { state = it }
        assertEquals(listOf(8, 8, 4), service.batches.map { it.posts.size })
        assertEquals(nearby, service.batches.flatMap { it.posts.map { it.id } }.toSet())
        assertTrue(service.batches.all { "スレ題: 修理" in it.contextText })
        session.analyze(original, "修理", false, true, setOf("150")) { state = it }
        val count = service.batches.size
        session.analyze(original.copy(posts = original.posts + post(161)), "修理", false, true, nearby) { state = it }
        assertEquals(count, service.batches.size)
        assertTrue(state.results.any { it.postId == "60" && it.shouldHide })
        val changed = original.copy(posts = original.posts.map { if (it.postNo == "51") post(51, "直前の訂正") else it })
        session.analyze(changed, "修理", false, true, nearby) { state = it }
        assertEquals(listOf("52", "53"), service.batches.last().posts.map { it.id })
        assertFalse(service.batches.flatMap { it.posts }.any { it.id == "100" })
    }

    @Test fun availabilityIsCheckedOncePerSessionUntilItExpiresOrFails() = runBlocking {
        val service = FakeAi()
        val clock = TestTimeSource()
        val session = CompatThreadAiSession(service, clock)
        val source = snapshot((1..3).map(::post))
        repeat(3) { session.analyze(source, "スレ", true, true, setOf("$it")) {} }
        assertEquals(1, service.availabilityCalls)
        clock += 6.minutes
        session.analyze(source, "スレ", true, false) {}
        assertEquals(2, service.availabilityCalls)
        clock += 6.minutes
        service.availabilityFails = true
        var state = CompatThreadAiState()
        session.analyze(source, "スレ", true, false) { state = it }
        assertNotNull(state.summaryError)
        service.availabilityFails = false
        session.analyze(source, "スレ", true, false) { state = it }
        assertEquals(4, service.availabilityCalls)
        assertNull(state.summaryError)
        clock += 10.seconds
        session.analyze(source, "スレ", true, false) {}
        assertEquals(4, service.availabilityCalls)
    }

    @Test fun moderationUnavailableExpiresEvenWhenSummaryRemainsAvailable() = runBlocking {
        val service = FakeAi().apply { moderationAvailable = false }
        val clock = TestTimeSource()
        val session = CompatThreadAiSession(service, clock)
        assertFalse(session.availability(forModeration = true).getOrThrow().supportsPostModeration)
        clock += 31.seconds
        service.moderationAvailable = true
        assertTrue(session.availability(forModeration = true).getOrThrow().supportsPostModeration)
        assertEquals(2, service.availabilityCalls)
        clock += 31.seconds
        session.availability(forModeration = true)
        assertEquals(2, service.availabilityCalls)
    }

    @Test fun viewportChangesQueuePostsWithoutCancellingAndSummaryRunsBesideModeration() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val service = FakeAi(true).apply { this.gate = gate }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..100).map(::post))
        val viewport = MutableStateFlow<Set<String>?>((1..10).map(Int::toString).toSet())
        var part = CompatThreadAiModerationPart()
        val job = launch { session.moderate(source, "スレ", viewport) { part = it } }
        withTimeout(10_000) { while (service.batches.isEmpty()) yield() }
        viewport.value = (5..15).map(Int::toString).toSet()
        yield()
        viewport.value = (5..20).map(Int::toString).toSet()
        var summary: CompatThreadAiSummaryPart? = null
        session.summarize(source, "スレ") { summary = it }
        assertNotNull(summary?.summary, "summary must not wait behind moderation")
        gate.complete(Unit)
        withTimeout(10_000) { while (part.running || part.results.none { it.postId == "20" }) yield() }
        assertEquals(listOf((1..10).map(Int::toString), (11..20).map(Int::toString)),
            service.batches.map { batch -> batch.posts.map { it.id } })
        assertEquals(1, service.availabilityCalls)
        job.cancelAndJoin()
    }

    @Test fun localSummaryWaitsOutsideItsInferenceDeadlineWhileModerationRuns() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val service = FakeAi().apply { this.gate = gate }
        val session = CompatThreadAiSession(service)
        val source = snapshot(listOf(post(1)))
        val moderation = launch { session.analyze(source, "スレ", false, true) {} }
        withTimeout(10_000) { while (service.batches.isEmpty()) yield() }
        val summary = launch { session.summarize(source, "スレ") {} }
        repeat(10) { yield() }
        assertTrue(service.summaries.isEmpty())
        gate.complete(Unit)
        moderation.join()
        summary.join()
        assertEquals(1, service.summaries.size)
    }

    @Test fun failedExternalModerationStopsRunningWhileViewportRemainsUnchanged() = runBlocking {
        val service = FakeAi(true).apply { fail = true }
        val viewport = MutableStateFlow<Set<String>?>(setOf("1"))
        var part = CompatThreadAiModerationPart()
        val job = launch { CompatThreadAiSession(service).moderate(snapshot(listOf(post(1))), "スレ", viewport) { part = it } }
        withTimeout(10_000) { while (part.error == null) yield() }
        assertFalse(part.running)
        assertTrue(job.isActive, "viewport worker should remain available for a retry")
        job.cancelAndJoin()
    }

    @Test fun aiHidingOmitsRowsAndNgToggleRestoresThemWithoutMutatingSnapshot() {
        val source = snapshot((1..5).map(::post))
        val index = buildCompatThreadNgRuleIndex(emptyList(), source.tabKey).copy(postNos = setOf("3"))
        val hidden = setOf("2", "3")
        assertEquals(listOf("1", "4", "5"), filterCompatThreadPosts(source.posts, true, index, hidden).map { it.postNo })
        assertEquals(source.posts, filterCompatThreadPosts(source.posts, false, index, hidden))
        assertEquals(listOf("1", "2", "4", "5"), filterCompatThreadPosts(source.posts, true, index).map { it.postNo })
        assertEquals(5, source.posts.size)
        assertEquals(setOf("3"), index.postNos)
        assertEquals(listOf("2", "3"), extractCompatPosts(source.posts, CompatExtractionKind.NG, source.tabKey,
            aiHiddenPostNos = hidden).map { it.postNo })
        assertEquals(emptyList(), extractCompatPosts(source.posts, CompatExtractionKind.OWN, source.tabKey,
            aiHiddenPostNos = hidden))
    }

    @Test fun opOwnAndDuplicateIdsRemainVisibleAndAutoHideOffKeepsOnlyCandidates() {
        val source = snapshot(listOf(post(1), post(2), post(3), post(4), post(4), post(5)))
        val state = CompatThreadAiState(posts = source.toThreadPage(source.tabKey).posts,
            results = (1..6).map { PostModerationResult("$it", true) })
        val resolved = state.resolveVisibility(setOf("2"), true)
        assertEquals(setOf("3", "5"), resolved.hiddenPostNos)
        assertEquals(emptySet(), state.resolveVisibility(setOf("2"), false).hiddenPostNos)
        assertEquals(setOf("3", "5"), state.resolveVisibility(setOf("2"), false).candidatePostNos)
    }

    @Test fun failedModerationDoesNotHidePostsAndRetryStillRunsSummary() = runBlocking {
        val service = FakeAi(true).apply { fail = true }
        val session = CompatThreadAiSession(service)
        var state = CompatThreadAiState()
        val source = snapshot((1..3).map(::post))
        session.analyze(source, "スレ", true, true) { state = it }
        assertNotNull(state.moderationError)
        assertNotNull(state.summary)
        assertTrue(state.resolveVisibility(emptySet(), true).hiddenPostNos.isEmpty())
        service.fail = false
        session.analyze(source, "スレ", true, true) { state = it }
        assertNull(state.moderationError)
        assertEquals(setOf("2"), state.resolveVisibility(emptySet(), true).hiddenPostNos)
    }

    @Test fun cancellationNeverPublishesLateDecisionsOrStartsSummary() = runBlocking {
        val service = FakeAi(true).apply { pause = true }
        val states = mutableListOf<CompatThreadAiState>()
        val job = launch { CompatThreadAiSession(service).analyze(snapshot((1..9).map(::post)), "スレ", true, true) { states += it } }
        withTimeout(10_000) { while (service.batches.isEmpty()) yield() }
        job.cancelAndJoin()
        assertTrue(states.all { it.results.isEmpty() })
        assertTrue(service.summaries.isEmpty())
    }

    @Test fun thresholdCategoriesAndAutoHideImmediatelyChangeNgVisibility() {
        val source = snapshot((1..3).map(::post))
        val index = buildCompatThreadNgRuleIndex(emptyList(), source.tabKey)
        val scores = OpenAiModerationScores(mapOf("harassment" to 0.8f, "sexual" to 0.95f))
        fun visible(threshold: Float, autoHide: Boolean, categories: Set<String>): List<String> {
            val state = CompatThreadAiState(posts = source.toThreadPage(source.tabKey).posts,
                results = listOf(scores.classify("2", threshold, categories)))
                .resolveVisibility(emptySet(), autoHide)
            return filterCompatThreadPosts(source.posts, true, index, state.hiddenPostNos).map { it.postNo }
        }
        assertEquals(listOf("1", "3"), visible(0.8f, true, setOf("harassment")))
        assertEquals(listOf("1", "2", "3"), visible(0.85f, true, setOf("harassment")))
        assertEquals(listOf("1", "3"), visible(0.85f, true, setOf("sexual")))
        assertEquals(listOf("1", "2", "3"), visible(0.8f, false, setOf("harassment")))
    }

    @Test fun summaryOnlyNeverClassifiesAndSettingsRouteIsAnExtension() = runBlocking {
        val service = FakeAi()
        var state = CompatThreadAiState()
        CompatThreadAiSession(service).analyze(snapshot((1..3).map(::post)), "スレ", true, false) { state = it }
        assertNotNull(state.summary)
        assertTrue(service.batches.isEmpty())
        val entries = compatRootSettingsGroups("test")
        assertTrue(entries.first { it.first == "ふたちゃ拡張" }.second.any { it.route == "ai" })
        assertEquals("AI・補助機能", "ai".compatSettingsTitle())
    }

    @Test fun speechSkipsHiddenRepliesWithoutLosingTheRawCursor() {
        val posts = (1..4).map(::post)
        val batch = buildCompatReadAloudBatch(posts, 1, 2, hiddenPostNos = setOf("2", "3"))
        assertEquals("テスト本文 4", batch.text)
        assertEquals(4, batch.nextPostIndex)
        assertEquals(0, batch.nextCharacterOffset)
        assertEquals("テスト本文 2", buildCompatReadAloudBatch(posts, 1, 0).text)
    }

    /** Device AI whose availability check and inference can be held, and which records overlap. */
    private class HeldDevice : OnDeviceAiService {
        var availabilityGate: CompletableDeferred<Unit>? = null
        var classifyGate: CompletableDeferred<Unit>? = null
        val classifyStarted = CompletableDeferred<Unit>()
        var running = 0
        var overlapped = false
        val summaries = mutableListOf<ThreadSummaryInput>()
        override suspend fun getAvailability(): AiAvailability {
            availabilityGate?.await()
            return AiAvailability(true, supportsThreadSummary = true, supportsPostModeration = true)
        }
        private suspend fun <T> busy(block: suspend () -> T): T {
            if (running++ > 0) overlapped = true
            try { return block() } finally { running-- }
        }
        override suspend fun summarizeThread(input: ThreadSummaryInput) = busy {
            summaries += input
            Result.success(ThreadSummary("要約", listOf("本文"), "端末"))
        }
        override suspend fun classifyPosts(input: PostModerationInput) = busy {
            classifyStarted.complete(Unit)
            classifyGate?.await()
            Result.success(input.posts.map { PostModerationResult(it.id, false) })
        }
    }

    // Round 3 A2: the OpenAI side of BOTH starts while the device availability check is busy.
    @Test fun hybridCloudStartsWithoutWaitingForTheDeviceAvailabilityCheck() = runBlocking {
        val device = HeldDevice().apply { availabilityGate = CompletableDeferred() }
        val cloud = FakeAi(true)
        val service = RoutedAiService(device, HybridModerationService(device, cloud), "both")
        val session = CompatThreadAiSession(service)
        var part = CompatThreadAiModerationPart()
        val job = launch { session.moderate(snapshot((1..3).map(::post)), "スレ", MutableStateFlow<Set<String>?>(null)) { part = it } }
        launch { session.summarize(snapshot((1..3).map(::post)), "スレ") {} }
        try {
            withTimeout(5_000) { while (part.results.size < 3) yield() }
            assertEquals(1, cloud.batches.size)
            assertTrue(part.results.none { it.isComplete }, "device side is still pending")
        } finally {
            device.availabilityGate!!.complete(Unit)
        }
        withTimeout(5_000) { while (part.results.any { !it.isComplete }) yield() }
        job.cancelAndJoin()
    }

    // Round 3 A5: a summary and the hybrid device judgement never run on the device AI at once.
    @Test fun hybridDeviceJudgementAndSummaryQueueForTheSameDeviceAi() = runBlocking {
        val device = HeldDevice().apply { classifyGate = CompletableDeferred() }
        val service = RoutedAiService(device, HybridModerationService(device, FakeAi(true)), "both")
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..3).map(::post))
        val moderation = launch { session.moderate(source, "スレ", MutableStateFlow<Set<String>?>(null)) {} }
        withTimeout(5_000) { device.classifyStarted.await() }
        var summary: CompatThreadAiSummaryPart? = null
        val summarizing = launch { session.summarize(source, "スレ") { summary = it } }
        delay(300)
        assertTrue(device.summaries.isEmpty(), "the summary waits for the device judgement")
        device.classifyGate!!.complete(Unit)
        summarizing.join()
        assertNotNull(summary?.summary)
        assertFalse(device.overlapped)
        moderation.cancelAndJoin()
    }

    // Round 3 A10: undecided device answers are retried a bounded number of times per input.
    @Test fun undecidedDevicePostsAreRetriedBoundedTimesUntilAnExplicitRetry() = runBlocking {
        val service = FakeAi().apply { hideOnly = true }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..2).map(::post))
        fun sentPost1() = service.batches.count { batch -> batch.posts.any { it.id == "1" } }
        repeat(5) { session.analyze(source, "スレ", false, true) {} }
        assertEquals(3, sentPost1())
        // An edited body is a new input and is judged again.
        session.analyze(snapshot(listOf(post(1, "編集後"), post(2))), "スレ", false, true) {}
        assertEquals(4, sentPost1())
        session.analyze(source, "スレ", false, true) {}
        assertEquals(4, sentPost1())
        session.moderate(source, "スレ", kotlinx.coroutines.flow.flowOf(null), retry = 1) {}
        assertEquals(5, sentPost1())
    }

    @Test fun deviceFailuresAreNotCountedAsUndecidedAnswers() = runBlocking {
        val service = FakeAi().apply { fail = true }
        val session = CompatThreadAiSession(service)
        val source = snapshot(listOf(post(1)))
        repeat(4) { session.analyze(source, "スレ", false, true) {} }
        assertEquals(4, service.batches.size)
    }

    // Round 4 A4-3: posts whose retries were used up were told that moving the viewport retries them.
    @Test fun exhaustedRetriesReportAnAccurateRetryAndClearingAllowsIt() = runBlocking {
        val service = FakeAi().apply { hideOnly = true }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..2).map(::post))
        val errors = (1..3).map {
            var error: String? = null
            session.analyze(source, "スレ", false, true) { error = it.moderationError ?: error }
            error
        }
        assertContains(errors[0].orEmpty(), "表示位置を移して戻す")
        assertEquals(COMPAT_AI_MODERATION_EXHAUSTED, errors[2])
        session.analyze(source, "スレ", false, true) {}
        assertEquals(3, service.batches.size)
        // Turning moderation off and on clears the budget, as the message says.
        session.clearModerationRetries()
        session.analyze(source, "スレ", false, true) {}
        assertEquals(4, service.batches.size)
    }

    // Round 4 A4-5: an extract-only fallback was cached as the thread's summary and never regenerated.
    @Test fun fallbackSummaryIsShownWithRetryButNotCached() = runBlocking {
        var fallback = true
        val service = object : OnDeviceAiService {
            var summaries = 0
            override suspend fun getAvailability() = AiAvailability(true, supportsThreadSummary = true, supportsPostModeration = true)
            override suspend fun classifyPosts(input: PostModerationInput) = Result.success(emptyList<PostModerationResult>())
            override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
                summaries++
                return Result.success(if (fallback) buildFallbackThreadSummary(input, "端末AI")
                    else ThreadSummary("見出し", listOf("要約"), "端末AI"))
            }
        }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..2).map(::post))
        var part = CompatThreadAiSummaryPart()
        session.summarize(source, "スレ") { part = it }
        assertTrue(part.summary!!.isFallbackThreadSummary)
        assertEquals(COMPAT_AI_SUMMARY_FALLBACK, part.error, "the panel shows its retry button")
        fallback = false
        session.summarize(source, "スレ") { part = it }
        assertEquals(2, service.summaries, "the fallback is regenerated")
        assertNull(part.error)
        session.summarize(source, "スレ") { part = it }
        assertEquals(2, service.summaries, "a model summary is cached")
    }
}
