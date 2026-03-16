package com.valoser.futacha.shared.ui.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun MediaThumbnailFallbackIcon(
    url: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = mediaThumbnailFallbackIcon(url),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

internal fun mediaThumbnailFallbackIcon(url: String?): ImageVector {
    return if (url != null && determineMediaType(url) == MediaType.Video) {
        Icons.Rounded.PlayArrow
    } else {
        Icons.Outlined.Image
    }
}
