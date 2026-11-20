@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import coil3.ImageLoader
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.aimoge.AimogePollDto
import com.valoser.futacha.shared.network.aimoge.AimogePollLinks
import com.valoser.futacha.shared.network.aimoge.AimogePollOptionDto
import com.valoser.futacha.shared.network.aimoge.AimogePostDto
import com.valoser.futacha.shared.network.aimoge.toPost
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FtutterAimogePollInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingAnOptionDeliversItsIdToTheInAppVoteHandler() {
        val threadId = "01a1ec77-e5c3-77af-aa69-5c17dac1aad4"
        val postId = "01a1ec77-ee54-71eb-8e6a-2424a485556b"
        val board = BoardSummary("aimoge-aimg", "あいもげ", "", "https://nijiurachan.net/b/aimg/", "")
        val urlLabel = "https://example.invalid/option"
        val post = AimogePostDto(id = postId, body = "アンケートの本文", poll = AimogePollDto(
            options = listOf(AimogePollOptionDto("opt-one", "選択肢A"), AimogePollOptionDto("opt-two", urlLabel))
        )).toPost(0)
        val page = ThreadPage(threadId, null, null, null, listOf(post))
        val tapped = AtomicReference<Pair<String, String>>()
        val handler: suspend (String, String) -> String = { id, option ->
            tapped.set(id to option)
            "テスト回答"
        }
        val loader = ImageLoader(rule.activity)
        AimogePollLinks.register(handler)
        try {
            rule.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                    FtutterTheme(null, FtutterBrightness.LIGHT) {
                        FtutterThreadScreen(
                            board, FtutterThreadRef(board.id, threadId, "テスト"), FakeBoardRepository(), null,
                            emptyMap(), parseFtutterDesign(emptyMap()), null, emptySet(), 0,
                            { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {},
                            initialPage = page, refreshOnOpen = false
                        )
                    }
                }
            }
            val matcher = hasTestTag("ftutter-post-body") and hasText(urlLabel, substring = true)
            rule.waitUntil(10_000) { rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val body = rule.onNode(matcher, useUnmergedTree = true).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val offset = layout.getBoundingBox(layout.layoutInput.text.text.indexOf(urlLabel)).center
            body.performTouchInput { click(offset) }
            rule.waitUntil(5_000) { tapped.get() != null }
            assertEquals(postId to "opt-two", tapped.get())
        } finally {
            AimogePollLinks.unregister(handler)
            com.valoser.futacha.shared.network.aimoge.AimogeNotices.clear()
            rule.runOnUiThread { rule.activity.setContent {} }
            rule.waitForIdle()
            loader.shutdown()
        }
    }
}
