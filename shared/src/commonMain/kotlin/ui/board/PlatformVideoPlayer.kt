package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class VideoPlayerState {
    Idle,
    Buffering,
    Ready,
    Error
}

@Composable
expect fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onStateChanged: (VideoPlayerState) -> Unit = {}
)
