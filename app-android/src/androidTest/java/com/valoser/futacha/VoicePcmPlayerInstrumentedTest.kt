@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.audio.voicevox.AndroidVoicePcmPlayer
import kotlinx.coroutines.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*

class VoicePcmPlayerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun requireMutedOutput() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertEquals("PCM playback tests must remain muted", 0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test fun tinyClipsAndStreamingBufferBoundariesFinishInOrder() = runBlocking {
        val player = AndroidVoicePcmPlayer(context)
        try {
            player.prepare {}
            for (frames in listOf(1, 24, 240, 2400, 8191, 8192, 8193, 24000)) {
                withTimeout(5_000) { player.play(wav(frames)) }
            }
        } finally { player.close() }
    }

    private fun wav(frames: Int): ByteArray = Buffer().apply {
        writeUtf8("RIFF"); writeIntLe(36 + frames * 2); writeUtf8("WAVEfmt "); writeIntLe(16)
        writeShortLe(1); writeShortLe(1); writeIntLe(24000); writeIntLe(48000)
        writeShortLe(2); writeShortLe(16); writeUtf8("data"); writeIntLe(frames * 2)
        repeat(frames) { writeShortLe(if (it % 24 < 12) 400 else -400) }
    }.readByteArray()
}
