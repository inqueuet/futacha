@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.CompatPostLayoutState
import com.valoser.futacha.shared.ui.compat.CompatPostRow
import com.valoser.futacha.shared.ui.futaber.FutaberReadAloud
import com.valoser.futacha.shared.ui.futaber.FutaberSpeechEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The 13.7 behaviours that only show on a real screen. */
class Release137ScreenCheckInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }
    @After fun wake() {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        CompatPostLayoutState.extraSpacingDp = null
    }

    @Test fun thePostGapIsReallyNarrowerAndWider() {
        rule.setContent { MaterialTheme { androidx.compose.foundation.layout.Column(Modifier.testTag("row")) {
            CompatPostRow(CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "test"), fontSize = 14, thumbnailSize = 64)
        } } }
        fun height(): Float { rule.waitForIdle(); return rule.onNodeWithTag("row").getBoundsInRoot().height.value }
        val standard = height()
        rule.runOnIdle { CompatPostLayoutState.extraSpacingDp = 0 }
        assertEquals(standard - 8f, height(), 1f)
        rule.runOnIdle { CompatPostLayoutState.extraSpacingDp = 8 }
        assertEquals(standard, height(), 1f)
        rule.runOnIdle { CompatPostLayoutState.extraSpacingDp = 20 }
        assertEquals(standard + 12f, height(), 1f)
    }

}
