package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Round 4 A4-5: the iOS extract-only fallback was returned like a model summary and cached.
class ThreadSummaryFallbackTest {
    private val input = ThreadSummaryInput("t", "スレ題", listOf(
        Post("1", author = null, subject = null, timestamp = "", messageHtml = "十分に長い本文の一行目です", imageUrl = null, thumbnailUrl = null)))

    @Test fun fallbackSummaryIsMarkedButStillShowsTheExtract() {
        val fallback = buildFallbackThreadSummary(input, "Apple Intelligence")
        assertTrue(fallback.isFallbackThreadSummary)
        assertTrue(fallback.providerLabel.startsWith("Apple Intelligence"))
        assertTrue(fallback.bullets.isNotEmpty())
        assertTrue(normalizeThreadSummary(fallback).isFallbackThreadSummary, "the futacha normalisation keeps the mark")
        assertFalse(buildExtractiveThreadSummary(input, "Apple Intelligence").isFallbackThreadSummary)
    }
}
