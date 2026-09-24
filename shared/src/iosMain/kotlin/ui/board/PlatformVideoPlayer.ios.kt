@file:Suppress("DEPRECATION")
@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.ui.board

import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerRateDidChangeNotification
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource
import com.valoser.futacha.shared.media.source.OriginalMediaPlayback
import com.valoser.futacha.shared.media.source.IosOriginalMediaAsset
import com.valoser.futacha.shared.media.source.IosLocalVideoDocument
import com.valoser.futacha.shared.media.video.VideoEditPlayback
import com.valoser.futacha.shared.media.video.editedVideoPlayerItem
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeZero
import kotlinx.cinterop.readValue
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private const val VIDEO_STATE_MESSAGE_HANDLER = "futachaVideoState"
private const val WEB_VIDEO_SYNC_APPLIED_RESULT = "applied"

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativePlatformVideoPlayer(
    videoUrl: String,
    playback: OriginalMediaPlayback?,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    volume: Float,
    isMuted: Boolean,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit,
    onPlaybackError: (VideoPlaybackError) -> Unit,
    editing: VideoEditPlayback?
) {
    // WKWebView calls these from a delegate that outlives individual Compose
    // recompositions.  Keep it pointed at the latest UI callbacks just as the
    // Android player does, so a reload/navigation cannot update stale state.
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentSizeCallback by rememberUpdatedState(onVideoSizeKnown)
    val currentControlsCallback by rememberUpdatedState(onControlsVisibilityChanged)
    val currentMediaInfoCallback by rememberUpdatedState(onMediaInfoKnown)
    val currentErrorCallback by rememberUpdatedState(onPlaybackError)
    DisposableEffect(videoUrl) {
        logVideoPlaybackEnvironment(videoUrl, resolveIosVideoPlaybackBackend(videoUrl).name)
        onDispose { }
    }
    when (resolveIosVideoPlaybackBackend(videoUrl)) {
        IosVideoPlaybackBackend.AV_PLAYER -> NativeAvVideoPlayer(
            videoUrl = videoUrl,
            playback = playback,
            modifier = modifier,
            onStateChanged = { currentCallback(it) },
            onVideoSizeKnown = { width, height -> currentSizeCallback(width, height) },
            areControlsVisible = areControlsVisible,
            onControlsVisibilityChanged = { currentControlsCallback(it) },
            onMediaInfoKnown = { currentMediaInfoCallback(it) },
            onPlaybackError = { currentErrorCallback(it) },
            volume = volume,
            isMuted = isMuted,
            editing = editing
        )
        IosVideoPlaybackBackend.WEB_VIEW -> WebVideoPlayer(
            videoUrl = videoUrl,
            playback = playback,
            modifier = modifier,
            onStateChanged = { currentCallback(it) },
            onVideoSizeKnown = { width, height -> currentSizeCallback(width, height) },
            areControlsVisible = areControlsVisible,
            onControlsVisibilityChanged = { currentControlsCallback(it) },
            onMediaInfoKnown = { currentMediaInfoCallback(it) },
            onPlaybackError = { currentErrorCallback(it) },
            volume = volume,
            isMuted = isMuted
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun NativeAvVideoPlayer(
    videoUrl: String,
    playback: OriginalMediaPlayback?,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit,
    onPlaybackError: (VideoPlaybackError) -> Unit,
    volume: Float,
    isMuted: Boolean,
    editing: VideoEditPlayback?
) {
    val url = remember(videoUrl) { NSURL.URLWithString(videoUrl) }
    if (url == null) {
        LaunchedEffect(videoUrl) {
            onPlaybackError(VideoPlaybackError(code = "invalid_url", message = "Invalid video URL"))
            onStateChanged(VideoPlayerState.Error)
        }
        UIKitView(factory = { UIView() }, modifier = modifier)
        return
    }

    var editItem by remember(videoUrl, editing) { mutableStateOf<AVPlayerItem?>(null) }
    LaunchedEffect(url, editing) {
        val preview = editing ?: return@LaunchedEffect
        val pin = preview.retain()
        try { editItem = editedVideoPlayerItem(url, preview) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            onPlaybackError(VideoPlaybackError("edit_preview", failure.message))
            onStateChanged(VideoPlayerState.Error)
        } finally { pin.close() }
    }
    if (editing != null && editItem == null) { Box(modifier); return }
    var activePlayer by remember { mutableStateOf<AVPlayer?>(null) }
    DisposableEffect(videoUrl, playback, editItem, editing) {
        val editPin = editing?.retain()
        val extension = videoUrl.substringBefore('#').substringBefore('?').substringAfterLast('.').lowercase()
        val originalAsset = playback?.let { IosOriginalMediaAsset(it, extension) }
        val created = try {
            if (editItem != null) AVPlayer.playerWithPlayerItem(editItem)
            else if (originalAsset == null) AVPlayer.playerWithURL(url)
            else AVPlayer.playerWithPlayerItem(AVPlayerItem(asset = originalAsset.asset))
        } catch (failure: Throwable) { originalAsset?.close(); editPin?.close(); throw failure }
        activePlayer = created
        onDispose {
            activePlayer = null
            try {
                created.pause()
                created.replaceCurrentItemWithPlayerItem(null)
            } finally { originalAsset?.close(); editPin?.close() }
        }
    }
    val player = activePlayer
    if (player == null) { Box(modifier); return }
    val controller = remember {
        AVPlayerViewController(nibName = null, bundle = null).apply {
            allowsPictureInPicturePlayback = false
        }
    }
    val gestureHandler = remember { NativeAvDoubleTapHandler() }
    val doubleTapGesture = remember {
        UITapGestureRecognizer(
            target = gestureHandler,
            action = NSSelectorFromString("handleDoubleTap:")
        ).apply {
            numberOfTapsRequired = 2uL
            cancelsTouchesInView = false
        }
    }
    SideEffect {
        gestureHandler.player = player
        player.volume = normalizeVideoPlayerVolume(volume, isMuted)
        controller.showsPlaybackControls = areControlsVisible
    }
    DisposableEffect(player, controller) {
        controller.player = player
        player.pause()
        val notificationCenter = NSNotificationCenter.defaultCenter
        val backgroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) {
            player.pause()
        }
        onDispose {
            notificationCenter.removeObserver(backgroundObserver)
            player.pause()
            if (controller.player === player) controller.player = null
        }
    }
    LaunchedEffect(player, editing) {
        val preview = editing ?: return@LaunchedEffect
        preview.pausePlayer = {
            player.pause()
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (seconds.isFinite()) preview.position((seconds * 1_000_000).toLong())
        }
        try {
            player.seekToTime(CMTimeMakeWithSeconds(preview.startUs / 1_000_000.0, 1_000_000),
                toleranceBefore = kCMTimeZero.readValue(), toleranceAfter = kCMTimeZero.readValue()) { finished ->
                if (finished && preview.isActive) player.play()
            }
            while (isActive && preview.isActive) {
                val seconds = CMTimeGetSeconds(player.currentTime())
                if (seconds.isFinite()) {
                    val time = (seconds * 1_000_000).toLong()
                    preview.position(time)
                    if (time >= preview.info.frames.durationUs - 1000) { preview.ended(); break }
                }
                delay(33)
            }
        } finally { preview.pausePlayer = null }
    }
    // Woken by rate/end notifications so the idle wait below reacts immediately.
    val statusWake = remember(player) { Channel<Unit>(Channel.CONFLATED) }
    DisposableEffect(player) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            center.addObserverForName(AVPlayerRateDidChangeNotification, player, null) { statusWake.trySend(Unit) },
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, null, null) { statusWake.trySend(Unit) },
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, null, null) { statusWake.trySend(Unit) }
        )
        onDispose { observers.forEach(center::removeObserver) }
    }
    LaunchedEffect(videoUrl, player) {
        onStateChanged(VideoPlayerState.Buffering)
        var lastState: VideoPlayerState? = VideoPlayerState.Buffering
        var reportedMediaInfo: VideoMediaInfo? = null
        var bufferingSince = TimeSource.Monotonic.markNow()
        while (isActive) {
            val item = player.currentItem
            if (item?.status == AVPlayerItemStatusFailed) {
                val error = item.error
                onPlaybackError(
                    VideoPlaybackError(
                        code = "AVPlayerItem failed",
                        message = error?.localizedDescription
                    )
                )
                if (lastState != VideoPlayerState.Error) {
                    onStateChanged(VideoPlayerState.Error)
                }
                break
            }
            val nextState = when {
                item?.status != AVPlayerItemStatusReadyToPlay -> VideoPlayerState.Buffering
                player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate ->
                    VideoPlayerState.Buffering
                player.timeControlStatus == AVPlayerTimeControlStatusPlaying -> VideoPlayerState.Ready
                else -> VideoPlayerState.Idle
            }
            if (nextState != lastState) {
                if (nextState == VideoPlayerState.Buffering) bufferingSince = TimeSource.Monotonic.markNow()
                lastState = nextState
                onStateChanged(nextState)
                onControlsVisibilityChanged(nextState != VideoPlayerState.Ready)
            }
            if (nextState == VideoPlayerState.Buffering && bufferingSince.elapsedNow().inWholeMilliseconds >= WEB_VIDEO_LOAD_TIMEOUT_MS) {
                player.pause()
                onPlaybackError(VideoPlaybackError("avplayer_timeout", "動画の読み込みまたは再生が停止しました"))
                onStateChanged(VideoPlayerState.Error)
                break
            }
            if (item?.status == AVPlayerItemStatusReadyToPlay) {
                val size = item.presentationSize.useContents {
                    width.toInt() to height.toInt()
                }
                if (size.first > 0 && size.second > 0) {
                    onVideoSizeKnown(size.first, size.second)
                }
                val durationSeconds = CMTimeGetSeconds(item.duration)
                val info = VideoMediaInfo(
                    width = size.first.takeIf { it > 0 },
                    height = size.second.takeIf { it > 0 },
                    durationMillis = durationSeconds
                        .takeIf { it.isFinite() && it >= 0.0 }
                        ?.times(1_000.0)?.toLong()
                )
                if (info != reportedMediaInfo) {
                    reportedMediaInfo = info
                    onMediaInfoKnown(info)
                }
            }
            val wait = avPlayerStatusPollDelayMillis(
                state = lastState,
                itemReady = item?.status == AVPlayerItemStatusReadyToPlay,
                mediaInfoReported = reportedMediaInfo != null
            )
            if (wait > 200L) {
                withTimeoutOrNull(wait) { statusWake.receive() }
            } else {
                delay(wait)
            }
        }
    }
    UIKitView(
        factory = {
            val view = requireNotNull(controller.view)
            view.addGestureRecognizer(doubleTapGesture)
            view
        },
        modifier = modifier,
        onRelease = { view ->
            view.removeGestureRecognizer(doubleTapGesture)
            controller.player = null
        },
        update = {
            controller.player = player
            controller.showsPlaybackControls = areControlsVisible
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class NativeAvDoubleTapHandler : NSObject() {
    var player: AVPlayer? = null

    @ObjCAction
    fun handleDoubleTap(recognizer: UITapGestureRecognizer) {
        val activePlayer = player ?: return
        val view = recognizer.view ?: return
        val tappedRight = recognizer.locationInView(view).useContents { x >= view.bounds.useContents { size.width / 2.0 } }
        val currentSeconds = CMTimeGetSeconds(activePlayer.currentTime())
        val durationSeconds = activePlayer.currentItem?.duration?.let(::CMTimeGetSeconds) ?: -1.0
        if (!currentSeconds.isFinite() || currentSeconds < 0.0) return
        val nextMillis = resolveReferenceVideoDoubleTapPosition(
            currentPositionMillis = (currentSeconds * 1_000.0).toLong(),
            durationMillis = durationSeconds
                .takeIf { it.isFinite() && it >= 0.0 }
                ?.times(1_000.0)?.toLong() ?: -1L,
            tappedRightHalf = tappedRight
        )
        activePlayer.seekToTime(CMTimeMakeWithSeconds(nextMillis / 1_000.0, 600))
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun WebVideoPlayer(
    videoUrl: String,
    playback: OriginalMediaPlayback?,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit,
    onPlaybackError: (VideoPlaybackError) -> Unit,
    volume: Float,
    isMuted: Boolean
) {
    val webmAllowed = remember {
        NSProcessInfo.processInfo.operatingSystemVersion.useContents { supportsIosWebmPath(majorVersion, minorVersion) }
    }
    if (extractVideoUrlExtension(videoUrl) == "webm" && !webmAllowed) {
        LaunchedEffect(videoUrl) {
            onPlaybackError(VideoPlaybackError("webm_os_version", "WebMの再生にはiOS 17.4以降が必要です"))
            onStateChanged(VideoPlayerState.Error)
        }
        Box(modifier); return
    }
    val localUrl = remember(videoUrl) { NSURL.URLWithString(videoUrl)?.takeIf { it.fileURL } }
    val needsDocument = playback != null || localUrl != null
    var document by remember { mutableStateOf<IosLocalVideoDocument?>(null) }
    LaunchedEffect(videoUrl, playback) {
        if (!needsDocument) return@LaunchedEffect
        var owned: IosLocalVideoDocument? = null
        try {
            val extension = videoUrl.substringBefore('#').substringBefore('?').substringAfterLast('.').lowercase()
            withTimeout(30_000) {
                owned = if (playback != null) IosLocalVideoDocument.create(playback, extension)
                else IosLocalVideoDocument.createLocal(requireNotNull(localUrl?.path), extension)
            }
            logVideoPlaybackDiagnostic("source", "transport=${if (playback != null) "shared-original" else "local-file"} contentType=${playback?.info()?.mimeType} tracks=${owned?.webmInfo}")
            owned?.webmInfo?.let { onMediaInfoKnown(it.mediaInfo()) }
            document = owned
            awaitCancellation()
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            onPlaybackError(VideoPlaybackError("original_timeout", "動画の読み込みが時間内に完了しませんでした"))
            onStateChanged(VideoPlayerState.Error)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            onPlaybackError(VideoPlaybackError("local_video", "動画データを読み込めませんでした"))
            onStateChanged(VideoPlayerState.Error)
        } finally { document = null; owned?.close() }
    }
    if (needsDocument && document == null) { Box(modifier); return }
    val localDocument = document
    var viewPin by remember { mutableStateOf<IosLocalVideoDocument?>(null) }
    val delegate = remember { WebVideoNavigationDelegate() }
    val syncTracker = remember { WebVideoSyncStateTracker() }
    val html = remember(videoUrl) { buildEmbeddedVideoHtml(videoUrl) }
    SideEffect {
        delegate.onStateChanged = onStateChanged
        delegate.onVideoSizeKnown = onVideoSizeKnown
        delegate.onControlsVisibilityChanged = onControlsVisibilityChanged
        delegate.onMediaInfoKnown = { info ->
            val tracks = localDocument?.webmInfo
            onMediaInfoKnown(info.copy(videoCodec = tracks?.videoCodec, audioCodec = tracks?.audioCodec,
                channelCount = tracks?.channels, sampleRate = tracks?.sampleRate))
        }
        delegate.onPlaybackError = onPlaybackError
    }
    DisposableEffect(delegate) {
        logVideoPlaybackDiagnostic("native-capability", "AVURLAsset(video/webm)=${AVURLAsset.isPlayableExtendedMIMEType("video/webm")} selected=WKWebView")
        onDispose { delegate.detach() }
    }
    LaunchedEffect(videoUrl) {
        onStateChanged(VideoPlayerState.Buffering)
        delay(WEB_VIDEO_LOAD_TIMEOUT_MS)
        if (!delegate.metadataReceived) delegate.fail(VideoPlaybackError("webkit_timeout", "動画の読み込みが時間内に完了しませんでした"))
    }
    UIKitView(
        factory = {
            val userContentController = WKUserContentController().apply {
                addScriptMessageHandler(delegate, name = VIDEO_STATE_MESSAGE_HANDLER)
            }
            val configuration = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                // Preserve WebKit's existing default. Explicitly requiring gestures for ALL
                // media also prevents metadata preload on some iOS versions. The generated
                // page has no automatic play call; playback starts from the user's controls.
                this.userContentController = userContentController
            }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                navigationDelegate = delegate
                if (localDocument == null) loadHTMLString(html, baseURL = null)
                else {
                    viewPin = localDocument.retain()
                    loadFileURL(localDocument.url, allowingReadAccessToURL = localDocument.readAccessUrl)
                }
                tag = html.hashCode().toLong()
            }
        },
        modifier = modifier,
        onRelease = { view ->
            view.stopLoading()
            view.loadHTMLString("", baseURL = null)
            syncTracker.clear()
            view.configuration.userContentController.removeScriptMessageHandlerForName(VIDEO_STATE_MESSAGE_HANDLER)
            view.navigationDelegate = null
            viewPin?.close(); viewPin = null
        },
        update = { view ->
            val desiredTag = html.hashCode().toLong()
            if (view.tag != desiredTag) {
                view.tag = desiredTag
                syncTracker.clear()
                if (localDocument == null) view.loadHTMLString(html, baseURL = null)
                else view.loadFileURL(localDocument.url, allowingReadAccessToURL = localDocument.readAccessUrl)
            } else {
                val syncState = resolveVideoPlayerWebSyncState(
                    volume = volume,
                    isMuted = isMuted,
                    areControlsVisible = areControlsVisible
                )
                if (syncTracker.shouldApply(syncState)) {
                    syncTracker.markPending(syncState)
                    view.evaluateJavaScript(
                        buildWebVideoSyncJavaScript(syncState)
                    ) { result, error ->
                        if (error == null && result as? String == WEB_VIDEO_SYNC_APPLIED_RESULT) {
                            syncTracker.markApplied(syncState)
                        } else {
                            syncTracker.clearPending(syncState)
                        }
                    }
                }
            }
        }
    )
}

private class WebVideoSyncStateTracker {
    private var appliedState: VideoPlayerWebSyncState? = null
    private var pendingState: VideoPlayerWebSyncState? = null

    fun shouldApply(nextState: VideoPlayerWebSyncState): Boolean {
        return shouldApplyVideoPlayerWebSyncState(
            appliedState = appliedState,
            pendingState = pendingState,
            nextState = nextState
        )
    }

    fun markPending(state: VideoPlayerWebSyncState) {
        pendingState = state
    }

    fun markApplied(state: VideoPlayerWebSyncState) {
        appliedState = state
        if (pendingState == state) {
            pendingState = null
        }
    }

    fun clearPending(state: VideoPlayerWebSyncState) {
        if (pendingState == state) {
            pendingState = null
        }
    }

    fun clear() {
        appliedState = null
        pendingState = null
    }
}

private fun buildWebVideoSyncJavaScript(state: VideoPlayerWebSyncState): String {
    val mutedFlag = if (state.isMuted) "true" else "false"
    val controlsFlag = if (state.areControlsVisible) "true" else "false"
    return """
        (function(){
            var v=document.querySelector('video');
            if(!v){return 'missing';}
            v.muted=$mutedFlag;
            v.volume=${state.volume};
            v.controls=$controlsFlag;
            return '$WEB_VIDEO_SYNC_APPLIED_RESULT';
        })()
    """.trimIndent()
}

@OptIn(ExperimentalForeignApi::class)
private class WebVideoNavigationDelegate : NSObject(), WKNavigationDelegateProtocol, WKScriptMessageHandlerProtocol {
    var onStateChanged: ((VideoPlayerState) -> Unit)? = null
    var onVideoSizeKnown: ((Int, Int) -> Unit)? = null
    var onControlsVisibilityChanged: ((Boolean) -> Unit)? = null
    var onMediaInfoKnown: ((VideoMediaInfo) -> Unit)? = null
    var onPlaybackError: ((VideoPlaybackError) -> Unit)? = null
    var metadataReceived = false
        private set
    private var terminal = false

    fun fail(error: VideoPlaybackError) {
        if (terminal) return
        terminal = true
        logVideoPlaybackDiagnostic("failed", "engine=WKWebView code=${error.code} message=${error.message}")
        onPlaybackError?.invoke(error)
        onStateChanged?.invoke(VideoPlayerState.Error)
    }

    fun detach() {
        terminal = true
        onStateChanged = null; onVideoSizeKnown = null; onControlsVisibilityChanged = null
        onMediaInfoKnown = null; onPlaybackError = null
    }

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val message = didReceiveScriptMessage.body as? String ?: return
        if (terminal || !didReceiveScriptMessage.frameInfo.mainFrame) return
        when {
            message.startsWith("diagnostic:") -> logVideoPlaybackDiagnostic("web", message.removePrefix("diagnostic:"))
            message == "buffering" -> onStateChanged?.invoke(VideoPlayerState.Buffering)
            message == "ready" -> onStateChanged?.invoke(VideoPlayerState.Ready)
            message == "idle" -> onStateChanged?.invoke(VideoPlayerState.Idle)
            message.startsWith("error:") -> {
                val parts = message.split(':', limit = 3)
                fail(
                    VideoPlaybackError(
                        code = parts.getOrNull(1),
                        message = parts.getOrNull(2)
                    )
                )
            }
            message == "controls_visible" -> onControlsVisibilityChanged?.invoke(true)
            message == "controls_hidden" -> onControlsVisibilityChanged?.invoke(false)
            message.startsWith("size:") -> {
                val parts = message.removePrefix("size:").split(',')
                if (parts.size != 2) return
                val width = parts[0].toIntOrNull() ?: return
                val height = parts[1].toIntOrNull() ?: return
                if (width > 0 && height > 0) {
                    onVideoSizeKnown?.invoke(width, height)
                }
            }
            message.startsWith("media:") -> {
                metadataReceived = true
                val parts = message.removePrefix("media:").split(',')
                if (parts.size != 3) return
                val width = parts[0].toIntOrNull()?.takeIf { it > 0 }
                val height = parts[1].toIntOrNull()?.takeIf { it > 0 }
                val durationMillis = parts[2].toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.times(1_000.0)?.toLong()
                onMediaInfoKnown?.invoke(
                    VideoMediaInfo(width = width, height = height, durationMillis = durationMillis)
                )
            }
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        if (!terminal) onStateChanged?.invoke(VideoPlayerState.Buffering)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        // HTML navigation finishing says nothing about the media's readiness.
        if (terminal) return
        webView.evaluateJavaScript(
            "(function(){var v=document.querySelector('video'); if(!v){return '';} return String(v.videoWidth||0)+','+String(v.videoHeight||0);})()"
        ) { result, error ->
            if (terminal || error != null) return@evaluateJavaScript
            val rawSize = result as? String ?: return@evaluateJavaScript
            val parts = rawSize.split(',')
            if (parts.size != 2) return@evaluateJavaScript
            val width = parts[0].toIntOrNull() ?: return@evaluateJavaScript
            val height = parts[1].toIntOrNull() ?: return@evaluateJavaScript
            if (width > 0 && height > 0) {
                onVideoSizeKnown?.invoke(width, height)
            }
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError
    ) {
        fail(
            VideoPlaybackError(
                code = "WKNavigation ${withError.code}",
                message = withError.localizedDescription
            )
        )
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError
    ) {
        fail(
            VideoPlaybackError(
                code = "WKNavigation ${withError.code}",
                message = withError.localizedDescription
            )
        )
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        fail(VideoPlaybackError("webkit_terminated", "動画の再生処理が終了しました"))
    }
}
