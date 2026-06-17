package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
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
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy

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
    val imageLoader = LocalFutachaImageLoader.current
    val candidates = remember(thumbnailUrl, fullImageUrl) {
        buildList {
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
            fullImageUrl
                ?.takeIf { it.isNotBlank() && it != thumbnailUrl }
                ?.let(::add)
        }
    }
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)
    val imageRequest = remember(activeUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(activeUrl)
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
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

    LaunchedEffect(imageState, activeUrl, candidateIndex, candidates.size) {
        if (activeUrl.isNullOrBlank()) return@LaunchedEffect
        if (imageState is AsyncImagePainter.State.Error && candidateIndex < candidates.lastIndex) {
            candidateIndex += 1
        }
    }

    val shouldShowFallback = activeUrl.isNullOrBlank() ||
        (imageState is AsyncImagePainter.State.Error && candidateIndex >= candidates.lastIndex)

    if (shouldShowFallback) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = fallbackTint
        )
    } else {
        Image(
            painter = imagePainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

private const val CATALOG_VIDEO_FALLBACK_TIMEOUT_MILLIS = 2_500L
private const val CATALOG_FALLBACK_NEGATIVE_CACHE_TTL_MILLIS = 60_000L
