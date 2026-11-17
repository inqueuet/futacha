@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.net.Uri
import android.system.Os
import android.system.OsConstants
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.testing.video.VideoEditFixtures
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.CopyOnWriteArrayList

/** Existing viewer API, actual Media3 source states, local files only, no autoplay or external launch. */
class FtutterM60VideoStatusInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val opened = CopyOnWriteArrayList<String>()
    private val files = mutableListOf<File>()
    private var loader: ImageLoader? = null

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader?.shutdown()
        files.forEach { it.delete() }
    }

    @Test fun bufferingIsVisibleAndItsTerminalFailureOffersTheOriginalUrl() {
        val fifo = newFile("mp4")
        Os.mkfifo(fifo.path, "600".toInt(8))
        val url = Uri.fromFile(fifo).toString()
        var writer: FileDescriptor? = null
        try {
            host(mutableStateOf(url))
            rule.onNodeWithTag("ftutter-viewer-video-spinner").assertIsDisplayed()
            rule.onNodeWithTag("ftutter-viewer-video-error").assertDoesNotExist()
            val player = actualPlayer()
            rule.runOnIdle {
                assertEquals("The displayed spinner must accompany actual Media3 buffering", 2,
                    player.javaClass.getMethod("getPlaybackState").invoke(player))
            }
            writer = releaseBufferingIntoMissingFile(fifo)
            awaitError()
            // The terminal branch disposes NativePlatformVideoPlayer and its view.
            // Inspect the SAME instance retained during buffering; Media3 keeps
            // its terminal playbackError after release, so view lookup is invalid here.
            rule.runOnIdle {
                assertNotNull("UI failure must come from an actual Media3 error",
                    player.javaClass.getMethod("getPlayerError").invoke(player))
            }
            rule.onNodeWithTag("ftutter-viewer-video-spinner").assertDoesNotExist()
            rule.onNodeWithTag("ftutter-viewer-video-open-external").performClick()
            assertEquals(listOf(url), opened.toList())
        } finally {
            // Even an assertion failure must unblock a native open before player disposal.
            if (writer == null && fifo.exists()) writer = releaseBufferingIntoMissingFile(fifo)
            writer?.let(Os::close)
        }
    }

    @Test fun missingVideoExplainsTheFailureAndOffersAnExplicitBrowserAction() {
        val missing = Uri.fromFile(newFile("mp4")).toString()
        host(mutableStateOf(missing))
        awaitError()
        rule.onNodeWithText("動画を再生できませんでした").assertIsDisplayed()
        rule.onNodeWithText("エラー:", substring = true).assertIsDisplayed()
        assertTrue("Opening the viewer must not launch another app", opened.isEmpty())
        rule.onNodeWithTag("ftutter-viewer-video-open-external").performClick()
        assertEquals(listOf(missing), opened.toList())
    }

    @Test fun changingToAPlayableUrlClearsFailureAndPreservesPausedAndLoopControls() {
        val url = mutableStateOf(Uri.fromFile(newFile("mp4")).toString())
        host(url)
        awaitError()
        val playable = newFile("mp4").apply { writeBytes(VideoEditFixtures.bytes("landscape")) }
        rule.runOnIdle { url.value = Uri.fromFile(playable).toString() }
        rule.waitUntil(15000) {
            rule.runOnIdle {
                val view = rule.activity.findViewById<ViewGroup>(android.R.id.content).findMedia3PlayerView()
                val player = view?.let(::media3PlayerFromView)
                if (player == null) false else {
                    player.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType).invoke(player, 0f)
                    (player.javaClass.getMethod("getPlaybackState").invoke(player) as Int) == 3
                }
            }
        }
        rule.onNodeWithTag("ftutter-viewer-video-error").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-viewer-video-spinner").assertDoesNotExist()
        val player = rule.runOnIdle { requireNotNull(media3PlayerFromView(requireNotNull(
            rule.activity.findViewById<ViewGroup>(android.R.id.content).findMedia3PlayerView()))) }
        rule.runOnIdle {
            assertEquals(false, player.javaClass.getMethod("getPlayWhenReady").invoke(player))
            assertEquals(0, player.javaClass.getMethod("getRepeatMode").invoke(player))
        }
        rule.onNodeWithTag("ftutter-viewer-loop").performClick()
        rule.waitUntil(5000) { rule.runOnIdle { player.javaClass.getMethod("getRepeatMode").invoke(player) == 1 } }
        assertTrue(opened.isEmpty())
    }

    private fun host(url: androidx.compose.runtime.State<String>) {
        val images = ImageLoader.Builder(rule.activity).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.GRAY, 8, 8), chain.request) })
        }.build().also { loader = it }
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.DARK) {
                CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
                    FtutterViewerScreen(media = listOf(CompatPostSnapshot(position = 0, postNo = "60", timestamp = "",
                        messageHtml = "video", imageUrl = url.value)), initialIndex = 0,
                        settings = FtutterMediaSettings(showStrip = false, videoAutoPlay = false, videoLoop = false), privacyAlpha = 1f,
                        tools = FtutterMediaSaveTools("test", {}, {}, { _, _ -> FtutterBatchSaveResult(0, 0) }, false), posts = emptyList(),
                        conversationOf = { emptyList() }, onToggleStrip = {}, onSearch = {},
                        onOpenBrowser = { opened += it }, onBackToPost = {}, onClose = {})
                }
            }
        }
    }
    private fun awaitError() {
        rule.waitUntil(15000) { rule.onAllNodesWithTag("ftutter-viewer-video-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ftutter-viewer-video-error").assertIsDisplayed()
    }
    private fun newFile(extension: String) = File(rule.activity.cacheDir, "m60-${System.nanoTime()}.$extension").also(files::add)
    private fun actualPlayer(): Any {
        rule.waitUntil(5_000) { rule.runOnIdle {
            rule.activity.findViewById<ViewGroup>(android.R.id.content).findMedia3PlayerView()?.let(::media3PlayerFromView) != null
        } }
        return rule.runOnIdle { requireNotNull(media3PlayerFromView(requireNotNull(
            rule.activity.findViewById<ViewGroup>(android.R.id.content).findMedia3PlayerView()))) }
    }
    private fun releaseBufferingIntoMissingFile(fifo: File): FileDescriptor {
        // FileDataSource seeks after opening. A FIFO cannot seek, so Media3 can retry
        // and block in open again forever if the path remains a FIFO with no writer.
        // Keep a nonblocking writer open while unlinking the path: the current open
        // is released, and every retry sees a real missing-file error at this SAME URL.
        val descriptor = Os.open(fifo.path, OsConstants.O_RDWR or OsConstants.O_NONBLOCK, 0)
        try {
            assertTrue("The terminal-error fixture must remove the retry path", fifo.delete())
            return descriptor
        } catch (failure: Throwable) { Os.close(descriptor); throw failure }
    }
}
