package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.compat.*
import kotlinx.coroutines.*
import kotlin.test.*

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
        var fail = false
        var pause = false
        var hideOnly = false
        override suspend fun getAvailability(): AiAvailability {
            availabilityCalls++
            return AiAvailability(true, supportsThreadSummary = summaryAvailable, supportsPostModeration = true)
        }
        override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> {
            batches += input
            if (pause) awaitCancellation()
            return if (fail) Result.failure(IllegalStateException("failed")) else Result.success(
                input.posts.map { PostModerationResult(it.id, it.id.toInt() % 2 == 0) }.filter { !hideOnly || it.shouldHide })
        }
        override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
            summaries += input
            return Result.success(ThreadSummary(input.title.orEmpty(), listOf("要約本文"), "テスト端末AI"))
        }
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

    @Test fun externalModerationIncludesLastPostInOneBatchAndWorksWithoutLocalSummary() = runBlocking {
        val service = FakeAi(true).apply { summaryAvailable = false }
        var state = CompatThreadAiState()
        CompatThreadAiSession(service).analyze(snapshot((1..101).map(::post)), "スレ", true, true) { state = it }
        assertEquals(listOf(101), service.batches.map { it.posts.size })
        assertEquals("101", service.batches.single().posts.last().id)
        assertEquals(101, state.results.size)
        assertNotNull(state.summaryError)
        assertFalse(state.running)
        assertTrue(service.summaries.isEmpty())
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
        assertEquals(listOf("3", "11"), service.batches.last().posts.map { it.id })
        assertEquals(2, service.summaries.size)
        session.analyze(snapshot(initial, "other:123"), "別板", false, true) {}
        assertEquals(5, service.batches.size)
    }

    @Test fun localHideOnlyResponsesAlsoCacheUnflaggedPosts() = runBlocking {
        val service = FakeAi().apply { hideOnly = true }
        val session = CompatThreadAiSession(service)
        val source = snapshot((1..8).map(::post))
        session.analyze(source, "スレ", false, true) {}
        var state = CompatThreadAiState()
        session.analyze(source.copy(revision = 2), "スレ", false, true) { state = it }
        assertEquals(1, service.batches.size)
        assertEquals(8, state.results.size)
        assertEquals(setOf("2", "4", "6", "8"), state.resolveVisibility(emptySet(), true).hiddenPostNos)
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
}
