package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier
) {
    val player = remember(videoUrl) {
        val url = NSURL.URLWithString(videoUrl)
        url?.let { AVPlayer(uRL = it) }
    }

    DisposableEffect(player) {
        player?.play()
        onDispose {
            player?.pause()
        }
    }

    UIKitView(
        factory = {
            val playerViewController = AVPlayerViewController()
            playerViewController.player = player
            playerViewController.showsPlaybackControls = true
            playerViewController.view
        },
        modifier = modifier,
        update = { view ->
            // 既存のplayerを更新
        }
    )
}
