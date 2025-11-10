package com.valoser.futacha.shared.ui.board

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    val uri = remember(videoUrl) { Uri.parse(videoUrl) }
    val controller = remember { MediaController(context) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    val currentVideoView by rememberUpdatedState(videoView)

    AndroidView(
        factory = {
            VideoView(context).apply {
                videoView = this
                setMediaController(controller)
                controller.setAnchorView(this)
                setVideoURI(uri)
                setOnPreparedListener { it.start() }
            }
        },
        update = { view ->
            videoView = view
            if (view.tag != videoUrl) {
                view.tag = videoUrl
                view.setVideoURI(uri)
                view.start()
            }
        },
        modifier = modifier
    )

    DisposableEffect(videoUrl, currentVideoView) {
        onDispose {
            currentVideoView?.apply {
                setOnPreparedListener(null)
                stopPlayback()
                suspend()
            }
            controller.setAnchorView(null)
            controller.hide()
        }
    }
}
