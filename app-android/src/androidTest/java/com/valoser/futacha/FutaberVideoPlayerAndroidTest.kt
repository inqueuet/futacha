package com.valoser.futacha

import android.net.Uri
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.futaber.FutaberTheme
import com.valoser.futacha.shared.ui.futaber.FutaberThemeMode
import com.valoser.futacha.shared.ui.futaber.FutaberVideoPlayer
import com.valoser.futacha.testing.video.VideoPlaybackFixtures
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The video viewer of ふたばー風モード (the picture viewer's video page): its states and controls. A FIFO keeps the
 * player buffering (a missing file would fail at once), and a missing file shows the failed state.
 */
class FutaberVideoPlayerAndroidTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun withBlockingVideoSource(block: (String) -> Unit) {
        val fifo = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "futaber-player-${System.nanoTime()}.mp4")
        Os.mkfifo(fifo.path, "600".toInt(8))
        try {
            block(Uri.fromFile(fifo).toString())
        } finally {
            thread(isDaemon = true) { runCatching { FileOutputStream(fifo).close() } }.join(5_000L)
            fifo.delete()
        }
    }

    @Test
    fun whileTheVideoBuffersTheViewerShowsTheSpinnerAndTheControlsThatStopAndOpenTheBrowser() = withBlockingVideoSource { url ->
        var stopped = 0
        var opened = 0
        rule.setContent {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberVideoPlayer(
                    videoUrl = url, posterUrl = null, active = true,
                    onStop = { stopped++ }, onOpenExternal = { opened++ }
                )
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-video-spinner").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-video-panel").assertIsDisplayed()
        // 音量：ミュートの切り替えと音量の表示。
        rule.onNodeWithText("音量 90%").assertIsDisplayed()
        rule.onNodeWithTag("futaber-video-volume").assertIsDisplayed()
        rule.onNodeWithContentDescription("ミュート").performClick()
        rule.onNodeWithText("ミュート中").assertIsDisplayed()
        rule.onNodeWithContentDescription("ミュート解除").performClick()
        rule.onNodeWithText("音量 90%").assertIsDisplayed()
        // 停止とブラウザで開く。
        rule.onNodeWithTag("futaber-video-external").performClick()
        assertEquals(1, opened)
        rule.onNodeWithTag("futaber-video-stop").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun aVideoThatCannotPlayShowsTheErrorAndKeepsStopAndTheBrowserButOffersNoVolume() {
        val missing = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "no-such-video.mp4")
        missing.delete()
        var stopped = false
        rule.setContent {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberVideoPlayer(
                    videoUrl = Uri.fromFile(missing).toString(), posterUrl = null, active = true,
                    onStop = { stopped = true }, onOpenExternal = {}
                )
            }
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-video-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("動画を再生できませんでした").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("futaber-video-volume").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("futaber-video-external").assertIsDisplayed()
        rule.onNodeWithTag("futaber-video-stop").performClick()
        assertTrue(stopped)
    }

    @Test
    fun aVideoThatPlaysKeepsItsControlsUsableWhetherTheyHidAfterStartingOrStayedAfterTheClipEnded() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "futaber-play-${System.nanoTime()}.webm")
        file.writeBytes(VideoPlaybackFixtures.webm())
        try {
            var stopped = false
            rule.setContent {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberVideoPlayer(
                        videoUrl = Uri.fromFile(file).toString(), posterUrl = null, active = true,
                        onStop = { stopped = true }, onOpenExternal = {}
                    )
                }
            }
            // 読み込みが終わるとスピナーは消える（デコードできない環境では、ここで飛ばす）。
            val started = runCatching {
                rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-video-spinner").fetchSemanticsNodes().isEmpty() }
            }.isSuccess
            org.junit.Assume.assumeTrue("The emulator could not decode the sample video", started)
            rule.onNodeWithTag("futaber-video-player").assertIsDisplayed()
            // 再生中ならコントロールは数秒で隠れ、タップで戻る。短い素材が終わった後は、そのまま残る。どちらでも停止が押せる。
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("futaber-video-panel").fetchSemanticsNodes().isEmpty()) {
                rule.onNodeWithTag("futaber-video-player").performClick()
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-video-panel").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("futaber-video-stop").performClick()
            assertTrue(stopped)
        } finally {
            file.delete()
        }
    }

    @Test
    fun aVideoOpensStoppedAndDoesNotStartPlayingOnItsOwn() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "futaber-stopped-${System.nanoTime()}.webm")
        file.writeBytes(VideoPlaybackFixtures.webm())
        try {
            rule.setContent {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberVideoPlayer(
                        videoUrl = Uri.fromFile(file).toString(), posterUrl = null, active = true,
                        onStop = {}, onOpenExternal = {}
                    )
                }
            }
            rule.waitUntil(20_000) {
                rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content).findMedia3PlayerView()
                    ?.let(::media3PlayerFromView) != null
            }
            val started = runCatching {
                rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-video-spinner").fetchSemanticsNodes().isEmpty() }
            }.isSuccess
            org.junit.Assume.assumeTrue("The emulator could not decode the sample video", started)
            // 読み込みが済んでも、再生は始まらない（押して初めて再生する）。
            Thread.sleep(1_500)
            rule.runOnIdle {
                val player = requireNotNull(
                    media3PlayerFromView(requireNotNull(rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content).findMedia3PlayerView()))
                )
                assertEquals(false, player.javaClass.getMethod("getPlayWhenReady").invoke(player))
                assertEquals(false, player.javaClass.getMethod("isPlaying").invoke(player))
            }
        } finally {
            file.delete()
        }
    }
}
