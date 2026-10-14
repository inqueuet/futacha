package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.board.futachaArchiveReportLive
import com.valoser.futacha.shared.ui.board.viewedThreadArchiveReportDue
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The B4 (history and tabs) and B5 (patrol and the outside services) parity batches of the ふたばー風 mode (pure parts). */
class FutaberParityB45Test {
    private val may = BoardSummary("may-b", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val img = BoardSummary("img-b", "img/b", "", "https://img.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private val boards = listOf(may, img, tutorial)

    private fun history(board: BoardSummary, threadId: String, fallen: Boolean = false) = ThreadHistoryEntry(
        threadId = threadId, boardId = board.id, title = "題$threadId", titleImageUrl = "", boardName = board.name,
        boardUrl = futaberHistoryThreadUrl(board, threadId), lastVisitedEpochMillis = 1L, replyCount = 3,
        isAutoRefreshDisabled = fallen
    )

    // ---- settings ----

    @Test fun theNewExtensionsAreOffAndTheArchiveReportIsOnByDefault() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extHistoryArchive)
        assertFalse(defaults.extForegroundPatrol)
        assertFalse(defaults.extExternalWatcher)
        // The decision of 2026-10-12: the shared switch is on until someone turns it off.
        assertTrue(defaults.archiveReportEnabled)
        val on = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.EXT_HISTORY_ARCHIVE to "ON",
                FutaberSettingKeys.EXT_FOREGROUND_PATROL to "ON",
                FutaberSettingKeys.EXT_EXTERNAL_WATCHER to "ON"
            )
        )
        assertTrue(on.extHistoryArchive && on.extForegroundPatrol && on.extExternalWatcher)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.ARCHIVE_REPORT to "OFF")).archiveReportEnabled)
        assertTrue(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.ARCHIVE_REPORT to "ON")).archiveReportEnabled)
        // The same key as としあき(仮) and ふたちゃ.
        assertEquals("compat.archive_report.enabled", FutaberSettingKeys.ARCHIVE_REPORT)
    }

    // ---- W07: when a viewed thread is noted ----

    @Test fun aViewedThreadIsNotedOnlyForALiveReadWithTheSwitchOnAndNotOnTheDesktop() {
        assertTrue(viewedThreadArchiveReportDue(enabledPreference = null, liveFetch = true, desktop = false))
        assertTrue(viewedThreadArchiveReportDue(enabledPreference = "ON", liveFetch = true, desktop = false))
        assertFalse(viewedThreadArchiveReportDue(enabledPreference = "OFF", liveFetch = true, desktop = false))
        assertFalse(viewedThreadArchiveReportDue(enabledPreference = null, liveFetch = false, desktop = false))
        assertFalse(viewedThreadArchiveReportDue(enabledPreference = null, liveFetch = true, desktop = true))
    }

    @Test fun futachaCountsOnlyAPageReadFromTheBoardNowAsLive() {
        assertTrue(futachaArchiveReportLive(isCachedPage = false, isArchived = false, staticView = false, restoring = false))
        assertFalse(futachaArchiveReportLive(isCachedPage = true, isArchived = false, staticView = false, restoring = false))
        assertFalse(futachaArchiveReportLive(isCachedPage = false, isArchived = true, staticView = false, restoring = false))
        assertFalse(futachaArchiveReportLive(isCachedPage = false, isArchived = false, staticView = true, restoring = false))
        assertFalse(futachaArchiveReportLive(isCachedPage = false, isArchived = false, staticView = false, restoring = true))
    }

    // ---- H04 / 全件削除 ----

    @Test fun theImportMessageSaysWhereTheBodiesOpen() {
        val ok = futaberHistoryImportMessage("履歴アーカイブを読み込みました: 2件、追加1件、更新1件")
        assertTrue(ok.endsWith(FUTABER_HISTORY_IMPORT_NOTICE))
        assertEquals("インポートできる履歴アーカイブがありません", futaberHistoryImportMessage("インポートできる履歴アーカイブがありません"))
    }

    @Test fun theBandShowsOnlyTheChipsWhoseSwitchIsOn() = runBlocking {
        coroutineScope {
            val controller = FutaberHistoryController(this, AppStateStore(FakePlatformStateStorage()))
            val none = futaberHistoryTools(controller, clearEnabled = false, archiveEnabled = false)
            assertNull(none.onClearAll)
            assertNull(none.onOpenArchive)
            val both = futaberHistoryTools(controller, clearEnabled = true, archiveEnabled = true)
            assertNotNull(both.onClearAll).invoke()
            assertEquals(FutaberHistoryDialog.ConfirmClear, controller.dialog)
            assertNotNull(both.onOpenArchive).invoke()
            assertEquals(FutaberHistoryDialog.ArchiveChooser, controller.dialog)
            controller.dismiss()
            assertNull(controller.dialog)
        }
    }

    @Test fun clearingRemovesEveryHistoryRowAndTheOwnPostRecords() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(history(may, "100"), history(img, "200")))
        store.addSelfPostIdentifier("100", "101", may.id)
        val messages = mutableListOf<String>()
        coroutineScope {
            val controller = FutaberHistoryController(this, store)
            controller.onMessage = { messages += it }
            controller.openClear()
            controller.clearAll()
            assertNull(controller.dialog)
        }
        assertTrue(store.history.first().isEmpty())
        assertTrue(store.selfPostIdentifiersByThread.first().isEmpty())
        assertEquals(listOf(FUTABER_HISTORY_CLEARED_NOTICE), messages)
    }

    // ---- H08 ----

    @Test fun theTabCheckTakesTheTabsWithAHistoryRowOnARealBoardThatHaveNotFallen() {
        val rows = listOf(history(may, "1"), history(img, "2", fallen = true), history(tutorial, "3"), history(may, "4"))
        val tabs = listOf(
            FutaberTabKey(may.id, "1"),
            FutaberTabKey(img.id, "2"),        // already fallen
            FutaberTabKey(tutorial.id, "3"),   // the tutorial board is read from the bundled data
            FutaberTabKey(may.id, "9"),        // no history row
            FutaberTabKey("gone", "4"),        // the board was removed
            FutaberTabKey(may.id, "1")         // twice
        )
        assertEquals(listOf("1"), futaberTabCheckTargets(tabs, rows, boards).map { it.threadId })
    }

    @Test fun theOutcomeCountsTheThreadsTheCheckFoundFallen() {
        val targets = listOf(history(may, "1"), history(may, "2"), history(img, "3"))
        val after = listOf(history(may, "1", fallen = true), history(may, "2"), history(img, "3", fallen = true))
        val outcome = futaberTabCheckOutcome(targets, after, failed = 1)
        assertEquals(FutaberTabCheckOutcome(checked = 3, newlyFallen = 2, failed = 1), outcome)
        assertEquals("タブを3件確認しました（落ちたスレ 2件）。1件は確認できませんでした", futaberTabCheckMessage(outcome))
        assertEquals("タブを1件確認しました（落ちたスレはありません）", futaberTabCheckMessage(FutaberTabCheckOutcome(1, 0, 0)))
    }

    @Test fun theTabCheckMarksAFallenThreadInTheSharedHistoryWithItsOwnRefresher() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val alive = history(may, "10")
        val gone = history(may, "20")
        val other = history(img, "30") // not a tab: the check leaves it alone
        store.setHistory(listOf(alive, gone, other))
        val fetched = mutableListOf<String>()
        val repository = object : BoardRepository by FakeBoardRepository() {
            // The delegate's own default would call the delegate's getThread, not this one.
            override suspend fun getThreadIfModified(
                board: String,
                threadId: String,
                validators: com.valoser.futacha.shared.network.HttpConditionalValidators?
            ) = com.valoser.futacha.shared.repo.ConditionalThreadFetchResult.Modified(getThread(board, threadId), null)

            override suspend fun getThread(board: String, threadId: String): ThreadPage {
                fetched += threadId
                if (threadId == "20") throw NetworkException("gone", statusCode = 404)
                return ThreadPage(threadId, "題$threadId", null, null, (1..5).map { index ->
                    Post("${threadId}$index", index - 1, "としあき", null, "10/12", messageHtml = "本文$index", imageUrl = null, thumbnailUrl = null)
                })
            }
        }
        val refresher = HistoryRefresher(
            stateStore = store, repository = repository, dispatcher = Dispatchers.Default,
            maxConcurrency = 1, cursorNamespace = FUTABER_TAB_CHECK_CURSOR_NAMESPACE
        )
        val messages = mutableListOf<String>()
        coroutineScope {
            val checker = FutaberTabChecker(this, store, refresher)
            val targets = futaberTabCheckTargets(listOf(FutaberTabKey(may.id, "10"), FutaberTabKey(may.id, "20")), store.history.first(), boards)
            checker.check(targets, boards) { messages += it }
            assertTrue(checker.running)
        }
        assertEquals(setOf("10", "20"), fetched.toSet())
        val after = store.history.first()
        assertTrue(after.single { it.threadId == "20" }.isAutoRefreshDisabled)
        assertFalse(after.single { it.threadId == "10" }.isAutoRefreshDisabled)
        assertFalse(after.single { it.threadId == "30" }.isAutoRefreshDisabled)
        assertEquals(FUTABER_TAB_CHECK_RUNNING_NOTICE, messages.first())
        assertEquals("タブを2件確認しました（落ちたスレ 1件）", messages.last())
    }

    @Test fun theTabCheckSaysWhenThereIsNothingToCheck() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val messages = mutableListOf<String>()
        coroutineScope {
            val checker = FutaberTabChecker(
                this, store, HistoryRefresher(store, FakeBoardRepository(), Dispatchers.Default, cursorNamespace = FUTABER_TAB_CHECK_CURSOR_NAMESPACE)
            )
            checker.check(emptyList(), boards) { messages += it }
            assertFalse(checker.running)
        }
        assertEquals(listOf(FUTABER_TAB_CHECK_NONE_NOTICE), messages)
    }

    // ---- W06 ----

    @Test fun anExternalWatcherRowBecomesANotificationThatOpensOnItsBoard() {
        val entry = CompatExternalWatcherEntry(
            key = "nijilog-1", active = false, title = "スレ題", replyCount = 42,
            threadUrl = "https://may.2chan.net/b/res/1234.htm", thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg",
            extractedKeyword = " キーワード ", boardName = "虹裏may", updatedAtEpochMillis = 0L, insertedAtEpochMillis = 5L
        )
        val result = assertNotNull(futaberWatchResultFromExternal(entry))
        assertEquals("キーワード", result.keyword)
        assertFalse(result.active)
        assertEquals(5L, result.insertedAtEpochMillis)
        assertEquals(5L, result.history.contentUpdatedAtEpochMillis)
        assertEquals(futaberCompatBoardKey(may), result.history.boardKey)
        val ref = assertNotNull(futaberRefForWatchResult(result, boards))
        assertEquals(may.id, ref.boardId)
        assertEquals("1234", ref.threadId)
        assertEquals(42, ref.replyCount)
        // An address that is not an official thread is left out; the rows keep the key にじろぐ deletes them by.
        val other = entry.copy(key = "nijilog-2", threadUrl = "https://example.com/res/1.htm")
        assertNull(futaberWatchResultFromExternal(other))
        val rows = futaberExternalNotificationRows(listOf(entry, other))
        assertEquals(listOf("nijilog-1"), rows.map { it.second })
        assertEquals("「キーワード」  虹裏may（落ちました）", futaberWatchSubtitle(rows.single().first, openable = true))
    }

    @Test fun thePatrolNoticeNamesWhereTheResultsAre() {
        assertEquals("巡回で3件のスレッドが見つかりました（管理画面の「通知」）", futaberPatrolMatchNotice(3))
    }
}
