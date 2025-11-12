package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path

private const val MAX_IMAGE_LOADER_PARALLELISM = 3
private const val IMAGE_MEMORY_CACHE_BYTES = 32L * 1024L * 1024L
private const val IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
private const val IMAGE_DISK_CACHE_DIR = "futacha_image_cache"

val LocalFutachaImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("FutachaImageLoader is not provided")
}

@Composable
fun rememberFutachaImageLoader(
    maxParallelism: Int = MAX_IMAGE_LOADER_PARALLELISM
): ImageLoader {
    val platformContext = LocalPlatformContext.current
    val fetcherDispatcher = remember(maxParallelism) {
        Dispatchers.IO.limitedParallelism(maxParallelism.coerceAtLeast(1))
    }
    val memoryCache = remember {
        MemoryCache.Builder()
            .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
            .build()
    }
    val diskCache = remember {
        createImageDiskCache()
    }
    return remember(platformContext, fetcherDispatcher, memoryCache, diskCache) {
        ImageLoader.Builder(platformContext)
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

private fun createImageDiskCache(): DiskCache? {
    val directory = runCatching { ensureCacheDirectory() }.getOrNull() ?: return null
    return runCatching {
        DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
            .build()
    }.getOrNull()
}

private fun ensureCacheDirectory(): Path {
    val cacheDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(IMAGE_DISK_CACHE_DIR)
    FileSystem.SYSTEM.createDirectories(cacheDir, mustCreate = false)
    return cacheDir
}
