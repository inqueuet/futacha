@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ThreadPostMetadata
import com.valoser.futacha.shared.ui.board.ThreadScrollbar
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.ftutter.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Reads local fixtures only: no posting or votes are sent. */
class ThreadFeedbackFixesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val databaseName = "feedback-${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private val board = CompatBoard("board", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val tab = CompatTab("tab", "https://may.2chan.net/b/res/123.htm", "https://may.2chan.net/b/res/123.htm",
        "board", "虹裏", "123", "文字サイズの確認", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = databaseName)
        runBlocking { store.initialize(); store.upsertBoard(board); store.openTab(tab) }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(databaseName)
    }

    @Test fun replyTitleFollowsTheThreadTitleSizeSetting() {
        var size by mutableStateOf("12")
        rule.setContent { MaterialTheme {
            CompatPostScreen(tab, board, repository = null, store = store,
                preferences = mapOf("compat.thread.threadTitleFontSize" to size), appVersion = "14.3",
                fileSystem = null, onToolbarEdit = {}, onBack = {})
        } }
        fun fontSize(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("compat-post-thread-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        assertEquals(12f, fontSize(), 0.01f)
        rule.runOnIdle { size = "18" }
        assertEquals(18f, fontSize(), 0.01f)
        rule.runOnIdle { size = "標準" }
        assertEquals(20f, fontSize(), 0.01f)
    }

    @Test fun compactHeaderStillShowsTheAimogeSaidaneCount() {
        val post = Post(id = "100555050", author = null, subject = null, timestamp = "now", messageHtml = "body",
            imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx7")
        rule.setContent { MaterialTheme {
            ThreadPostMetadata(post, isOp = false, posterIdLabel = null, posterIdValue = null,
                saidaneLabel = post.saidaneLabel, onUrlClick = {}, onSaidaneClick = {}, compactHeader = true)
        } }
        rule.onNodeWithText("そうだねx7").assertIsDisplayed()
    }

    @Test fun readOnlyHeaderKeepsTheCountInBothLayouts() {
        var compact by mutableStateOf(false)
        val post = Post(id = "123", author = null, subject = null, timestamp = "now", messageHtml = "body",
            imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx7")
        rule.setContent { MaterialTheme {
            ThreadPostMetadata(post, isOp = false, posterIdLabel = null, posterIdValue = null,
                saidaneLabel = post.saidaneLabel, onUrlClick = {}, onSaidaneClick = null, compactHeader = compact)
        } }
        rule.onNodeWithText("そうだねx7").assertIsDisplayed().assertIsNotEnabled()
        rule.runOnIdle { compact = true }
        rule.onNodeWithText("そうだねx7").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun ownPostCountIsReadableWithoutEnablingSelfVotes() {
        var votes = 0
        val post = Post(id = "123", author = null, subject = null, timestamp = "now", messageHtml = "body",
            imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx7")
        rule.setContent { MaterialTheme {
            ThreadPostMetadata(post, isOp = false, isSelfPost = true, posterIdLabel = null, posterIdValue = null,
                saidaneLabel = post.saidaneLabel, onUrlClick = {}, onSaidaneClick = { votes++ }, compactHeader = true)
        } }
        rule.onNodeWithText("そうだねx7").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, votes) }
    }

    @Test fun standardThreadScrollbarCanBeDragged() = dragScrollbar(ftutter = false)
    @Test fun ftutterScrollbarKeepsDraggingWhenVisibleRowCountChanges() = dragScrollbar(ftutter = true)

    private fun dragScrollbar(ftutter: Boolean) {
        lateinit var list: LazyListState
        rule.setContent { MaterialTheme {
            list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(200) { i -> Text("レス $i", Modifier.height(if (i < 20) 40.dp else 120.dp)) }
                }
                if (ftutter) FtutterScrollbar(FtutterScrollbarSettings(true, true, false, 8), list)
                else ThreadScrollbar(list, Modifier.align(Alignment.CenterEnd).testTag("standard-thread-scrollbar"))
            }
        } }
        val bar = rule.onNodeWithTag(if (ftutter) "compat-fast-scrollbar" else "standard-thread-scrollbar")
        bar.performTouchInput {
            down(Offset(width - 4f, 10f))
            repeat(8) { moveTo(Offset(width - 4f, 10f + height * 0.04f * (it + 1)), delayMillis = 100) }
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue("scroll index ${list.firstVisibleItemIndex}", list.firstVisibleItemIndex >= 40) }
    }
}
