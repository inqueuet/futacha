package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import com.valoser.futacha.shared.media.source.OriginalMediaCacheConfiguration
import com.valoser.futacha.shared.media.source.OriginalMediaSession
import com.valoser.futacha.shared.media.source.OriginalMediaSource
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

internal const val ORIGINAL_MEDIA_CACHE_DIR = "futacha_original_media_cache"
val LocalOriginalMediaSource = staticCompositionLocalOf<OriginalMediaSource?> { null }

internal data class ImageDiskBudget(val images: Long, val originals: Long)
internal fun splitImageDiskBudget(total: Long): ImageDiskBudget {
    require(total >= 2)
    val originals = total / 2
    return ImageDiskBudget(total - originals, originals)
}

/** Cold background launches have no Compose tree to configure the original store.
 * Foreground configuration remains authoritative, including compatibility mode quotas. */
suspend fun initializeOriginalMediaCache(
    session: OriginalMediaSession,
    platformContext: Any?,
    lightweightMode: Boolean
) {
    val directory = withContext(AppDispatchers.io) {
        resolveImageCacheDirectory(platformContext, CompatibilityCacheLocation.INTERNAL, ORIGINAL_MEDIA_CACHE_DIR)
            ?: okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(ORIGINAL_MEDIA_CACHE_DIR)
    }
    val total = (if (lightweightMode) 128L else 256L) * 1024 * 1024
    session.configureIfAbsent(OriginalMediaCacheConfiguration(directory, splitImageDiskBudget(total).originals))
}

@Composable
internal fun ConfigureOriginalMediaCache(
    session: OriginalMediaSession?,
    platformContext: Any?,
    maxBytes: Long,
    location: CompatibilityCacheLocation
) {
    LaunchedEffect(session, platformContext, maxBytes, location) {
        if (session == null) return@LaunchedEffect
        // Resolving Android's external/internal paths can touch storage.
        val directory = withContext(AppDispatchers.io) {
            resolveImageCacheDirectory(platformContext, location, ORIGINAL_MEDIA_CACHE_DIR)
                ?: okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(ORIGINAL_MEDIA_CACHE_DIR)
        }
        session.configure(OriginalMediaCacheConfiguration(directory, maxBytes))
    }
}

internal fun ImageLoader.originalMediaSource(): OriginalMediaSource? =
    (this as? StableImageLoader)?.originalMediaSource

/** Count this only under the general image cache, even when catalog shares the source. */
internal suspend fun ImageLoader.originalMediaCacheSizeBytes(): Long = originalMediaSource()?.sizeBytes() ?: 0L

internal suspend fun clearFutachaImageCaches(vararg loaders: ImageLoader, clearMemory: Boolean = true) {
    withContext(AppDispatchers.io) {
        loaders.mapNotNull { it.originalMediaSource() }.distinct().forEach { it.clear() }
        loaders.mapNotNull { it.diskCache }.distinct().forEach { it.clear() }
        if (clearMemory) loaders.mapNotNull { it.memoryCache }.distinct().forEach { it.clear() }
    }
}
