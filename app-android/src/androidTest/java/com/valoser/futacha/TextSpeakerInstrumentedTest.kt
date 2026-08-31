package com.valoser.futacha

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.audio.createTextSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextSpeakerInstrumentedTest {
    @Test
    fun preparationKeepsMainLooperResponsiveAndAlwaysFinishesWithinWatchdog() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val speaker = createTextSpeaker(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val mainLooperHeartbeat = CompletableDeferred<Unit>()

            try {
                val preparation = scope.async { runCatching { speaker.prepare() } }
                Handler(Looper.getMainLooper()).postDelayed(
                    { mainLooperHeartbeat.complete(Unit) },
                    100L
                )

                withTimeout(2_000L) { mainLooperHeartbeat.await() }
                val preparationResult = withTimeout(12_000L) { preparation.await() }

                // Devices without a Japanese engine may legitimately fail. The
                // contract under test is that initialization never freezes the UI
                // or waits forever at response zero.
                if (preparationResult.isSuccess) {
                    // On images that provide Japanese TTS, exercise the real
                    // onStart/onDone path as well as engine initialization.
                    withTimeout(12_000L) { speaker.speak("読み上げの動作確認です") }
                }
            } finally {
                speaker.close()
                scope.cancel()
            }
        }
    }
}
