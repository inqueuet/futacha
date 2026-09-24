package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.media.video.VideoEditPlayback
import com.valoser.futacha.shared.ui.image.LocalOriginalMediaSource
import kotlinx.coroutines.*

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
fun PlatformVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onStateChanged: (VideoPlayerState) -> Unit = {},
    onVideoSizeKnown: (width: Int, height: Int) -> Unit = { _, _ -> },
    areControlsVisible: Boolean = true,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    volume: Float = 1f,
    isMuted: Boolean = false,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit = {},
    onPlaybackError: (VideoPlaybackError) -> Unit = {},
    isActive: Boolean = true,
    fallbackVideoUrls: List<String> = emptyList(),
    onPlaybackSourceChanged: (String) -> Unit = {}
) {
    val source = LocalOriginalMediaSource.current
    key(videoUrl, source, isActive, fallbackVideoUrls) {
        val candidates = remember { videoPlaybackSources(videoUrl, fallbackVideoUrls) }
        var candidateIndex by remember { mutableStateOf(0) }
        val currentIndex = candidateIndex
        val currentUrl = candidates[currentIndex]
        LaunchedEffect(currentUrl) { onPlaybackSourceChanged(currentUrl) }
        if (!isActive) Box(modifier)
        else key(currentUrl) {
            var candidateError by remember { mutableStateOf<VideoPlaybackError?>(null) }
            var terminal by remember { mutableStateOf(false) }
            if (terminal) Box(modifier)
            else VideoPlayerContent(currentUrl, source, modifier, { state ->
                if (!terminal && currentIndex == candidateIndex) {
                    if (state == VideoPlayerState.Error) {
                        terminal = true
                        if (currentIndex < candidates.lastIndex) {
                            logVideoPlaybackDiagnostic("fallback", "from=$currentUrl to=${candidates[currentIndex + 1]} error=$candidateError")
                            candidateIndex++
                            onMediaInfoKnown(VideoMediaInfo())
                            onStateChanged(VideoPlayerState.Buffering)
                        } else {
                            candidateError?.let(onPlaybackError)
                            onStateChanged(VideoPlayerState.Error)
                        }
                    } else onStateChanged(state)
                }
            }, onVideoSizeKnown, areControlsVisible, onControlsVisibilityChanged, volume, isMuted,
                onMediaInfoKnown, { candidateError = it })
        }
    }
}

@Composable
private fun VideoPlayerContent(
    videoUrl: String,
    source: OriginalMediaSource?,
    modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit,
    onVideoSizeKnown: (Int, Int) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    volume: Float,
    isMuted: Boolean,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit,
    onPlaybackError: (VideoPlaybackError) -> Unit
) {
    var legacy by remember { mutableStateOf(source == null || !isSharedOriginalVideoUrl(videoUrl)) }
    var playback by remember { mutableStateOf<OriginalMediaPlayback?>(null) }
    var alive by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val stateCallback by rememberUpdatedState(onStateChanged)
    val errorCallback by rememberUpdatedState(onPlaybackError)
    DisposableEffect(Unit) { onDispose { alive = false } }
    LaunchedEffect(Unit) {
        if (legacy) return@LaunchedEffect
        var owned: OriginalMediaPlayback? = null
        try {
            stateCallback(VideoPlayerState.Buffering)
            owned = requireNotNull(source).acquireForPlayback(originalVideoRequest(videoUrl))
            withTimeout(20_000) { owned.info() }
            playback = owned
            // Completion registers this very revision for metadata, only if that setting is ON.
            // The native player reads the growing prefix concurrently and owns its own pin.
            owned.complete().close()
            awaitCancellation()
        } catch (_: OriginalMediaCacheUnavailable) {
            // Only cache-open failures precede HTTP and permit the legacy player to fetch.
            legacy = true
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            failed = true
            logVideoPlaybackDiagnostic("source-error", "url=$videoUrl failure=$failure")
            if (alive) {
                errorCallback(VideoPlaybackError("original_media", "動画データを読み込めませんでした"))
                stateCallback(VideoPlayerState.Error)
            }
        } finally { playback = null; owned?.close() }
    }
    if (legacy || playback != null) {
        NativePlatformVideoPlayer(videoUrl, playback, modifier,
            onStateChanged = { if (alive && !failed) stateCallback(it) },
            onVideoSizeKnown = { w, h -> if (alive && !failed) onVideoSizeKnown(w, h) },
            areControlsVisible = areControlsVisible,
            onControlsVisibilityChanged = { if (alive && !failed) onControlsVisibilityChanged(it) },
            volume = volume, isMuted = isMuted,
            onMediaInfoKnown = { if (alive && !failed) onMediaInfoKnown(it) },
            onPlaybackError = { if (alive && !failed) errorCallback(it) })
    } else Box(modifier)
}

@Composable
internal expect fun NativePlatformVideoPlayer(
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
    editing: VideoEditPlayback? = null
)

/**
 * How long the iOS player status loop may wait before checking again. While
 * loading or playing it polls every 200 ms; once paused or finished with its
 * media info reported it waits up to 2 seconds, and AVPlayer rate / end
 * notifications wake it earlier, so a paused viewer does not poll 5 times a second.
 */
internal fun avPlayerStatusPollDelayMillis(
    state: VideoPlayerState?,
    itemReady: Boolean,
    mediaInfoReported: Boolean
): Long = if (state == VideoPlayerState.Idle && itemReady && mediaInfoReported) 2_000L else 200L
