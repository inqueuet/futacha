package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.media.source.isSharedOriginalImageUrl
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Futaba serves every thumbnail at no more than 250px (JPEG quality ~65), so a
 * thumbnail slot sized in dp is stretched about 3x on a high-density screen.
 * This mode lets the slot show the original image, decoded down to the slot.
 */
enum class HighQualityThumbnailMode(val storedValue: String, val label: String) {
    OFF("off", "しない"),

    /** Only originals the viewer, a save or auto-save already downloaded; no traffic. */
    CACHED("cached", "取得済みの元画像のみ"),
    UNMETERED("wifi", "Wi-Fi回線では元画像を取得"),
    ALWAYS("always", "常に元画像を取得");

    companion object {
        // Wi-Fi fetches are the default (user decision, 2026-09-29); metered
        // connections still use only already-fetched originals.
        val DEFAULT = UNMETERED

        fun fromStored(value: String?): HighQualityThumbnailMode {
            val normalized = value?.trim()?.lowercase() ?: return DEFAULT
            return entries.firstOrNull { it.storedValue == normalized || it.label.lowercase() == normalized }
                ?: DEFAULT
        }
    }
}

// Shown under Network > 画像の取得: the thread page must keep the reference rows.
internal const val HIGH_QUALITY_THUMBNAIL_PREFERENCE_PATH = "network"
internal const val HIGH_QUALITY_THUMBNAIL_PREFERENCE_KEY = "networkThumbHighQuality"

/** Below this stretch the thumbnail already looks close to the original. */
internal const val HIGH_QUALITY_THUMBNAIL_MIN_UPSCALE = 1.5f

/**
 * Futaba only shrinks: an original no larger than a thumbnail keeps its own
 * dimensions (a 249x250 thumbnail is still a shrunk one).
 */
internal const val HIGH_QUALITY_THUMBNAIL_MIN_SHRUNK_EDGE_PX = 240

/**
 * Network upgrades need a size stated by the page. A larger or unknown file is
 * only shown when it is already cached, so one heavy thread cannot evict the
 * viewer's originals or spend tens of MB on thumbnails.
 */
internal const val HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES = 4L * 1024L * 1024L

data class HighQualityThumbnailPlan(
    val url: String,
    val allowNetwork: Boolean,
    val widthPx: Int,
    val heightPx: Int,
    val allowAnimation: Boolean
)

/**
 * Decides whether a thumbnail slot should be upgraded to its original.
 *
 * [boxWidthPx]/[boxHeightPx] are the bounds the thumbnail is fitted into;
 * the displayed size is the thumbnail scaled to fit them, as both modes draw it,
 * or scaled to fill them when the slot crops ([crop]).
 */
internal fun planHighQualityThumbnail(
    mode: HighQualityThumbnailMode,
    originalUrl: String?,
    thumbnailUrl: String?,
    thumbnailWidthPx: Int?,
    thumbnailHeightPx: Int?,
    boxWidthPx: Int,
    boxHeightPx: Int,
    originalSizeBytes: Long?,
    isUnmeteredConnection: Boolean,
    allowAnimation: Boolean = false,
    crop: Boolean = false
): HighQualityThumbnailPlan? {
    if (mode == HighQualityThumbnailMode.OFF) return null
    val url = originalUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
    // Videos have no larger still than their thumbnail; other hosts and local
    // files are outside the shared original store.
    if (!isSharedOriginalImageUrl(url)) return null
    val thumbnail = thumbnailUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
    // A slot already showing its source (for example あぷ小) has nothing to gain.
    if (thumbnail == url) return null
    val width = thumbnailWidthPx?.takeIf { it > 0 } ?: return null
    val height = thumbnailHeightPx?.takeIf { it > 0 } ?: return null
    if (max(width, height) < HIGH_QUALITY_THUMBNAIL_MIN_SHRUNK_EDGE_PX) return null
    if (boxWidthPx <= 0 || boxHeightPx <= 0) return null
    val widthScale = boxWidthPx.toFloat() / width
    val heightScale = boxHeightPx.toFloat() / height
    val scale = if (crop) max(widthScale, heightScale) else min(widthScale, heightScale)
    if (!scale.isFinite() || scale < HIGH_QUALITY_THUMBNAIL_MIN_UPSCALE) return null
    val networkPermitted = when (mode) {
        HighQualityThumbnailMode.ALWAYS -> true
        HighQualityThumbnailMode.UNMETERED -> isUnmeteredConnection
        else -> false
    }
    val sizeAllowsNetwork = originalSizeBytes != null &&
        originalSizeBytes in 1..HIGH_QUALITY_THUMBNAIL_MAX_NETWORK_BYTES
    return HighQualityThumbnailPlan(
        url = url,
        allowNetwork = networkPermitted && sizeAllowsNetwork,
        widthPx = (width * scale).roundToInt().coerceAtLeast(1),
        heightPx = (height * scale).roundToInt().coerceAtLeast(1),
        allowAnimation = allowAnimation
    )
}

internal fun buildHighQualityThumbnailRequest(
    platformContext: PlatformContext,
    plan: HighQualityThumbnailPlan
): ImageRequest = ImageRequest.Builder(platformContext)
    .data(plan.url)
    .size(plan.widthPx, plan.heightPx)
    // Never enlarge a small original; a smaller decode is drawn to the slot.
    .precision(Precision.INEXACT)
    .crossfade(false)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    // The original store honours this: false only reads an existing file.
    .networkCachePolicy(if (plan.allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
    // The thumbnail remains the answer when the original is missing, so do
    // not spend requests on guessed extensions or archives.
    .futabaExtensionFallbackPolicy(
        FutabaExtensionFallbackPolicy(maxAttempts = 0, allowVideoFallback = false, maxVideoAttempts = 0)
    )
    .skipArchiveImageFallback()
    .apply { if (!plan.allowAnimation) staticImageDecoding() }
    .build()

/**
 * Forces a single still frame so an animated original never starts playing
 * where the thumbnail was still. Platforms whose decoder is already static
 * leave the request unchanged.
 */
internal expect fun ImageRequest.Builder.staticImageDecoding(): ImageRequest.Builder

/** Provided by the app for both modes; tests and previews keep plain thumbnails. */
val LocalHighQualityThumbnailMode = compositionLocalOf { HighQualityThumbnailMode.OFF }

@Composable
internal fun rememberHighQualityThumbnailMode(store: CompatibilityStore?): HighQualityThumbnailMode {
    val mode = remember(store) {
        store?.preferences
            ?.map { preferences ->
                HighQualityThumbnailMode.fromStored(
                    preferences.compatPreferenceValue(
                        HIGH_QUALITY_THUMBNAIL_PREFERENCE_PATH,
                        HIGH_QUALITY_THUMBNAIL_PREFERENCE_KEY
                    )
                )
            }
            ?.distinctUntilChanged()
            ?: flowOf(HighQualityThumbnailMode.DEFAULT)
    }
    return mode.collectAsState(HighQualityThumbnailMode.DEFAULT).value
}

/**
 * Returns the original's painter once it has loaded, or null while the
 * thumbnail should stay. The original is requested only after the thumbnail
 * is shown, so it never delays the first picture. After a miss the slot waits
 * for the viewer, a save or auto-save to store that original, then retries.
 */
@Composable
internal fun rememberHighQualityThumbnailOverride(
    plan: HighQualityThumbnailPlan?,
    thumbnailReady: Boolean,
    imageLoader: ImageLoader
): Painter? {
    if (plan == null || !thumbnailReady) return null
    val platformContext = LocalPlatformContext.current
    val originals = LocalOriginalMediaSource.current
    var attempt by remember(plan) { mutableIntStateOf(0) }
    val request = remember(platformContext, plan, attempt) {
        buildHighQualityThumbnailRequest(platformContext, plan).newBuilder()
            // A distinct request per attempt; an identical one would not re-run.
            .memoryCacheKeyExtra(HIGH_QUALITY_ATTEMPT_KEY, attempt.toString())
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader)
    val state by painter.state.collectAsState()
    val failed = state is AsyncImagePainter.State.Error
    LaunchedEffect(originals, plan, attempt, failed) {
        if (!failed || originals == null) return@LaunchedEffect
        originals.persistedUrls.first { it == plan.url }
        attempt += 1
    }
    return painter.takeIf { state is AsyncImagePainter.State.Success }
}

private const val HIGH_QUALITY_ATTEMPT_KEY = "futacha#high-quality-attempt"
