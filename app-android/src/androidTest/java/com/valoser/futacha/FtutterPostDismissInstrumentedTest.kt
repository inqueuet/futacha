@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
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

/** Product dialog with real back/outside dismissal; send callbacks never use the network. */
class FtutterPostDismissInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var dismisses = 0
    private var submitted: FtutterPostInput? = null
    private val closeDrafts = mutableListOf<FtutterDraft>()
    private val initial = FtutterDraft(title = "題名", name = "名前", mail = "sage", comment = "閉じても残す本文")
    private val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1sAAAAASUVORK5CYII=", 0)

    @After fun cleanup() {
        IncomingSharedAttachment.pending.value?.let(IncomingSharedAttachment::consume)
    }

    private fun show(image: ImageData? = null, maxBytes: Long = 1_000_000) {
        image?.let(IncomingSharedAttachment::offer)
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                if (visible) FtutterPostDialog(
                    FtutterPostTarget.Reply("test", "123"), "https://may.2chan.net/b/", "検証板",
                    initial, null, "TEST1234", false, BoardPostingCapabilities(maxBytes, setOf("png")),
                    AttachmentPickerPreference.ALWAYS_ASK, null, { "test" }, null,
                    onDeleteKeyChange = {},
                    onSaveDraft = { draft, announce -> if (announce) closeDrafts.add(draft) },
                    onSend = { submitted = it; Result.success("456") },
                    onSent = { visible = false },
                    onDismiss = { dismisses++; visible = false })
            }
        }
        if (image != null) {
            rule.onNodeWithText("共有された画像を添付").performScrollTo().performClick()
            if (image.bytes.size <= maxBytes) {
                rule.waitUntil(10_000) { rule.onAllNodesWithText(image.fileName).fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test fun backWithImageAsksBeforeDiscardAndCancelKeepsTheActualAttachment() {
        show(ImageData(png, "retained.png"))
        Espresso.pressBack()
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertIsDisplayed()
        assertEquals(0, dismisses)
        assertTrue(closeDrafts.isEmpty())
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("ftutter-post-comment").performScrollTo().assertTextContains(initial.comment)
        rule.onNodeWithText("retained.png").performScrollTo().assertExists()
        rule.onNodeWithTag("ftutter-post-send").performClick()
        rule.waitUntil(5_000) { submitted != null }
        rule.runOnIdle {
            assertEquals(initial, submitted?.draft)
            assertEquals("retained.png", submitted?.image?.fileName)
            assertTrue(checkNotNull(submitted?.image).bytes.isNotEmpty())
            assertEquals(0, dismisses)
        }
    }

    @Test fun outsideWithHandwritingAsksAndBackFromConfirmationKeepsItUntilExplicitDiscard() {
        show(ImageData(png, "drawing_retained.png", isHandwriting = true))
        tapOutsideDialog()
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertIsDisplayed()
        assertEquals(0, dismisses)
        Espresso.pressBack()
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertDoesNotExist()
        rule.onNodeWithText("drawing_retained.png").performScrollTo().assertExists()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertIsDisplayed()
        rule.onNodeWithText("破棄して閉じる").performClick()
        rule.onNodeWithTag("ftutter-post-dialog").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, dismisses)
            assertEquals(listOf(initial), closeDrafts)
            assertNull(submitted)
        }
    }

    @Test fun pendingCompressionAlsoNeedsConsentBeforeClosing() {
        show(ImageData(png, "pending.png"), maxBytes = 32)
        rule.onNodeWithText("容量を超えています。圧縮しますか？").performScrollTo().assertExists()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithText("添付ファイルを破棄して閉じますか？").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("容量を超えています。圧縮しますか？").performScrollTo().assertExists()
        rule.runOnIdle { assertEquals(0, dismisses) }
    }

    @Test fun textOnlyBackStillSavesTheDraftAndClosesDirectly() {
        show()
        Espresso.pressBack()
        rule.onNodeWithTag("ftutter-post-dialog").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, dismisses)
            assertEquals(listOf(initial), closeDrafts)
            assertNull(submitted)
        }
    }

    private fun tapOutsideDialog() {
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = android.os.SystemClock.uptimeMillis()
        val outsideY = rule.activity.resources.displayMetrics.heightPixels / 2f
        val down = MotionEvent.obtain(started, started, MotionEvent.ACTION_DOWN, 1f, outsideY, 0)
        val up = MotionEvent.obtain(started, started + 60, MotionEvent.ACTION_UP, 1f, outsideY, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue(automation.injectInputEvent(down, true))
            assertTrue(automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        rule.waitForIdle()
    }
}
