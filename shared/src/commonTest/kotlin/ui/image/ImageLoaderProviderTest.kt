package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import com.valoser.futacha.shared.compat.PRACTICALLY_UNLIMITED_COMPAT_IMAGE_CACHE_BYTES
import com.valoser.futacha.shared.compat.parseCompatImageCacheQuotaBytes
import com.valoser.futacha.shared.compat.parseCompatThreadCacheQuotaBytes
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
class ImageLoaderProviderTest {
    @Test
    fun memoryOnlyToDiskReadyPromotionKeepsOwnerAndInFlightDelegateAlive() {
        val memoryOnly = RecordingImageLoader()
        val diskReady = RecordingImageLoader()
        val stableLoader = StableImageLoader(memoryOnly)
        val ownerIdentity = stableLoader
        memoryOnly.startInFlightRequest()

        assertSame(memoryOnly.defaults, stableLoader.defaults)
        stableLoader.promote(diskReady)

        assertSame(ownerIdentity, stableLoader)
        assertSame(diskReady.defaults, stableLoader.defaults)
        assertEquals(0, memoryOnly.shutdownCount)
        assertEquals(0, memoryOnly.cancelledInFlightRequestCount)
        assertEquals(0, diskReady.shutdownCount)

        stableLoader.shutdown()
        stableLoader.shutdown()

        assertEquals(1, memoryOnly.shutdownCount)
        assertEquals(1, memoryOnly.cancelledInFlightRequestCount)
        assertEquals(1, diskReady.shutdownCount)
    }

    @Test
    fun delegateCreatedAfterOwnerShutdownIsClosedAsOrphan() {
        val shutdowns = mutableListOf<String>()
        val delegates = StableImageLoaderDelegateState(
            initialDelegate = "memory-only",
            shutdownDelegate = { shutdowns += it }
        )

        delegates.shutdown()
        delegates.promote("late-disk-ready")

        assertEquals(listOf("memory-only", "late-disk-ready"), shutdowns)
    }

    @Test
    fun loaderConfigurationIdentitySeparatesCacheAndProfileGenerations() {
        val regular = resolveCacheConfig(
            lightweightMode = false,
            performanceProfile = DevicePerformanceProfile(isLowRam = false, isLowStorage = false)
        )
        val lightweight = resolveCacheConfig(
            lightweightMode = true,
            performanceProfile = DevicePerformanceProfile(isLowRam = false, isLowStorage = false)
        )
        val internal = ImageLoaderConfigurationIdentity(
            cacheConfig = regular,
            cacheLocation = CompatibilityCacheLocation.INTERNAL,
            diskCacheDirectoryName = IMAGE_DISK_CACHE_DIR
        )

        assertEquals(internal, internal.copy())
        assertFalse(internal == internal.copy(cacheConfig = lightweight))
        assertFalse(internal == internal.copy(cacheLocation = CompatibilityCacheLocation.DEVICE))
        assertFalse(internal == internal.copy(diskCacheDirectoryName = CATALOG_IMAGE_DISK_CACHE_DIR))
    }

    private class RecordingImageLoader : ImageLoader {
        override val defaults = ImageRequest.Defaults()
        override val components = ComponentRegistry.Builder().build()
        override val memoryCache: MemoryCache? = null
        override val diskCache: DiskCache? = null
        var shutdownCount = 0
        var cancelledInFlightRequestCount = 0
        private var hasInFlightRequest = false

        fun startInFlightRequest() {
            hasInFlightRequest = true
        }

        override fun enqueue(request: ImageRequest) = error("not used")
        override suspend fun execute(request: ImageRequest): ImageResult = error("not used")
        override fun newBuilder(): ImageLoader.Builder = error("not used")
        override fun shutdown() {
            shutdownCount += 1
            if (hasInFlightRequest) {
                cancelledInFlightRequestCount += 1
                hasInFlightRequest = false
            }
        }
    }

    @Test
    fun imageDiskCacheInitializationSuccess_preservesResultWithoutDiagnostics() {
        val diagnostics = mutableListOf<ImageDiskCacheFailureDiagnostic>()

        val result = attemptImageDiskCacheInitialization(
            stage = ImageDiskCacheFailureStage.CACHE_CREATION,
            location = CompatibilityCacheLocation.INTERNAL,
            directoryName = IMAGE_DISK_CACHE_DIR,
            reportFailure = diagnostics::add
        ) {
            "disk-cache"
        }

        assertEquals("disk-cache", result)
        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun imageDiskCacheInitializationFailure_keepsMemoryOnlyFallbackAndReportsOnce() {
        val diagnostics = mutableListOf<ImageDiskCacheFailureDiagnostic>()

        val result = attemptImageDiskCacheInitialization(
            stage = ImageDiskCacheFailureStage.DIRECTORY_RESOLUTION,
            location = CompatibilityCacheLocation.EXTERNAL_SD,
            directoryName = CATALOG_IMAGE_DISK_CACHE_DIR,
            reportFailure = diagnostics::add
        ) {
            error("/private/device/path must not reach diagnostics")
        }

        assertNull(result)
        assertEquals(
            listOf(
                ImageDiskCacheFailureDiagnostic(
                    stage = ImageDiskCacheFailureStage.DIRECTORY_RESOLUTION,
                    location = CompatibilityCacheLocation.EXTERNAL_SD,
                    cacheKind = ImageDiskCacheKind.CATALOG
                )
            ),
            diagnostics
        )
    }

    @Test
    fun imageDiskCacheFailureMessage_containsCategoriesWithoutPathsOrExceptionText() {
        val sensitiveText = "/private/device/path"
        val diagnostic = ImageDiskCacheFailureDiagnostic(
            stage = ImageDiskCacheFailureStage.CACHE_CREATION,
            location = CompatibilityCacheLocation.INTERNAL,
            cacheKind = imageDiskCacheKind("$sensitiveText/custom-cache")
        )

        val message = imageDiskCacheFailureMessage(diagnostic)

        assertEquals(
            "Image disk cache unavailable; stage=cache_creation, location=internal, cache=custom",
            message
        )
        assertFalse(message.contains(sensitiveText))
    }

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
