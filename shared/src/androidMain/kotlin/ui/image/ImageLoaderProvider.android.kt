package com.valoser.futacha.shared.ui.image

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import android.os.Environment
import android.os.Build
import coil3.ImageLoader
import coil3.ComponentRegistry
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.video.VideoFrameDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import java.io.File

actual fun ComponentRegistry.Builder.addPlatformImageComponents() {
    add(CompatFixtureUriFetcher.Factory())
    add(CompatApngDecoder.Factory())
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        add(CompatAwebpDecoder.Factory())
    }
    add(VideoFrameDecoder.Factory())
    add(CompatFallbackGifDecoder.Factory())
    // Coil's ImageDecoder-backed factory handles animated WebP on API 28+
    // (and GIF as well). The legacy fallback remains necessary on older APIs.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        add(AnimatedImageDecoder.Factory())
    }
    add(GifDecoder.Factory())
}

/**
 * The shared loader supplies its own component registry. Resolve the
 * checked-in tutorial drawable by resource name before Coil's numeric-only
 * Android resource fetcher handles the request.
 */
private class CompatFixtureUriFetcher(
    private val resourceId: Int,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val context = options.context
        val drawable = ResourcesCompat.getDrawable(context.resources, resourceId, context.theme)
            ?: error("Could not resolve Android resource: $resourceId")
        return ImageFetchResult(
            image = drawable.toBitmap().asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val raw = data.toString()
            val resourcePath = raw.removePrefix("android.resource://")
            val resourceParts = resourcePath.split('/')
            if (resourceParts.size == 3 && resourceParts[0] == options.context.packageName) {
                val resourceId = options.context.resources.getIdentifier(
                    resourceParts[2],
                    resourceParts[1],
                    resourceParts[0]
                )
                if (resourceId != 0) return CompatFixtureUriFetcher(resourceId, options)
            }
            return null
        }
    }
}

actual fun getPlatformDiskCacheDirectory(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): String? {
    val context = platformContext as? Context ?: return null
    val root = resolvePlatformCacheRoot(context, location)
    return File(root, IMAGE_DISK_CACHE_DIR).absolutePath
}

actual fun getPlatformCacheAvailableBytes(
    platformContext: Any?,
    location: CompatibilityCacheLocation
): Long? {
    val context = platformContext as? Context ?: return null
    return runCatching { resolvePlatformCacheRoot(context, location).usableSpace }
        .getOrNull()
        ?.takeIf { it >= 0L }
}

private fun resolvePlatformCacheRoot(
    context: Context,
    location: CompatibilityCacheLocation
): File {
    val internalRoot = File(context.applicationInfo.dataDir, "cache")
    return when (location) {
        CompatibilityCacheLocation.INTERNAL -> internalRoot
        CompatibilityCacheLocation.DEVICE -> context.externalCacheDir ?: internalRoot
        CompatibilityCacheLocation.EXTERNAL_SD -> getExternalFilesDirsSafely(context)
            .asSequence()
            .filterNotNull()
            .firstOrNull(::isRemovableStorageDirectory)
            ?: internalRoot
    }
}

actual fun isPlatformRemovableCacheAvailable(platformContext: Any?): Boolean {
    val context = platformContext as? Context ?: return false
    return getExternalFilesDirsSafely(context)
        .asSequence()
        .filterNotNull()
        .any(::isRemovableStorageDirectory)
}

private fun getExternalFilesDirsSafely(context: Context): Array<File?> =
    runCatching { context.getExternalFilesDirs(null) }.getOrDefault(emptyArray())

/**
 * Some devices and test environments expose external-files paths which are not
 * registered as storage volumes. Android throws [IllegalArgumentException]
 * instead of returning false when such a path is passed to
 * [Environment.isExternalStorageRemovable]. Treat those entries as unavailable
 * so opening the compatibility settings screen cannot crash the app.
 */
internal fun isRemovableStorageDirectory(
    directory: File,
    removableCheck: (File) -> Boolean = Environment::isExternalStorageRemovable,
): Boolean = runCatching { removableCheck(directory) }.getOrDefault(false)
