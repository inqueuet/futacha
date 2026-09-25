package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.image.rememberGenerationMetadata
import com.valoser.futacha.shared.ui.image.PromptAiBadge

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.VideoThumbnailRequestPriority
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.videoThumbnailRequestPriority

@Composable
internal fun CatalogPreviewImage(
    thumbnailUrl: String?,
    fullImageUrl: String?,
    targetSizePx: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = Color.Gray
) {
    val platformContext = LocalPlatformContext.current
    val features = LocalFutachaSharedFeatures.current
    val imageLoader = if (features == null) LocalFutachaImageLoader.current
        else com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader.current
    val lowQuality = features?.value("catalog", "catalogEco") == "ON" ||
        (features?.value("catalog", "catalogMobileEco") == "ON" && !com.valoser.futacha.shared.ui.compat.isCompatWifiConnected(platformContext))
    val crop = features?.value("catalog", "catalogThumbCrop")?.let { it == "ON" } ?: true
    // Eco OFF means normal thumbnails, not original-resolution downloads.
    val candidates = remember(thumbnailUrl, fullImageUrl, lowQuality) {
        listOfNotNull(thumbnailUrl?.takeIf { lowQuality }?.replace("/thumb/", "/cat/"),
            thumbnailUrl, fullImageUrl).filter(String::isNotBlank).distinct()
    }
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)
    val imageRequest = remember(activeUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(activeUrl)
            .crossfade(false)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .videoThumbnailRequestPriority(VideoThumbnailRequestPriority.PREFETCH)
            .futabaExtensionFallbackPolicy(
                FutabaExtensionFallbackPolicy(
                    maxAttempts = 5,
                    allowVideoFallback = true,
                    preferStaticCandidates = true,
                    maxVideoAttempts = 2,
                    videoFallbackTimeoutMillis = CATALOG_VIDEO_FALLBACK_TIMEOUT_MILLIS,
                    negativeCacheTtlMillis = CATALOG_FALLBACK_NEGATIVE_CACHE_TTL_MILLIS
                )
            )
            .build()
    }
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val imageState by imagePainter.state.collectAsState()

    val failure = (imageState as? AsyncImagePainter.State.Error)?.result?.throwable
    val canAdvance = imageState is AsyncImagePainter.State.Error &&
        shouldAdvanceCatalogPreviewCandidate(candidateIndex, candidates, fullImageUrl, failure)
    LaunchedEffect(imageState, activeUrl, candidateIndex, candidates.size) {
        if (activeUrl.isNullOrBlank()) return@LaunchedEffect
        if (canAdvance) {
            candidateIndex += 1
        }
    }

    val shouldShowFallback = activeUrl.isNullOrBlank() ||
        (imageState is AsyncImagePainter.State.Error && !canAdvance)

    val promptMetadata = rememberGenerationMetadata(fullImageUrl, imageState)
    Box(modifier) {
        if (shouldShowFallback) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Image(
                painter = imagePainter,
                contentDescription = contentDescription,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        PromptAiBadge(promptMetadata, Modifier.align(Alignment.BottomEnd))
    }
}

private const val CATALOG_VIDEO_FALLBACK_TIMEOUT_MILLIS = 2_500L
private const val CATALOG_FALLBACK_NEGATIVE_CACHE_TTL_MILLIS = 60_000L

/**
 * The next catalog preview candidate after a failed one. Stepping between thumbnail
 * variants (eco /cat/ -> /thumb/) is always allowed, but the original is a full-size
 * download: like the thread thumbnails it is tried only when the thumbnail is really
 * gone (404/410), not after a timeout or another transient failure.
 */
internal fun shouldAdvanceCatalogPreviewCandidate(
    candidateIndex: Int,
    candidates: List<String>,
    fullImageUrl: String?,
    failure: Throwable?
): Boolean {
    val next = candidates.getOrNull(candidateIndex + 1) ?: return false
    return next != fullImageUrl || com.valoser.futacha.shared.ui.image.isMissingImage(failure)
}
