package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.DevicePerformanceProfile
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.FileSystem
import okio.Path

private const val DEFAULT_MAX_PARALLELISM = 3
private const val DEFAULT_IMAGE_MEMORY_CACHE_BYTES = 32L * 1024L * 1024L
private const val DEFAULT_IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
private const val LIGHT_IMAGE_MEMORY_CACHE_BYTES = 16L * 1024L * 1024L
private const val LIGHT_IMAGE_DISK_CACHE_BYTES = 64L * 1024L * 1024L
private const val LIGHT_MAX_PARALLELISM = 2
internal const val IMAGE_DISK_CACHE_DIR = "futacha_image_cache"

data class ImageCacheConfig(
    val memoryCacheBytes: Long,
    val diskCacheBytes: Long,
    val parallelism: Int
)

val LocalFutachaImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaImageLoader is not provided")
}

expect fun getPlatformDecoders(): List<Decoder.Factory>

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
    val memoryCache = remember(cacheConfig) {
        MemoryCache.Builder()
            .maxSizeBytes(cacheConfig.memoryCacheBytes)
            .build()
    }
    val diskCache = remember(cacheConfig) {
        createImageDiskCache(cacheConfig.diskCacheBytes)
    }
    return remember(platformContext, fetcherDispatcher, memoryCache, diskCache) {
        ImageLoader.Builder(platformContext)
            .components {
                add(FutabaExtensionFallbackInterceptor())
                getPlatformDecoders().forEach { add(it) }
            }
            .fetcherCoroutineContext(fetcherDispatcher)
            .decoderCoroutineContext(fetcherDispatcher)
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
 * Since the catalog parser guesses .jpg for full images, this interceptor handles
 * cases where the actual file is .gif, .png, .webm, or .mp4.
 */
private class FutabaExtensionFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val initialRequest = chain.request
        val initialResult = chain.proceed()

        // Only retry if we got an error and the URL looks like a Futaba source URL ending in .jpg
        if (initialResult is ErrorResult) {
            val url = initialRequest.data.toString()
            if (url.contains("/src/") && url.endsWith(".jpg", ignoreCase = true)) {
                // Try extensions in order of likelihood for "playable" or static content
                val extensions = listOf(".webp", ".gif", ".png", ".mp4", ".webm")
                
                for (ext in extensions) {
                    val newUrl = url.replace(Regex("(?i)\\.jpg$"), ext)
                    val newRequest = initialRequest.newBuilder().data(newUrl).build()
                    val newResult = chain.withRequest(newRequest).proceed()
                    
                    if (newResult is SuccessResult) {
                        return newResult
                    }
                }
            }
        }
        return initialResult
    }
}

fun resolveImageCacheDirectory(): Path? = runCatching { ensureCacheDirectory() }.getOrNull()

private fun createImageDiskCache(maxBytes: Long): DiskCache? {
    val directory = resolveImageCacheDirectory() ?: return null
    return runCatching {
        DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(maxBytes)
            .build()
    }.getOrNull()
}

private fun ensureCacheDirectory(): Path {
    return FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(IMAGE_DISK_CACHE_DIR)
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
