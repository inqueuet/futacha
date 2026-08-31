package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry
import coil3.decode.SkiaImageDecoder
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(SkiaImageDecoder.Factory())
    add(IosVideoFrameFetcher.Factory())
}

actual fun getPlatformDiskCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): String? {
    val cacheRoot = (NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String)?.trim()
    if (cacheRoot.isNullOrEmpty()) return null
    return "$cacheRoot/$IMAGE_DISK_CACHE_DIR"
}

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformCacheAvailableBytes(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): Long? {
    val directory = getPlatformDiskCacheDirectory(platformContext, location)
        ?.substringBeforeLast('/')
        ?: return null
    val attributes = runCatching {
        NSFileManager.defaultManager.attributesOfFileSystemForPath(directory, null)
    }.getOrNull()
    return (attributes?.get(NSFileSystemFreeSize) as? NSNumber)
        ?.longValue
        ?.takeIf { it >= 0L }
}

actual fun isPlatformRemovableCacheAvailable(platformContext: Any?): Boolean = false
