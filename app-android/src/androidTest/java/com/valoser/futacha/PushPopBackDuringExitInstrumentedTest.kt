@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.PushPopScreen
import com.valoser.futacha.shared.ui.PushPopTransition
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PushPopBackDuringExitInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun anotherBackDuringPopBelongsToTheCatalog() {
        val screen = mutableStateOf(PushPopScreen("thread", 1, Unit))
        val calls = mutableListOf<String>()
        rule.setContent {
            PushPopTransition(screen.value) { shown ->
                PlatformBackHandler {
                    calls += shown.id
                    screen.value = PushPopScreen("catalog", 0, Unit)
                }
                Box(Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(listOf("thread", "catalog"), calls)
    }
}
