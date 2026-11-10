package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.futaber.FutaberPreferenceKeys
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberTabsToStore
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterList
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.TimeSource

/** The shared tab trim on iOS's store (plan B4): the same rule as Android and desktop, and the time at the largest limit. */
class IosSharedTabTrimTest {
    private val board = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(board)
    private fun url(no: Int) = "${board}res/$no.htm"
    private fun tab(no: Int) = CompatTab(compatTabKey(url(no)), url(no), url(no), boardKey, "may", "$no", "スレ$no",
        insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())

    private fun withStore(block: suspend (IosCompatibilityStore) -> Unit) = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fs)
            store.initialize()
            store.upsertBoard(CompatBoard(boardKey, "may", board, board, 0))
            block(store)
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }

    /** Audit M1: the limit is not restored, so the restore raises it instead of closing what it just brought back. */
    @Test fun aRestoreRaisesTheLimitInsteadOfClosingTheTabs() = withStore { store ->
        val board = store.boards.first()
        val backup = CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = board, tabs = (1..300).map(::tab))
        store.importSettingsBackup(encodeCompatSettingsBackup(backup), restoreUserSettings = true, restoreNgRules = false)
        assertEquals(300, store.tabs.first().size)
        assertEquals("300", store.preferences.first()[COMPAT_SHARED_TAB_LIMIT_KEY])
    }

    /** Audit L4: a tab brought back at the limit is not trimmed at once, and the limit holds. */
    @Test fun aTabBroughtBackAtTheLimitStays() = withStore { store ->
        val futacha = ExperienceProfile.FUTACHA.persistedValue
        store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        (1..20).forEach { store.openTab(tab(it), null) }
        store.closeTabs(setOf(tab(1).key), 1_000L, closedBy = futacha)
        store.openTab(tab(21), null)
        assertEquals(tab(1).key, store.popClosedTabBatch(futacha, 1_000L)?.tabs?.single()?.tab?.key)
        val tabs = store.tabs.first()
        assertTrue(tabs.any { it.key == tab(1).key })
        assertTrue(tabs.size <= 20, "the limit holds: ${tabs.size}")
    }

    /** Audit L6: a copy of the history read before a clear of everything adds nothing, on iOS's store too. */
    @Test fun aCopyReadBeforeAClearAllAddsNothing() = withStore { store ->
        val visited = kotlin.time.Clock.System.now().toEpochMilliseconds() - 5_000
        val copy = listOf(ThreadHistoryEntry("1", "may", "スレ", "", "may", url(1), visited, 3))
        store.clearHistory()
        store.importModernHistory(copy)
        assertTrue(store.history.first().isEmpty())
        store.forgetHistoryTombstones(listOf(url(1)))
        store.importModernHistory(copy)
        assertEquals(1, store.history.first().size)
    }

    /** Plan B10: the settings backup carries each mode's marks, on iOS's store too. */
    @Test fun aBackupBringsBackEachModesMarks() = withStore { store ->
        val futaber = ExperienceProfile.FUTABER.persistedValue
        val ftutter = ExperienceProfile.FTUTTER.persistedValue
        store.openSharedTab(tab(1), futaber)
        store.openSharedTab(tab(2), ftutter)
        val payload = store.exportSettingsBackup()
        store.closeTabs(setOf(tab(1).key, tab(2).key), 10L, emptyMap(), null)
        assertTrue(store.loadTabViews(futaber).isEmpty())
        store.openSharedTab(tab(3), futaber)
        store.importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)
        assertEquals(listOf(tab(3).key, tab(1).key), store.loadTabViews(futaber).map { it.tabKey })
        assertEquals(listOf(tab(2).key), store.loadTabViews(ftutter).map { it.tabKey })
    }

    /** Plan B8b: no mode's old row exempts anything (both strips are views of the shared tabs); the held tab stays. */
    @Test fun theHeldOnesStayAndTheModesOldRowsExemptNothing() = withStore { store ->
        val listed = (1..195).map(::tab)
        assertEquals(195, store.addTabsIfAbsent(listed, emptyMap()))
        val summaries = store.boards.first().map { it.toBoardSummary() }
        val futaberRow = futaberTabsToStore((1..50).map { FutaberTabKey(summaries.single().id, "$it") }, summaries)
        val ftutterRow = encodeFtutterList((51..195).map(::url))
        store.savePreferences(mapOf(FutaberPreferenceKeys.TABS to futaberRow, FtutterKeys.TABS to ftutterRow))
        store.setScreenHeldTabs(ExperienceProfile.FUTABER, setOf(tab(1001).key))
        (1001..1110).forEach { store.openTab(tab(it), null) }
        val kept = store.tabs.first().map(CompatTab::key).toSet()
        assertTrue(tab(1001).key in kept, "the held tab stays")
        assertTrue(kept.size in 100..200, "within the limit: ${kept.size}")
        assertTrue(tab(1).key !in kept, "the oldest of ふたばー風's old row goes too")
        assertTrue(tab(51).key !in kept, "and of ふたったー風's")
        assertTrue((1100..1110).all { tab(it).key in kept }, "the newest stay")
        assertEquals(ftutterRow, store.preferences.first()[FtutterKeys.TABS], "the old rows are untouched")
        assertEquals(futaberRow, store.preferences.first()[FutaberPreferenceKeys.TABS])
    }

    @Test fun updatingOneTabAmong990StaysQuick() = withStore { store ->
        store.savePreferences(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "$COMPAT_SHARED_TAB_LIMIT_MAX"))
        assertEquals(990, store.addTabsIfAbsent((1..990).map(::tab), emptyMap()))
        val times = (1..20).map { round ->
            val mark = TimeSource.Monotonic.markNow()
            assertTrue(store.updateTabIfPresent(tab(500 + round).key) { it.copy(replyCount = round) })
            mark.elapsedNow().inWholeMilliseconds
        }.sorted()
        println("IosSharedTabTrimTest: updateTabIfPresent with 990 tabs, median ${times[times.size / 2]} ms, max ${times.last()} ms")
        assertEquals(990, store.tabs.first().size)
        // A loose bound: the measured number goes to the plan (tabs/tabViews split in B6 if it is slow).
        assertTrue(times[times.size / 2] < 2_000, "median ${times[times.size / 2]} ms")
    }
}
