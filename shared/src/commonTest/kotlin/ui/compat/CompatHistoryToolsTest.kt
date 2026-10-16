package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatOwnPostPreferencePrefix
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.ui.board.HistoryLifeFilter
import com.valoser.futacha.shared.ui.board.HistorySortDirection
import com.valoser.futacha.shared.ui.board.HistorySortOption
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatHistoryToolsTest {
    private val boardUrl = "https://may.2chan.net/b/"

    private fun compat(no: String, title: String, visited: Long, replies: Int = 1) = CompatHistoryEntry(
        canonicalUrl = "${boardUrl}res/$no.htm",
        originalUrl = "${boardUrl}res/$no.htm",
        boardKey = compatBoardKey(boardUrl),
        boardName = "mayb",
        threadNo = no,
        title = title,
        replyCount = replies,
        contentUpdatedAtEpochMillis = visited,
        lastVisitedEpochMillis = visited
    )

    private fun shared(no: String, dead: Boolean = false, saved: Boolean = false, alive: Long? = null) = ThreadHistoryEntry(
        threadId = no,
        boardId = "may",
        title = "AppState title $no",
        titleImageUrl = "",
        boardName = "AppState board",
        // ふたちゃ stores the thread's own URL here.
        boardUrl = "${boardUrl}res/$no.htm",
        lastVisitedEpochMillis = 1L,
        replyCount = 999,
        isAutoRefreshDisabled = dead,
        hasAutoSave = saved,
        lastConfirmedAliveEpochMillis = alive
    )

    @Test
    fun rowsKeepThisModesTextAndTakeTheStateOfTheMatchingAppStateRow() {
        val histories = listOf(compat("100", "alpha", 30), compat("200", "beta", 20), compat("300", "gamma", 10))
        val appState = listOf(shared("100", dead = true), shared("200", saved = true, alive = 5L))
        val own = setOf(compatTabKey("${boardUrl}res/300.htm"))
        val rows = buildCompatHistoryToolRows(histories, appState, own)

        assertEquals(histories, rows.map { it.entry })
        assertEquals(listOf("alpha", "beta", "gamma"), rows.map { it.view.title })
        assertEquals(listOf(1, 1, 1), rows.map { it.view.replyCount })
        assertEquals(listOf(true, false, false), rows.map { it.view.isAutoRefreshDisabled })
        assertEquals(listOf(false, true, false), rows.map { it.view.hasAutoSave })
        assertEquals(listOf(null, 5L, null), rows.map { it.view.lastConfirmedAliveEpochMillis })
        assertEquals(listOf(false, false, true), rows.map { it.view.hasSelfPost })
    }

    @Test
    fun anOwnPostMarkerOfTheThreadMarksItAsWrittenIn() {
        val url = "${boardUrl}res/400.htm"
        val preferences = mapOf(
            compatOwnPostPreferencePrefix(compatTabKey(url)) + "401" to "1",
            "compat.control.controlHistoryTools" to "ON"
        )
        val keys = compatOwnPostTabKeys(preferences)
        assertEquals(setOf(compatTabKey(url)), keys)
        val row = buildCompatHistoryToolRows(listOf(compat("400", "own", 1)), emptyList(), keys).single()
        assertTrue(row.view.hasSelfPost)
    }

    @Test
    fun theMarksAreTheFutaberWordsForTheMatchedState() {
        val rows = buildCompatHistoryToolRows(
            listOf(compat("100", "a", 1), compat("200", "b", 1)),
            listOf(shared("100", dead = true, saved = true)),
            setOf(compatTabKey("${boardUrl}res/100.htm"))
        )
        assertEquals("落ちた・書き込み済み・保存済み", com.valoser.futacha.shared.ui.futaber.futaberHistoryMarks(rows[0].view))
        assertEquals("", com.valoser.futacha.shared.ui.futaber.futaberHistoryMarks(rows[1].view))
    }

    @Test
    fun theDefaultSettingsKeepTheDrawerOrderAndFiltersApplyBeforeTheDisplayLimit() {
        val histories = (1..200).map { compat("${1000 + it}", if (it % 2 == 0) "even $it" else "odd $it", it.toLong()) }
        val rows = buildCompatHistoryToolRows(histories, emptyList(), emptySet())
        assertEquals(histories, applyCompatHistoryTools(rows, HistoryViewSettings.Default).map { it.entry })

        // The query finds rows past the first hundred (the drawer shows 100 of the filtered list).
        val filtered = applyCompatHistoryTools(rows, HistoryViewSettings(titleQuery = "odd 19"))
        assertTrue(filtered.any { it.entry.title == "odd 199" })
        assertTrue(filtered.all { it.entry.title.startsWith("odd 19") })

        val byTitle = applyCompatHistoryTools(
            buildCompatHistoryToolRows(listOf(compat("1", "b", 1), compat("2", "a", 2), compat("3", "c", 3)), emptyList(), emptySet()),
            HistoryViewSettings(sortOption = HistorySortOption.Title, sortDirection = HistorySortDirection.Ascending)
        )
        assertEquals(listOf("a", "b", "c"), byTitle.map { it.entry.title })
    }

    @Test
    fun theLifeFilterUsesTheAppStateState() {
        val rows = buildCompatHistoryToolRows(
            listOf(compat("100", "a", 1), compat("200", "b", 1)),
            listOf(shared("100", dead = true)),
            emptySet()
        )
        assertEquals(listOf("a"), applyCompatHistoryTools(rows, HistoryViewSettings(lifeFilter = HistoryLifeFilter.Expired)).map { it.entry.title })
        assertEquals(listOf("b"), applyCompatHistoryTools(rows, HistoryViewSettings(lifeFilter = HistoryLifeFilter.Unknown)).map { it.entry.title })
    }

    @Test
    fun theTwoDatesThisModeDoesNotKeepAreNotOfferedAndFallBackToTheVisitTime() {
        assertEquals(
            listOf(HistorySortOption.LastVisited, HistorySortOption.Title, HistorySortOption.ReplyCount),
            COMPAT_HISTORY_SORT_OPTIONS
        )
        assertEquals(HistorySortOption.LastVisited, HistoryViewSettings(sortOption = HistorySortOption.LastSelfPost).compatSupported().sortOption)
        assertEquals(HistorySortOption.LastVisited, HistoryViewSettings(sortOption = HistorySortOption.LastConfirmedAlive).compatSupported().sortOption)
        assertEquals(HistorySortOption.ReplyCount, HistoryViewSettings(sortOption = HistorySortOption.ReplyCount).compatSupported().sortOption)
        // ふたちゃ's sheet still offers all five by default.
        assertEquals(5, HistorySortOption.entries.size)
    }

    @Test
    fun theLongPressMenuIsUnchangedWithTheToolsOff() {
        assertEquals(listOf("削除する", "全て削除する"), compatHistoryContextChoices(toolsAvailable = false, refreshAvailable = true))
        assertEquals(
            listOf("削除する", "全て削除する", "絞り込み・並べ替え…", "履歴のエクスポート／インポート…", "履歴を一括更新"),
            compatHistoryContextChoices(toolsAvailable = true, refreshAvailable = true)
        )
        assertFalse("履歴を一括更新" in compatHistoryContextChoices(toolsAvailable = true, refreshAvailable = false))
    }

    @Test
    fun theSwitchIsOffUnlessStoredOn() {
        assertFalse(compatHistoryToolsEnabled(emptyMap()))
        assertFalse(compatHistoryToolsEnabled(mapOf(COMPAT_HISTORY_TOOLS_STORAGE_KEY to "OFF")))
        assertTrue(compatHistoryToolsEnabled(mapOf(COMPAT_HISTORY_TOOLS_STORAGE_KEY to "ON")))
        // Loaded before the own-post markers (rows are read in key order up to a limit).
        assertTrue(COMPAT_HISTORY_TOOLS_STORAGE_KEY < "compat.ownpost.")
        assertTrue(COMPAT_COMMON_NG_APPLY_STORAGE_KEY < "compat.ownpost.")
    }

    @Test
    fun theControlPageGetsTheTwoSwitchesOnlyWithASharedStoreAndOutsideFutachasPresentation() {
        assertEquals(compatSettingsGroups("control", false), compatSettingsGroups("control", false, includeAppStateEntries = false))
        // ふたちゃ's presentation never gets the two switches; its only addition is its own draft switch (batch 4).
        assertEquals(
            compatSettingsGroups("control", true).map { (group, entries) ->
                group to if (group == "ふたちゃ拡張") entries + futachaDraftPersistEntry() else entries
            },
            compatSettingsGroups("control", true, includeAppStateEntries = true)
        )
        assertEquals(compatSettingsGroups("thread", false), compatSettingsGroups("thread", false, includeAppStateEntries = true))
        val groups = compatSettingsGroups("control", false, includeAppStateEntries = true)
        assertEquals(compatSettingsGroups("control", false).map { it.first }, groups.map { it.first })
        val extension = groups.first { it.first == "ふたちゃ拡張" }.second
        assertEquals(
            listOf("controlHistoryTools", "controlCommonNgApply"),
            extension.takeLast(2).map { it.preferenceKey }
        )
        extension.takeLast(2).forEach { entry ->
            assertEquals("OFF", entry.summary)
            assertTrue(compatIsBooleanPreference("control", entry))
            assertTrue(compatBooleanPreferenceSummary(entry.preferenceKey).isNotEmpty())
        }
        assertEquals(
            "ONにすると、ふたちゃ・ふたばー風で登録したNGワードでもレスやスレが隠れます",
            compatBooleanPreferenceSummary("controlCommonNgApply")
        )
    }

    @Test
    fun importMessagesSayWhatComesBackInThisMode() {
        val done = compatHistoryImportMessage("履歴アーカイブを読み込みました: 3件、追加1件、更新2件")
        assertTrue(done.contains("としあき(仮)では履歴の一覧だけが戻り、保存した本文はふたちゃで開けます"))
        assertTrue(done.contains("としあき(仮)の履歴は新しい順に200件まで保存され、ドロワーには100件まで表示されます"))
        assertEquals("インポートできる履歴アーカイブがありません", compatHistoryImportMessage("インポートできる履歴アーカイブがありません"))
    }

    @Test
    fun theRefreshMessageCountsOnlyThisRunsFailures() {
        assertEquals("履歴を更新しました", compatHistoryRefreshResultMessage(null, 100L))
        val old = HistoryRefresher.RefreshError(errorCount = 2, totalThreads = 5, timestamp = 50L, errors = emptyList())
        assertEquals("履歴を更新しました", compatHistoryRefreshResultMessage(old, 100L))
        val now = old.copy(timestamp = 150L)
        assertEquals("履歴を更新しました（5件中2件は取得できませんでした）", compatHistoryRefreshResultMessage(now, 100L))
    }

    /** 監査M22：裏で作るドロワーの行は、行の対応・絞り込み・印・件数とも以前（画面の中で作っていた）と同じ。 */
    @Test
    fun theToolsViewBuiltOffTheMainThreadMatchesTheRows() {
        val histories = (1..300).map { compat("${1000 + it}", if (it % 3 == 0) "ねこ$it" else "犬$it", visited = it.toLong(), replies = it) }
        val shared = (1..300 step 2).map { shared("${1000 + it}", dead = it % 4 == 1, saved = it % 5 == 0) }
        val settings = com.valoser.futacha.shared.ui.board.HistoryViewSettings(titleQuery = "ねこ")
        val view = buildCompatHistoryToolsView(histories, shared, emptySet(), settings)
        val rows = buildCompatHistoryToolRows(histories, shared, emptySet())
        assertEquals(applyCompatHistoryTools(rows, settings).map { it.entry }, view.entries)
        assertEquals(300, view.totalCount)
        assertEquals(
            rows.associate { it.entry.canonicalUrl to com.valoser.futacha.shared.ui.futaber.futaberHistoryMarks(it.view) }.filterValues(String::isNotEmpty),
            view.marks
        )
        assertEquals(true, view.entries.all { it.title.startsWith("ねこ") })
    }

    /** Re-audit L13: a long build of the drawer's rows checks for cancellation, so it gives way when its inputs change. */
    @Test
    fun aLongBuildOfTheRowsGivesWayWhenCancelled() {
        val histories = (1..1_000).map { compat("$it", "スレ$it", visited = it.toLong()) }
        var checks = 0
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            buildCompatHistoryToolRows(histories, emptyList(), emptySet()) {
                checks++
                if (checks > 1) throw kotlinx.coroutines.CancellationException("inputs changed")
            }
        }
        kotlin.test.assertEquals(2, checks)
    }
}
