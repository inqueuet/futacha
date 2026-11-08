package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** L-B7「開く・更新・タップ」 (look doc §1.2 S22・G21・F03・G20): the pure parts. */
@OptIn(ExperimentalCoroutinesApi::class)
class FutaberOpenRefreshTest {
    private fun content(id: String) = ThreadPageContent(ThreadPage(id, null, null, null, emptyList()))

    // ---- S22 ----

    @Test fun theOpenFlowMarksOneRowAndTakesOnlyTheAnswerOfThatPress() {
        val idle = FutaberOpenFlow()
        assertNull(idle.pressedThreadId("b"))
        val (loading, first) = idle.start("b", "1")
        assertEquals("1", loading.pressedThreadId("b"))
        assertNull(loading.pressedThreadId("other"), "the row of another board is not marked")
        assertTrue(loading.accepts(first))
        // Another row replaces the first: the first answer is no longer wanted.
        val (replaced, second) = loading.start("b", "2")
        assertFalse(replaced.accepts(first))
        assertTrue(replaced.accepts(second))
        assertEquals(replaced, replaced.finish(first), "a late answer of the first press changes nothing")
        assertNull(replaced.finish(second).opening)
        // Cancelled (back, a board switch): nothing is marked and no answer is taken.
        val cancelled = replaced.cancel()
        assertNull(cancelled.opening)
        assertFalse(cancelled.accepts(second))
    }

    @Test fun aRowsThreadOpensWithThePageItLoadedOnce() = runTest {
        val opener = FutaberCatalogOpener()
        val gate = CompletableDeferred<ThreadPageContent>()
        var opened = 0
        opener.open(this, "b", "1", fetch = { gate.await() }) { opened++ }
        runCurrent()
        assertTrue(opener.isLoading)
        assertEquals("1", opener.pressedThreadId("b"))
        assertEquals(0, opened, "the thread waits for its page")
        val page = content("1")
        gate.complete(page)
        runCurrent()
        assertEquals(1, opened)
        assertFalse(opener.isLoading)
        assertNull(opener.pressedThreadId("b"))
        val handoff = assertNotNull(opener.take(FutaberTabKey("b", "1")))
        assertSame(page, handoff.content?.getOrNull())
        assertFalse(handoff.fromTab)
        assertNull(opener.take(FutaberTabKey("b", "1")), "handed over once")
    }

    @Test fun aFailedLoadStillOpensTheThreadWithTheFailureForTheFallbacks() = runTest {
        val opener = FutaberCatalogOpener()
        var opened = 0
        opener.open(this, "b", "9", fetch = { error("404") }) { opened++ }
        runCurrent()
        assertEquals(1, opened, "404, no connection, a timeout: the thread opens and its fallbacks take over")
        val handoff = assertNotNull(opener.take(FutaberTabKey("b", "9")))
        assertTrue(handoff.content?.isFailure == true)
    }

    /** Audit A9: a fetch that never answers opens the thread with a timeout once the limit has passed. */
    @Test fun aFetchThatNeverAnswersOpensWithATimeout() = runTest {
        val opener = FutaberCatalogOpener(fetchTimeoutMillis = 1_000)
        var opened = 0
        opener.open(this, "b", "5", fetch = { CompletableDeferred<ThreadPageContent>().await() }) { opened++ }
        runCurrent()
        assertTrue(opener.isLoading)
        testScheduler.advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, opened)
        assertFalse(opener.isLoading)
        val handoff = assertNotNull(opener.take(FutaberTabKey("b", "5")))
        assertTrue(handoff.content?.exceptionOrNull() is FutaberOpenTimeoutException)
    }

    @Test fun backAndAnotherRowCancelTheLoad() = runTest {
        val opener = FutaberCatalogOpener()
        val firstGate = CompletableDeferred<ThreadPageContent>()
        val secondGate = CompletableDeferred<ThreadPageContent>()
        val opened = mutableListOf<String>()
        opener.open(this, "b", "1", fetch = { firstGate.await() }) { opened += "1" }
        runCurrent()
        opener.open(this, "b", "2", fetch = { secondGate.await() }) { opened += "2" }
        runCurrent()
        assertEquals("2", opener.pressedThreadId("b"), "the second row replaces the first")
        firstGate.complete(content("1"))
        runCurrent()
        assertEquals(emptyList(), opened, "the first press no longer opens")
        opener.cancel()
        assertFalse(opener.isLoading)
        secondGate.complete(content("2"))
        runCurrent()
        assertEquals(emptyList(), opened, "back cancelled the second")
        assertNull(opener.take(FutaberTabKey("b", "2")))
    }

    @Test fun anotherWayOfOpeningDropsTheHandoffAndATabTellsItCameFromTheStrip() = runTest {
        val opener = FutaberCatalogOpener()
        opener.open(this, "b", "1", fetch = { content("1") }) {}
        runCurrent()
        opener.reset()
        assertNull(opener.take(FutaberTabKey("b", "1")), "a history / tab opening does not get the catalog's page")
        opener.openedFromTab(FutaberTabKey("b", "3"))
        assertNull(opener.take(FutaberTabKey("b", "4")), "only for the thread opened")
        opener.openedFromTab(FutaberTabKey("b", "3"))
        val tab = assertNotNull(opener.take(FutaberTabKey("b", "3")))
        assertTrue(tab.fromTab)
        assertNull(tab.content)
    }

    // ---- G21 ----

    @Test fun pullingUpPastTheEndRefreshesOnlyAfterTheThresholdAndOnlyByTheFinger() {
        val pull = FutaberEndPull(thresholdPx = 60f)
        pull.start()
        pull.onScroll(-30f, byFinger = true, atEnd = true)
        assertFalse(pull.release(), "30 of 60: not yet")
        pull.start()
        pull.onScroll(-40f, byFinger = true, atEnd = true)
        pull.onScroll(-25f, byFinger = true, atEnd = true)
        assertTrue(pull.release(), "65 of 60")
        assertFalse(pull.release(), "once per pull")
        // A fling that runs into the end never refreshes.
        pull.start()
        pull.onScroll(-500f, byFinger = false, atEnd = true)
        assertFalse(pull.release())
        // Not at the end (the list still scrolls): nothing.
        pull.start()
        pull.onScroll(-500f, byFinger = true, atEnd = false)
        assertFalse(pull.release())
        // Pulling back down takes it off.
        pull.start()
        pull.onScroll(-80f, byFinger = true, atEnd = true)
        pull.onPullBack(50f)
        assertEquals(30f, pull.pulledPx)
        assertFalse(pull.release())
        // A new touch starts from nothing.
        pull.onScroll(-59f, byFinger = true, atEnd = true)
        pull.start()
        pull.onScroll(-10f, byFinger = true, atEnd = true)
        assertFalse(pull.release())
    }

    // ---- F03 ----

    @Test fun theNewCountIsTheFetchedPostsOverTheBaseAndNeverNegative() {
        assertEquals(3, futaberNewCountAfterLoad(10, 13))
        assertEquals(0, futaberNewCountAfterLoad(13, 13))
        assertEquals(0, futaberNewCountAfterLoad(13, 10), "a page with fewer posts says 0")
        assertEquals("新着 0", futaberNewPostsToastText(futaberNewCountAfterLoad(13, 13)))
    }

    @Test fun theBoxFollowsARefreshByHandOrATabsOpeningOnly() {
        // Opened from the catalog: nothing is armed.
        val catalog = FutaberNewCountNotice(initialBase = null)
        catalog.onLivePage(20)
        assertNull(catalog.message)
        // The refresh button / the pull: the page's count before.
        catalog.arm(20)
        catalog.onLivePage(23)
        assertEquals("新着 3", catalog.message)
        catalog.hide()
        catalog.onLivePage(30)
        assertNull(catalog.message, "a later reload (the post screen, the auto scroll) shows nothing")
        // A failed refresh shows nothing.
        catalog.arm(30)
        catalog.disarm()
        catalog.onLivePage(31)
        assertNull(catalog.message)
        // From a tab: the stored seen count.
        val tab = FutaberNewCountNotice(initialBase = 5)
        tab.onLivePage(9)
        assertEquals("新着 4", tab.message)
    }

    // ---- G20 ----

    @Test fun tapToScrollIsOnUnlessTurnedOffAndPagesByThirds() {
        assertTrue(FutaberDisplaySettings.from(emptyMap()).tapScroll, "never set: on (futaber's 「する」)")
        assertTrue(FutaberDisplaySettings().tapScroll)
        assertTrue(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.TAP_SCROLL to "ON")).tapScroll)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.TAP_SCROLL to "OFF")).tapScroll, "an explicit OFF stays")
        assertEquals(1, futaberTapScrollDirection(yInList = 900f, listHeight = 1000f), "lower third: the next screen")
        assertEquals(-1, futaberTapScrollDirection(yInList = 100f, listHeight = 1000f), "upper third: the previous screen")
        assertEquals(0, futaberTapScrollDirection(yInList = 500f, listHeight = 1000f), "the middle: nothing")
        assertEquals(0, futaberTapScrollDirection(yInList = 1200f, listHeight = 1000f), "outside the list: nothing")
    }

    // ---- S22 colours ----

    @Test fun aLoadingRowIsBlueWithWhiteTextAboveTheReadGrey() {
        for (colors in listOf(FutaberTokens.light, FutaberTokens.dark)) {
            assertEquals(colors.selection, futaberCatalogRowGround(colors, read = true, opening = true))
            assertEquals(colors.readRow, futaberCatalogRowGround(colors, read = true, opening = false))
            assertEquals(colors.background, futaberCatalogRowGround(colors, read = false, opening = false))
        }
    }
}
