package com.valoser.futacha

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CompatDrawingOrientationInstrumentedTest {
    @get:Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun postDrawingRequestsSensorLandscapeLikeBothReferenceManifests() {
        val compositionApplied = CountDownLatch(1)
        val originalOrientation = AtomicInteger(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        val originalConfiguration = AtomicInteger(Configuration.ORIENTATION_UNDEFINED)
        rule.scenario.onActivity { activity ->
            originalOrientation.set(activity.requestedOrientation)
            originalConfiguration.set(activity.resources.configuration.orientation)
            activity.setContent {
                MaterialTheme {
                    CompatPostDrawingScreen(
                        onSaved = {},
                        onBack = {},
                        forceLandscape = true
                    )
                }
                SideEffect { compositionApplied.countDown() }
            }
        }

        assertTrue(
            "drawing composition did not become active",
            compositionApplied.await(5, TimeUnit.SECONDS)
        )
        rule.scenario.onActivity { activity ->
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                activity.requestedOrientation
            )
        }

        // First settle the physical configuration, then restore the original
        // request. Merely assigning UNSPECIFIED is immediate at the property
        // level but leaves a portrait rotation in flight on API 26; that event
        // can race the next Compose test Activity and detach its hierarchy.
        val settledRequest = if (originalConfiguration.get() == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        rule.scenario.onActivity { activity -> activity.requestedOrientation = settledRequest }
        val orientationDeadline = System.currentTimeMillis() + 5_000L
        var settled = false
        while (!settled && System.currentTimeMillis() < orientationDeadline) {
            rule.scenario.onActivity { activity ->
                settled = activity.resources.configuration.orientation == originalConfiguration.get()
            }
            if (!settled) Thread.sleep(50L)
        }
        assertTrue("drawing host did not settle back to its original orientation", settled)
        rule.scenario.onActivity { activity ->
            activity.requestedOrientation = originalOrientation.get()
            assertEquals(originalOrientation.get(), activity.requestedOrientation)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
