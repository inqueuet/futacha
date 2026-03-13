package com.valoser.futacha

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.util.rememberUrlLauncher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UrlLauncherInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rememberUrlLauncher_launchesMailtoWithSendToIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_SENDTO).apply {
                addDataScheme("mailto")
            },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()),
            true
        )

        try {
            setLauncherContent("mailto:admin@valoser.com?subject=test")
            rule.onNodeWithText("Launch").performClick()
            rule.waitUntil(5_000) { monitor.hits > 0 }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun rememberUrlLauncher_launchesHttpsWithBrowsableViewIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()),
            true
        )

        try {
            setLauncherContent("https://example.com/path")
            rule.onNodeWithText("Launch").performClick()
            rule.waitUntil(5_000) { monitor.hits > 0 }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun rememberUrlLauncher_ignoresBlankInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sendToMonitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_SENDTO),
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()),
            true
        )
        val viewMonitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_VIEW),
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()),
            true
        )

        try {
            setLauncherContent("   ")
            rule.onNodeWithText("Launch").performClick()
            rule.waitForIdle()
            Thread.sleep(300)
            assertEquals(0, sendToMonitor.hits)
            assertEquals(0, viewMonitor.hits)
        } finally {
            instrumentation.removeMonitor(sendToMonitor)
            instrumentation.removeMonitor(viewMonitor)
        }
    }

    private fun setLauncherContent(url: String) {
        rule.setContent {
            val launcher = rememberUrlLauncher()
            Button(onClick = { launcher(url) }) {
                Text("Launch")
            }
        }
    }
}
