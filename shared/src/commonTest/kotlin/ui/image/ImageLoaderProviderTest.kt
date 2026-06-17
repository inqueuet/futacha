package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageLoaderProviderTest {
    @Test
    fun resolveFutabaExtensionFallbackCandidates_uses_default_video_candidates() {
        assertEquals(
            listOf("webm", "mp4"),
            resolveFutabaExtensionFallbackCandidates("jpg")
        )
    }

    @Test
    fun resolveFutabaExtensionFallbackCandidates_can_disable_video_fallback_per_request() {
        val policy = FutabaExtensionFallbackPolicy(
            maxAttempts = 3,
            allowVideoFallback = false
        )

        assertEquals(
            listOf("gif", "png", "webp"),
            resolveFutabaExtensionFallbackCandidates("jpg", policy)
        )
    }

    @Test
    fun resolveFutabaExtensionFallbackCandidates_can_try_static_candidates_before_videos() {
        val policy = FutabaExtensionFallbackPolicy(
            maxAttempts = 5,
            allowVideoFallback = true,
            preferStaticCandidates = true,
            maxVideoAttempts = 2,
            videoFallbackTimeoutMillis = 2_500L
        )

        assertEquals(
            listOf("gif", "png", "webp", "webm", "mp4"),
            resolveFutabaExtensionFallbackCandidates("jpg", policy)
        )
    }

    @Test
    fun resolveFutabaExtensionFallbackCandidates_keeps_static_candidates_for_video_urls() {
        val policy = FutabaExtensionFallbackPolicy(
            maxAttempts = 3,
            allowVideoFallback = false
        )

        assertEquals(
            listOf("jpg", "jpeg", "png"),
            resolveFutabaExtensionFallbackCandidates("webm", policy)
        )
    }

    @Test
    fun resolveFutabaExtensionFallbackCandidates_respects_zero_attempt_policy() {
        val policy = FutabaExtensionFallbackPolicy(maxAttempts = 0)

        assertEquals(
            emptyList(),
            resolveFutabaExtensionFallbackCandidates("jpg", policy)
        )
    }
}
