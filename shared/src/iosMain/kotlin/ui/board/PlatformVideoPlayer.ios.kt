package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit
) {
    val extension = remember(videoUrl) {
        videoUrl.substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
    }
    val currentCallback by rememberUpdatedState(onStateChanged)
    if (extension == "webm") {
        WebVideoPlayer(
            videoUrl = videoUrl,
            modifier = modifier,
            onStateChanged = { currentCallback(it) }
        )
    } else {
        AvKitVideoPlayer(
            videoUrl = videoUrl,
            modifier = modifier,
            onStateChanged = { currentCallback(it) }
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun AvKitVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit
) {
    val player = remember(videoUrl) {
        NSURL.URLWithString(videoUrl)?.let { AVPlayer(uRL = it) }
    }
    val controller = remember(player) {
        AVPlayerViewController().apply {
            showsPlaybackControls = true
            this.player = player
        }
    }
    LaunchedEffect(player) {
        onStateChanged(VideoPlayerState.Buffering)
        val item = player?.currentItem
        if (item == null) {
            onStateChanged(VideoPlayerState.Error)
            return@LaunchedEffect
        }
        while (isActive) {
            when (item.status) {
                AVPlayerItemStatusReadyToPlay -> {
                    onStateChanged(VideoPlayerState.Ready)
                    player.play()
                    return@LaunchedEffect
                }
                AVPlayerItemStatusFailed -> {
                    onStateChanged(VideoPlayerState.Error)
                    return@LaunchedEffect
                }
            }
            delay(120)
        }
    }
    DisposableEffect(player) {
        onDispose {
            player?.pause()
        }
    }
    UIKitView(
        factory = {
            controller.view
        },
        modifier = modifier,
        update = {
            controller.player = player
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun WebVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit
) {
    val delegate = remember { WebVideoNavigationDelegate() }
    val html = remember(videoUrl) {
        """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
        body,html { margin:0; padding:0; background-color:black; height:100%; }
        video { width:100%; height:100%; object-fit:contain; background-color:black; }
        </style>
        </head>
        <body>
        <video controls playsinline autoplay src="$videoUrl"></video>
        </body>
        </html>
        """.trimIndent()
    }
    SideEffect {
        delegate.onStateChanged = onStateChanged
    }
    LaunchedEffect(videoUrl) {
        onStateChanged(VideoPlayerState.Buffering)
    }
    UIKitView(
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
            }
            WKWebView(frame = CGRectZero, configuration = configuration).apply {
                navigationDelegate = delegate
                loadHTMLString(html, baseURL = null)
                tag = html.hashCode().toLong()
            }
        },
        modifier = modifier,
        update = { view ->
            val desiredTag = html.hashCode().toLong()
            if (view.tag.toLong() != desiredTag) {
                view.tag = desiredTag
                view.loadHTMLString(html, baseURL = null)
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class WebVideoNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    var onStateChanged: ((VideoPlayerState) -> Unit)? = null

    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onStateChanged?.invoke(VideoPlayerState.Buffering)
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onStateChanged?.invoke(VideoPlayerState.Ready)
    }

    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError
    ) {
        onStateChanged?.invoke(VideoPlayerState.Error)
    }

    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError
    ) {
        onStateChanged?.invoke(VideoPlayerState.Error)
    }
}
