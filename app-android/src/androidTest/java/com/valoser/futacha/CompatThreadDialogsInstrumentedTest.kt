@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.COMPAT_POST_DELETE_KEY_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives the thread dialogs that live outside CompatThreadScreen (post and
 * media long-press menus, reply and extraction popups) through the states they
 * share with the thread screen.
 */
class CompatThreadDialogsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "thread_dialogs_${System.nanoTime()}.db"
    private val store = AndroidCompatibilityStore(context, databaseName = databaseName)
    private val url = "https://may.2chan.net/b/res/100.htm"
    private val tabKey = compatTabKey(url)
    private val imageUrl = "https://may.2chan.net/b/src/100.jpg"

    @After fun close() {
        runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    private fun showThread(extraImagePosts: Int = 0, quoteChain: Boolean = false) {
        runBlocking {
            store.initialize()
            store.savePreference(COMPAT_POST_DELETE_KEY_STORAGE_KEY, "key123")
            val boardUrl = "https://may.2chan.net/b/"
            val boardKey = compatBoardKey(boardUrl)
            store.upsertBoard(CompatBoard(boardKey, "検証", boardUrl, boardUrl, 0))
            store.openTab(CompatTab(key = tabKey, canonicalUrl = url, originalUrl = url, boardKey = boardKey,
                boardName = "検証", threadNo = "100", title = "スレ本文", replyCount = 1,
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1))
            store.saveThreadSnapshot(CompatThreadSnapshot(tabKey = tabKey, revision = 1, fetchedAtEpochMillis = 1,
                posts = listOf(
                    CompatPostSnapshot(0, "100", timestamp = "", messageHtml = "スレ本文",
                        imageUrl = imageUrl, thumbnailUrl = "https://may.2chan.net/b/thumb/100s.jpg"),
                    CompatPostSnapshot(1, "101", timestamp = "", messageHtml = "&gt;&gt;100")
                ) + (if (quoteChain) listOf(
                    CompatPostSnapshot(2, "102", timestamp = "", messageHtml = "&gt;&gt;101")
                ) else emptyList()) + (1..extraImagePosts).map { index ->
                    CompatPostSnapshot(1 + index, "${200 + index}", timestamp = "", messageHtml = "画像レス$index",
                        imageUrl = "https://may.2chan.net/b/src/${200 + index}.jpg",
                        thumbnailUrl = "https://may.2chan.net/b/thumb/${200 + index}s.jpg")
                }))
        }
        rule.setContent {
            MaterialTheme { CompatibilityApp(store = store, repository = null, initialThreadDeepLink = url, onExitApplication = {}) }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-101", useUnmergedTree = true).fetchSemanticsNodes(false).isNotEmpty()
        }
    }

    private fun longPressPost(postNo: String, text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("compat-thread-post-$postNo")), useUnmergedTree = true)
            .performTouchInput { longClick() }
    }

    private fun longPressImage() {
        rule.onNodeWithContentDescription("No.100の画像").performTouchInput { longClick() }
        rule.onNodeWithText("リンクURLをコピー").assertIsDisplayed()
    }

    @Test fun postMenuReportsSaidaneFailureAndOpensDeleteWithStoredKey() {
        showThread()
        longPressPost("100", "スレ本文")
        rule.onNodeWithText("そうだね").performClick()
        rule.onNodeWithText("通信機能を初期化できませんでした").assertIsDisplayed()
        rule.onAllNodesWithText("そうだね").assertCountEquals(0)

        longPressPost("100", "スレ本文")
        rule.onNodeWithText("削除").performClick()
        rule.onNodeWithText("レス削除 No.100").assertIsDisplayed()
        val keyField = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        assertEquals("key123", keyField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun mediaMenuCopiesReloadsAndOpensImageNgRegistration() {
        showThread()
        longPressImage()
        rule.onNodeWithText("リンクURLをコピー").performClick()
        rule.onNodeWithText("コピーしました").assertIsDisplayed()
        rule.onAllNodesWithText("リンクURLをコピー").assertCountEquals(0)
        rule.runOnIdle {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(imageUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }

        longPressImage()
        rule.onNodeWithText("サムネイルを再読み込みする").performClick()
        rule.onAllNodesWithText("リンクURLをコピー").assertCountEquals(0)

        longPressImage()
        rule.onNodeWithText("NG画像に登録").performClick()
        rule.onNodeWithText("この板のみ").assertIsDisplayed()
        rule.onNodeWithText("登録する").assertIsDisplayed()
    }

    @Test fun replyAndExtractionPopupsOpenAndCloseThroughSharedState() {
        showThread()
        rule.onNodeWithText(">>100").performClick()
        rule.onNodeWithTag("compat-quote-popup").assertIsDisplayed()
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-quote-popup")), useUnmergedTree = true)
            .performTouchInput { longClick() }
        rule.onNodeWithText("クイック").assertIsDisplayed()
        Espresso.pressBack()
        rule.onAllNodesWithText("クイック").assertCountEquals(0)
        Espresso.pressBack()
        rule.onAllNodesWithTag("compat-quote-popup").assertCountEquals(0)

        // レス抽出 is a default toolbar command, so the other menu omits it.
        rule.onNodeWithContentDescription("レス抽出").performClick()
        rule.onNodeWithText("画像レス").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-extraction-popup").fetchSemanticsNodes(false).isNotEmpty()
        }
        rule.onNode(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-extraction-popup")), useUnmergedTree = true)
            .performClick()
        rule.onAllNodesWithTag("compat-extraction-popup").assertCountEquals(0)
    }

    @Test fun largeExtractionBuildsOnlyVisibleRowsAndScrollsToTheLast() {
        showThread(extraImagePosts = 300)
        rule.onNodeWithContentDescription("レス抽出").performClick()
        rule.onNodeWithText("画像レス").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-extraction-popup-list").fetchSemanticsNodes(false).isNotEmpty()
        }
        val postRow = SemanticsMatcher("post row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("compat-thread-post-") == true
        }
        val built = rule.onAllNodes(postRow and hasAnyAncestor(hasTestTag("compat-extraction-popup-list")),
            useUnmergedTree = true).fetchSemanticsNodes(false).size
        // 301 posts match; a non-lazy column built every one of them.
        assertTrue("built $built rows", built in 1..60)

        rule.onNodeWithTag("compat-extraction-popup-list").performScrollToIndex(300)
        rule.onNode(hasText("画像レス300") and hasAnyAncestor(hasTestTag("compat-extraction-popup-list")),
            useUnmergedTree = true).assertExists()
    }

    @Test fun quoteInsideTheQuotePopupReplacesItWithTheQuotedPost() {
        showThread(quoteChain = true)
        rule.onNodeWithText(">>101").performClick()
        rule.onNodeWithTag("compat-quote-popup").assertIsDisplayed()
        // The popup shows No.101, whose own quote is resolved off the main thread.
        rule.onNode(hasText(">>100") and hasAnyAncestor(hasTestTag("compat-quote-popup")), useUnmergedTree = true)
            .performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("スレ本文") and hasAnyAncestor(hasTestTag("compat-quote-popup")), useUnmergedTree = true)
                .fetchSemanticsNodes(false).isNotEmpty()
        }
    }
}
