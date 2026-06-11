package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val DEFAULT_MAX_PARALLELISM = 3
private const val DEFAULT_IMAGE_MEMORY_CACHE_BYTES = 32L * 1024L * 1024L
private const val DEFAULT_IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
private const val LIGHT_IMAGE_MEMORY_CACHE_BYTES = 16L * 1024L * 1024L
private const val LIGHT_IMAGE_DISK_CACHE_BYTES = 64L * 1024L * 1024L
private const val LIGHT_MAX_PARALLELISM = 2
private const val VIDEO_FALLBACK_MAX_PARALLELISM = 2
private const val VIDEO_FALLBACK_TIMEOUT_MILLIS = 10_000L
internal const val IMAGE_DISK_CACHE_DIR = "futacha_image_cache"

data class ImageCacheConfig(
    val memoryCacheBytes: Long,
    val diskCacheBytes: Long,
    val parallelism: Int
)

val LocalFutachaImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaImageLoader is not provided")
}

expect fun ComponentRegistry.Builder.addPlatformImageComponents()
expect fun getPlatformDiskCacheDirectory(platformContext: Any?): String?

@Composable
@OptIn(ExperimentalCoroutinesApi::class)
fun rememberFutachaImageLoader(
    lightweightMode: Boolean = false
): ImageLoader {
    val platformContext = LocalPlatformContext.current
    val performanceProfile = remember(platformContext) {
        detectDevicePerformanceProfile(platformContext)
    }
    val cacheConfig = remember(lightweightMode, performanceProfile) {
        resolveCacheConfig(lightweightMode, performanceProfile)
    }
    val fetcherDispatcher: CoroutineDispatcher = remember(cacheConfig.parallelism) {
        AppDispatchers.imageFetch(cacheConfig.parallelism)
    }
    val decoderDispatcher: CoroutineDispatcher = remember(cacheConfig.parallelism) {
        AppDispatchers.imageDecode(cacheConfig.parallelism)
    }
    val memoryCache = remember(cacheConfig) {
        MemoryCache.Builder()
            .maxSizeBytes(cacheConfig.memoryCacheBytes)
            .build()
    }
    val diskCache = remember(platformContext, cacheConfig) {
        createImageDiskCache(platformContext, cacheConfig.diskCacheBytes)
    }
    return remember(platformContext, fetcherDispatcher, decoderDispatcher, memoryCache, diskCache) {
        ImageLoader.Builder(platformContext)
            .components {
                add(FutabaExtensionFallbackInterceptor())
                addPlatformImageComponents()
            }
            .fetcherCoroutineContext(fetcherDispatcher)
            .decoderCoroutineContext(decoderDispatcher)
            .memoryCache { memoryCache }
            .apply {
                diskCache?.let { cache ->
                    diskCache { cache }
                }
            }
            .build()
    }
}

/**
 * Interceptor that attempts to find the correct file extension for Futaba images.
 * Some boards still expose `.jpg` links for source media that are actually other formats,
 * so this interceptor retries likely alternatives.
 */
private class FutabaExtensionFallbackInterceptor : Interceptor {
    private val sourceExtensionRegex = Regex("(?i)\\.([a-z0-9]{3,4})(?=([?#].*)?$)")
    private val exhaustedUrlsMutex = Mutex()
    private val exhaustedUrls = LinkedHashSet<String>()
    private val exhaustedUrlMaxEntries = 256
    private val recoveredUrlsMutex = Mutex()
    private val recoveredUrls = LinkedHashMap<String, String>()
    private val recoveredUrlMaxEntries = 256
    private val videoFallbackSemaphore = Semaphore(permits = VIDEO_FALLBACK_MAX_PARALLELISM)
    private val videoFallbackExtensions = setOf("webm", "mp4")

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val initialRequest = chain.request
        val initialResult = chain.proceed()

        // Retry with alternative extensions for Futaba source media URLs.
        if (initialResult is ErrorResult) {
            val url = initialRequest.data.toString()
            if (url.contains("/src/")) {
                if (isExhausted(url)) {
                    return initialResult
                }
                readRecoveredUrl(url)?.let { recoveredUrl ->
                    val recoveredResult = proceedWithFallbackUrl(
                        chain = chain,
                        initialRequest = initialRequest,
                        fallbackUrl = recoveredUrl
                    )
                    if (recoveredResult is SuccessResult) {
                        markRecovered(url, recoveredUrl)
                        return recoveredResult
                    }
                    forgetRecoveredUrl(url)
                }
                val normalizedUrl = url.substringBefore('#').substringBefore('?')
                val currentExtension = sourceExtensionRegex
                    .find(normalizedUrl)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.lowercase()
                val fallbackExtensions = fallbackExtensionsFor(currentExtension)
                for (ext in fallbackExtensions) {
                    val newUrl = replaceOrAppendExtension(url, ext)
                    if (newUrl == url) continue
                    val newResult = proceedWithFallbackUrl(
                        chain = chain,
                        initialRequest = initialRequest,
                        fallbackUrl = newUrl
                    )

                    if (newResult is SuccessResult) {
                        markRecovered(url, newUrl)
                        return newResult
                    }
                }
                rememberExhaustedUrl(url)
            }
        }
        return initialResult
    }

    private fun fallbackExtensionsFor(currentExtension: String?): List<String> {
        val candidates = when (currentExtension) {
            "jpg", "jpeg" -> listOf("webm", "mp4", "gif", "png")
            "webm", "mp4" -> listOf("jpg", "jpeg", "png")
            "gif", "png", "webp" -> listOf("jpg", "jpeg")
            else -> listOf("jpg", "jpeg", "webm", "mp4")
        }
        return candidates
            .filterNot { it == currentExtension }
            .take(2)
    }

    private suspend fun proceedWithFallbackUrl(
        chain: Interceptor.Chain,
        initialRequest: coil3.request.ImageRequest,
        fallbackUrl: String
    ): ImageResult? {
        val request = initialRequest.newBuilder().data(fallbackUrl).build()
        val proceed: suspend () -> ImageResult = {
            chain.withRequest(request).proceed()
        }
        return if (fallbackUrl.extensionOrNull() in videoFallbackExtensions) {
            videoFallbackSemaphore.withPermit {
                withTimeoutOrNull(VIDEO_FALLBACK_TIMEOUT_MILLIS) {
                    proceed()
                } ?: run {
                    Logger.w(
                        "FutabaExtensionFallbackInterceptor",
                        "Timed out fetching video fallback candidate after ${VIDEO_FALLBACK_TIMEOUT_MILLIS}ms: $fallbackUrl"
                    )
                    null
                }
            }
        } else {
            proceed()
        }
    }

    private suspend fun isExhausted(url: String): Boolean {
        return exhaustedUrlsMutex.withLock { url in exhaustedUrls }
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
            exhaustedUrls.remove(url)
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

    private suspend fun rememberExhaustedUrl(url: String) {
        exhaustedUrlsMutex.withLock {
            exhaustedUrls.remove(url)
            exhaustedUrls.add(url)
            while (exhaustedUrls.size > exhaustedUrlMaxEntries) {
                val eldest = exhaustedUrls.firstOrNull() ?: break
                exhaustedUrls.remove(eldest)
            }
        }
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

fun resolveImageCacheDirectory(platformContext: Any?): Path? = runCatching {
    ensureCacheDirectory(platformContext)
}.getOrNull()

private fun createImageDiskCache(platformContext: Any?, maxBytes: Long): DiskCache? {
    val directory = resolveImageCacheDirectory(platformContext) ?: return null
    return runCatching {
        DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(maxBytes)
            .build()
    }.getOrNull()
}

private fun ensureCacheDirectory(platformContext: Any?): Path {
    val platformDirectory = getPlatformDiskCacheDirectory(platformContext)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.toPath()
    return platformDirectory ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(IMAGE_DISK_CACHE_DIR)
}

private fun resolveCacheConfig(
    lightweightMode: Boolean,
    performanceProfile: DevicePerformanceProfile
): ImageCacheConfig {
    val useLight = lightweightMode || performanceProfile.isLowSpec
    return if (useLight) {
        ImageCacheConfig(
            memoryCacheBytes = LIGHT_IMAGE_MEMORY_CACHE_BYTES,
            diskCacheBytes = LIGHT_IMAGE_DISK_CACHE_BYTES,
            parallelism = LIGHT_MAX_PARALLELISM
        )
    } else {
        ImageCacheConfig(
            memoryCacheBytes = DEFAULT_IMAGE_MEMORY_CACHE_BYTES,
            diskCacheBytes = DEFAULT_IMAGE_DISK_CACHE_BYTES,
            parallelism = DEFAULT_MAX_PARALLELISM
        )
    }
}
