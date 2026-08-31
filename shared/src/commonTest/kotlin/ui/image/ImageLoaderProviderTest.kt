package com.valoser.futacha.shared.ui.image

import coil3.network.DeDupeConcurrentRequestStrategy
import com.valoser.futacha.shared.compat.PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES
import com.valoser.futacha.shared.compat.parseCompatImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatThreadCacheQuotaBytes
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
class ImageLoaderProviderTest {
    @Test
    fun defaultCacheConfig_supportsLargeThumbnailCollections() {
        val regular = resolveCacheConfig(
            lightweightMode = false,
            performanceProfile = DevicePerformanceProfile(isLowRam = false, isLowStorage = false)
        )
        val lightweight = resolveCacheConfig(
            lightweightMode = true,
            performanceProfile = DevicePerformanceProfile(isLowRam = false, isLowStorage = false)
        )

        assertEquals(64L * 1024L * 1024L, regular.memoryCacheBytes)
        assertEquals(256L * 1024L * 1024L, regular.diskCacheBytes)
        assertEquals(6, regular.parallelism)
        assertEquals(32L * 1024L * 1024L, lightweight.memoryCacheBytes)
        assertEquals(128L * 1024L * 1024L, lightweight.diskCacheBytes)
        assertEquals(3, lightweight.parallelism)
    }

    @Test
    fun imageNetworkPolicyMatchesReferenceStallAndTotalTimeouts() {
        assertEquals(90_000L, IMAGE_REQUEST_TIMEOUT_MILLIS)
        assertEquals(15_000L, IMAGE_CONNECT_TIMEOUT_MILLIS)
        assertEquals(15_000L, IMAGE_SOCKET_TIMEOUT_MILLIS)
    }

    @Test
    fun concurrentRequestsForTheSameImageAreDeduplicated() {
        assertIs<DeDupeConcurrentRequestStrategy>(createFutachaConcurrentRequestStrategy())
    }

    @Test
    fun imageCacheCleanServiceContractAppliesReferenceQuotaToLoaderConfig() {
        val profile = DevicePerformanceProfile(isLowRam = false, isLowStorage = false)
        assertEquals(
            256L * 1024L * 1024L,
            resolveCacheConfig(
                lightweightMode = false,
                performanceProfile = profile,
                diskCacheBytesOverride = parseCompatImageCacheQuotaBytes("256MB")
            ).diskCacheBytes
        )
        assertEquals(
            PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES,
            parseCompatImageCacheQuotaBytes("無制限")
        )
        assertEquals(32L * 1024L * 1024L, parseCompatThreadCacheQuotaBytes("32MB"))
    }

    @Test
    fun resolveFutabaExtensionFallbackCandidates_tries_staticAndVideoCandidatesByDefault() {
        assertEquals(
            listOf("gif", "png", "webp", "webm", "mp4"),
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
