package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.test.*

/** Fake audio-session driver: verifies real player ownership/dispatch without producing audio. */
class IosVoicePcmStopTest {
    @Test fun stopDefersSessionReleaseUntilMainQueueStopsPlaybackAndReleasesOffMain() = pumpingMain {
        val mainBarrierRan = MutableStateFlow(false)
        val driver = Driver { mainBarrierRan.value }
        val coordinator = IosAudioSessionCoordinator(driver)
        val player = IosVoicePcmPlayer { assertNotNull(coordinator.acquireSpeech()) }
        try {
            player.prepare {}
            withContext(Dispatchers.Main) {
                dispatch_async(dispatch_get_main_queue()) { mainBarrierRan.value = true }
                player.stop()
                player.stop()
            }
            withTimeout(5_000) { while (driver.deactivations.value.isEmpty()) delay(1) }
            assertEquals(listOf(false to true), driver.deactivations.value,
                "deactivation must run off main, after the queued player-stop turn")
        } finally { player.close() }
    }

    @Test fun closingOneSpeechPlayerPreservesAnotherPlayersLease() = pumpingMain {
        val driver = Driver { true }
        val coordinator = IosAudioSessionCoordinator(driver)
        val first = IosVoicePcmPlayer { assertNotNull(coordinator.acquireSpeech()) }
        val second = IosVoicePcmPlayer { assertNotNull(coordinator.acquireSpeech()) }
        try {
            first.prepare {}; second.prepare {}
            withContext(Dispatchers.Main) { first.stop(); first.close() }
            withTimeout(5_000) { while (driver.activations.value < 3) delay(1) }
            assertTrue(driver.deactivations.value.isEmpty())
            withContext(Dispatchers.Main) { second.stop(); second.close() }
            withTimeout(5_000) { while (driver.deactivations.value.isEmpty()) delay(1) }
            assertEquals(listOf(false to true), driver.deactivations.value)
        } finally { first.close(); second.close() }
    }

    private class Driver(private val barrierRan: () -> Boolean) : IosAudioSessionDriver {
        val activations = MutableStateFlow(0)
        val deactivations = MutableStateFlow<List<Pair<Boolean, Boolean>>>(emptyList())
        override fun configurePlayback(mixWithOthers: Boolean) = true
        override fun configureRecording() = true
        override fun activate(): Boolean { activations.value++; return true }
        override fun deactivateNotifyingOthers(): Boolean {
            deactivations.value += NSThread.isMainThread to barrierRan()
            return true
        }
    }
    private fun pumpingMain(block: suspend () -> Unit) = runBlocking {
        val work = async(Dispatchers.Default) { block() }
        while (!work.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        work.await()
    }
}
