package com.valoser.futacha.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G-4: the shared audio session is released, mixes while muted and is restored after voice input. */
class IosAudioSessionCoordinatorTest {
    private class FakeDriver(var activationSucceeds: Boolean = true) : IosAudioSessionDriver {
        val calls = mutableListOf<String>()
        override fun configurePlayback(mixWithOthers: Boolean): Boolean {
            calls += if (mixWithOthers) "playback+mix" else "playback"
            return true
        }
        override fun configureRecording(): Boolean {
            calls += "record"
            return true
        }
        override fun activate(): Boolean {
            calls += "activate"
            return activationSucceeds
        }
        override fun deactivateNotifyingOthers(): Boolean {
            calls += "deactivate+notify"
            return true
        }
    }

    @Test
    fun closingTheLastPlayerDeactivatesSoOtherAppsResume() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val lease = coordinator.acquire(mixWithOthers = false)
        assertEquals(listOf("playback", "activate"), driver.calls)
        driver.calls.clear()
        lease.close()
        lease.close()
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun mutedPlaybackMixesWithOtherAppsUntilAnyPlayerIsAudible() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val muted = coordinator.acquire(mixWithOthers = true)
        assertEquals(listOf("playback+mix", "activate"), driver.calls)
        driver.calls.clear()
        val audible = coordinator.acquire(mixWithOthers = false)
        assertEquals(listOf("playback", "activate"), driver.calls)
        driver.calls.clear()
        audible.updateMixWithOthers(true)
        assertEquals(listOf("playback+mix", "activate"), driver.calls)
        driver.calls.clear()
        audible.close()
        assertEquals(listOf("playback+mix", "activate"), driver.calls)
        driver.calls.clear()
        muted.updateMixWithOthers(true)
        assertEquals(emptyList(), driver.calls, "an unchanged mute state does nothing")
        muted.close()
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun activationFailureDuringACallDoesNotBlockPlayback() {
        val driver = FakeDriver(activationSucceeds = false)
        val coordinator = IosAudioSessionCoordinator(driver)
        val lease = coordinator.acquire(mixWithOthers = false)
        driver.calls.clear()
        lease.close()
        assertEquals(emptyList(), driver.calls, "a session that never became active is not deactivated")
    }

    @Test
    fun voiceInputRestoresPlaybackAndLetsOtherAppsResume() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        assertTrue(coordinator.beginRecording())
        assertEquals(listOf("record", "activate"), driver.calls)
        driver.calls.clear()
        coordinator.endRecording()
        coordinator.endRecording()
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    /** E4-3: the off-main release of an earlier voice input must not end a newer one. */
    @Test
    fun lateReleaseOfAnEarlierVoiceInputKeepsTheNewerOneRecording() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val first = assertNotNull(coordinator.beginRecordingSession())
        val second = assertNotNull(coordinator.beginRecordingSession())
        driver.calls.clear()
        coordinator.endRecording(first)
        assertEquals(emptyList(), driver.calls, "the stale release left the microphone session alone")
        coordinator.endRecording(second)
        coordinator.endRecording(second)
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun voiceInputEndingUnderAnOpenPlayerReturnsToItsPlaybackSession() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val lease = coordinator.acquire(mixWithOthers = true)
        assertTrue(coordinator.beginRecording())
        driver.calls.clear()
        coordinator.acquire(mixWithOthers = true).close()
        assertEquals(emptyList(), driver.calls, "recording keeps the session until it ends")
        coordinator.endRecording()
        assertEquals(listOf("playback+mix", "activate"), driver.calls)
        driver.calls.clear()
        lease.close()
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun failedMicrophoneStartRestoresPlayback() {
        val driver = FakeDriver(activationSucceeds = false)
        val coordinator = IosAudioSessionCoordinator(driver)
        assertFalse(coordinator.beginRecording())
        assertEquals(listOf("record", "activate", "playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun readAloudKeepsItsSessionWhenAPlayerCloses() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val speech = assertNotNull(coordinator.acquireSpeech())
        val lease = coordinator.acquire(mixWithOthers = true)
        driver.calls.clear()
        lease.close()
        assertEquals(listOf("playback", "activate"), driver.calls)
        speech.close()
        assertNull(IosAudioSessionCoordinator(FakeDriver(activationSucceeds = false)).acquireSpeech())
    }

    @Test
    fun endingReadAloudLetsOtherAppsResume() {
        val driver = FakeDriver()
        val coordinator = IosAudioSessionCoordinator(driver)
        val speech = assertNotNull(coordinator.acquireSpeech())
        driver.calls.clear()
        speech.close()
        assertEquals(listOf("playback", "deactivate+notify"), driver.calls)
    }

    @Test
    fun failedReadAloudActivationHoldsNoSession() {
        val driver = FakeDriver(activationSucceeds = false)
        val coordinator = IosAudioSessionCoordinator(driver)
        assertNull(coordinator.acquireSpeech())
        assertEquals(listOf("playback", "activate"), driver.calls)
        // The failed speech request left no holder: a later muted player still mixes.
        driver.activationSucceeds = true
        driver.calls.clear()
        val video = coordinator.acquire(mixWithOthers = true)
        video.close()
        assertEquals(listOf("playback+mix", "activate", "playback", "deactivate+notify"), driver.calls)
    }
}
