@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.createPlatformStateStorage
import com.valoser.futacha.shared.ui.board.IncomingSharedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.*
import org.junit.Assert.*

/** Real MainActivity, mock tutorial thread/editor, and foreign-task content share. Never sends a post. */
class MainActivitySharedDraftInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sender = M66SenderFixture(instrumentation)
    private lateinit var app: FutachaApplication
    private lateinit var original: MainActivity
    private lateinit var originalLaunchIntent: Intent
    private lateinit var profile: ExperienceProfile
    private var passwordHash: String? = null
    private lateinit var tutorial: BoardSummary
    private var history = emptyList<ThreadHistoryEntry>()
    private var preferences = emptyMap<String, String>()
    private var capturedState = false
    private val previousAttachment = IncomingSharedAttachment.pending.value
    private var uri: Uri? = null
    private val mains = CopyOnWriteArrayList<MainActivity>()
    private val resumed = AtomicReference<MainActivity?>()
    private val stopped = AtomicBoolean()
    private val marker = "M66 keep this unsent draft"
    private val password = "audit-m66-lock"
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is MainActivity) mains += activity }
        override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.set(activity) }
        override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) resumed.compareAndSet(activity, null) }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) { if (::original.isInitialized && activity === original) stopped.set(true) }
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        original = rule.activity; resumed.set(original)
        originalLaunchIntent = Intent(original.intent)
        app.registerActivityLifecycleCallbacks(lifecycle)
        profile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            passwordHash = app.appStateStore.appLockPasswordHash.first()
            tutorial = requireNotNull(app.appStateStore.boards.first().firstOrNull { it.url.contains("example.com/t/") }) {
                "The owned AVD needs the bundled local tutorial board; this test never rewrites board registrations"
            }
            history = app.appStateStore.history.first()
            preferences = app.compatibilityStore.preferences.first().filterKeys { it.startsWith("compat.ftutter.") }
            capturedState = true
            app.appStateStore.clearAppLockPassword()
            app.compatibilityStore.savePreferences(preferences.keys.associateWith<String, String?> { null } +
                mapOf("compat.ftutter.deleteKey" to "auditm66"))
        }
        switchProfile(ExperienceProfile.FTUTTER)
        previousAttachment?.let(IncomingSharedAttachment::consume)
    }
    private fun switchProfile(next: ExperienceProfile) {
        val store = app.experienceProfileStore
        if (store.readActiveProfile() == next) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(store.readActiveProfile(), next)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }
    @After fun cleanup() {
        if (!::app.isInitialized) return
        try {
            instrumentation.runOnMainSync {
                // The Compose rule's ActivityScenario also matches lifecycle by launch Intent.
                // Restore only during teardown, after the share/lock/Back assertions are over.
                if (::original.isInitialized && !original.isDestroyed) original.intent = Intent(originalLaunchIntent)
                mains.filterNot { it.isDestroyed }.forEach { it.finish() }
                if (::original.isInitialized && !original.isDestroyed) original.finish()
            }
            awaitPlatform("Every Main created by this test must finish") {
                (!::original.isInitialized || original.isDestroyed) && mains.all { it.isDestroyed }
            }
        } finally {
            try { sender.close() } finally {
                app.unregisterActivityLifecycleCallbacks(lifecycle)
                uri?.let { app.contentResolver.delete(it, null, null) }
                IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
                previousAttachment?.let(IncomingSharedAttachment::offer)
                if (capturedState) runBlocking(Dispatchers.IO) {
                    val current = app.compatibilityStore.preferences.first().keys.filter { it.startsWith("compat.ftutter.") }
                    app.compatibilityStore.savePreferences(current.associateWith<String, String?> { null } + preferences)
                    app.appStateStore.setHistory(history)
                    createPlatformStateStorage(app).updateAppLockPasswordHash(passwordHash.orEmpty())
                }
                if (capturedState) switchProfile(profile)
            }
        }
    }
    private fun awaitTag(tag: String) = rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun openDraft() {
        awaitTag("ftutter-board-row")
        rule.onNode(hasTestTag("ftutter-board-row") and (hasText(tutorial.name) or hasAnyDescendant(hasText(tutorial.name))))
            .performScrollTo().performClick()
        awaitTag("ftutter-catalog-item"); rule.onAllNodesWithTag("ftutter-catalog-item")[0].performClick()
        awaitTag("ftutter-post"); rule.onNodeWithTag("ftutter-thread-tool-edit").performClick()
        awaitTag("ftutter-post-comment"); rule.onNodeWithTag("ftutter-post-comment").performTextInput(marker)
        rule.waitUntil(10_000) { runBlocking { app.compatibilityStore.loadPreference("compat.ftutter.drafts") }?.contains(marker) == true }
    }
    private fun awaitPlatform(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) { if (SystemClock.uptimeMillis() > deadline) throw AssertionError(message); SystemClock.sleep(25) }
    }
    private fun share() {
        val content = Uri.parse("content://com.valoser.futacha.test.attachments/audit-m66-draft-${System.nanoTime()}.jpg").also { uri = it }
        val bytes = java.io.ByteArrayOutputStream().also { stream ->
            android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GREEN); compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream); recycle()
            }
        }.toByteArray()
        app.contentResolver.openOutputStream(content, "w")!!.use { it.write(bytes) }
        stopped.set(false)
        val foreignTask = sender.start(content)
        assertNotEquals("The sender must be a distinct task", original.taskId, foreignTask)
        awaitPlatform("Original must reach ON_STOP before the foreign sender dispatches the share") {
            if (!stopped.get()) return@awaitPlatform false
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("M66 send once")
                ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        awaitPlatform("Shared bytes must arrive through MainActivity's real content reader") { IncomingSharedAttachment.pending.value != null && resumed.get() != null }
        assertSame(original, resumed.get())
    }
    private fun requestDiscardConfirmation() {
        // Cancelling the first confirmation can restore editor focus and reopen the IME.
        // Back must reach the dialog rather than merely hiding that keyboard.
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("添付ファイルを破棄して閉じますか？").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertIsDisplayed()
    }
    private fun unlock() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("解除").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("パスワード").performTextReplacement(password)
        rule.onNodeWithText("解除").performClick()
        awaitTag("ftutter-post-comment")
    }
    @Test fun sharingPreservesTheOpenDraftAndRequiresExplicitAttachmentAndDiscardConfirmation() {
        openDraft(); share()
        rule.onNodeWithTag("ftutter-post-comment").assertTextContains(marker)
        rule.onNodeWithTag("ftutter-post-detach").assertDoesNotExist()
        rule.onNodeWithText("共有された画像を添付").performScrollTo().performClick()
        awaitTag("ftutter-post-detach")
        assertNull(IncomingSharedAttachment.pending.value)
        requestDiscardConfirmation()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("ftutter-post-comment").assertTextContains(marker)
        rule.onNodeWithTag("ftutter-post-detach").assertExists()
        requestDiscardConfirmation()
        rule.onNodeWithText("破棄して閉じる").performClick()
        rule.onNodeWithTag("ftutter-post-dialog").assertDoesNotExist()
        Espresso.pressBack(); awaitTag("ftutter-catalog")
        Espresso.pressBack(); awaitTag("ftutter-board-row")
        // Root Back intentionally leaves the target app; Espresso must not
        // require one of its Activities to remain resumed after this operation.
        Espresso.pressBackUnconditionally()
        awaitPlatform("Root Back must return to the external sender task") {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("M66 send once")?.isNotEmpty() == true
        }
        val retainedRoot = !original.isFinishing && !original.isDestroyed
        app.startActivity(requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName)))
        awaitPlatform("Launcher must return to Main after the root Back action") { resumed.get() != null }
        if (retainedRoot) assertSame(original, resumed.get())
        awaitTag("ftutter-board-row")
    }
    @Test fun backgroundShareRequiresUnlockAgainAndThenReturnsToTheSameUnsentDraft() {
        openDraft()
        runBlocking { app.appStateStore.setAppLockPassword(password) }
        // Setting a password in an already unlocked foreground session keeps it open.
        // The real ON_STOP in share(), rather than password creation, must require unlock.
        rule.waitForIdle()
        rule.onNodeWithTag("ftutter-post-comment").assertTextContains(marker)
        rule.onNodeWithText("解除").assertDoesNotExist()
        share()
        rule.onNodeWithText("解除").assertIsDisplayed()
        unlock()
        rule.onNodeWithTag("ftutter-post-comment").assertTextContains(marker)
        rule.onNodeWithText("共有された画像を添付").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ftutter-post-detach").assertDoesNotExist()
        assertEquals(0, mains.count { it !== original })
    }
}
