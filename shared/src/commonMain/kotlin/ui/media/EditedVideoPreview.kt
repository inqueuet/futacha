package com.valoser.futacha.shared.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.ui.board.*
import kotlinx.coroutines.*

@Composable
internal fun EditedVideoPreview(source: VideoEditSource, playback: VideoEditPlayback, modifier: Modifier,
    onState: (VideoPlayerState) -> Unit, onError: (String) -> Unit) {
    var ready by remember(playback) { mutableStateOf(false) }
    val error by rememberUpdatedState(onError)
    DisposableEffect(playback) { onDispose { playback.close() } }
    LaunchedEffect(source, playback) {
        try {
            source.useFile {
                ready = true
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { playback.close(); playback.awaitReleased() } }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error(failure.message ?: "編集プレビューを開けません") }
        finally { ready = false }
    }
    if (!ready) Box(modifier) else NativePlatformVideoPlayer(
        videoUrl = com.valoser.futacha.shared.util.localMediaFileUri(source.path), playback = null, modifier = modifier,
        onStateChanged = { if (playback.isActive) {
            if (it == VideoPlayerState.Error) error("編集プレビューを再生できません。コマ表示へ戻ります。") else onState(it)
        } },
        onVideoSizeKnown = { _, _ -> }, areControlsVisible = false, onControlsVisibilityChanged = {},
        volume = 1f, isMuted = false, onMediaInfoKnown = {},
        onPlaybackError = { if (playback.isActive) error(it.message ?: "編集プレビューを再生できません") }, editing = playback)
}
