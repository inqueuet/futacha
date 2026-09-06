package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

internal data class ViewerImagePainter(
    val painter: Painter,
    val state: AsyncImagePainter.State
)

/** Keep both the painter and its collected state scoped to the current request. */
@Composable
internal fun rememberViewerImagePainter(
    request: ImageRequest?,
    imageLoader: ImageLoader
): ViewerImagePainter = key(request, imageLoader) {
    if (request == null) {
        // A null Coil model is an error, not a request that waits for layout/data.
        remember { ViewerImagePainter(ColorPainter(Color.Transparent), AsyncImagePainter.State.Empty) }
    } else {
        val painter = rememberAsyncImagePainter(request, imageLoader)
        // Keying only the painter would still let collectAsState retain the old
        // request's Error for the first composition after switching flows.
        val state by painter.state.collectAsState()
        ViewerImagePainter(painter, state)
    }
}
