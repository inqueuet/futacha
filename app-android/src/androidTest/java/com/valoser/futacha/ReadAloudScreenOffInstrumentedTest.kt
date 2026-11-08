@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.futaber.FutaberReadAloud
import com.valoser.futacha.shared.ui.futaber.FutaberSpeechEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test

/** A real recomposer and a really dark screen (no compose test clock); the engine is silent. */
class ReadAloudScreenOffInstrumentedTest {
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Test fun readingGoesOnWithTheScreenReallyOff() {
        val spoken = AtomicInteger(); val unfinished = AtomicInteger()
        val engine = object : FutaberSpeechEngine {
            override suspend fun prepare() {}
            override suspend fun speak(text: String) { delay(150); spoken.incrementAndGet() }
            override fun stop() {}
            override fun close() {}
        }
        val posts = (1..12).map { Post(id = "$it", order = it, author = null, subject = null, timestamp = "", messageHtml = "本文$it", imageUrl = null, thumbnailUrl = null) }
        var reader: FutaberReadAloud? = null
        var scroll: (suspend (Int) -> Unit)? = null
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard"); SystemClock.sleep(1_000)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val scope = rememberCoroutineScope()
                val list = rememberLazyListState()
                reader = remember { FutaberReadAloud(scope, { engine }) }
                scroll = { row -> unfinished.incrementAndGet(); list.animateScrollToItem(row); unfinished.decrementAndGet() }
                LazyColumn(state = list) { items(500) { Text("row $it", Modifier.fillMaxWidth()) } }
            } }
            SystemClock.sleep(1_000)
            scenario.onActivity { reader!!.start(posts, 0, scrollToRow = { scroll!!(it * 40) }) }
            val on = SystemClock.uptimeMillis() + 10_000
            while (spoken.get() < 2 && SystemClock.uptimeMillis() < on) SystemClock.sleep(20)
            assertTrue("screen on spoken=${spoken.get()} message=${reader!!.message}", spoken.get() >= 2)
            shell("input keyevent KEYCODE_SLEEP")
            val power = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(PowerManager::class.java)
            while (power.isInteractive) SystemClock.sleep(20)
            val atSleep = spoken.get()
            val until = SystemClock.uptimeMillis() + 45_000
            while (spoken.get() < 12 && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
            android.util.Log.i("ReadOff", "atSleep=$atSleep spoken=${spoken.get()} unfinished=${unfinished.get()} message=${reader!!.message}")
            shell("input keyevent KEYCODE_WAKEUP")
            assertEquals("from $atSleep message=${reader!!.message}", 12, spoken.get())
            assertTrue("spoke after the screen went off", atSleep < 12)
        }
        shell("wm dismiss-keyguard")
    }
}
