package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class VideoPlayerState {
    Idle,
    Buffering,
    Ready,
    Error
}

data class VideoMediaInfo(
    val videoCodec: String? = null,
    val codecId: String? = null,
    val profile: String? = null,
    val level: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val audioCodec: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val durationMillis: Long? = null
)

data class VideoPlaybackError(
    val code: String? = null,
    val message: String? = null
)

@Composable
expect fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onStateChanged: (VideoPlayerState) -> Unit = {},
    onVideoSizeKnown: (width: Int, height: Int) -> Unit = { _, _ -> },
    areControlsVisible: Boolean = true,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    volume: Float = 1f,
    isMuted: Boolean = false,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit = {},
    onPlaybackError: (VideoPlaybackError) -> Unit = {}
)
