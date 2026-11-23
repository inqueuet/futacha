@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.board.ThreadFormDialog
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * may 1449257110: with the keyboard up, the reply form's comment went off the top of the screen (lines 1 and 2 hidden)
 * because the dialog window was panned and the form also padded the keyboard. The form's top bar and the comment's
 * first line stay on the screen.
 */
class ThreadFormImeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader

    @Before fun setUp() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun tearDown() { loader.shutdown() }

    @Test
    fun theCommentsFirstLineStaysOnTheScreenWithTheKeyboardUp() {
        val comment = (1..6).joinToString("\n") { "行$it のテキスト" }
        rule.setContent {
            MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ThreadFormDialog(title = "返信", subtitle = null, attachmentPickerPreference = AttachmentPickerPreference.MEDIA,
                    preferredFileManagerPackage = null, emailPresets = listOf("sage"), comment = comment, onCommentChange = {},
                    name = "", onNameChange = {}, email = "", onEmailChange = {}, subject = "", onSubjectChange = {},
                    password = "key", onPasswordChange = {}, selectedImage = null, onImageSelected = {},
                    onDismiss = {}, onSubmit = {}, onClear = {}, isSubmitEnabled = true,
                    sendDescription = "送信", boardUrl = "https://may.2chan.net/b/", isReply = true)
            } }
        }
        rule.onNode(hasSetTextAction() and hasText("行1", substring = true)).performClick()
        rule.waitUntil(10_000) { imeVisible() }
        Thread.sleep(800) // the keyboard's animation and any pan of the window
        rule.waitForIdle()
        val screenHeight = rule.activity.resources.displayMetrics.heightPixels
        val bar = rule.onNodeWithText("返信").fetchSemanticsNode()
        val field = rule.onNode(hasSetTextAction() and hasText("行1", substring = true)).fetchSemanticsNode()
        val barTop = bar.positionOnScreen.y
        val fieldTop = field.positionOnScreen.y
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { shot ->
            java.io.File(rule.activity.getExternalFilesDir(null), "thread-form-ime.png").outputStream().use {
                shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertTrue("the form's title is on the screen (top $barTop)", barTop >= 0f)
        assertTrue("the comment's first line is on the screen (top $fieldTop of $screenHeight)", fieldTop >= 0f && fieldTop < screenHeight)
    }

    private fun imeVisible(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // The dialog has its own window; any window of the app that sees the keyboard is enough.
            visible = rule.activity.window.decorView.rootWindowInsets?.let {
                WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(WindowInsetsCompat.Type.ime())
            } ?: false
        }
        return visible || isImeShownBySystem()
    }

    private fun isImeShownBySystem(): Boolean {
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys input_method").let { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
            }
        return Regex("mInputShown=true|isInputViewShown=true").containsMatchIn(out)
    }
}
