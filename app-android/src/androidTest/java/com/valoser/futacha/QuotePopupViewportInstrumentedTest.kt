@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.*
import org.junit.*
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.compat.CompatReplyPreviewPopup

class QuotePopupViewportInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    private fun post(id: String, body: String, refs: List<QuoteReference> = emptyList()) = Post(
        id, author = null, subject = null, timestamp = "", messageHtml = body,
        imageUrl = null, thumbnailUrl = null, quoteReferences = refs)
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown() }

    @Test fun replacingTheOpenQuoteWithItsNestedSourcesStartsAtTheFirstSource() {
        val targets = (0..7).map { post("${1000 + it}", "nested-source-$it<br>extra line") }
        val reference = QuoteReference(">nested sources", targets.map { it.id })
        val parent = (0..17).map { i -> post("${5000+i}",
            if (i == 15) "&gt;nested sources<br>parent-$i" else "parent-$i<br>extra line",
            if (i == 15) listOf(reference) else emptyList()) }
        var current by mutableStateOf(QuotePreviewState("parent", parent, emptyMap()))
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                QuotePreviewDialog(current, onDismiss = {}, onUrlClick = {}, onQuoteClick = {
                    current = QuotePreviewState("nested", targets, emptyMap())
                })
            }
        } }
        rule.onNode(hasScrollAction()).performScrollToIndex(15)
        rule.onNodeWithText("parent-15", substring = true).assertIsDisplayed()
        rule.onNodeWithText(">nested sources", substring = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(24f, 8f))
        }
        rule.waitForIdle()
        rule.onNodeWithText("nested", substring = false).assertIsDisplayed()
        rule.onNodeWithText("nested-source-0", substring = true).assertIsDisplayed()
    }

    @Test fun compatibilityRelatedPopupResetsWhenItsSourcesAreReplaced() {
        val parent = (0..17).map { CompatPostSnapshot(it, "${5000 + it}", timestamp = "",
            messageHtml = if (it == 15) "&gt;No.1000<br>compat-parent-$it" else "compat-parent-$it<br>extra line") }
        val nested = (0..7).map { CompatPostSnapshot(it, "${1000 + it}", timestamp = "",
            messageHtml = "compat-nested-$it<br>extra line") }
        var posts by mutableStateOf(parent)
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                CompatReplyPreviewPopup(posts, emptySet(), 16, 40,
                    hideDefaultNameAndSubject = false, simpleQuoteCount = false,
                    saidaneDisplayMode = "COUNT", saidaneThreshold = 1,
                    onDismiss = {}, onQuoteClick = { _, _ -> posts = nested }, onUrlClick = {},
                    onMediaUrlClick = { _, _ -> }, onLongClick = {}, onHeaderClick = {},
                    onHeaderLongClick = {}, onMediaClick = {}, onMediaLongClick = {})
            }
        } }
        rule.onNode(hasScrollAction()).performScrollToIndex(15)
        rule.onNodeWithText("compat-parent-15", substring = true).assertIsDisplayed()
        rule.onNodeWithText(">No.1000", substring = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(24f, 8f))
        }
        rule.waitForIdle()
        rule.onNodeWithText("compat-nested-0", substring = true).assertIsDisplayed()
    }
}
