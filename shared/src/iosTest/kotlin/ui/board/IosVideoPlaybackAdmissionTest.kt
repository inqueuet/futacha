package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.audio.IosAudioSessionCoordinator
import com.valoser.futacha.shared.audio.IosAudioSessionDriver
import com.valoser.futacha.shared.media.source.OriginalMediaInfo
import com.valoser.futacha.shared.util.iosWebmPlaybackAvailable
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Current runtime, injected OS capability; fake audio driver means no AVAudioSession activation or sound. */
class IosVideoPlaybackAdmissionTest {
    private class Driver : IosAudioSessionDriver {
        val calls = mutableListOf<String>()
        override fun configurePlayback(mixWithOthers: Boolean): Boolean { calls += "configure"; return true }
        override fun configureRecording(): Boolean { calls += "record"; return true }
        override fun activate(): Boolean { calls += "activate"; return true }
        override fun deactivateNotifyingOthers(): Boolean { calls += "deactivate"; return true }
    }

    @Test fun legacyWebmNeverInvokesSourceBookmarkDocumentOrAudioFactories(): Unit = runBlocking {
        val driver = Driver()
        val audio = IosAudioSessionCoordinator(driver)
        for ((major, minor) in listOf(15L to 1L, 16L to 7L, 17L to 3L)) {
            val capability = { url: String, info: OriginalMediaInfo? ->
                iosVideoPlaybackCapabilityError(url, supportsIosWebmPath(major, minor), info)
            }
            val url = "file:///saved/CLIP.WeBm?name=clip.mp4"
            var otherFactories = 0
            repeat(3) { // Same production factory boundary used by source, bookmark and document preparation.
                assertFailsWith<UnsupportedVideoPlayback> {
                    prepareVideoPlaybackResource(url, capability = capability) { otherFactories++ }
                }
            }
            assertFailsWith<UnsupportedVideoPlayback> {
                prepareVideoPlaybackResource(url, capability = capability) { audio.acquire(mixWithOthers = false) }
            }
            assertEquals(0, otherFactories)
            assertTrue(driver.calls.isEmpty())
        }
    }

    @Test fun sourceResolvedToWebmIsRejectedBeforeAudioButAppleContainersKeepTheAudioLifecycle(): Unit = runBlocking {
        val driver = Driver()
        val audio = IosAudioSessionCoordinator(driver)
        val capability = { url: String, info: OriginalMediaInfo? -> iosVideoPlaybackCapabilityError(url, false, info) }
        val primary = "https://may.2chan.net/b/src/redirect.mp4"
        assertFailsWith<UnsupportedVideoPlayback> {
            prepareVideoPlaybackResource(primary, OriginalMediaInfo("video/webm", 42, resolvedUrl = primary), capability) {
                audio.acquire(mixWithOthers = false)
            }
        }
        assertTrue(driver.calls.isEmpty())
        prepareVideoPlaybackResource(primary, capability = capability) { audio.acquire(mixWithOthers = true) }.close()
        // Closing the final mixed lease restores ordinary playback before notifying other audio sessions.
        assertEquals(listOf("configure", "activate", "configure", "deactivate"), driver.calls)
    }

    @Test fun platformPreflightUsesTheActualRuntimeCapabilityWithoutPretendingItIsAnOldRuntime() {
        val url = "https://may.2chan.net/b/src/clip.webm"
        assertEquals(iosVideoPlaybackCapabilityError(url, iosWebmPlaybackAvailable()), platformVideoPlaybackCapabilityError(url))
        assertNull(platformVideoPlaybackCapabilityError("file:///saved/clip.mp4"))
        assertNull(platformVideoPlaybackCapabilityError("https://example.test/unknown?id=webm"))
    }
}
