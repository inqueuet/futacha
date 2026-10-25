package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoPlaybackError
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import com.valoser.futacha.shared.ui.board.formatVideoPlaybackError
import com.valoser.futacha.shared.ui.privacy.PrivacyVideoOverlay

/** Same player/options as the other modes; status belongs to this URL's active player lifetime. */
@Composable
internal fun FtutterViewerVideoPage(
    videoUrl: String,
    isActive: Boolean,
    privacyAlpha: Float,
    onOpenBrowser: (String) -> Unit
) {
    var state by remember(videoUrl, isActive) { mutableStateOf(VideoPlayerState.Buffering) }
    var error by remember(videoUrl, isActive) { mutableStateOf<VideoPlaybackError?>(null) }
    Box(Modifier.fillMaxSize()) {
        PlatformVideoPlayer(
            videoUrl = videoUrl,
            isActive = isActive,
            modifier = Modifier.fillMaxSize().testTag("ftutter-viewer-video"),
            onStateChanged = { next ->
                state = next
                if (next != VideoPlayerState.Error) error = null
            },
            onPlaybackError = { error = it }
        )
        PrivacyVideoOverlay(legacyContentAlpha = privacyAlpha)
        if (isActive && state == VideoPlayerState.Buffering) {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .7f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.testTag("ftutter-viewer-video-spinner"))
                Text("動画を読み込み中", color = Color.White)
            }
        }
        if (isActive && state == VideoPlayerState.Error) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 480.dp)
                    .background(Color.Black.copy(alpha = .85f)).verticalScroll(rememberScrollState())
                    .padding(16.dp).testTag("ftutter-viewer-video-error"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("動画を再生できませんでした", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                formatVideoPlaybackError(error)?.let { detail ->
                    Text(detail, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                TextButton(
                    onClick = { onOpenBrowser(videoUrl) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("ftutter-viewer-video-open-external")
                ) { Text("ブラウザで開く") }
            }
        }
    }
}
