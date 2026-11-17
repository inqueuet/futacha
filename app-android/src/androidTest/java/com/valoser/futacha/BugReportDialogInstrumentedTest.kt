@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")
package com.valoser.futacha

import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.board.BugReportDialog
import org.junit.*
import org.junit.Assert.*

/** In-memory clipboard and intercepted ACTION_SENDTO; no email client is opened. */
class BugReportDialogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private class MemoryClipboard : ClipboardManager {
        var copiedText: AnnotatedString? = null
        override fun setText(annotatedString: AnnotatedString) { copiedText = annotatedString }
        override fun getText() = copiedText
        override fun hasText() = !copiedText?.text.isNullOrEmpty()
    }
    private val clipboard = MemoryClipboard()
    private val mail = mutableListOf<Intent>()
    private var dismissed = 0
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }
    private fun open() {
        val context = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) {
                assertEquals(Intent.ACTION_SENDTO, intent.action)
                mail += intent
            }
        }
        rule.setContent {
            var shown by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalContext provides context, LocalClipboardManager provides clipboard) {
                MaterialTheme { if (shown) BugReportDialog("13.1", "test", onDismiss = { dismissed++; shown = false }) }
            }
        }
    }
    private fun fill(text: String) {
        rule.onNodeWithTag("bug-report-steps").performTextReplacement(text)
        Espresso.closeSoftKeyboard(); rule.waitForIdle()
    }
    @Test fun longMultilineInputKeepsCopyReachableAndCopiesEveryCharacter() {
        open()
        val steps = (1..350).joinToString("\n") { "再現手順$it：画面から戻ると画像が消えます" }
        assertTrue(steps.length > 4000)
        fill(steps)
        rule.onNodeWithTag("bug-report-copy").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(checkNotNull(clipboard.copiedText).text.endsWith(steps)) }
        rule.onNodeWithTag("bug-report-steps").performTextReplacement("編集を続けられます")
        rule.onNodeWithTag("bug-report-steps").assertTextContains("編集を続けられます")
        assertTrue(mail.isEmpty())
    }
    @Test fun oversizedMailCopiesTheFullReportAndOffersAnEmptyMailInsteadOfTruncatingIt() {
        open()
        val steps = "日本語の長い報告です。".repeat(140)
        fill(steps)
        rule.onNodeWithText("メールで報告").performClick()
        rule.onNodeWithText("報告文をコピーしました").assertIsDisplayed()
        rule.runOnIdle { assertTrue(checkNotNull(clipboard.copiedText).text.endsWith(steps)); assertTrue(mail.isEmpty()) }
        rule.onNodeWithText("メールを開く").performClick()
        rule.runOnIdle {
            assertEquals(1, mail.size)
            val uri = checkNotNull(mail.single().data)
            assertEquals("mailto", uri.scheme)
            assertTrue(uri.toString().startsWith("mailto:admin@valoser.com?"))
            assertFalse(uri.toString().contains("&body="))
            assertTrue(checkNotNull(clipboard.copiedText).text.endsWith(steps))
        }
    }
    @Test fun backAndOutsideDismissalsRequireDiscardConfirmationAndKeepCancelledInput() {
        open(); fill("失いたくない再現手順")
        Espresso.pressBack()
        rule.onNodeWithText("入力を破棄しますか？").assertIsDisplayed()
        rule.onNodeWithText("入力を続ける").performClick()
        rule.onNodeWithTag("bug-report-steps").assertTextContains("失いたくない再現手順")
        assertEquals(0, dismissed)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val time = SystemClock.uptimeMillis()
        val y = rule.activity.window.decorView.height / 2f
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 16 else 0, action, 2f, y, 0)
            try { automation.injectInputEvent(event, true) } finally { event.recycle() }
        }
        rule.waitForIdle()
        rule.onNodeWithText("入力を破棄しますか？").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("bug-report-steps").assertTextContains("失いたくない再現手順")
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("破棄して閉じる").performClick()
        assertEquals(1, dismissed)
    }
    @Test fun shortReportsUseTheReviewedFullMailBodyAndEmptyDialogsCloseImmediately() {
        open(); fill("短い再現手順")
        rule.onNodeWithText("メールで報告").performClick()
        rule.runOnIdle {
            assertEquals(1, mail.size)
            val encoded = checkNotNull(mail.single().dataString).substringAfter("&body=")
            assertTrue(android.net.Uri.decode(encoded).endsWith("短い再現手順"))
            assertTrue(mail.single().dataString!!.length <= 8192)
        }
        rule.onNodeWithTag("bug-report-steps").performTextReplacement("")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("閉じる").performClick()
        assertEquals(1, dismissed)
    }
}
