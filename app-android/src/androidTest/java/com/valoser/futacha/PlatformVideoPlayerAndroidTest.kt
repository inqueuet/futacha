package com.valoser.futacha

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlatformVideoPlayerAndroidTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun platformVideoPlayer_usesSymmetricTenSecondTransportControls() {
        rule.setContent {
            PlatformVideoPlayer(
                videoUrl = "file:///does/not/exist-seek-contract.mp4",
                modifier = Modifier.size(100.dp),
                onStateChanged = {}
            )
        }

        rule.waitUntil(5_000) {
            rule.activity.findViewById<ViewGroup>(android.R.id.content)
                .findMedia3PlayerView()
                ?.let(::media3PlayerFromView) != null
        }

        rule.runOnIdle {
            val playerView = requireNotNull(
                rule.activity.findViewById<ViewGroup>(android.R.id.content).findMedia3PlayerView()
            )
            val player = requireNotNull(media3PlayerFromView(playerView))
            assertEquals(10_000L, player.javaClass.getMethod("getSeekBackIncrement").invoke(player))
            assertEquals(10_000L, player.javaClass.getMethod("getSeekForwardIncrement").invoke(player))
        }
    }

    @Test
    fun platformVideoPlayer_missingFile_reportsErrorAndHandlesControlUpdates() {
        val missingVideoUrl = "file:///does/not/exist-${System.currentTimeMillis()}.mp4"

        rule.setContent {
            var playbackState by remember { mutableStateOf(VideoPlayerState.Idle) }
            var isMuted by remember { mutableStateOf(false) }
            var volume by remember { mutableFloatStateOf(0.9f) }

            Column {
                Text("state:${playbackState.name}")
                Button(onClick = { isMuted = !isMuted }) {
                    Text(if (isMuted) "Unmute" else "Mute")
                }
                Button(onClick = { volume = 0.3f }) {
                    Text("Volume30")
                }
                PlatformVideoPlayer(
                    videoUrl = missingVideoUrl,
                    modifier = Modifier.size(1.dp),
                    onStateChanged = { playbackState = it },
                    volume = volume,
                    isMuted = isMuted
                )
            }
        }

        rule.onNodeWithText("Mute").performClick()
        rule.onNodeWithText("Volume30").performClick()

        // A missing local file can transition Buffering -> Error between two Compose frames,
        // especially on API 37. Buffering is an observation, not a required durable state;
        // the externally meaningful contract is that controls remain usable and Error arrives.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("state:Error").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithText("state:Error").assertIsDisplayed()
    }
}

private fun View.findMedia3PlayerView(): View? {
    if (javaClass.name == "androidx.media3.ui.PlayerView") return this
    if (this !is ViewGroup) return null
    repeat(childCount) { index ->
        getChildAt(index).findMedia3PlayerView()?.let { return it }
    }
    return null
}

private fun media3PlayerFromView(view: View): Any? = runCatching {
    view.javaClass.getMethod("getPlayer").invoke(view)
}.getOrNull()
