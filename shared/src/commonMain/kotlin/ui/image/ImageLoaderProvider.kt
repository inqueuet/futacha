package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ComponentRegistry
import coil3.Extras
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.network.DeDupeConcurrentRequestStrategy
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import com.valoser.futacha.shared.media.isFutabaVideoExtension
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock

private const val DEFAULT_MAX_PARALLELISM = 6
private const val DEFAULT_IMAGE_MEMORY_CACHE_BYTES = 64L * 1024L * 1024L
private const val DEFAULT_IMAGE_DISK_CACHE_BYTES = 256L * 1024L * 1024L
private const val LIGHT_IMAGE_MEMORY_CACHE_BYTES = 32L * 1024L * 1024L
private const val LIGHT_IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
private const val LIGHT_MAX_PARALLELISM = 3
private const val VIDEO_FALLBACK_MAX_PARALLELISM = 3
private const val VIDEO_FALLBACK_TIMEOUT_MILLIS = 20_000L
private const val FALLBACK_NEGATIVE_CACHE_TTL_MILLIS = 30_000L
internal const val IMAGE_REQUEST_TIMEOUT_MILLIS = 90_000L
internal const val IMAGE_CONNECT_TIMEOUT_MILLIS = 15_000L
internal const val IMAGE_SOCKET_TIMEOUT_MILLIS = 15_000L
internal const val IMAGE_DISK_CACHE_DIR = "futacha_image_cache"
internal const val CATALOG_IMAGE_DISK_CACHE_DIR = "futacha_catalog_image_cache"
private const val IMAGE_DISK_CACHE_LOG_TAG = "FutachaImageLoader"

internal enum class ImageDiskCacheFailureStage {
    DIRECTORY_RESOLUTION,
    CACHE_CREATION
}

internal enum class ImageDiskCacheKind {
    GENERAL,
    CATALOG,
    CUSTOM
}

internal data class ImageDiskCacheFailureDiagnostic(
    val stage: ImageDiskCacheFailureStage,
    val location: CompatibilityCacheLocation,
    val cacheKind: ImageDiskCacheKind
)

internal fun imageDiskCacheKind(directoryName: String): ImageDiskCacheKind = when (directoryName) {
    IMAGE_DISK_CACHE_DIR -> ImageDiskCacheKind.GENERAL
    CATALOG_IMAGE_DISK_CACHE_DIR -> ImageDiskCacheKind.CATALOG
    else -> ImageDiskCacheKind.CUSTOM
}

internal fun imageDiskCacheFailureMessage(
    diagnostic: ImageDiskCacheFailureDiagnostic
): String = "Image disk cache unavailable; " +
    "stage=${diagnostic.stage.name.lowercase()}, " +
    "location=${diagnostic.location.name.lowercase()}, " +
    "cache=${diagnostic.cacheKind.name.lowercase()}"

internal fun <T> attemptImageDiskCacheInitialization(
    stage: ImageDiskCacheFailureStage,
    location: CompatibilityCacheLocation,
    directoryName: String,
    reportFailure: (ImageDiskCacheFailureDiagnostic) -> Unit,
    block: () -> T
): T? = try {
    block()
} catch (_: Throwable) {
    reportFailure(
        ImageDiskCacheFailureDiagnostic(
            stage = stage,
            location = location,
            cacheKind = imageDiskCacheKind(directoryName)
        )
    )
    null
}

private fun reportImageDiskCacheFailure(diagnostic: ImageDiskCacheFailureDiagnostic) {
    Logger.w(IMAGE_DISK_CACHE_LOG_TAG, imageDiskCacheFailureMessage(diagnostic))
}

@OptIn(ExperimentalCoilApi::class)
internal fun createFutachaConcurrentRequestStrategy() = DeDupeConcurrentRequestStrategy()

enum class CompatibilityCacheLocation {
    INTERNAL,
    DEVICE,
    EXTERNAL_SD
}

/** Accepts both final-APK raw values and labels saved by earlier compatibility builds. */
internal fun parseCompatCacheLocation(value: String?): CompatibilityCacheLocation = when (
    value?.trim()?.lowercase()
) {
    "internal", "内部ストレージ" -> CompatibilityCacheLocation.INTERNAL
    "sdcard", "外部sdカード", "外部sdカード(利用不可)" -> CompatibilityCacheLocation.EXTERNAL_SD
    else -> CompatibilityCacheLocation.DEVICE
}

data class ImageCacheConfig(
    val memoryCacheBytes: Long,
    val diskCacheBytes: Long,
    val parallelism: Int
)

internal data class ImageLoaderConfigurationIdentity(
    val cacheConfig: ImageCacheConfig,
    val cacheLocation: CompatibilityCacheLocation,
    val diskCacheDirectoryName: String
)

/**
 * Keeps the public loader identity stable while a disk-ready delegate replaces
 * the cold-start memory-only delegate. Mutations happen from Compose's main
 * lifecycle; request threads only read [activeDelegate].
 */
internal class StableImageLoaderDelegateState<T : Any>(
    initialDelegate: T,
    private val shutdownDelegate: (T) -> Unit
) {
    @kotlin.concurrent.Volatile
    private var activeDelegate: T = initialDelegate
    private val retiredDelegates = mutableListOf<T>()
    private var isShutdown = false

    fun current(): T = activeDelegate

    fun promote(nextDelegate: T) {
        if (isShutdown) {
            shutdownDelegate(nextDelegate)
            return
        }
        val previousDelegate = activeDelegate
        if (previousDelegate === nextDelegate) return
        retiredDelegates += previousDelegate
        activeDelegate = nextDelegate
    }

    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        retiredDelegates.forEach(shutdownDelegate)
        retiredDelegates.clear()
        shutdownDelegate(activeDelegate)
    }
}

internal class StableImageLoader(initialDelegate: ImageLoader) : ImageLoader {
    private val delegates = StableImageLoaderDelegateState(
        initialDelegate = initialDelegate,
        shutdownDelegate = ImageLoader::shutdown
    )

    override val defaults: ImageRequest.Defaults
        get() = delegates.current().defaults
    override val components: ComponentRegistry
        get() = delegates.current().components
    override val memoryCache: MemoryCache?
        get() = delegates.current().memoryCache
    override val diskCache: DiskCache?
        get() = delegates.current().diskCache

    override fun enqueue(request: ImageRequest) = delegates.current().enqueue(request)

    override suspend fun execute(request: ImageRequest): ImageResult =
        delegates.current().execute(request)

    override fun newBuilder(): ImageLoader.Builder = delegates.current().newBuilder()

    override fun shutdown() = delegates.shutdown()

    fun promote(nextDelegate: ImageLoader) = delegates.promote(nextDelegate)
}

data class FutabaExtensionFallbackPolicy(
    val maxAttempts: Int = 5,
    val allowVideoFallback: Boolean = true,
    val preferStaticCandidates: Boolean = true,
    val maxVideoAttempts: Int = 2,
    val videoFallbackTimeoutMillis: Long = VIDEO_FALLBACK_TIMEOUT_MILLIS,
    val negativeCacheTtlMillis: Long = FALLBACK_NEGATIVE_CACHE_TTL_MILLIS
)

private val FutabaExtensionFallbackPolicyKey = Extras.Key(FutabaExtensionFallbackPolicy())
private val VideoThumbnailRequestPriorityKey = Extras.Key(VideoThumbnailRequestPriority.VISIBLE)

internal fun ImageRequest.Builder.videoThumbnailRequestPriority(
    priority: VideoThumbnailRequestPriority
): ImageRequest.Builder = apply {
    extras[VideoThumbnailRequestPriorityKey] = priority
}

internal fun coil3.request.Options.videoThumbnailRequestPriority(): VideoThumbnailRequestPriority =
    getExtra(VideoThumbnailRequestPriorityKey)

private val apuSmallRequestRegex = Regex(
    "^https?://dec\\.2chan\\.net/(?:up2?|up)/+(?:thumb|src)/+[^?#]+(?:[?#].*)?$",
    RegexOption.IGNORE_CASE
)

internal fun suppressFutabaExtensionFallbackForUrl(url: String): Boolean =
    apuSmallRequestRegex.matches(url)

fun ImageRequest.Builder.futabaExtensionFallbackPolicy(
    policy: FutabaExtensionFallbackPolicy
): ImageRequest.Builder = apply {
    extras[FutabaExtensionFallbackPolicyKey] = policy
}

val LocalFutachaImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaImageLoader is not provided")
}

val LocalFutachaCatalogImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaCatalogImageLoader is not provided")
}

expect fun ComponentRegistry.Builder.addPlatformImageComponents()
expect fun getPlatformDiskCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation = CompatibilityCacheLocation.INTERNAL
): String?

expect fun getPlatformCacheAvailableBytes(
    platformContext: Any?,
    location: CompatibilityCacheLocation = CompatibilityCacheLocation.INTERNAL
): Long?

expect fun isPlatformRemovableCacheAvailable(platformContext: Any?): Boolean

@Composable
@OptIn(ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
fun rememberFutachaImageLoader(
    lightweightMode: Boolean = false,
    performanceProfile: DevicePerformanceProfile,
    httpClient: HttpClient? = null,
    diskCacheBytesOverride: Long? = null,
    cacheLocation: CompatibilityCacheLocation = CompatibilityCacheLocation.INTERNAL,
    parallelismOverride: Int? = null,
    diskCacheDirectoryName: String = IMAGE_DISK_CACHE_DIR
): ImageLoader {
    val platformContext = LocalPlatformContext.current
    val imageHttpClient = remember(httpClient) {
        httpClient?.config {
            install(HttpTimeout) {
                requestTimeoutMillis = IMAGE_REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = IMAGE_CONNECT_TIMEOUT_MILLIS
                socketTimeoutMillis = IMAGE_SOCKET_TIMEOUT_MILLIS
            }
        }
    }
    DisposableEffect(imageHttpClient, httpClient) {
        onDispose {
            if (imageHttpClient != null && imageHttpClient !== httpClient) {
                imageHttpClient.close()
            }
        }
    }
    val cacheConfig = remember(
        lightweightMode,
        performanceProfile,
        diskCacheBytesOverride,
        parallelismOverride
    ) {
        resolveCacheConfig(
            lightweightMode = lightweightMode,
            performanceProfile = performanceProfile,
            diskCacheBytesOverride = diskCacheBytesOverride,
            parallelismOverride = parallelismOverride
        )
    }
    val fetcherDispatcher: CoroutineDispatcher = remember(cacheConfig.parallelism) {
        AppDispatchers.imageFetch(cacheConfig.parallelism)
    }
    val decoderDispatcher: CoroutineDispatcher = remember(cacheConfig.parallelism) {
        AppDispatchers.imageDecode(cacheConfig.parallelism)
    }
    val configurationIdentity = remember(cacheConfig, cacheLocation, diskCacheDirectoryName) {
        ImageLoaderConfigurationIdentity(
            cacheConfig = cacheConfig,
            cacheLocation = cacheLocation,
            diskCacheDirectoryName = diskCacheDirectoryName
        )
    }
    // Give each loader generation ownership of its MemoryCache. If the platform
    // context or injected HTTP client changes, disposing the old loader must not
    // clear a cache that the replacement loader is already using.
    val memoryCache = remember(platformContext, imageHttpClient, configurationIdentity) {
        MemoryCache.Builder()
            .maxSizeBytes(cacheConfig.memoryCacheBytes)
            .build()
    }
    val normalMemoryPolicy = remember(cacheConfig, performanceProfile.totalRamMb) {
        resolveImageMemoryPressurePolicy(
            baseParallelism = cacheConfig.parallelism,
            baseMemoryCacheBytes = cacheConfig.memoryCacheBytes,
            memoryClassMb = performanceProfile.totalRamMb,
            level = ImageMemoryPressureLevel.NORMAL
        )
    }
    val pressureGate = remember(configurationIdentity, performanceProfile.totalRamMb) {
        AdaptiveImageRequestGate(normalMemoryPolicy)
    }
    val pressureScope = rememberCoroutineScope()
    var pressureSignal by remember(configurationIdentity) {
        mutableStateOf(ImageMemoryPressureSignal())
    }
    DisposableEffect(platformContext, configurationIdentity) {
        val monitor = createImageMemoryPressureMonitor(platformContext) { pressure ->
            pressureScope.launch {
                pressureSignal = pressureSignal.withPressure(pressure)
            }
        }
        onDispose { monitor.close() }
    }
    LaunchedEffect(
        pressureSignal,
        pressureGate,
        memoryCache,
        normalMemoryPolicy,
        performanceProfile.totalRamMb
    ) {
        val activeSignal = pressureSignal
        val policy = resolveImageMemoryPressurePolicy(
            baseParallelism = cacheConfig.parallelism,
            baseMemoryCacheBytes = cacheConfig.memoryCacheBytes,
            memoryClassMb = performanceProfile.totalRamMb,
            level = activeSignal.level
        )
        pressureGate.updatePolicy(policy)
        withContext(AppDispatchers.imageDecode(1)) {
            memoryCache.maxSize = policy.memoryCacheBytes
            memoryCache.trimToSize(policy.memoryCacheBytes)
        }
        if (activeSignal.level != ImageMemoryPressureLevel.NORMAL) {
            delay(imageMemoryPressureRecoveryDelayMillis(activeSignal.level))
            if (pressureSignal.generation == activeSignal.generation) {
                pressureSignal = ImageMemoryPressureSignal(
                    level = ImageMemoryPressureLevel.NORMAL,
                    generation = activeSignal.generation
                )
            }
        }
    }
    val stableImageLoader = remember(
        platformContext,
        fetcherDispatcher,
        decoderDispatcher,
        memoryCache,
        pressureGate,
        imageHttpClient,
        configurationIdentity
    ) {
        StableImageLoader(
            buildFutachaImageLoader(
                platformContext = platformContext,
                fetcherDispatcher = fetcherDispatcher,
                decoderDispatcher = decoderDispatcher,
                memoryCache = memoryCache,
                diskCache = null,
                pressureGate = pressureGate,
                imageHttpClient = imageHttpClient
            )
        )
    }
    // Resolving Context.cacheDir/getExternalFilesDirs and opening Coil's DiskCache can
    // touch the filesystem.  Do not perform either operation from composition: on a
    // cold Android process cacheDir initialization has been observed to block the main
    // thread for hundreds of milliseconds. The loader starts with memory caching,
    // then promotes its internal delegate when disk caching is ready. Its public
    // identity never changes during cold start, so existing image requests continue.
    LaunchedEffect(platformContext, stableImageLoader, configurationIdentity) {
        var createdDiskCache: DiskCache? = null
        var handedOffToLoader = false
        try {
            withContext(AppDispatchers.io) {
                createdDiskCache = createImageDiskCache(
                    platformContext,
                    cacheConfig.diskCacheBytes,
                    cacheLocation,
                    diskCacheDirectoryName
                )
            }
            if (!currentCoroutineContext().isActive) return@LaunchedEffect
            createdDiskCache?.let { readyDiskCache ->
                stableImageLoader.promote(
                    buildFutachaImageLoader(
                        platformContext = platformContext,
                        fetcherDispatcher = fetcherDispatcher,
                        decoderDispatcher = decoderDispatcher,
                        memoryCache = memoryCache,
                        diskCache = readyDiskCache,
                        pressureGate = pressureGate,
                        imageHttpClient = imageHttpClient
                    )
                )
                handedOffToLoader = true
            }
        } finally {
            if (!handedOffToLoader) {
                createdDiskCache?.shutdown()
            }
        }
    }
    return stableImageLoader
}

@OptIn(ExperimentalCoilApi::class)
private fun buildFutachaImageLoader(
    platformContext: coil3.PlatformContext,
    fetcherDispatcher: CoroutineDispatcher,
    decoderDispatcher: CoroutineDispatcher,
    memoryCache: MemoryCache,
    diskCache: DiskCache?,
    pressureGate: AdaptiveImageRequestGate,
    imageHttpClient: HttpClient?
): ImageLoader = ImageLoader.Builder(platformContext)
    .components {
        add(ImageMemoryPressureInterceptor(pressureGate))
        add(FutabaExtensionFallbackInterceptor())
        // A manually registered factory takes precedence over Coil's service-loaded
        // default. Reusing the app client also applies Android's main-thread-safe
        // response cleanup to image requests cancelled by Compose.
        imageHttpClient?.let {
            add(
                KtorNetworkFetcherFactory(
                    httpClient = it,
                    concurrentRequestStrategy = createFutachaConcurrentRequestStrategy()
                )
            )
        }
        addPlatformImageComponents()
    }
    .fetcherCoroutineContext(fetcherDispatcher)
    .decoderCoroutineContext(decoderDispatcher)
    .memoryCache { memoryCache }
    // Passing null explicitly prevents Coil's default singleton DiskCache from
    // touching the filesystem before Futacha's dedicated cache is ready.
    .diskCache(diskCache)
    .build()

/**
 * Interceptor that attempts to find the correct file extension for Futaba images.
 * Some boards still expose `.jpg` links for source media that are actually other formats,
 * so this interceptor retries likely alternatives.
 */
private class FutabaExtensionFallbackInterceptor : Interceptor {
    private val sourceExtensionRegex = Regex("(?i)\\.([a-z0-9]{3,4})(?=([?#].*)?$)")
    private val exhaustedUrlsMutex = Mutex()
    private val exhaustedUrls = LinkedHashMap<ExhaustedFallbackKey, ExhaustedFallbackEntry>()
    private val exhaustedUrlMaxEntries = 2_048
    private val recoveredUrlsMutex = Mutex()
    private val recoveredUrls = LinkedHashMap<String, String>()
    private val recoveredUrlMaxEntries = 2_048
    private val videoFallbackSemaphore = Semaphore(permits = VIDEO_FALLBACK_MAX_PARALLELISM)
    private val videoFallbackExtensions = FUTABA_COMPAT_VIDEO_EXTENSIONS

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val initialRequest = chain.request
        val initialResult = chain.proceed()
        val policy = initialRequest.getExtra(FutabaExtensionFallbackPolicyKey)

        // Retry with alternative extensions for Futaba source media URLs.
        if (initialResult is ErrorResult) {
            val url = initialRequest.data.toString()
            // あぷ小 already gives the application the real source URL.  A
            // failed derived thumbnail must not fan out into every supported
            // extension (and the old builder also produced /up2//src/ URLs).
            if (suppressFutabaExtensionFallbackForUrl(url)) return initialResult
            if (url.contains("/src/") && policy.maxAttempts > 0) {
                if (isExhausted(url, policy)) {
                    return initialResult
                }
                readRecoveredUrl(url)?.let { recoveredUrl ->
                    if (policy.allowsExtension(recoveredUrl.extensionOrNull())) {
                        val recoveredResult = proceedWithFallbackUrl(
                            chain = chain,
                            initialRequest = initialRequest,
                            fallbackUrl = recoveredUrl,
                            policy = policy
                        )
                        if (recoveredResult is SuccessResult) {
                            markRecovered(url, recoveredUrl)
                            return recoveredResult
                        }
                        forgetRecoveredUrl(url)
                    }
                }
                val normalizedUrl = url.substringBefore('#').substringBefore('?')
                val currentExtension = sourceExtensionRegex
                    .find(normalizedUrl)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.lowercase()
                val fallbackExtensions = resolveFutabaExtensionFallbackCandidates(
                    currentExtension = currentExtension,
                    policy = policy
                )
                for (ext in fallbackExtensions) {
                    val newUrl = replaceOrAppendExtension(url, ext)
                    if (newUrl == url) continue
                    val newResult = proceedWithFallbackUrl(
                        chain = chain,
                        initialRequest = initialRequest,
                        fallbackUrl = newUrl,
                        policy = policy
                    )

                    if (newResult is SuccessResult) {
                        markRecovered(url, newUrl)
                        return newResult
                    }
                }
                rememberExhaustedUrl(url, policy)
            }
        }
        return initialResult
    }

    private suspend fun proceedWithFallbackUrl(
        chain: Interceptor.Chain,
        initialRequest: coil3.request.ImageRequest,
        fallbackUrl: String,
        policy: FutabaExtensionFallbackPolicy
    ): ImageResult? {
        val request = initialRequest.newBuilder().data(fallbackUrl).build()
        val proceed: suspend () -> ImageResult = {
            chain.withRequest(request).proceed()
        }
        return if (fallbackUrl.extensionOrNull() in videoFallbackExtensions) {
            val timeoutMillis = policy.videoFallbackTimeoutMillis.coerceAtLeast(1L)
            videoFallbackSemaphore.withPermit {
                withTimeoutOrNull(timeoutMillis) {
                    proceed()
                } ?: run {
                    Logger.w(
                        "FutabaExtensionFallbackInterceptor",
                        "Timed out fetching video fallback candidate after ${timeoutMillis}ms: $fallbackUrl"
                    )
                    null
                }
            }
        } else {
            proceed()
        }
    }

    private suspend fun isExhausted(url: String, policy: FutabaExtensionFallbackPolicy): Boolean {
        val key = ExhaustedFallbackKey(url = url, policySignature = policy.cacheSignature())
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        return exhaustedUrlsMutex.withLock {
            val entry = exhaustedUrls.remove(key) ?: return@withLock false
            if (entry.isExpired(nowMillis)) {
                false
            } else {
                exhaustedUrls[key] = entry
                true
            }
        }
    }

    private suspend fun readRecoveredUrl(url: String): String? {
        return recoveredUrlsMutex.withLock {
            recoveredUrls.remove(url)?.also { recoveredUrl ->
                recoveredUrls[url] = recoveredUrl
            }
        }
    }

    private suspend fun markRecovered(url: String, recoveredUrl: String) {
        exhaustedUrlsMutex.withLock {
            removeExhaustedEntriesForUrlLocked(url)
        }
        recoveredUrlsMutex.withLock {
            recoveredUrls.remove(url)
            recoveredUrls[url] = recoveredUrl
            while (recoveredUrls.size > recoveredUrlMaxEntries) {
                val eldest = recoveredUrls.keys.firstOrNull() ?: break
                recoveredUrls.remove(eldest)
            }
        }
    }

    private suspend fun forgetRecoveredUrl(url: String) {
        recoveredUrlsMutex.withLock {
            recoveredUrls.remove(url)
        }
    }

    private suspend fun rememberExhaustedUrl(url: String, policy: FutabaExtensionFallbackPolicy) {
        if (policy.negativeCacheTtlMillis <= 0L) return
        val key = ExhaustedFallbackKey(url = url, policySignature = policy.cacheSignature())
        val entry = ExhaustedFallbackEntry(
            timestampMillis = Clock.System.now().toEpochMilliseconds(),
            ttlMillis = policy.negativeCacheTtlMillis
        )
        exhaustedUrlsMutex.withLock {
            exhaustedUrls.remove(key)
            exhaustedUrls[key] = entry
            while (exhaustedUrls.size > exhaustedUrlMaxEntries) {
                val eldest = exhaustedUrls.keys.firstOrNull() ?: break
                exhaustedUrls.remove(eldest)
            }
        }
    }

    private fun removeExhaustedEntriesForUrlLocked(url: String) {
        exhaustedUrls.keys
            .filter { it.url == url }
            .forEach(exhaustedUrls::remove)
    }

    private fun replaceOrAppendExtension(url: String, extension: String): String {
        return if (sourceExtensionRegex.containsMatchIn(url)) {
            url.replace(sourceExtensionRegex, ".$extension")
        } else {
            "$url.$extension"
        }
    }

    private fun String.extensionOrNull(): String? {
        return sourceExtensionRegex
            .find(substringBefore('#').substringBefore('?'))
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
    }
}

internal fun resolveFutabaExtensionFallbackCandidates(
    currentExtension: String?,
    policy: FutabaExtensionFallbackPolicy = FutabaExtensionFallbackPolicy()
): List<String> {
    if (policy.maxAttempts <= 0) return emptyList()
    val normalizedExtension = currentExtension?.lowercase()
    val candidates = reorderFutabaExtensionFallbackCandidates(
        candidates = when (normalizedExtension) {
            "jpg", "jpeg" -> listOf("webm", "mp4", "gif", "png", "webp")
            "webm", "mp4" -> listOf("jpg", "jpeg", "png", "gif", "webp")
            "gif" -> listOf("jpg", "jpeg", "png", "webp")
            "png" -> listOf("jpg", "jpeg", "gif", "webp")
            "webp" -> listOf("jpg", "jpeg", "gif", "png")
            else -> listOf("jpg", "jpeg", "gif", "png", "webp", "webm", "mp4")
        },
        preferStaticCandidates = policy.preferStaticCandidates
    )
    val result = ArrayList<String>(policy.maxAttempts)
    var videoAttempts = 0
    for (candidate in candidates) {
        if (candidate == normalizedExtension) continue
        if (!policy.allowsExtension(candidate)) continue
        if (isFutabaVideoExtension(candidate)) {
            if (videoAttempts >= policy.maxVideoAttempts) continue
            videoAttempts += 1
        }
        result += candidate
        if (result.size >= policy.maxAttempts) break
    }
    return result
}

private fun FutabaExtensionFallbackPolicy.allowsExtension(extension: String?): Boolean {
    if (extension == null) return true
    return allowVideoFallback || !isFutabaVideoExtension(extension)
}

private fun reorderFutabaExtensionFallbackCandidates(
    candidates: List<String>,
    preferStaticCandidates: Boolean
): List<String> {
    if (!preferStaticCandidates) return candidates
    return candidates
        .filterNot { isFutabaVideoExtension(it) } +
        candidates.filter { isFutabaVideoExtension(it) }
}

private data class ExhaustedFallbackKey(
    val url: String,
    val policySignature: String
)

private data class ExhaustedFallbackEntry(
    val timestampMillis: Long,
    val ttlMillis: Long
) {
    fun isExpired(nowMillis: Long): Boolean {
        return ttlMillis != Long.MAX_VALUE &&
            hasEpochIntervalElapsed(nowMillis, timestampMillis, ttlMillis)
    }
}

private fun FutabaExtensionFallbackPolicy.cacheSignature(): String {
    return listOf(
        maxAttempts,
        allowVideoFallback,
        preferStaticCandidates,
        maxVideoAttempts,
        videoFallbackTimeoutMillis,
        negativeCacheTtlMillis
    ).joinToString(separator = "|")
}

fun resolveImageCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation = CompatibilityCacheLocation.INTERNAL,
    directoryName: String = IMAGE_DISK_CACHE_DIR
): Path? = attemptImageDiskCacheInitialization(
    stage = ImageDiskCacheFailureStage.DIRECTORY_RESOLUTION,
    location = location,
    directoryName = directoryName,
    reportFailure = ::reportImageDiskCacheFailure
) {
    ensureCacheDirectory(platformContext, location, directoryName)
}

private fun createImageDiskCache(
    platformContext: Any?,
    maxBytes: Long,
    location: CompatibilityCacheLocation,
    directoryName: String
): DiskCache? {
    val directory = resolveImageCacheDirectory(platformContext, location, directoryName) ?: return null
    return attemptImageDiskCacheInitialization(
        stage = ImageDiskCacheFailureStage.CACHE_CREATION,
        location = location,
        directoryName = directoryName,
        reportFailure = ::reportImageDiskCacheFailure
    ) {
        DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(maxBytes)
            .build()
    }
}

private fun ensureCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation,
    directoryName: String
): Path {
    require(directoryName.isNotBlank() && '/' !in directoryName && '\\' !in directoryName) {
        "Invalid image cache directory"
    }
    val platformDirectory = getPlatformDiskCacheDirectory(platformContext, location)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.toPath()
    if (platformDirectory != null) {
        if (directoryName == IMAGE_DISK_CACHE_DIR) return platformDirectory
        return platformDirectory.parent?.resolve(directoryName)
            ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(directoryName)
    }
    return FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(directoryName)
}

internal fun resolveCacheConfig(
    lightweightMode: Boolean,
    performanceProfile: DevicePerformanceProfile,
    diskCacheBytesOverride: Long? = null,
    parallelismOverride: Int? = null
): ImageCacheConfig {
    val useLight = lightweightMode || performanceProfile.isLowSpec
    return if (useLight) {
        ImageCacheConfig(
            memoryCacheBytes = LIGHT_IMAGE_MEMORY_CACHE_BYTES,
            diskCacheBytes = diskCacheBytesOverride ?: LIGHT_IMAGE_DISK_CACHE_BYTES,
            parallelism = parallelismOverride?.coerceIn(1, 8) ?: LIGHT_MAX_PARALLELISM
        )
    } else {
        ImageCacheConfig(
            memoryCacheBytes = DEFAULT_IMAGE_MEMORY_CACHE_BYTES,
            diskCacheBytes = diskCacheBytesOverride ?: DEFAULT_IMAGE_DISK_CACHE_BYTES,
            parallelism = parallelismOverride?.coerceIn(1, 8) ?: DEFAULT_MAX_PARALLELISM
        )
    }
}
