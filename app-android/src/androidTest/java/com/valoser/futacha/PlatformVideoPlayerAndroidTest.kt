package com.valoser.futacha

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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import org.junit.Rule
import org.junit.Test

class PlatformVideoPlayerAndroidTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

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

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("state:Buffering").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithText("state:Buffering").assertIsDisplayed()

        rule.onNodeWithText("Mute").performClick()
        rule.onNodeWithText("Volume30").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("state:Error").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithText("state:Error").assertIsDisplayed()
    }
}
