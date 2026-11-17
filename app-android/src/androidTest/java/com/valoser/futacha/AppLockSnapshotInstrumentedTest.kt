@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.state.buildAppLockPasswordHash
import com.valoser.futacha.shared.ui.FutachaApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Root hook and OS Overview capture. The only private body marker is synthetic, with memory-only images. */
class AppLockSnapshotInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())
    private val retryStates = mutableListOf<AppStateStore>()
    private val ready = AtomicBoolean()
    private var loader: ImageLoader? = null
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val marker = Color.rgb(13, 199, 47)
    private val taskTitle = "AUDIT M65"

    @After fun cleanup() {
        rule.mainClock.autoAdvance = true
        try {
            // ActivityScenario's lifecycle request does not close the real OS
            // Overview. Reorder this same ComponentActivity/task natively first;
            // SINGLE_TOP avoids creating a second Activity instead of cleaning ours.
            val activity = rule.activity
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity.startActivity(Intent(activity, activity.javaClass).addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                ))
            }
            assertTrue("The existing fixture task did not leave Overview",
                device.wait(Until.hasObject(By.pkg(activity.packageName)), 5_000))
            rule.waitUntil(5_000) {
                rule.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED
            }
            rule.runOnUiThread { activity.setContent {} }
        } finally {
            loader?.shutdown()
            state.close()
            retryStates.forEach { it.close() }
        }
    }

    private fun root(store: AppStateStore = state, waitForUnlock: Boolean = true) {
        val images = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(Color.BLUE, 64, 64), chain.request) })
        }.build().also { loader = it }
        rule.runOnUiThread {
            @Suppress("DEPRECATION")
            rule.activity.setTaskDescription(ActivityManager.TaskDescription(taskTitle))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
                Box(Modifier.fillMaxSize()) {
                    FutachaApp(stateStore = store, sharedRepository = FakeBoardRepository(),
                        onAppUnlockedChanged = { ready.set(it) })
                    // The Root still owns protection/lifecycle; this deterministic marker makes the task bitmap measurable.
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(marker))) {
                        Text("M65 PRIVATE BODY")
                    }
                }
            }
        }
        if (waitForUnlock) rule.waitUntil(5_000) { ready.get() }
        else rule.waitUntil(5_000) { rule.onAllNodesWithText("起動ロックの設定を読み込めませんでした。").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(rule.activity.filesDir, "m65-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun markerPixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
            val p = bitmap.getPixel(x, y)
            if (kotlin.math.abs(Color.red(p) - 13) < 8 && kotlin.math.abs(Color.green(p) - 199) < 8 &&
                kotlin.math.abs(Color.blue(p) - 47) < 8) count++
        }
        return count
    }
    private fun foregroundScreenshot(name: String): Bitmap {
        var rendered: Bitmap? = null
        rule.waitUntil(5_000) {
            val bitmap = screenshot(name)
            if (markerPixels(bitmap) > 5_000) { rendered = bitmap; true }
            else { bitmap.recycle(); false }
        }
        return requireNotNull(rendered)
    }
    private fun overviewMarkerPixels(name: String): Int {
        assertTrue(device.pressHome())
        device.waitForIdle(5_000)
        assertTrue(device.pressRecentApps())
        rule.waitUntil(5_000) {
            device.hasObject(By.descContains(taskTitle)) || device.hasObject(By.textContains(taskTitle))
        }
        device.waitForIdle(5_000)
        val bitmap = screenshot(name)
        return try { markerPixels(bitmap) } finally { bitmap.recycle() }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun lockSettingProtectsActualOverviewWithoutWaitingForAnotherRootFrame() {
        root()
        rule.mainClock.autoAdvance = false
        runBlocking { state.setAppLockPassword("m65-local-only") }
        // No Compose clock advance: only the Root hook's direct configuration collector can protect this snapshot.
        val foreground = foregroundScreenshot("enabled-foreground")
        try { assertTrue("API33+ must keep ordinary foreground screenshots usable", markerPixels(foreground) > 5_000) }
        finally { foreground.recycle() }
        assertTrue("Actual Overview must not contain the Root's pre-lock body marker", overviewMarkerPixels("enabled-overview") < 50)
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun lockOffKeepsActualOverviewAndForegroundScreenshotsUsable() {
        root()
        val foreground = foregroundScreenshot("disabled-foreground")
        try { assertTrue(markerPixels(foreground) > 5_000) } finally { foreground.recycle() }
        assertTrue("OFF is an explicit control: the actual task snapshot must contain the body marker", overviewMarkerPixels("disabled-overview") > 1_000)
    }

    @SdkSuppress(maxSdkVersion = 32)
    @Test fun legacyRootConfigurationRestoresOnlyItsOwnSecureBitWithoutAComposeFrame() {
        rule.runOnUiThread {
            rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        root()
        rule.mainClock.autoAdvance = false
        runBlocking { state.setAppLockPassword("m65-local-only") }
        rule.waitUntil(5_000) { rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        runBlocking { state.clearAppLockPassword() }
        rule.waitUntil(5_000) { rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
        assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test fun preExistingSecureFlagIsNeverRemovedByLockOffOrRootDisposal() {
        rule.runOnUiThread { rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        root()
        runBlocking { state.setAppLockPassword("m65-local-only"); state.clearAppLockPassword() }
        rule.runOnUiThread { rule.activity.setContent {} }
        assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertTrue(Build.VERSION.SDK_INT >= 26)
    }

    private fun verifyExplicitRetry(configuredAfterRecovery: Boolean) {
        val recovered = AtomicBoolean()
        val subscriptions = AtomicInteger()
        val storage = object : BaseInMemoryPlatformStateStorage() {
            override val appLockPasswordHash = flow {
                subscriptions.incrementAndGet()
                if (!recovered.get()) throw IOException("M65 local read failure")
                emitAll(appLockPasswordHashState)
            }
        }
        storage.appLockPasswordHashState.value = if (configuredAfterRecovery)
            buildAppLockPasswordHash("m65-local-only", "m65-retry-fixture") else null
        val retryState = AppStateStore(storage).also { retryStates += it }
        rule.runOnUiThread { rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        root(retryState, waitForUnlock = false)
        assertFalse(ready.get())
        if (Build.VERSION.SDK_INT < 33) {
            assertTrue("Unread configuration must remain protected before explicit retry",
                rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        val failedSubscriptions = subscriptions.get()
        recovered.set(true)
        rule.onNodeWithText("再試行").performClick()
        // Both the actual Root and its snapshot protection must reopen the failed
        // setting flow. No automatic/unbounded retries are introduced by the test.
        rule.waitUntil(5_000) { subscriptions.get() >= failedSubscriptions + 2 }
        if (configuredAfterRecovery) {
            rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            assertFalse("Recovered ON must still require the Root password", ready.get())
            if (Build.VERSION.SDK_INT < 33) assertTrue(rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            rule.mainClock.autoAdvance = false
            runBlocking { retryState.clearAppLockPassword() }
        } else {
            rule.waitUntil(5_000) { ready.get() }
            rule.mainClock.autoAdvance = false
        }
        if (Build.VERSION.SDK_INT < 33) {
            rule.waitUntil(5_000) { rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
        } else {
            assertTrue("Recovered OFF must restore actual task snapshots, even with the Root frame clock stopped",
                overviewMarkerPixels(if (configuredAfterRecovery) "retry-on-then-off" else "retry-off") > 1_000)
        }
    }

    @Test fun explicitReadRetryRestoresOffSnapshotPolicy() = verifyExplicitRetry(configuredAfterRecovery = false)
    @Test fun explicitReadRetryKeepsOnPolicyAndContinuesFollowingLaterOff() = verifyExplicitRetry(configuredAfterRecovery = true)
}
