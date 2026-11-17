package com.valoser.futacha.shared.ui.image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
@Composable
internal actual fun platformAnimatedViewerPainter(state: AsyncImagePainter.State, fallback: Painter, animate: Boolean): Painter = fallback
