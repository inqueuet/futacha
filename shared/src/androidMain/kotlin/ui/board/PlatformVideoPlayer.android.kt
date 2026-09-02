package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.MotionEvent
import android.view.GestureDetector
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
actual fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (width: Int, height: Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    volume: Float,
    isMuted: Boolean,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit,
    onPlaybackError: (VideoPlaybackError) -> Unit
) {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentSizeCallback by rememberUpdatedState(onVideoSizeKnown)
    val currentControlsCallback by rememberUpdatedState(onControlsVisibilityChanged)
    val currentMediaInfoCallback by rememberUpdatedState(onMediaInfoKnown)
    val currentErrorCallback by rememberUpdatedState(onPlaybackError)
    var player by remember(context) { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(context) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .apply {
                // API 37 emulator images currently expose goldfish hardware decoders that
                // advertise support but fail while queueing the first buffers. Prefer the
                // platform software codecs there; physical devices retain hardware decode.
                if (isAndroidEmulator()) {
                    setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
                    forceDisableMediaCodecAsynchronousQueueing()
                }
            }
        val createdPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    AndroidVideoPlaybackCache.createDataSourceFactory(context)
                )
            )
            // Media3 defaults to an asymmetric 5-second rewind and 15-second
            // fast-forward.  The reference viewer's gesture is ±10 seconds;
            // make the visible controller buttons obey that same contract.
            .setSeekBackIncrementMs(REFERENCE_VIDEO_SEEK_STEP_MILLIS)
            .setSeekForwardIncrementMs(REFERENCE_VIDEO_SEEK_STEP_MILLIS)
            .build()
            .apply {
            playWhenReady = FUTACHA_VIDEO_PREVIEW_AUTOPLAY
        }
        player = createdPlayer
        onDispose {
            if (player === createdPlayer) {
                player = null
            }
            createdPlayer.release()
        }
    }

    val mediaItem = remember(videoUrl) {
        MediaItem.fromUri(videoUrl)
    }

    LaunchedEffect(mediaItem, player) {
        val activePlayer = player ?: return@LaunchedEffect
        currentCallback(VideoPlayerState.Buffering)
        // The legacy viewer opens a video on its first frame.  Preparing the
        // player must not be interpreted as a request to start playback.
        activePlayer.setMediaItem(mediaItem)
        activePlayer.playWhenReady = FUTACHA_VIDEO_PREVIEW_AUTOPLAY
        activePlayer.prepare()
    }

    LaunchedEffect(volume, isMuted, player) {
        player?.volume = normalizeVideoPlayerVolume(volume, isMuted)
    }

    AndroidView(
        factory = {
            lateinit var targetView: PlayerView
            val doubleTapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(event: MotionEvent): Boolean = true

                    override fun onDoubleTap(event: MotionEvent): Boolean {
                        val activePlayer = player ?: return false
                        activePlayer.seekTo(
                            resolveReferenceVideoDoubleTapPosition(
                                currentPositionMillis = activePlayer.currentPosition,
                                durationMillis = activePlayer.duration,
                                tappedRightHalf = event.x >= (targetView.width / 2f)
                            )
                        )
                        currentControlsCallback(true)
                        return true
                    }
                }
            )
            PlayerView(context).apply {
                targetView = this
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                setOnTouchListener { _, event ->
                    doubleTapDetector.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        currentControlsCallback(true)
                    }
                    false
                }
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            if (areControlsVisible) {
                view.showController()
            } else {
                view.hideController()
            }
        },
        modifier = modifier
    )

    DisposableEffect(player) {
        val activePlayer = player ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> currentCallback(VideoPlayerState.Buffering)
                    Player.STATE_READY -> currentCallback(resolveReadyVideoPlayerState(activePlayer.isPlaying))
                    Player.STATE_ENDED -> currentCallback(VideoPlayerState.Idle)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentCallback(resolveReadyVideoPlayerState(isPlaying))
            }

            override fun onPlayerError(error: PlaybackException) {
                currentErrorCallback(
                    VideoPlaybackError(
                        code = "${error.errorCodeName} (${error.errorCode})",
                        message = error.message ?: error.cause?.message
                    )
                )
                currentCallback(VideoPlayerState.Error)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentSizeCallback(videoSize.width, videoSize.height)
                currentMediaInfoCallback(activePlayer.toVideoMediaInfo())
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentMediaInfoCallback(activePlayer.toVideoMediaInfo())
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
        }
    }
}

private fun ExoPlayer.toVideoMediaInfo(): VideoMediaInfo {
    val video = videoFormat
    val audio = audioFormat
    val profileLevel = parseVideoCodecProfileLevel(video?.codecs)
    return VideoMediaInfo(
        videoCodec = video?.sampleMimeType,
        codecId = video?.codecs,
        profile = profileLevel.profile,
        level = profileLevel.level,
        width = video?.width?.takeIf { it > 0 },
        height = video?.height?.takeIf { it > 0 },
        frameRate = video?.frameRate?.takeIf { it > 0f },
        bitrate = video?.bitrate?.takeIf { it > 0 },
        audioCodec = audio?.sampleMimeType,
        sampleRate = audio?.sampleRate?.takeIf { it > 0 },
        channelCount = audio?.channelCount?.takeIf { it > 0 },
        durationMillis = duration.takeIf { it != C.TIME_UNSET && it >= 0L }
    )
}

private fun isAndroidEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    return fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk_gphone") ||
        model.contains("emulator") ||
        product.contains("sdk_gphone")
}
