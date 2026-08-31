package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformImageComponents() = Unit

actual fun getPlatformDiskCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): String? = null

actual fun getPlatformCacheAvailableBytes(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): Long? = null

actual fun isPlatformRemovableCacheAvailable(platformContext: Any?): Boolean = false
