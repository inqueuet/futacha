@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.board.IncomingSharedAttachment
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FtutterPostGuidanceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var originalAutomationFlags: Int? = null

    @After fun cleanup() {
        IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
        originalAutomationFlags?.let { saved ->
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply { flags = saved }
        }
    }

    @Test fun missingKeyCanBeSetWithoutLosingTheDraftWithConfirmation() = verifyMissingKey(true)
    @Test fun missingKeyCanBeSetWithoutLosingTheDraftWithoutConfirmation() = verifyMissingKey(false)

    private fun verifyMissingKey(confirm: Boolean) {
        var sends = 0
        var savedKey: String? = null
        var submitted: FtutterPostInput? = null
        val initial = FtutterDraft(title = "題名", name = "名前", mail = "sage", comment = "残す本文")
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1sAAAAASUVORK5CYII=", 0)
        IncomingSharedAttachment.offer(ImageData(png, "retained.png"))
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"), "https://may.2chan.net/b/", "検証板",
                initial, null, "", confirm, BoardPostingCapabilities(1_000_000, setOf("png")),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null,
                onDeleteKeyChange = { savedKey = it }, onSaveDraft = { _, _ -> },
                onSend = { sends++; submitted = it; Result.success("456") }, onSent = {}, onDismiss = {})
        } }
        rule.onNodeWithTag("ftutter-post-key-required").assertIsDisplayed()
        rule.onNodeWithText("共有された画像を添付").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("retained.png").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().performClick()
        showKeyboard()
        rule.onNodeWithTag("ftutter-post-key-required").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().assertIsDisplayed()
        val commentBounds = rule.onNodeWithTag("ftutter-post-comment").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue("The editor must remain visible with the keyboard open: $commentBounds", commentBounds.height >= 48 * density)
        capture("missing-key-keyboard-$confirm")
        tapAboveKeyboard("ftutter-post-send")
        rule.onNodeWithText("この内容で書き込みますか？").assertDoesNotExist()
        assertEquals(0, sends)
        tapAboveKeyboard("ftutter-post-set-delete-key")
        rule.onNodeWithTag("ftutter-delete-key").performClick().performTextInput("TEST1234")
        showKeyboard()
        capture("key-settings-keyboard-$confirm")
        tapAboveKeyboard("key-settings-save", rule.onNodeWithText("OK"))
        rule.onNodeWithTag("ftutter-post-key-required").assertDoesNotExist()
        assertEquals("TEST1234", savedKey)
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().assertTextContains(initial.comment)
        rule.onNodeWithText("retained.png").performScrollTo().assertExists()
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().performClick()
        showKeyboard()
        capture("configured-key-draft-retained-$confirm")
        tapAboveKeyboard("ftutter-post-send")
        if (confirm) {
            rule.onNodeWithText("この内容で書き込みますか？").assertIsDisplayed()
            assertEquals(0, sends)
            rule.onNodeWithTag("ftutter-post-confirm").performClick()
        }
        rule.waitUntil(5_000) { sends == 1 }
        assertEquals(initial, submitted?.draft)
        assertEquals("TEST1234", submitted?.deleteKey)
        assertEquals("retained.png", submitted?.image?.fileName)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val directory = File(rule.activity.getExternalFilesDir(null), "ftutter-guidance-shots").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun showKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (originalAutomationFlags == null) originalAutomationFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isRoot()
                override fun getDescription() = "Show the real software keyboard in the focused dialog"
                override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                    val focused = requireNotNull(view.findFocus())
                    val input = focused.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    input.restartInput(focused)
                    input.showSoftInput(focused, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    controller.loopMainThreadForAtLeast(500)
                }
            })
        rule.waitUntil(5_000) {
            automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
    }

    private fun tapAboveKeyboard(tag: String, node: SemanticsNodeInteraction = rule.onNodeWithTag(tag)) {
        val origin = IntArray(2)
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isRoot()
                override fun getDescription() = "Locate the dialog on the device screen"
                override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                    requireNotNull(view.findFocus()).getLocationOnScreen(origin)
                }
            })
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val imeBounds = android.graphics.Rect()
        InstrumentationRegistry.getInstrumentation().uiAutomation.windows.first {
            it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }.getBoundsInScreen(imeBounds)
        assertTrue("$tag is hidden by the keyboard: ${bounds.bottom + origin[1]} > ${imeBounds.top}",
            bounds.bottom + origin[1] <= imeBounds.top)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = android.os.SystemClock.uptimeMillis()
        val x = bounds.center.x + origin[0]
        val y = bounds.center.y + origin[1]
        val down = android.view.MotionEvent.obtain(started, started, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(automation.injectInputEvent(down, true)) } finally { down.recycle() }
        android.os.SystemClock.sleep(60)
        val up = android.view.MotionEvent.obtain(started, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, y, 0)
        up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(automation.injectInputEvent(up, true)) } finally { up.recycle() }
        rule.waitForIdle()
    }

    private fun sourceAttachmentDraft(boardUrl: String) {
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"), boardUrl, "検証板",
                FtutterDraft(comment = "取り込んだ本文", sourceAttachmentPath = "/imported/old.png"), null, "KEY", false,
                BoardPostingCapabilities(1_000_000, setOf("png")),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null,
                onDeleteKeyChange = {}, onSaveDraft = { _, _ -> },
                onSend = { Result.success(null) }, onSent = {}, onDismiss = {})
        } }
    }

    /** L15: the imported attachment notice appears once. */
    @Test fun theImportedAttachmentNoticeAppearsOnce() {
        sourceAttachmentDraft("https://may.2chan.net/b/")
        rule.onAllNodesWithTag("ftutter-draft-attachment-reselect").assertCountEquals(1)
        rule.onNodeWithText("添付ボタンから選び直してください", substring = true).assertExists()
    }

    /** L15: an img reply cannot attach, so the notice does not point to the attach button and can be dismissed. */
    @Test fun anImgReplyExplainsTheImportedAttachmentIsUnusedAndCanDismissIt() {
        sourceAttachmentDraft("https://img.2chan.net/b/")
        rule.onAllNodesWithTag("ftutter-draft-attachment-reselect").assertCountEquals(1)
        rule.onAllNodesWithText("添付ボタン", substring = true).assertCountEquals(0)
        rule.onNodeWithText("取り込み元の添付は使われません", substring = true).assertExists()
        rule.onNodeWithTag("ftutter-draft-attachment-dismiss").performScrollTo().performClick()
        rule.onAllNodesWithTag("ftutter-draft-attachment-reselect").assertCountEquals(0)
    }

    @Test fun cancellingKeySettingsKeepsTheUnsentDraftAndDoesNotStoreAKey() {
        var sends = 0
        var keyChanges = 0
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"), "https://may.2chan.net/b/", "検証板",
                FtutterDraft(comment = "取消しても残す"), null, "", true, BoardPostingCapabilities(1_000_000, setOf("png")),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null,
                onDeleteKeyChange = { keyChanges++ }, onSaveDraft = { _, _ -> },
                onSend = { sends++; Result.success(null) }, onSent = {}, onDismiss = {})
        } }
        rule.onNodeWithTag("ftutter-post-set-delete-key").performClick()
        rule.onNodeWithTag("ftutter-delete-key").performTextInput("DISCARD")
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("ftutter-post-key-required").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().assertTextContains("取消しても残す")
        assertEquals(0, sends)
        assertEquals(0, keyChanges)
    }

    @Test fun missingBodyIsCheckedBeforeTheConfirmationAndExistingKeyIsNotReplaced() {
        var sends = 0
        var keyChanges = 0
        rule.setContent { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterPostDialog(FtutterPostTarget.Reply("test", "123"), "https://may.2chan.net/b/", "検証板",
                FtutterDraft(), null, "EXISTING", true, BoardPostingCapabilities(1_000_000, setOf("png")),
                AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null,
                onDeleteKeyChange = { keyChanges++ }, onSaveDraft = { _, _ -> },
                onSend = { sends++; Result.success(null) }, onSent = {}, onDismiss = {})
        } }
        rule.onNodeWithTag("ftutter-post-key-required").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-post-send").performClick()
        rule.onNodeWithText("この内容で書き込みますか？").assertDoesNotExist()
        rule.onNodeWithTag("ftutter-post-error").performScrollTo().assertExists()
        assertEquals(0, sends)
        assertEquals(0, keyChanges)
    }
}
