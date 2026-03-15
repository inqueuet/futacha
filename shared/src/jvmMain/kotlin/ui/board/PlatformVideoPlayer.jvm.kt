package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    LaunchedEffect(videoUrl) {
        onStateChanged(VideoPlayerState.Buffering)
    }
}
