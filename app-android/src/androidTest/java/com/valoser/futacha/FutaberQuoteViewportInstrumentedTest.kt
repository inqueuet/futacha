@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.futaber.*
import org.junit.*

/** Real quote-line touch; production overlay; same last-layer composition as FutaberThreadScreen. */
class FutaberQuoteViewportInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun post(id: String, body: String, refs: List<QuoteReference> = emptyList()) = Post(id, author = null, subject = null,
        timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null, quoteReferences = refs)
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }
    @Test fun nestedQuoteStartsAtItsFirstSourceAndBackRestoresTheParentViewport() {
        val targets = (0..29).map { post("${1000 + it}", "同じ引用本文<br>target-body-$it") }
        val old = (0..59).map { i ->
            if (i == 40) post("${5000 + i}", "&gt;同じ引用本文<br>old-body-$i",
                listOf(QuoteReference(">同じ引用本文", targets.map { it.id })))
            else post("${5000 + i}", "old-body-$i<br>extra line")
        }
        val all = targets + old
        val layers = mutableStateListOf(FutaberQuoteLayer(FutaberQuoteKind.Replies, old, FutaberAnchor(150f, 900f, 940f)))
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            layers.lastOrNull()?.let { layer ->
                FutaberQuoteOverlay(layer, all, emptyMap(), futaberReplyIndex(all),
                    onNested = { kind, posts, anchor -> layers += FutaberQuoteLayer(kind, posts, anchor) },
                    onDismiss = { layers.removeAt(layers.lastIndex) })
            }
        } }
        val scroll = hasScrollAction() and hasAnyAncestor(hasTestTag("futaber-quote-card"))
        rule.onNode(scroll).performScrollToIndex(40)
        rule.onNodeWithText("old-body-40", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("futaber-quote-line").performTouchInput { click() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("引用元、30件").assertExists()
        rule.onNodeWithText("target-body-0", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("target-body-29", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("futaber-quote-scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, 2f)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("返信先、60件").assertExists()
        rule.onNodeWithText("old-body-40", substring = true).assertIsDisplayed()
    }
}
