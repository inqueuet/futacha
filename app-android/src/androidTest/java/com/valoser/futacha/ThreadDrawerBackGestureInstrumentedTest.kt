package com.valoser.futacha

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.InputDevice
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import coil3.ImageLoader
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Inject through Android's input dispatcher, including SystemUI's edge recognizer. */
@SdkSuppress(minSdkVersion = 34)
class ThreadDrawerBackGestureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = "drawer-system-gesture-${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private var width = 0f
    private var height = 0f
    private var modernImageLoader: ImageLoader? = null

    @Before
    fun openThreadWithCachedPosts() {
        assumeTrue("Run with Android gesture navigation enabled",
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2)
        val boardUrl = "https://drawer-gesture.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = database)
            store.initialize()
            store.upsertBoard(CompatBoard(boardKey, "Gesture test", boardUrl, boardUrl, 0))
            for ((number, title) in listOf("731" to "FIRST", "732" to "SECOND")) {
                val url = "${boardUrl}res/$number.htm"
                val key = compatTabKey(url)
                store.openTab(CompatTab(key = key, canonicalUrl = url, originalUrl = url,
                    boardKey = boardKey, boardName = "Gesture test", threadNo = number,
                    title = title, replyCount = 40, insertedAtEpochMillis = number.toLong(),
                    contentUpdatedAtEpochMillis = 1, snapshotRevision = 1))
                store.saveThreadSnapshot(CompatThreadSnapshot(key, 1, 1,
                    posts = (0..40).map { index -> CompatPostSnapshot(position = index,
                        postNo = "$number$index", timestamp = "09/07 12:00",
                        messageHtml = "$title-$index 本文の上から履歴を開く確認") }))
            }
        }
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null,
                        initialThreadDeepLink = "${boardUrl}res/732.htm", onExitApplication = {})
                }
            }
            width = rule.activity.window.decorView.width.toFloat()
            height = rule.activity.window.decorView.height.toFloat()
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("SECOND-0 本文の上から履歴を開く確認")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun disposeFixture() {
        if (::store.isInitialized) {
            rule.runOnUiThread { rule.activity.setContent {} }
            runBlocking { store.closeForTest() }
            context.deleteDatabase(database)
        }
        modernImageLoader?.shutdown()
    }

    @Test
    fun modernThreadSystemLeftEdgeOpensHistoryAndRightEdgeKeepsBack() {
        val loader = ImageLoader(context).also { modernImageLoader = it }
        val backs = java.util.concurrent.atomic.AtomicInteger(0)
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                    MaterialTheme {
                        ThreadScreen(board = mockBoardSummaries.first(), history = mockThreadHistory,
                            threadId = "354621", threadTitle = "チュートリアル", initialReplyCount = 17,
                            preferencesState = ScreenPreferencesState(appVersion = "test"),
                            onBack = { backs.incrementAndGet() })
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("履歴を開く").assertIsDisplayed()
        swipe(.01f, .75f, .5f)
        rule.onNodeWithTag("history-drawer").assertIsDisplayed()
        org.junit.Assert.assertEquals(0, backs.get())
        swipe(.99f, .25f, .5f)
        rule.waitUntil(5_000) {
            rule.waitForIdle()
            runCatching { rule.onNodeWithTag("history-drawer").assertIsNotDisplayed() }.isSuccess
        }
        rule.onNodeWithContentDescription("履歴を開く").assertIsDisplayed()
        swipe(.99f, .25f, .5f)
        rule.waitUntil(5_000) { backs.get() == 1 }
        org.junit.Assert.assertEquals(1, backs.get())
    }

    @Test
    fun leftEdgeAtEveryHeightOpensHistoryAndRightEdgeClosesIt() {
        for (y in listOf(.30f, .50f, .80f)) {
            swipe(.01f, .75f, y)
            rule.onNodeWithContentDescription("履歴").assertIsDisplayed().performClick()
            rule.onNodeWithText("履歴").assertIsDisplayed()
            // Back while the drawer is already visible must dismiss it.
            swipe(.99f, .25f, .5f)
            rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()
            rule.onNodeWithContentDescription("ドロワー").assertIsDisplayed()
            rule.onNodeWithTag("compat-thread-post-7320").assertIsDisplayed()
        }
    }

    @Test
    fun cancelledLeftEdgeKeepsThreadAndCenterPagingAndRightBackStillWork() {
        swipe(.01f, .35f, .5f, cancel = true)
        rule.onNodeWithContentDescription("ドロワー").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-post-7320").assertIsDisplayed()
        swipe(.75f, .25f, .5f)
        waitForSelectedTab("731")
        rule.onNodeWithTag("compat-thread-post-7310").assertIsDisplayed()
        swipe(.25f, .75f, .5f)
        waitForSelectedTab("732")
        rule.onNodeWithTag("compat-thread-post-7320").assertIsDisplayed()
        swipe(.99f, .25f, .5f)
        rule.onNodeWithTag("compat-board-list").assertIsDisplayed()
    }

    @Test
    fun searchLeftEdgeDismissesSearchInsteadOfOpeningHistory() {
        rule.onNodeWithContentDescription("検索").performClick()
        rule.onNodeWithTag("compat-thread-search-field").assertIsDisplayed()
        waitForIme(visible = true)
        // The reference search has staged Back (IME, focus, then search).
        repeat(3) { stage ->
            swipe(.01f, .75f, .5f)
            if (stage == 0) waitForIme(visible = false)
            assertTrue(rule.onAllNodesWithText("履歴").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithContentDescription("開いているタブ").assertIsNotDisplayed()
        }
        rule.onNodeWithContentDescription("ドロワー").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()
    }

    private fun waitForSelectedTab(number: String) {
        // A neighbor preview can already be displayed before the asynchronous
        // scroll-anchor write commits the tab switch. Wait for actual selection
        // before starting the reverse swipe (especially on slower devices).
        rule.waitUntil(5_000) {
            runBlocking { store.workspace.first() }.activeTabKey ==
                compatTabKey("https://drawer-gesture.2chan.net/b/res/$number.htm")
        }
        rule.waitForIdle()
    }

    private fun waitForIme(visible: Boolean) {
        rule.waitUntil(5_000) {
            var showing = false
            rule.runOnUiThread {
                showing = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            showing == visible
        }
    }

    private fun swipe(start: Float, end: Float, y: Float, cancel: Boolean = false) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x * width, y * height, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
            // SystemUI uses real time, while Compose animations use the test
            // clock. Keep both moving so an old drag cannot settle after Back.
            rule.mainClock.advanceTimeByFrame()
        }
        inject(MotionEvent.ACTION_DOWN, start)
        for (step in 1..24) {
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, start + (end - start) * step / 24f)
        }
        if (cancel) {
            for (step in 1..24) {
                SystemClock.sleep(16)
                inject(MotionEvent.ACTION_MOVE, end + (start - end) * step / 24f)
            }
        }
        inject(MotionEvent.ACTION_UP, if (cancel) start else end)
        SystemClock.sleep(500)
        rule.waitForIdle()
    }
}
