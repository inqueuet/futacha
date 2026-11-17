@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.runBlocking
import org.junit.*

/**
 * "1レス" is shown, yet tapping it lists nothing and the quote does not show its source.
 * The posts are the shapes found on live may/img threads (2026-10-08) where the parser's count and the
 * tap resolver disagreed: a quote starting with digits, an ID ending in a period, and a quote that
 * extends the quoted line.
 */
class QuoteReplyCountInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val db = "quote_reply_count_test.db"
    private val board = "https://may.2chan.net/b/"

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize(); store.savePreference("compat.commonUsedVersion", "1.0") }
        loader = ImageLoader.Builder(rule.activity).components { add(TutorialImageFetcherFactory()) }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    private fun waitTag(tag: String, dump: String = "QUOTE_TREE") = try {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        val n = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (i in 0 until n) rule.onAllNodes(isRoot())[i].printToLog(dump); throw e
    }

    private fun post(no: String, html: String, id: String? = null, replies: Int = 0, refs: List<QuoteReference> = emptyList()) =
        Post(no, author = null, subject = null, timestamp = "", messageHtml = html, imageUrl = null, thumbnailUrl = null,
            posterId = id, referencedCount = replies, quoteReferences = refs)

    private fun openThread() {
        val item = CatalogItem(id = "95", threadUrl = "${board}res/95.htm", title = "QUOTES",
            thumbnailUrl = null, fullImageUrl = null, replyCount = 5)
        runBlocking {
            store.upsertBoard(CompatBoard(compatBoardKey(board), "mayb", board, board, 0))
            store.savePreference("compat.control.controlPostTapBehavior", "legacy")
        }
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = listOf(item)
            override suspend fun getThreadByUrl(threadUrl: String) = ThreadPage("95", "mayb", null, null, listOf(
                post("101", "1000なら懐かしのキャラ祭り<br>SRC-DIGITS", replies = 1),
                post("102", "&gt;&gt;1000なら懐かしのキャラ祭り<br>REPLY-DIGITS", refs = listOf(QuoteReference(">>1000なら懐かしのキャラ祭り", listOf("101")))),
                post("103", "ID付きの本文SRC-ID", id = "ID:TrUQupJ.", replies = 1),
                post("104", "&gt;ID:TrUQupJ.<br>REPLY-ID", refs = listOf(QuoteReference(">ID:TrUQupJ.", listOf("103")))),
                post("105", "新モデル早く出してね<br>SRC-LONGER", replies = 1),
                post("106", "&gt;いつもどうりエロい事出来る新モデル早く出してね<br>REPLY-LONGER",
                    refs = listOf(QuoteReference(">いつもどうりエロい事出来る新モデル早く出してね", listOf("105")))))
            )
        }
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                CompatibilityApp(store = store, repository = repo, imageLoader = loader, onExitApplication = {})
            }
        } }
        rule.onNodeWithText(board).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("QUOTES").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("QUOTES").performClick()
        waitTag("compat-thread-post-106")
    }

    private fun popupHas(text: String) = rule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("compat-quote-popup")))

    private fun quoteShowsSource(replyNo: String, sourceMarker: String) {
        // Tap the first line, which is the quote; the centre of the row can land on the line below it.
        rule.onNode(hasText(">", substring = true) and hasAnyAncestor(hasTestTag("compat-thread-post-$replyNo")) and !hasText("No.$replyNo", substring = true))
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(24f, 8f)) }
        waitTag("compat-quote-popup", "QUOTE_TREE_$replyNo")
        popupHas(sourceMarker).assertExists()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-quote-popup").fetchSemanticsNodes().isEmpty() }
    }

    private fun replyCountListsReply(sourceNo: String, replyMarker: String, viaMenu: Boolean) {
        // The reply count is part of the header line, which also carries "No.<number>".
        rule.onNode(hasText("No.$sourceNo", substring = true) and hasAnyAncestor(hasTestTag("compat-thread-post-$sourceNo")))
            .performTouchInput { longClick() }
        if (viaMenu) {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("引用したレスを抽出").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("引用したレスを抽出").performClick()
        }
        waitTag("compat-extraction-popup", "QUOTE_TREE_H$sourceNo")
        rule.onNode(hasText(replyMarker, substring = true) and hasAnyAncestor(hasTestTag("compat-extraction-popup"))).assertExists()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-extraction-popup").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun numberLedQuoteShowsItsSourceAndTheReplyCountListsTheReply() {
        openThread()
        quoteShowsSource("102", "SRC-DIGITS")
        replyCountListsReply("101", "REPLY-DIGITS", viaMenu = false)
    }

    @Test fun idEndingInAPeriodShowsItsSourceAndTheReplyCountListsTheReply() {
        openThread()
        quoteShowsSource("104", "SRC-ID")
        replyCountListsReply("103", "REPLY-ID", viaMenu = true)
    }

    @Test fun quoteThatExtendsTheQuotedLineShowsItsSourceAndTheReplyCountListsTheReply() {
        openThread()
        quoteShowsSource("106", "SRC-LONGER")
        replyCountListsReply("105", "REPLY-LONGER", viaMenu = false)
    }
}
