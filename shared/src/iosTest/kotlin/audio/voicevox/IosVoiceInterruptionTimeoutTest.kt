@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Buffer
import platform.AVFAudio.*
import platform.Foundation.*
import kotlin.test.*

class IosVoiceInterruptionTimeoutTest {
    @Test fun missingInterruptionEndFailsWithinTheBudgetWithoutStartingAudio() = pumpingMain {
        val released = MutableStateFlow(false)
        val driver = object : IosAudioSessionDriver {
            override fun configurePlayback(mixWithOthers: Boolean) = true
            override fun configureRecording() = true
            override fun activate() = true
            override fun deactivateNotifyingOthers(): Boolean { released.value = true; return true }
        }
        val coordinator = IosAudioSessionCoordinator(driver)
        val player = IosVoicePcmPlayer(interruptionTimeoutMillis = 25, acquireSession = {
            assertNotNull(coordinator.acquireSpeech())
        })
        try {
            player.prepare {}
            withContext(Dispatchers.Main) {
                NSNotificationCenter.defaultCenter.postNotificationName(
                    AVAudioSessionInterruptionNotification, null,
                    mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(int = 1))
                )
            }
            val failure = runCatching { withTimeout(500) { player.play(silentWav()) } }.exceptionOrNull()
            assertEquals("音声の割り込みが終わらないため停止しました", failure?.message)
        } finally {
            player.close()
            withTimeout(5_000) { while (!released.value) delay(1) }
        }
    }
    private fun silentWav(): ByteArray = Buffer().apply {
        writeUtf8("RIFF"); writeIntLe(436); writeUtf8("WAVEfmt "); writeIntLe(16)
        writeShortLe(1); writeShortLe(1); writeIntLe(24000); writeIntLe(48000)
        writeShortLe(2); writeShortLe(16); writeUtf8("data"); writeIntLe(400); write(ByteArray(400))
    }.readByteArray()
    private fun pumpingMain(block: suspend () -> Unit) = runBlocking {
        val work = async(Dispatchers.Default) { block() }
        while (!work.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        work.await()
    }
}
