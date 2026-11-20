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

    private fun show(boardUrl: String, reply: Boolean, legacy: Boolean, filer: String? = null) {
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterPostDialog(
                    if (reply) FtutterPostTarget.Reply("test", "123") else FtutterPostTarget.Create("test"),
                    boardUrl, "検証板",
                    FtutterDraft(), null, "TEST1234", false, BoardPostingCapabilities(1_000_000, setOf("png")),
                    AttachmentPickerPreference.ALWAYS_ASK, filer, { "ふたちゃ ver1.0/Android/検証機/15" },
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

    /** aimg seq41: the attach button offers the multiple pick right away, not three taps deep. */
    @Test fun theAttachButtonOffersOneOrSeveral() {
        show("https://may.2chan.net/b/", reply = true, legacy = false)
        rule.onNodeWithTag("ftutter-post-attach").performClick()
        rule.onNodeWithTag("ftutter-post-attach-one").assertExists()
        rule.onNodeWithTag("ftutter-post-attach-multi").assertExists()
        // may 1448778226: pictures come from the gallery and videos have their own item.
        rule.onNodeWithTag("ftutter-post-attach-video").assertExists()
    }

    /** Audit 2026-11-20 中2・低6: the sheet's pickers open the system chooser for the allowed types only, never the preferred filer. */
    @Test fun thePickersAskForTheAllowedTypesWithoutThePreferredFiler() {
        show("https://may.2chan.net/b/", reply = true, legacy = false, filer = "com.example.filer")
        fun picked(item: String): android.content.Intent {
            var seen: android.content.Intent? = null
            val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                    if (intent.action != android.content.Intent.ACTION_GET_CONTENT) return null
                    seen = intent
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
            }
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            instrumentation.addMonitor(monitor)
            try {
                rule.onNodeWithTag("ftutter-post-attach").performClick()
                rule.onNodeWithTag(item).performClick()
                rule.waitUntil(5_000) { seen != null }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            return checkNotNull(seen)
        }
        val image = picked("ftutter-post-attach-one")
        org.junit.Assert.assertEquals("image/*", image.type)
        org.junit.Assert.assertNull(image.`package`)
        org.junit.Assert.assertEquals(listOf("image/jpeg", "image/png", "image/gif", "image/webp"),
            image.getStringArrayExtra(android.content.Intent.EXTRA_MIME_TYPES)?.toList())
        val video = picked("ftutter-post-attach-video")
        org.junit.Assert.assertEquals("video/*", video.type)
        org.junit.Assert.assertNull(video.`package`)
        org.junit.Assert.assertEquals(listOf("video/mp4", "video/webm"),
            video.getStringArrayExtra(android.content.Intent.EXTRA_MIME_TYPES)?.toList())
    }

    /** Audit 2026-11-20 中1: the legacy dialog has no video item. */
    @Test fun theLegacyDialogHasNoVideoItem() {
        show("https://may.2chan.net/b/", reply = true, legacy = true)
        rule.onNodeWithTag("ftutter-post-attach").performScrollTo().performClick()
        rule.onAllNodesWithTag("ftutter-post-attach-video").assertCountEquals(0)
    }
}
