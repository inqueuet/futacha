@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Counts committed compositions, with a remembered content lambda and stable child arguments. */
class FtutterPreferenceRecompositionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }

    @Composable private fun Body(counter: AtomicInteger) {
        SideEffect { counter.incrementAndGet() }
        Text("unchanged reply body")
    }
    @Composable private fun LinkConsumer(counter: AtomicInteger) {
        rememberFtutterUrlLauncher()
        SideEffect { counter.incrementAndGet() }
    }

    @Test fun unrelatedPreferenceWritesDoNotRecomposeReplyBodiesOrLinkConsumers() {
        val body = AtomicInteger()
        val links = AtomicInteger()
        var preferences by mutableStateOf(mapOf(FtutterKeys.USE_CUSTOM_TAB to "true"))
        rule.setContent {
            val content: @Composable () -> Unit = remember { { Body(body); LinkConsumer(links) } }
            FtutterTheme(null, FtutterBrightness.LIGHT, preferences = preferences, content = content)
        }
        rule.waitForIdle()
        val bodyBefore = body.get()
        val linksBefore = links.get()
        repeat(5) { index ->
            rule.runOnUiThread { preferences = preferences + (FtutterKeys.STATS_OPENED to index.toString()) }
            rule.waitForIdle()
        }
        assertEquals("Unchanged reply content must be skipped", bodyBefore, body.get())
        assertEquals("Unrelated preferences must not invalidate URL launchers", linksBefore, links.get())
    }

    @Test fun changingCustomTabsRecomposesItsConsumerWithoutRecomposingReplyBodies() {
        val body = AtomicInteger()
        val links = AtomicInteger()
        var preferences by mutableStateOf(mapOf(FtutterKeys.USE_CUSTOM_TAB to "true"))
        rule.setContent {
            val content: @Composable () -> Unit = remember { { Body(body); LinkConsumer(links) } }
            FtutterTheme(null, FtutterBrightness.LIGHT, preferences = preferences, content = content)
        }
        rule.waitForIdle()
        val bodyBefore = body.get()
        val linksBefore = links.get()
        rule.runOnUiThread { preferences = mapOf(FtutterKeys.USE_CUSTOM_TAB to "false") }
        rule.waitForIdle()
        assertEquals("The browser setting must leave unrelated replies untouched", bodyBefore, body.get())
        assertEquals("Its actual consumer must receive the new browser setting", linksBefore + 1, links.get())
    }
}
