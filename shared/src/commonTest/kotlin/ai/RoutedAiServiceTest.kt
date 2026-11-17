package com.valoser.futacha.shared.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private class Gated(private val label: String, override val isExternalService: Boolean) : OnDeviceAiService {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        override suspend fun getAvailability(): AiAvailability {
            started.complete(Unit)
            gate.await()
            return AiAvailability(true, supportsThreadSummary = true, supportsPostModeration = true, providerLabel = label)
        }
        override suspend fun summarizeThread(input: ThreadSummaryInput) = Result.failure<ThreadSummary>(IllegalStateException("unused"))
        override suspend fun classifyPosts(input: PostModerationInput) = Result.success(emptyList<PostModerationResult>())
    }

    // Round 3 A2: a busy device check must not delay the OpenAI side.
    @Test fun moderationAvailabilityDoesNotWaitForTheDeviceSummaryCheck() = runBlocking {
        val device = Gated("端末", false)
        val routed = RoutedAiService(device, Fake("OpenAI", true), "mixed")
        val moderation = withTimeout(1_000) { routed.moderationAvailability() }
        assertTrue(moderation.supportsPostModeration)
        assertFalse(moderation.supportsThreadSummary)
        assertEquals("OpenAI Moderation", moderation.moderationProviderLabel)
        assertFalse(device.started.isCompleted)
        val summary = async { routed.summaryAvailability() }
        device.gate.complete(Unit)
        assertTrue(summary.await().supportsThreadSummary)
        assertFalse(summary.await().supportsPostModeration)
    }

    @Test fun combinedAvailabilityProbesBothProvidersConcurrently() = runBlocking {
        val device = Gated("端末", false)
        val cloud = Gated("OpenAI", true)
        val routed = RoutedAiService(device, cloud, "mixed")
        val availability = async { routed.getAvailability() }
        withTimeout(1_000) { device.started.await(); cloud.started.await() }
        device.gate.complete(Unit)
        cloud.gate.complete(Unit)
        assertTrue(availability.await().supportsThreadSummary)
        assertTrue(availability.await().supportsPostModeration)
    }

    @Test fun unavailableTaskReasonKeepsItsPrefix() = runBlocking {
        val routed = RoutedAiService(Fake("端末", false, false), Fake("OpenAI", true, false), "mixed")
        assertEquals("要約: 利用できません", routed.summaryAvailability().unavailableReason)
        assertEquals("判定: 利用できません", routed.moderationAvailability().unavailableReason)
    }

    @Test fun summarySharesTheHybridDeviceLockOnlyForTheSameDeviceAi() {
        val device = Fake("端末", false)
        val hybrid = HybridModerationService(device, Fake("OpenAI", true))
        assertSame(hybrid.localInferenceLock, RoutedAiService(device, hybrid, "both").localInferenceLock)
        assertNotSame(hybrid.localInferenceLock, RoutedAiService(Fake("別", false), hybrid, "both").localInferenceLock)
    }
}
