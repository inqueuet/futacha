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
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.delay
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
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
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val extension = remember(videoUrl) {
        videoUrl.substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
    }
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentSizeCallback by rememberUpdatedState(onVideoSizeKnown)
    if (extension == "webm") {
        WebVideoPlayer(
            videoUrl = videoUrl,
            modifier = modifier,
            onStateChanged = { currentCallback(it) },
            volume = volume,
            isMuted = isMuted
        )
    } else {
        AvKitVideoPlayer(
            videoUrl = videoUrl,
            modifier = modifier,
            onStateChanged = { currentCallback(it) },
            onVideoSizeKnown = { width, height -> currentSizeCallback(width, height) },
            volume = volume,
            isMuted = isMuted
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun AvKitVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    volume: Float,
    isMuted: Boolean
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
        if (player == null) {
            onStateChanged(VideoPlayerState.Error)
            return@LaunchedEffect
        }
        delay(120)
        onStateChanged(VideoPlayerState.Ready)
        player.play()
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
    onStateChanged: (VideoPlayerState) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val delegate = remember { WebVideoNavigationDelegate() }
    // FIX: XSS対策 - HTML属性インジェクションを防ぐ
    // Note: &はURLのクエリパラメータで正当に使用されるためエスケープしない
    // <, >, " をエスケープしてHTMLタグ挿入と属性脱出を防止
    val sanitizedUrl = remember(videoUrl) {
        videoUrl
            .replace("<", "%3C")
            .replace(">", "%3E")
            .replace("\"", "%22")
    }
    val html = remember(sanitizedUrl, isMuted) {
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
        <video controls playsinline autoplay src="$sanitizedUrl" ${if (isMuted) "muted" else ""}></video>
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
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                navigationDelegate = delegate
                loadHTMLString(html, baseURL = null)
                tag = html.hashCode().toLong()
            }
        },
        modifier = modifier,
        onRelease = { view ->
            view.stopLoading()
            view.loadHTMLString("", baseURL = null)
            view.navigationDelegate = null
        },
        update = { view ->
            val desiredTag = html.hashCode().toLong()
            if (view.tag.toLong() != desiredTag) {
                view.tag = desiredTag
                view.loadHTMLString(html, baseURL = null)
            } else {
                val mutedFlag = if (isMuted) "true" else "false"
                val volumeValue = volume.coerceIn(0f, 1f)
                view.evaluateJavaScript(
                    "var v=document.querySelector('video'); if(v){ v.muted=$mutedFlag; v.volume=$volumeValue; }",
                    completionHandler = null
                )
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class WebVideoNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    var onStateChanged: ((VideoPlayerState) -> Unit)? = null

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onStateChanged?.invoke(VideoPlayerState.Buffering)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onStateChanged?.invoke(VideoPlayerState.Ready)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError
    ) {
        onStateChanged?.invoke(VideoPlayerState.Error)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError
    ) {
        onStateChanged?.invoke(VideoPlayerState.Error)
    }
}
