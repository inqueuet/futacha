@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import org.junit.Rule
import org.junit.Test

/**
 * ふたったー風：imgの返信でも「あぷ小」を出す（あいもげスレ01a1e1d7「imgだとあぷ小機能が使えない」）。
 * あぷ小は本文にファイル名を書くだけなので、添付欄の無いimgの返信でも使える。ファイルの添付は今までどおり出さない。
 * 送信・アップロードはしない。
 */
class FtutterImgUpsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(boardUrl: String, reply: Boolean, legacy: Boolean) {
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterPostDialog(
                    if (reply) FtutterPostTarget.Reply("test", "123") else FtutterPostTarget.Create("test"),
                    boardUrl, "検証板",
                    FtutterDraft(), null, "TEST1234", false, BoardPostingCapabilities(1_000_000, setOf("png")),
                    AttachmentPickerPreference.ALWAYS_ASK, null, { "ふたちゃ ver1.0/Android/検証機/15" },
                    uploadUps = { _, _ -> Result.success("fu0000000.png") },
                    onDeleteKeyChange = {},
                    onSaveDraft = { _, _ -> },
                    onSend = { Result.success(null) },
                    onSent = {},
                    onDismiss = {},
                    legacyDialog = legacy
                )
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("ftutter-post-dialog").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    @Test fun theImgReplySheetOffersUpsWithoutTheFileAttachment() {
        show("https://img.2chan.net/b/", reply = true, legacy = false)
        rule.onNodeWithTag("ftutter-post-ups").assertExists()
        rule.onAllNodesWithTag("ftutter-post-attach").assertCountEquals(0)
    }

    @Test fun theImgReplyLegacyDialogOffersUpsWithoutTheFileAttachment() {
        show("https://img.2chan.net/b/", reply = true, legacy = true)
        rule.onNodeWithTag("ftutter-post-ups").performScrollTo().assertExists()
        rule.onAllNodesWithTag("ftutter-post-attach").assertCountEquals(0)
    }

    @Test fun anotherBoardsReplyStillOffersBoth() {
        show("https://may.2chan.net/b/", reply = true, legacy = true)
        rule.onNodeWithTag("ftutter-post-ups").performScrollTo().assertExists()
        rule.onNodeWithTag("ftutter-post-attach").assertExists()
    }
}
