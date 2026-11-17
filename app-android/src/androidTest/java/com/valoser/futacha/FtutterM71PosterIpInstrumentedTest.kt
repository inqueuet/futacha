@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Real Thread UI with in-memory pages only: no network, external apps, posting or audio. */
class FtutterM71PosterIpInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("m71", "M71 board", "test", "https://may.2chan.net/b/", "")
    private val ipv4 = "192.0.*(isp.example)"
    private val ipv6 = "2001:DB8:abcd::*"
    private lateinit var loader: ImageLoader
    private var saved = emptyList<FtutterNgRule>()

    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader.shutdown()
    }
    private fun post(id: String, identity: String, body: String) = Post(
        id, author = "M71-name-$id", subject = null, timestamp = "26/10/22(木)10:00:00 $identity",
        posterId = identity.takeIf { it.startsWith("ID:") }, messageHtml = body, imageUrl = null, thumbnailUrl = null
    )
    private fun show(ip: String, ng: List<FtutterNgRule> = emptyList()) {
        val posts = listOf(post("100", "IP:$ip", "M71-target"), post("101", "IP:198.51.100.99", "M71-other"),
            post("102", "ID:source", "&gt;IP:$ip"))
        val page = ThreadPage("100", null, null, null, posts)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterThreadScreen(board, FtutterThreadRef(board.id, "100", "M71 thread"), FakeBoardRepository(), null,
                        emptyMap(), parseFtutterDesign(emptyMap()), null, emptySet(), 0,
                        { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, {},
                        initialPage = page, refreshOnOpen = false, ngRules = ng, boards = listOf(board),
                        onSaveNgRules = { transform, _ -> saved = transform(saved) })
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("M71-other").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tapQuote(ip: String) {
        rule.onNode(hasTestTag("ftutter-post-body") and hasText(">IP:$ip"), useUnmergedTree = true)
            .performScrollTo().performTouchInput { click() }
    }
    private fun popupText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("ftutter-popup"))
    private fun verifyQuote(ip: String) {
        show(ip)
        tapQuote(ip)
        rule.waitUntil(10_000) { rule.onAllNodes(popupText("M71-target")).fetchSemanticsNodes().size == 1 }
        rule.onNode(popupText("M71-target")).assertIsDisplayed()
        rule.onAllNodes(popupText("M71-other")).assertCountEquals(0)
        rule.onAllNodes(popupText(">IP:$ip")).assertCountEquals(0)
    }

    @Test fun ipv4QuoteTapShowsOnlyThePriorIpHeaderTarget() = verifyQuote(ipv4)
    @Test fun ipv6QuoteTapShowsOnlyThePriorIpHeaderTarget() = verifyQuote(ipv6)

    @Test fun importedNgHidesAnIpAuthorAndTheQuotePopupKeepsItsBodyHidden() {
        val ng = FtutterNgRule("IP:$ipv6", FtutterNgType.ID.stored)
        val plan = ftutterBackupPlan(mapOf("NGList" to FtutterPrefValue.Texts(listOf(
            encodeFtutterNgList(listOf(ng)).drop(1).dropLast(1)
        ))), emptyList(), emptyList(), emptyMap(), 1)
        show(ipv6, parseFtutterNgList(plan.preferences[FtutterKeys.NG_LIST]))
        rule.onAllNodes(hasText("M71-target") and hasAnyAncestor(hasTestTag("ftutter-thread-list")))
            .assertCountEquals(0)
        tapQuote(ipv6)
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("NG", substring = true) and hasAnyAncestor(hasTestTag("ftutter-popup"))).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(popupText("M71-target")).assertCountEquals(0)
    }

    @Test fun ipHeaderLongPressPrefillsAndSavesTheSameLiteralNgIdentity() {
        show(ipv4)
        rule.onNode(hasTestTag("ftutter-post-id") and hasText("IP:$ipv4", substring = true))
            .performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithTag("ftutter-id-ng").performClick()
        rule.onNodeWithTag("ftutter-ng-text").assertTextContains("IP:$ipv4")
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(10_000) { saved.size == 1 }
        assertEquals("IP:$ipv4", saved.single().text)
        assertEquals(FtutterNgType.ID.stored, saved.single().type)
    }
}
