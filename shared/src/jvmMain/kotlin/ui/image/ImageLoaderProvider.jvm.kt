package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(coil3.decode.SkiaImageDecoder.Factory())
    add(DesktopVideoFrameFetcher.Factory())
    add(DesktopLocalImageFetcher.Factory())
}

actual fun getPlatformDiskCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): String? = com.valoser.futacha.shared.desktop.DesktopEnvironment.current?.cacheDirectory
    ?.resolve("images")?.also { it.mkdirs() }?.absolutePath

actual fun getPlatformCacheAvailableBytes(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): Long? = com.valoser.futacha.shared.desktop.DesktopEnvironment.current?.cacheDirectory?.usableSpace

actual fun isPlatformRemovableCacheAvailable(platformContext: Any?): Boolean = false
