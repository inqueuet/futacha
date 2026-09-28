package com.valoser.futacha.shared.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RoutedAiServiceTest {
    private class Fake(private val label: String, override val isExternalService: Boolean, private val available: Boolean = true) : OnDeviceAiService {
        var summaries = 0
        var moderations = 0
        override suspend fun getAvailability() = AiAvailability(available, supportsThreadSummary = available, supportsPostModeration = available, providerLabel = label)
        override suspend fun summarizeThread(input: ThreadSummaryInput): Result<ThreadSummary> {
            summaries++
            return Result.success(ThreadSummary(label, listOf(label), label))
        }
        override suspend fun classifyPosts(input: PostModerationInput): Result<List<PostModerationResult>> { moderations++; return Result.success(emptyList()) }
    }
    @Test fun summaryAndModerationUseTheirOwnProviders() = runBlocking {
        val summary = Fake("将来の別プロバイダー", true)
        val moderation = Fake("OpenAI", true)
        val routed = RoutedAiService(summary, moderation, "mixed")
        assertEquals("将来の別プロバイダー", routed.summarizeThread(ThreadSummaryInput("t", null, emptyList())).getOrThrow().headline)
        routed.classifyPosts(PostModerationInput("t", emptyList())).getOrThrow()
        assertEquals(1, summary.summaries)
        assertEquals(0, summary.moderations)
        assertEquals(0, moderation.summaries)
        assertEquals(1, moderation.moderations)
    }
    @Test fun unavailableLocalAiDoesNotDisableExternalModeration() = runBlocking {
        val routed = RoutedAiService(Fake("端末", false, false), Fake("OpenAI", true), "mixed")
        val availability = routed.getAvailability()
        assertTrue(availability.isAvailable)
        assertFalse(availability.supportsThreadSummary)
        assertTrue(availability.supportsPostModeration)
        assertFalse(availability.externalSummary)
        assertTrue(availability.externalModeration)
    }
}
