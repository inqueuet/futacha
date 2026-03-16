@file:Suppress("DEPRECATION")

package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
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
    val currentCallback = rememberUpdatedState(onStateChanged).value
    val currentSizeCallback = rememberUpdatedState(onVideoSizeKnown).value
    WebVideoPlayer(
        videoUrl = videoUrl,
        modifier = modifier,
        onStateChanged = { currentCallback(it) },
        onVideoSizeKnown = { width, height -> currentSizeCallback(width, height) },
        volume = volume,
        isMuted = isMuted
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun WebVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val delegate = remember { WebVideoNavigationDelegate() }
    val html = remember(videoUrl) { buildEmbeddedVideoHtml(videoUrl) }
    SideEffect {
        delegate.onStateChanged = onStateChanged
        delegate.onVideoSizeKnown = onVideoSizeKnown
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
            if (view.tag != desiredTag) {
                view.tag = desiredTag
                view.loadHTMLString(html, baseURL = null)
            } else {
                val mutedFlag = if (isMuted) "true" else "false"
                val volumeValue = normalizeVideoPlayerVolume(volume, isMuted)
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
    var onVideoSizeKnown: ((Int, Int) -> Unit)? = null

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onStateChanged?.invoke(VideoPlayerState.Buffering)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.evaluateJavaScript(
            "(function(){var v=document.querySelector('video'); if(!v){return '';} return String(v.videoWidth||0)+','+String(v.videoHeight||0);})()"
        ) { result, error ->
            if (error != null) return@evaluateJavaScript
            val rawSize = result as? String ?: return@evaluateJavaScript
            val parts = rawSize.split(',')
            if (parts.size != 2) return@evaluateJavaScript
            val width = parts[0].toIntOrNull() ?: return@evaluateJavaScript
            val height = parts[1].toIntOrNull() ?: return@evaluateJavaScript
            if (width > 0 && height > 0) {
                onVideoSizeKnown?.invoke(width, height)
            }
        }
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
