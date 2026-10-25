@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.audio.AndroidSpeechPlaybackService
import com.valoser.futacha.shared.ui.board.AppVideoAudio
import com.valoser.futacha.testing.video.VideoPlaybackFixtures
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** L117: this app's video with sound pauses the reading aloud (not stops it), which goes on once the video stops. */
@UnstableApi
class VideoReadAloudFocusInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file = File(context.cacheDir, "l117-${System.nanoTime()}.webm")
    private val pauses = AtomicInteger()
    private val resumes = AtomicInteger()
    private val stops = AtomicInteger()
    private var lease: AutoCloseable? = null
    private var exo: ExoPlayer? = null
    private var audio: AppVideoAudio? = null

    @Before fun prepare() {
        file.writeBytes(VideoPlaybackFixtures.webm())
        lease = runBlocking {
            AndroidSpeechPlaybackService.acquire(context,
                onFocusPause = { pauses.incrementAndGet() },
                onFocusResume = { resumes.incrementAndGet() }) { stops.incrementAndGet() }
        }
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { audio?.release(); exo?.release() }
        lease?.close()
        file.delete()
    }

    private fun startVideo(audible: Boolean) = instrumentation.runOnMainSync {
        val player = ExoPlayer.Builder(context).build()
        exo = player
        audio = AppVideoAudio(player, audible).also { created ->
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            created.player.play()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            assertTrue(what, SystemClock.uptimeMillis() < end)
            SystemClock.sleep(50)
        }
    }

    @Test fun aVideoHeardPausesTheReadingWhichGoesOnAfterIt() {
        startVideo(audible = true)
        waitFor("The reading paused for the video") { pauses.get() == 1 }
        SystemClock.sleep(1_000) // the video's own focus request has been answered
        assertEquals("The reading was not stopped by the video", 0, stops.get())

        instrumentation.runOnMainSync { audio!!.player.pause() }
        waitFor("The reading goes on once the video is paused") { resumes.get() == 1 }
        assertEquals(0, stops.get())
    }

    @Test fun mutingTheVideoLetsTheReadingGoOn() {
        startVideo(audible = true)
        waitFor("The reading paused for the video") { pauses.get() == 1 }
        instrumentation.runOnMainSync { audio!!.setAudible(false) }
        waitFor("The reading goes on with the video muted") { resumes.get() == 1 }
        assertEquals(0, stops.get())
    }

    @Test fun aMutedVideoPausesNothing() {
        startVideo(audible = false)
        SystemClock.sleep(1_500)
        assertEquals(0, pauses.get())
        assertEquals(0, stops.get())
    }
}
