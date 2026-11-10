package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatTabTrimRanks
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.withStores
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.FutachaThreadHistoryContext
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_TAB_PROFILE
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.FtutterStoreTabWriter
import com.valoser.futacha.shared.ui.ftutter.FtutterTabSeed
import com.valoser.futacha.shared.ui.ftutter.ftutterAddLegacyTabs
import com.valoser.futacha.shared.ui.ftutter.ftutterTabSeedFromPage
import com.valoser.futacha.shared.ui.ftutter.ftutterTabSnapshot
import com.valoser.futacha.shared.ui.futaber.FUTABER_TAB_TRIMMED_NOTICE
import com.valoser.futacha.shared.ui.futaber.FutaberStoreTabWriter
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberTabSnapshot
import com.valoser.futacha.shared.ui.recordFutachaVisitedThread
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The low findings of the audits of 2026-11-09 (rounds 4 and 5): L1, L2, L4, L5, L6, L7, N2 and N3. */
class SharedTabsRemainingAuditTest {
    private val futacha = ExperienceProfile.FUTACHA.persistedValue
    private val now get() = Clock.System.now().toEpochMilliseconds()
    private fun url(threadId: String) = SingleMode.threadUrl(may, threadId)

    private fun ftutterWriter(store: DesktopCompatibilityStore, rows: MutableMap<String, List<String>> = mutableMapOf(), at: Long = 1_000L) =
        FtutterStoreTabWriter(store, { SingleMode.boards }, { emptyList() }, { key, edit -> rows[key] = edit(rows[key].orEmpty()) }, {}, { at })

    private suspend fun waitUntil(what: String, condition: () -> Boolean) {
        try { withTimeout(10_000) { while (!condition()) delay(20) } }
        catch (timeout: kotlinx.coroutines.TimeoutCancellationException) { throw AssertionError("timed out: $what", timeout) }
    }

    @Test fun l1ClosingATabAlsoInTheOldRowsTakesItOutOfThem() = withStores { compat, _ ->
        val rows = mutableMapOf(FtutterKeys.TABS to listOf(url("1"), url("2")), FtutterKeys.PROTECTED to listOf(url("1")))
        val writer = ftutterWriter(compat, rows)
        writer.open(url("1"))
        writer.close(listOf(url("1")))
        val snapshot = ftutterTabSnapshot(
            compat.tabs.first(), compat.loadTabViews(FTUTTER_TAB_PROFILE),
            rows[FtutterKeys.TABS].orEmpty(), rows[FtutterKeys.PROTECTED].orEmpty().toSet(), emptySet()
        )
        assertFalse(url("1") in snapshot.legacy, "the closed tab does not come back from the old row")
        assertFalse(url("1") in snapshot.protected)
        assertEquals(listOf(url("2")), rows[FtutterKeys.TABS], "another thread in the row stays")
    }

    @Test fun l2AnotherModesTabIsNotAFtutterOnlyTab() = withStores { compat, _ ->
        val other = SingleMode.tab(may, "1", 1)
        compat.openTab(other)
        val writer = ftutterWriter(compat)
        writer.open(url("2"))
        val opened = compat.tabs.first().single { it.threadNo == "2" }
        var ranks = compatTabTrimRanks(compat.loadTabViews(FTUTTER_TAB_PROFILE))
        assertEquals(1, ranks[other.key], "a tab another mode opened is not in the first group to go")
        assertEquals(0, ranks[opened.key], "a tab only this mode opened is")
        writer.move(listOf(url("1"), url("2")))
        ranks = compatTabTrimRanks(compat.loadTabViews(FTUTTER_TAB_PROFILE))
        assertEquals(1, ranks[other.key], "moving the tabs does not turn it into one")
        assertEquals(0, ranks[opened.key])
    }

    @Test fun l4ATabBroughtBackAtTheLimitStaysAndLeavesTheRecord() = withStores { compat, _ ->
        compat.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        (1..20).forEach { compat.openTab(SingleMode.tab(may, "$it", it.toLong())) }
        val oldest = SingleMode.tab(may, "1", 1)
        compat.closeTabs(setOf(oldest.key), now, closedBy = futacha)
        compat.openTab(SingleMode.tab(may, "21", 21))
        assertEquals(oldest.key, compat.popClosedTabBatch(futacha, now)?.tabs?.single()?.tab?.key)
        val tabs = compat.tabs.first()
        assertTrue(tabs.any { it.key == oldest.key }, "the tab brought back is not trimmed at once")
        assertTrue(tabs.size <= 20, "the limit holds: ${tabs.size}")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun l5TheComparisonRunsOffTheScreensThread() = withStores { compat, state ->
        val threads = CopyOnWriteArrayList<String>()
        val compatThreads = CopyOnWriteArrayList<String>()
        val observed = object : CompatibilityStore by compat {
            override val history = compat.history.onEach { compatThreads += Thread.currentThread().name }
        }
        val appState = MutableStateFlow<List<ThreadHistoryEntry>>(emptyList()).onEach { threads += Thread.currentThread().name }
        newSingleThreadContext("screen").use { screen ->
            coroutineScope {
                val sync = launch(screen) { synchronizeSharedHistory(state, observed, appStateHistory = appState, importDebounceMillis = 10) { _, _ -> } }
                try {
                    waitUntil("both directions read") { threads.isNotEmpty() && compatThreads.isNotEmpty() }
                } finally { sync.cancel() }
            }
        }
        assertFalse(threads.any { it.startsWith("screen") }, "$threads")
        assertFalse(compatThreads.any { it.startsWith("screen") }, "$compatThreads")
    }

    private suspend fun rowVisited(state: com.valoser.futacha.shared.state.AppStateStore, threadId: String, at: Long): List<ThreadHistoryEntry> {
        val context = FutachaThreadHistoryContext("スレ$threadId", url(threadId), 4, "")
        recordFutachaVisitedThread(state, state.history.first(), threadId, may, context, currentTimeMillis = at)
        return state.history.first()
    }

    @Test fun l6ACopyReadBeforeAClearAllAddsNothing() = withStores { compat, state ->
        val copy = rowVisited(state, "1", now - 5_000)
        compat.clearHistory()
        compat.importModernHistory(copy)
        assertTrue(compat.history.first().isEmpty(), "a row visited before the clear stays out")
        compat.importModernHistory(rowVisited(state, "2", now + 60_000))
        assertEquals(setOf("2"), compat.history.first().mapTo(HashSet()) { it.threadNo }, "a later visit comes in")
        compat.forgetHistoryTombstones(listOf(url("1")))
        compat.importModernHistory(copy)
        assertTrue(compat.history.first().any { it.threadNo == "1" }, "a row opened on purpose comes in even after the clock was set back")
    }

    @Test fun l7NoOldRowIsShownUntilTheMigrationRecordIsRead() {
        val ftutter = ftutterTabSnapshot(emptyList(), emptyList(), listOf(url("1")), setOf(url("1")), consumed = null)
        assertTrue(ftutter.legacy.isEmpty(), "a row already taken in cannot be told yet")
        assertTrue(ftutter.protected.isEmpty())
        val tutorial = FutaberTabKey(SingleMode.tutorial.id, "9")
        val row = listOf(FutaberTabKey(may.id, "1"), tutorial)
        assertEquals(listOf(tutorial), futaberTabSnapshot(emptyList(), emptyList(), row, consumed = null, SingleMode.boards).keys, "a row with no shared place cannot have been taken in")
    }

    @Test fun n2ATabTrimmedAtOnceTellsSo() = withStores { compat, _ ->
        (1..25).forEach { compat.openTab(SingleMode.tab(may, "$it", 2_000L + it)) }
        compat.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        val notices = mutableListOf<String>()
        var row = emptyList<FutaberTabKey>()
        val writer = FutaberStoreTabWriter(compat, { SingleMode.boards }, { emptyList() }, { row = it(row) }, { notices += it }, { 1_000L })
        val key = FutaberTabKey(may.id, "99")
        writer.apply(emptyList(), listOf(key))
        assertEquals(listOf(key), row, "the thread stays in this mode")
        assertEquals(listOf(FUTABER_TAB_TRIMMED_NOTICE), notices)
    }

    @Test fun n3OpeningInFtutterTakesTheDeletionTicket() {
        val source = File("src/commonMain/kotlin/ui/ftutter/FtutterApp.kt").readText()
        assertEquals(2, Regex("""historyDeletionTicket\(\)""").findAll(source).count(), "opening and adding a tab")
        assertEquals(2, Regex("""openedAtTicket = openedAtTicket""").findAll(source).count())
    }

    @Test fun r6_1MigrationDatesOnlyTheTabsOnlyThisModeHad() = withStores { compat, _ ->
        compat.openTab(SingleMode.tab(may, "1", 2_000L))
        ftutterAddLegacyTabs(compat, SingleMode.boards, emptyList(), listOf(url("1"), url("2")), emptySet(), 5_000L)
        val tabs = compat.tabs.first().associateBy { it.threadNo }
        val ranks = compatTabTrimRanks(compat.loadTabViews(FTUTTER_TAB_PROFILE))
        assertEquals(1, ranks[tabs.getValue("1").key], "ふたちゃ's tab is not only ふたったー風's")
        assertEquals(0, ranks[tabs.getValue("2").key], "a tab only this mode had goes first")
        assertEquals(listOf("1", "2"), ftutterAddedOrder(compat), "the bar's order is the old row's")
    }

    private suspend fun ftutterAddedOrder(compat: CompatibilityStore): List<String> {
        val byKey = compat.tabs.first().associateBy { it.key }
        return compat.loadTabViews(FTUTTER_TAB_PROFILE).sortedBy { it.ordinal }.map { byKey.getValue(it.tabKey).threadNo }
    }

    private val page = FtutterTabSeed("本文の題名", "https://may.2chan.net/b/thumb/1.jpg", 12)

    @Test fun r6_2ALinkOpenedTabGetsThePagesTitle() = withStores { compat, _ ->
        val writer = ftutterWriter(compat)
        writer.open(url("7"), FtutterTabSeed("", "", 0))
        assertEquals("(無題)", compat.tabs.first().single { it.threadNo == "7" }.title, "nothing can name it yet")
        writer.pageRead(url("7"), page)
        val tab = compat.tabs.first().single { it.threadNo == "7" }
        assertEquals("本文の題名", tab.title)
        assertEquals(page.thumbnailUrl, tab.thumbnailUrl)
        assertEquals(12, tab.replyCount)
        assertEquals(12, tab.checkedReplyCount, "what was just read is not new")
    }

    @Test fun r6_2APageReadBeforeTheTabIsWrittenStillNamesIt() = withStores { compat, _ ->
        val writer = ftutterWriter(compat)
        writer.pageRead(url("7"), page)
        writer.open(url("7"), FtutterTabSeed("", "", 0))
        assertEquals("本文の題名", compat.tabs.first().single { it.threadNo == "7" }.title)
    }

    @Test fun r6_2AReadPageKeepsWhatTheTabAlreadySays() = withStores { compat, _ ->
        compat.openTab(SingleMode.tab(may, "1", 2_000L, replyCount = 3).copy(thumbnailUrl = "https://may.2chan.net/b/thumb/old.jpg", checkedReplyCount = 1))
        ftutterWriter(compat).pageRead(url("1"), page)
        val tab = compat.tabs.first().single { it.threadNo == "1" }
        assertEquals("スレ1", tab.title)
        assertEquals("https://may.2chan.net/b/thumb/old.jpg", tab.thumbnailUrl)
        assertEquals(3, tab.replyCount)
        assertEquals(1, tab.checkedReplyCount, "the unread count is the other modes' to change")
    }

    @Test fun r6_2ThePageSaysTheTabsTitleAsTheHistoryDoes() {
        val posts = listOf(SingleMode.post("1", "本文の一行目"), SingleMode.post("2", "返信"))
        assertEquals(FtutterTabSeed("本文の一行目", "", 1), ftutterTabSeedFromPage("", posts))
        assertEquals("カタログの題名", ftutterTabSeedFromPage("カタログの題名", posts)?.title)
        assertEquals(null, ftutterTabSeedFromPage("", emptyList()))
        val source = File("src/commonMain/kotlin/ui/ftutter/FtutterApp.kt").readText()
        assertTrue("tabWriter.pageRead" in source, "the screen tells the writer")
    }
}
