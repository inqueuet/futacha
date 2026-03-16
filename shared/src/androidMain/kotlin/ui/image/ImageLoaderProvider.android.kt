package com.valoser.futacha.shared.ui.image

import android.content.Context
import coil3.ComponentRegistry
import coil3.video.VideoFrameDecoder
import coil3.gif.GifDecoder
import java.io.File

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(VideoFrameDecoder.Factory())
    add(GifDecoder.Factory())
}

actual fun getPlatformDiskCacheDirectory(platformContext: Any?): String? {
    val context = platformContext as? Context ?: return null
    return File(context.cacheDir, IMAGE_DISK_CACHE_DIR).absolutePath
}
