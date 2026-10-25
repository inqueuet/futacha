package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.boundedOriginalDecoding
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.skipArchiveImageFallback

/** A missing or malformed poster must never become a second video download through Coil. */
internal fun futaberVideoPosterUrl(posterUrl: String?): String? =
    posterUrl?.trim()?.takeIf(String::isNotEmpty)?.takeUnless(::isCompatVideoMediaUrl)

/** A static, viewport-sized poster, with no extension/archive fallback to an original video. */
@Composable
internal fun FutaberVideoPoster(posterUrl: String?, modifier: Modifier = Modifier) {
    val source = futaberVideoPosterUrl(posterUrl)
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { size = it }, contentAlignment = Alignment.Center) {
        // Also stays visible when a thumbnail request fails or there is no thumbnail.
        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Color.Gray)
        if (source != null && size.width > 0 && size.height > 0) {
            val context = LocalPlatformContext.current
            val request = remember(context, source, size) {
                ImageRequest.Builder(context).data(source)
                    .size(size.width, size.height).precision(Precision.INEXACT)
                    .futabaExtensionFallbackPolicy(FutabaExtensionFallbackPolicy(
                        maxAttempts = 0, allowVideoFallback = false, maxVideoAttempts = 0))
                    .skipArchiveImageFallback().boundedOriginalDecoding().build()
            }
            AsyncImage(model = request, imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "動画のプレビュー", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize())
        }
    }
}
