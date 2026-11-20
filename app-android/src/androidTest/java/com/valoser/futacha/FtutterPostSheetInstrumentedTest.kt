@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** T-B3：原作の書き込みのシート（H3・M17・M18・M46・M47・M48・H11）。送信は試験の中で答え、通信はしない。 */
class FtutterPostSheetInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val initial = FtutterDraft(title = "題名", name = "名前", mail = "sage", comment = "本文")
    private val savedDrafts = mutableListOf<FtutterDraft>()
    private var failed: FtutterPostToast? = null
    private var dismisses = 0
    private var answer = CompletableDeferred<Result<String?>>()

    private fun show(legacy: Boolean = false) {
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                if (visible) FtutterPostDialog(
                    FtutterPostTarget.Reply("test", "123"), "https://may.2chan.net/b/", "検証板",
                    initial, null, "TEST1234", false, BoardPostingCapabilities(1_000_000, setOf("png")),
                    AttachmentPickerPreference.ALWAYS_ASK, null, { "ふたちゃ ver1.0/Android/検証機/15" }, null,
                    onDeleteKeyChange = {},
                    onSaveDraft = { draft, _ -> savedDrafts.add(draft) },
                    onSend = { answer.await() },
                    onSent = { visible = false },
                    onDismiss = { dismisses++; visible = false },
                    legacyDialog = legacy,
                    onFailed = { toast, _ -> failed = toast; visible = false }
                )
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post-dialog").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(800)
        val dir = java.io.File(rule.activity.getExternalFilesDir(null), "ftutter-shots").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theSheetIsFullWidthAtTheBottomWithTheFieldsSideBySideAndTheKeyboardOnTheComment() {
        show()
        val width = rule.activity.resources.displayMetrics.widthPixels.toFloat()
        val sheet = rule.onNodeWithTag("ftutter-post-dialog").fetchSemanticsNode().boundsInWindow
        assertEquals(width, sheet.width, 2f)
        // 開くと本文に入力が向く（原作はすぐキーボード）。
        rule.waitUntil(5_000) {
            rule.onNodeWithTag("ftutter-post-comment").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        // 下の帯：︙・あぷ小は無し（アップロード不可の時）・「書き込む」。見出しと「閉じる」は無い。
        rule.onNodeWithTag("ftutter-post-menu").assertExists()
        rule.onNodeWithTag("ftutter-post-send").assertExists()
        rule.onAllNodesWithText("閉じる").assertCountEquals(0)
        rule.onAllNodesWithText("返信").assertCountEquals(0)
        // The keyboard lifts the sheet after the first measure: wait until it stops moving, then measure both again.
        var last = Float.NaN
        rule.waitUntil(5_000) {
            val top = rule.onNodeWithTag("ftutter-post-send").fetchSemanticsNode().boundsInWindow.top
            (top == last).also { last = top }
        }
        // Both are read in one go after the sheet settles; the keyboard can still move it between two reads.
        fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        rule.waitUntil(5_000) { kotlin.math.abs(bounds("ftutter-post-title").top - bounds("ftutter-post-name").top) <= 2f }
        val titleNow = bounds("ftutter-post-title")
        val name = bounds("ftutter-post-name")
        assertEquals("題名とおなまえは横並び", titleNow.top, name.top, 2f)
        assertTrue(name.left > titleNow.right - 1f)
        val send = rule.onNodeWithTag("ftutter-post-send").fetchSemanticsNode().boundsInWindow
        // With the keyboard up the fields scroll (the title may sit behind the band), so compare with the focused comment.
        val comment = rule.onNodeWithTag("ftutter-post-comment").fetchSemanticsNode().boundsInWindow
        assertTrue("書き込むは右下 send=$send comment=$comment width=$width", send.right > width * 0.8f && send.top >= comment.bottom - 1f)
        shot("t-b3-sheet")
    }

    @Test fun theMenuShowsTheKeyAndTheDeviceInfoAndAddsTheTextAsItIs() {
        show()
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithText("TEST1234").assertExists()
        rule.onNodeWithText("アップロードキャッシュのクリア").assertExists()
        rule.onNodeWithText("ふたちゃ ver1.0/Android/検証機/15").assertExists()
        shot("t-b3-menu")
        rule.onNodeWithTag("ftutter-post-device-info").performClick()
        rule.onNodeWithTag("ftutter-post-comment").assertTextContains("本文ふたちゃ ver1.0/Android/検証機/15")
    }

    @Test fun theDeleteKeyIsShownWithTheCountAndCanBeGenerated() {
        show()
        rule.onNodeWithTag("ftutter-post-menu").performClick()
        rule.onNodeWithTag("ftutter-post-delete-key").performClick()
        rule.onNodeWithTag("ftutter-delete-key").assertTextEquals("TEST1234")
        rule.onNodeWithTag("ftutter-delete-key-count").assertContentDescriptionEquals("残り 0 文字（半角相当）")
        rule.onNodeWithText("8/8").assertExists()
        shot("t-b3-delete-key")
        rule.onNodeWithTag("ftutter-delete-key-generate").performClick()
        val generated = rule.onNodeWithTag("ftutter-delete-key").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(8, generated.length)
        assertNotEquals("TEST1234", generated)
    }

    @Test fun sendingDimsWithABandAndAFailureClosesWithTheServersBodyKeepingTheDraft() {
        show()
        rule.onNodeWithTag("ftutter-post-send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("ftutter-post-sending").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("ftutter-post-title").assertCountEquals(0)
        shot("t-b3-sending")
        val body = "<html><body>あなたのipアドレスからは投稿できません</body></html>"
        answer.complete(Result.failure(IllegalStateException("返信の送信に失敗しました", NetworkException("返信に失敗しました", serverBody = body))))
        rule.waitUntil(5_000) { failed != null }
        rule.onAllNodesWithTag("ftutter-post-dialog").assertCountEquals(0)
        rule.runOnIdle {
            assertEquals(FtutterPostToast(body, FTUTTER_IP_REFUSAL_FOLLOW_UP), failed)
            assertEquals(initial, savedDrafts.last())
            assertEquals(0, dismisses)
        }
    }

    @Test fun theLegacySwitchKeepsTheCentreDialogAndItsReasons() {
        show(legacy = true)
        rule.onNodeWithText("閉じる").assertExists()
        rule.onNodeWithText("返信").assertExists()
        rule.onNodeWithTag("ftutter-post-send").performClick()
        answer.complete(Result.failure(IllegalStateException("返信の送信に失敗しました: 理由")))
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("ftutter-post-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ftutter-post-dialog").assertExists()
        rule.runOnIdle { assertNull(failed) }
        shot("t-b3-legacy")
    }
}
