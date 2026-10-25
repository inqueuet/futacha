package com.valoser.futacha

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.board.IncomingSharedAttachment
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Shares pointing at this app's own private files (L128) and shares / links relaunched from Recents (L151). */
@RunWith(AndroidJUnit4::class)
class MainActivityIncomingIntentInstrumentedTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(target.filesDir, "futacha/audit-l128")
    private val previousAttachment = IncomingSharedAttachment.pending.value
    private var scenario: ActivityScenario<MainActivity>? = null
    private val created = AtomicReference<MainActivity?>()
    private val pendingAtCreate = AtomicReference<String?>("unset")
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
            if (activity is MainActivity) { pendingAtCreate.set(activity.pendingDeepLinksForTest().thread); created.set(activity) }
        }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    @Before fun prepare() {
        (target.applicationContext as Application).registerActivityLifecycleCallbacks(callbacks)
        previousAttachment?.let(IncomingSharedAttachment::consume)
        folder.mkdirs()
    }

    @After fun cleanUp() {
        scenario?.close()
        created.get()?.let { activity -> activity.runOnUiThread { activity.finish() } }
        (target.applicationContext as Application).unregisterActivityLifecycleCallbacks(callbacks)
        IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
        previousAttachment?.let(IncomingSharedAttachment::offer)
        folder.deleteRecursively()
    }

    private fun ownUri(name: String, bytes: ByteArray): Uri {
        val file = File(folder, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(target, "${target.packageName}.fileprovider", file)
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
            .also { bitmap.recycle() }
    }

    private fun share(uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        setClass(target, MainActivity::class.java)
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
    }

    private fun launch(intent: Intent) { scenario = ActivityScenario.launch(intent) }

    /** As Recents relaunches the task's root: with its (replaced) intent and FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY. */
    private fun relaunchFromRecents(intent: Intent) {
        target.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        val end = SystemClock.uptimeMillis() + 15_000
        while (created.get() == null && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        assertTrue("MainActivity was created", created.get() != null)
    }

    private fun offeredWithin(millis: Long): ByteArray? {
        val end = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < end) {
            IncomingSharedAttachment.pending.value?.let { return it.bytes }
            SystemClock.sleep(50)
        }
        return null
    }

    @Test fun aShareOfTheAppsOwnPrivateFileIsNotRead() {
        launch(share(ownUri("cookies.json", """{"cookies":"secret"}""".toByteArray())))
        assertNull("The app's own JSON is not taken as an attachment", offeredWithin(3_000))
    }

    @Test fun aShareOfTheAppsOwnSavedImageIsStillRead() {
        val bytes = png()
        launch(share(ownUri("saved.png", bytes)))
        val offered = offeredWithin(10_000)
        assertTrue("A saved image shared back to the app is still attached", offered != null)
        assertArrayEquals(bytes, offered)
    }

    @Test fun aShareRelaunchedFromRecentsIsNotTakenAgain() {
        relaunchFromRecents(share(ownUri("again.png", png())))
        assertNull("The old share is not attached again", offeredWithin(3_000))
    }

    @Test fun aThreadLinkRelaunchedFromRecentsIsNotOpenedAgain() {
        relaunchFromRecents(Intent(Intent.ACTION_VIEW, Uri.parse("https://may.2chan.net/b/res/151.htm"))
            .setClass(target, MainActivity::class.java))
        assertNull("The old link is not pending again", pendingAtCreate.get())
    }
}
