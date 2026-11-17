package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Precision
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.compat.resolveCompatViewerMediaUrl
import com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.HighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.HighQualityThumbnailPlan
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalHighQualityThumbnailMode
import com.valoser.futacha.shared.ui.image.PromptAiBadge
import com.valoser.futacha.shared.ui.image.boundedOriginalDecoding
import com.valoser.futacha.shared.ui.image.buildHighQualityThumbnailRequest
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.planHighQualityThumbnail
import com.valoser.futacha.shared.ui.image.rememberGenerationMetadata
import com.valoser.futacha.shared.ui.image.rememberHighQualityThumbnailOverride
import com.valoser.futacha.shared.ui.image.skipArchiveImageFallback
import com.valoser.futacha.shared.ui.privacy.privacyMediaFilter

/** Map media metadata and actual cell bounds into the same upgrade policy as shared thread thumbnails. */
internal fun ftutterMediaThumbnailPlan(
    post: CompatPostSnapshot,
    mode: HighQualityThumbnailMode,
    box: IntSize,
    decodedThumbnailWidth: Int?,
    decodedThumbnailHeight: Int?,
    unmetered: Boolean
): HighQualityThumbnailPlan? = planHighQualityThumbnail(
    mode = mode,
    originalUrl = resolveCompatViewerMediaUrl(post)?.let(::ftutterNetworkUrl),
    thumbnailUrl = post.thumbnailUrl?.let(::ftutterNetworkUrl),
    thumbnailWidthPx = post.thumbnailWidth ?: decodedThumbnailWidth,
    thumbnailHeightPx = post.thumbnailHeight ?: decodedThumbnailHeight,
    boxWidthPx = box.width,
    boxHeightPx = box.height,
    originalSizeBytes = post.imageFileSizeBytes,
    isUnmeteredConnection = unmetered,
    crop = true
)

/** reduceThumbnail=OFF improves stretched thumbnails without unbounded original requests (audit M40). */
@Composable
internal fun FtutterMediaHighQualityThumbnail(post: CompatPostSnapshot, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    // Reuse the app's loader, original store and low-memory concurrency/cache policy.
    val imageLoader = LocalFutachaImageLoader.current
    val mode = LocalHighQualityThumbnailMode.current
    val unmetered = mode == HighQualityThumbnailMode.UNMETERED && isCompatWifiConnected(context)
    val original = resolveCompatViewerMediaUrl(post)?.let(::ftutterNetworkUrl)
    val thumbnail = post.thumbnailUrl?.takeIf(String::isNotBlank)?.takeUnless(::isCompatVideoMediaUrl)?.let(::ftutterNetworkUrl)
    val source = thumbnail ?: original?.takeUnless(::isCompatVideoMediaUrl)
    var box by remember { mutableStateOf(IntSize.Zero) }
    val request = remember(context, source, original, box) {
        if (source != null && source == original) {
            // An absent thumbnail must not become an unrestricted original download.
            buildHighQualityThumbnailRequest(context, HighQualityThumbnailPlan(source, allowNetwork = false,
                widthPx = box.width.coerceAtLeast(1), heightPx = box.height.coerceAtLeast(1), allowAnimation = false))
        } else {
            ImageRequest.Builder(context)
                .data(source)
                .size(box.width.coerceAtLeast(1), box.height.coerceAtLeast(1))
                .precision(Precision.INEXACT)
                .futabaExtensionFallbackPolicy(FutabaExtensionFallbackPolicy(maxAttempts = 0, allowVideoFallback = false, maxVideoAttempts = 0))
                .skipArchiveImageFallback()
                .boundedOriginalDecoding()
                .build()
        }
    }
    // Wait for measurement so even the first decode has the real slot bounds.
    Box(modifier.onSizeChanged { box = it }.privacyMediaFilter()) {
        if (box.width > 0 && box.height > 0) {
            val painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader)
            val state by painter.state.collectAsState()
            val decodedWidth = painter.intrinsicSize.width.takeIf { it.isFinite() }?.toInt()
            val decodedHeight = painter.intrinsicSize.height.takeIf { it.isFinite() }?.toInt()
            val plan = remember(post, mode, box, decodedWidth, decodedHeight, unmetered) {
                ftutterMediaThumbnailPlan(post, mode, box, decodedWidth, decodedHeight, unmetered)
            }
            val upgraded = rememberHighQualityThumbnailOverride(plan,
                thumbnailReady = state is AsyncImagePainter.State.Success, imageLoader = imageLoader)
            val metadata = rememberGenerationMetadata(original, state)
            if (state is AsyncImagePainter.State.Error) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                Image(upgraded ?: painter, contentDescription = "No.${post.postNo}",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            PromptAiBadge(metadata, Modifier.align(Alignment.BottomEnd))
        }
    }
}
