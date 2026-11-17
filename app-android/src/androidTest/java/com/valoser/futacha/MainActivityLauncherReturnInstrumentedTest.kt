package com.valoser.futacha

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M79: returning through the launcher icon must bring the existing task back as it was. A system
 * picker the app opened above MainActivity (here the real DocumentsUI) must still be there.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLauncherReturnInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val created = CopyOnWriteArrayList<MainActivity>()
    private val resumed = AtomicReference<MainActivity?>()
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is MainActivity) created += activity }
        override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.set(activity) }
        override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) resumed.compareAndSet(activity, null) }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    private val launcher get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(target.packageName, "com.valoser.futacha.MainActivityAliasCurrent"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    @Before fun prepare() { (target.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycle) }

    @After fun cleanup() {
        repeat(3) { if (device.currentPackageName?.contains("documentsui") == true) device.pressBack() }
        instrumentation.runOnMainSync { created.filterNot { it.isDestroyed }.forEach { it.finish() } }
        await("Main must finish after the test") { created.all { it.isDestroyed } }
        (target.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycle)
    }

    @Test fun theLauncherIconBringsBackAPickerOpenAboveTheApp() {
        target.startActivity(launcher)
        await("MainActivity must resume from the launcher intent") { resumed.get() != null }
        val main = requireNotNull(resumed.get())
        instrumentation.runOnMainSync {
            @Suppress("DEPRECATION")
            main.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 79)
        }
        await("The real DocumentsUI picker must open above the app") { device.currentPackageName?.contains("documentsui") == true }
        device.pressHome()
        await("Home must be shown") { device.currentPackageName?.let { !it.contains("documentsui") && it != target.packageName } == true }
        target.startActivity(launcher)
        SystemClock.sleep(2_500)
        assertTrue("The picker must still be on top, but ${device.currentPackageName} was shown",
            device.currentPackageName?.contains("documentsui") == true)
        assertEquals("No second MainActivity", 1, created.count { !it.isDestroyed })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError(message)
            SystemClock.sleep(50)
        }
    }
}
