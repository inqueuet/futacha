package com.valoser.futacha

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResult
import androidx.lifecycle.Lifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.board.IncomingSharedAttachment
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual external test-APK task -> exported MainActivity, without delivery flags supplied by the sender. */
@RunWith(AndroidJUnit4::class)
class MainActivityExternalTaskInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val sender = M66SenderFixture(instrumentation)
    private val previousAttachment = IncomingSharedAttachment.pending.value
    private var scenario: ActivityScenario<MainActivity>? = null
    private var scenarioActivity: MainActivity? = null
    private var scenarioLaunchIntent: Intent? = null
    private val resumed = AtomicReference<MainActivity?>()
    private val created = CopyOnWriteArrayList<MainActivity>()
    private val deliveredIntents = CopyOnWriteArrayList<String>()
    private var attachment: Uri? = null
    private val documents = mutableListOf<Uri>()
    private val htmlChildren = CopyOnWriteArrayList<SavedHtmlViewerActivity>()
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity is MainActivity) { created += activity; log("created", activity) }
            if (activity is SavedHtmlViewerActivity) htmlChildren += activity
        }
        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) { resumed.set(activity); log("resumed", activity) }
        }
        override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) resumed.compareAndSet(activity, null) }
        override fun onActivityDestroyed(activity: Activity) { if (activity is MainActivity) log("destroyed", activity) }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    }
    private fun log(event: String, activity: MainActivity) = android.util.Log.i("AuditM66",
        "$event id=${System.identityHashCode(activity)} task=${activity.taskId} root=${activity.isTaskRoot} action=${activity.intent.action}")
    @Before fun prepare() {
        (target.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycle)
        previousAttachment?.let(IncomingSharedAttachment::consume)
    }
    @After fun cleanup() {
        try {
            instrumentation.runOnMainSync {
                // ActivityScenario 1.7 matches every lifecycle event against the launch Intent.
                // Main legitimately replaces its Intent on external delivery; restore it only
                // after all delivery assertions, so Scenario can observe the real destruction.
                scenarioActivity?.takeUnless { it.isDestroyed }?.let { activity ->
                    scenarioLaunchIntent?.let { activity.intent = Intent(it) }
                }
                htmlChildren.filterNot { it.isDestroyed }.forEach { it.finish() }
                created.filterNot { it.isDestroyed }.forEach { it.finish() }
            }
            await("Every Main/native child created by this test must finish") { created.all { it.isDestroyed } && htmlChildren.all { it.isDestroyed } }
            await("Scenario must observe the original Activity's actual destruction") {
                scenario?.state?.let { it == Lifecycle.State.DESTROYED } ?: true
            }
            scenario?.close()
        } finally {
            try { sender.close() } finally {
                (target.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycle)
                attachment?.let { target.contentResolver.delete(it, null, null) }
                documents.forEach { target.contentResolver.delete(it, null, null) }
                IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
                previousAttachment?.let(IncomingSharedAttachment::offer)
            }
        }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError(message)
            SystemClock.sleep(25)
        }
    }
    private fun launchOriginal(): MainActivity {
        scenario = ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(target.packageName, MainActivity::class.java.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var original: MainActivity? = null
        scenario!!.onActivity {
            original = it
            scenarioActivity = it
            scenarioLaunchIntent = Intent(it.intent)
            it.addOnNewIntentListener { incoming ->
                incoming.getStringExtra("audit-m66-delivery")?.let(deliveredIntents::add)
            }
        }
        return requireNotNull(original)
    }
    private fun captureResumedActivity(): MainActivity {
        // Capture on the lifecycle thread. ON_PAUSE may clear the observed
        // reference immediately afterwards; all assertions use this one capture.
        var captured: MainActivity? = null
        await("MainActivity must be resumed before capturing its identity") {
            instrumentation.runOnMainSync { captured = resumed.get() }
            captured != null
        }
        return requireNotNull(captured)
    }
    private fun sendFromForeignTask(view: Boolean = false) {
        val foreignTask = sender.start(attachment, view)
        assertTrue("A normal external dispatch must start in a task distinct from Main", created.none { !it.isDestroyed && it.taskId == foreignTask })
        await("The separate test-APK sender must become visible") {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
            root.findAccessibilityNodeInfosByText("M66 send once").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        await("MainActivity must resume after the external dispatch") { resumed.get() != null }
    }
    @Test fun anExternalShareReusesTheExistingRootAndCopiesTheCompleteGrantedAttachment() {
        val bytes = ByteArrayOutputStream().also { stream -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED); compress(Bitmap.CompressFormat.JPEG, 90, stream); recycle()
        } }.toByteArray()
        val uri = Uri.parse("content://com.valoser.futacha.test.attachments/audit-m66-${System.nanoTime()}.jpg").also { attachment = it }
        target.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        val original = launchOriginal()
        val originalTask = original.taskId
        sendFromForeignTask()
        await("The real onCreate/onNewIntent attachment reader must copy the content URI") { IncomingSharedAttachment.pending.value != null }
        assertArrayEquals(bytes, IncomingSharedAttachment.pending.value!!.bytes)
        val actual = captureResumedActivity()
        android.util.Log.i("AuditM66", "delivery originalTask=$originalTask actualTask=${actual.taskId} count=${created.size}")
        assertSame("A foreign share must preserve the original MainActivity and its editor/session state", original, actual)
        assertEquals(originalTask, actual.taskId)
    }
    @Test fun anExternalViewRequestAlsoReusesTheExistingMainInsteadOfJoiningTheSenderTask() {
        val original = launchOriginal()
        sendFromForeignTask(view = true)
        val actual = captureResumedActivity()
        assertSame("External VIEW must reach the existing root through onNewIntent", original, actual)
        assertEquals(original.taskId, actual.taskId)
    }
    @Test fun launcherAliasNotificationAndWatchStyleIntentsKeepTheExistingRoot() {
        val original = launchOriginal()
        val intents = listOf(
            requireNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName)),
            buildWatchAlertContentIntent(target),
            Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        intents.forEachIndexed { index, template ->
            val delivery = "${System.nanoTime()}-$index"
            target.startActivity(Intent(template).putExtra("audit-m66-delivery", delivery))
            await("Each launcher/notification/watch intent must actually reach onNewIntent") { delivery in deliveredIntents }
            instrumentation.waitForIdleSync()
            val actual = captureResumedActivity()
            assertSame("Each delivered intent must retain the existing root", original, actual)
            assertEquals(original.taskId, actual.taskId)
        }
    }

    @Test fun reopeningTheRootClearsItsSavedHtmlChildWithoutReplacingTheMainInstance() {
        val original = launchOriginal()
        val uri = Uri.parse("content://com.valoser.futacha.test.attachments/audit-m66-${System.nanoTime()}.html").also(documents::add)
        target.contentResolver.openOutputStream(uri, "w")!!.use {
            it.write("<!doctype html><html><body>M66 local saved HTML</body></html>".toByteArray())
        }
        instrumentation.runOnMainSync {
            original.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html")
                .setClass(original, SavedHtmlViewerActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        await("The real saved HTML child must stay open") { htmlChildren.any { !it.isFinishing && !it.isDestroyed } }
        val child = htmlChildren.last()
        assertEquals("Native child must be above Main in its own task", original.taskId, child.taskId)
        target.startActivity(buildWatchAlertContentIntent(target))
        await("Returning to the root must clear its native child") { child.isDestroyed && resumed.get() != null }
        val actual = captureResumedActivity()
        assertSame(original, actual)
        assertEquals(original.taskId, actual.taskId)
    }

    @Test fun anExternalPickerReturnsItsGrantedResultToTheSameMainInstance() {
        val original = launchOriginal()
        val uri = Uri.parse("content://com.valoser.futacha.test.attachments/audit-m66-picker-${System.nanoTime()}.jpg").also { attachment = it }
        target.contentResolver.openOutputStream(uri, "w")!!.use { it.write(byteArrayOf(1, 2, 3)) }
        val result = AtomicReference<ActivityResult?>()
        var launcher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
        instrumentation.runOnMainSync {
            launcher = original.activityResultRegistry.register("audit-m66-picker", ActivityResultContracts.StartActivityForResult()) { result.set(it) }
            launcher!!.launch(sender.picker(uri))
        }
        await("The real external picker fixture must become visible") {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("M66 return picker")
                ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        await("The actual ActivityResultRegistry callback must receive the URI") { result.get() != null }
        assertEquals(Activity.RESULT_OK, result.get()!!.resultCode)
        assertEquals(uri, result.get()!!.data?.data)
        await("Main must resume after the result") { resumed.get() != null }
        val actual = captureResumedActivity()
        assertSame(original, actual)
        assertEquals(original.taskId, actual.taskId)
        instrumentation.runOnMainSync { launcher?.unregister() }
    }

}
