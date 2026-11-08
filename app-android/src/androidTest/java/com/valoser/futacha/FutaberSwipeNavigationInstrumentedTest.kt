package com.valoser.futacha

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードのスワイプを、実機のfutaberで確かめた動作（docs/thread-feedback-1444136530-2026-10-27.md 第4章）と
 * 同じにする。スレッド：右でカタログへ戻る・左端から右で板一覧・左で書き込み。カタログ：右で板一覧・左で最後に見たスレッド。
 * 通信はチュートリアル板の同梱データだけを使い、投稿は送らない。
 */
class FutaberSwipeNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val futaberKeys = listOf(
        "compat.futaber.lastBoardId",
        "compat.futaber.manageCategory",
        "compat.futaber.tabs",
        "compat.futaber.drafts",
        "compat.futaber.seen",
        "compat.futaber.catalog.displayStyle",
        "compat.futaber.catalog.sort"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.compatibilityStore.savePreferences(futaberKeys.associateWith { null }) }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-board-address")
        waitForTag("futaber-catalog-item")
        waitGone("futaber-drawer")
    }

    private fun openFirstThread(): String {
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-thread-title")
        waitForTag("futaber-post")
        return threadTitle()
    }

    private fun threadTitle(): String = rule.onNodeWithTag("futaber-thread-title").fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    /** A quick horizontal swipe across the middle of [tag], from [fromX] to [toX] (fractions of its width). */
    private fun swipeAcross(tag: String, fromX: Float, toX: Float) {
        rule.onNodeWithTag(tag).performTouchInput {
            val y = height * 0.5f
            swipe(Offset(width * fromX, y), Offset(width * toX, y), durationMillis = 200)
        }
        rule.waitForIdle()
    }

    /** A swipe to the right that starts on the very left edge (2px in). */
    private fun swipeFromLeftEdge(tag: String) {
        rule.onNodeWithTag(tag).performTouchInput {
            val y = height * 0.5f
            swipe(Offset(2f, y), Offset(width * 0.7f, y), durationMillis = 200)
        }
        rule.waitForIdle()
    }

    /** L-B2 (V05): the thread has no "←" any more; the system's back returns to the catalog. */
    private fun backToCatalog() {
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test fun aRightSwipeOnTheThreadGoesBackToTheCatalog() {
        openTutorialCatalog()
        openFirstThread()
        swipeAcross("futaber-thread-body", 0.25f, 0.85f)
        waitGone("futaber-thread-title")
        waitForTag("futaber-catalog-item")
        assertTrue(rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aLeftSwipeOnTheThreadOpensTheWritingScreen() {
        openTutorialCatalog()
        openFirstThread()
        swipeAcross("futaber-thread-body", 0.85f, 0.15f)
        waitForTag("futaber-post-screen")
        // Closing the writing screen leaves the thread where it was; nothing was sent.
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
        waitForTag("futaber-thread-title")
    }

    @Test fun aRightSwipeFromTheLeftEdgeOfTheThreadOpensTheBoardList() {
        openTutorialCatalog()
        openFirstThread()
        swipeFromLeftEdge("futaber-thread-body")
        waitForTag("futaber-drawer")
        // The thread stays under the board list, as in the original.
        assertTrue(rule.onAllNodesWithTag("futaber-thread-title").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun scrollingTheThreadIsNotASwipe() {
        openTutorialCatalog()
        val title = openFirstThread()
        rule.onNodeWithTag("futaber-thread-body").performTouchInput { swipeUp(durationMillis = 300) }
        rule.waitForIdle()
        assertEquals(title, threadTitle())
        assertTrue(rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isEmpty())
    }

    @Test fun aLeftSwipeOnTheCatalogGoesBackIntoTheLastThread() {
        openTutorialCatalog()
        val title = openFirstThread()
        backToCatalog()
        waitGone("futaber-thread-title")
        waitForTag("futaber-catalog-item")
        swipeAcross("futaber-catalog-body", 0.85f, 0.15f)
        waitForTag("futaber-thread-title")
        assertEquals(title, threadTitle())
    }

    @Test fun aRightSwipeOnTheCatalogOpensTheBoardList() {
        openTutorialCatalog()
        swipeAcross("futaber-catalog-body", 0.25f, 0.85f)
        waitForTag("futaber-drawer")
    }
}
