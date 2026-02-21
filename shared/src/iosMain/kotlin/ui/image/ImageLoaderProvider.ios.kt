package com.valoser.futacha.shared.ui.image

import coil3.decode.Decoder
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun getPlatformDecoders(): List<Decoder.Factory> {
    return emptyList()
}

actual fun getPlatformDiskCacheDirectory(platformContext: Any?): String? {
    val cacheRoot = (NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String)?.trim()
    if (cacheRoot.isNullOrEmpty()) return null
    return "$cacheRoot/$IMAGE_DISK_CACHE_DIR"
}
