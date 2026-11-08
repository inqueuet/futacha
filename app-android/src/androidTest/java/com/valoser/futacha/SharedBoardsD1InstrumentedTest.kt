@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** D1 on the Android store (D-B1): see [aRestoredBoardAndItsRowsSurviveTheBoardSynchronizationOfEveryMode]. */
@RunWith(AndroidJUnit4::class)
class SharedBoardsD1InstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * D1 (D-B1, docs/futaber-evolution-data-2026-10-31.md §4.1): a board restored into this store (a settings backup, the
     * old toshiaki APK's files), with what hangs under it, survives the board synchronization of the AppState modes and
     * of としあき(仮), with this store's real cascade. The AppState modes' bridge used to delete it (and, by the foreign
     * keys, its tab, history, board NG rule and new-thread draft) because the AppState list did not have it. A deletion
     * in the mode's own list still takes it from both.
     */
    @Test
    fun aRestoredBoardAndItsRowsSurviveTheBoardSynchronizationOfEveryMode() = runBlocking {
        for (usesAppStateData in listOf(true, false)) {
            for (legacy in listOf(false, true)) checkRestoredBoardSurvives(usesAppStateData, legacy)
        }
    }

    private suspend fun checkRestoredBoardSurvives(usesAppStateData: Boolean, legacy: Boolean) = coroutineScope {
        val name = "compat_d1_${System.nanoTime()}.db"
        val compat = AndroidCompatibilityStore(context, databaseName = name)
        val state = com.valoser.futacha.shared.state.AppStateStore(com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        val label = "appState=$usesAppStateData legacy=$legacy"
        val may = com.valoser.futacha.shared.model.BoardSummary("b", "may", "", "https://may.2chan.net/b/futaba.php", "")
        val imgUrl = "https://img.2chan.net/b/"
        val imgKey = compatBoardKey(imgUrl)
        val thread = "${imgUrl}res/200.htm"
        val tab = CompatTab(compatTabKey(thread), thread, thread, imgKey, "img", "200", "スレ",
            insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        suspend fun synchronizeUntil(timeoutMillis: Long = 10_000, done: suspend () -> Boolean): Boolean {
            val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                com.valoser.futacha.shared.compat.bridgeProfileBoardsAndHistory(state, compat, usesAppStateData) { _, error -> failures += error }
            }
            val reached = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                while (!done()) kotlinx.coroutines.delay(20)
                kotlinx.coroutines.delay(150)
                true
            } ?: false
            job.cancelAndJoin()
            assertTrue("$label: ${failures.toList()}", failures.isEmpty())
            return reached
        }
        try {
            compat.initialize()
            state.setBoards(listOf(may))
            compat.upsertBoards(com.valoser.futacha.shared.compat.modernBoardsToCompatibility(listOf(may)))
            synchronizeUntil(timeoutMillis = 500) { false }
            if (legacy) {
                com.valoser.futacha.shared.ui.compat.importCompatLegacyBackupData(
                    compat,
                    listOf(
                        com.valoser.futacha.shared.compat.CompatLegacyBackupData(
                            fileType = "keyword",
                            boards = listOf(com.valoser.futacha.shared.compat.CompatLegacyBoardRecord("img", imgUrl, imgUrl, 0)),
                            catalogNgWords = listOf(com.valoser.futacha.shared.compat.CompatLegacyScopedWord(imgUrl, "荒らし"))
                        )
                    ),
                    compat.boards.first()
                )
                compat.openTab(tab)
            } else {
                compat.importSettingsBackup(
                    encodeCompatSettingsBackup(
                        com.valoser.futacha.shared.compat.CompatSettingsBackup(
                            exportedAtEpochMillis = 1,
                            boards = listOf(CompatBoard(imgKey, "img", imgUrl, imgUrl, 0)),
                            tabs = listOf(tab),
                            ngRules = listOf(
                                CompatNgRule(
                                    com.valoser.futacha.shared.compat.compatNgRuleId(CompatNgKind.CATALOG_WORD, imgKey, "荒らし"),
                                    CompatNgKind.CATALOG_WORD, imgKey, "荒らし", 2
                                )
                            ),
                            workspace = null
                        )
                    )
                )
            }
            compat.saveBuildDraft(com.valoser.futacha.shared.compat.CompatBuildDraft(imgKey, comment = "下書き", updatedAtEpochMillis = 3))
            assertTrue(label, synchronizeUntil { state.boards.first().any { it.url.startsWith(imgUrl) || it.id == imgKey } })
            val lost = buildList {
                if (compat.boards.first().none { it.key == imgKey }) add("the board")
                if (compat.tabs.first().none { it.key == tab.key }) add("its tab")
                if (compat.ngRules.first().none { it.scopeKey == imgKey }) add("its NG rule")
                if (compat.loadBuildDraft(imgKey)?.comment != "下書き") add("its draft")
            }
            assertEquals(label, emptyList<String>(), lost)
            if (usesAppStateData) {
                state.updateBoards { boards -> boards.filterNot { it.url.startsWith(imgUrl) || it.id == imgKey } }
            } else {
                compat.deleteBoard(imgKey)
            }
            assertTrue(label, synchronizeUntil {
                state.boards.first().none { it.url.startsWith(imgUrl) || it.id == imgKey } && compat.boards.first().none { it.key == imgKey }
            })
            synchronizeUntil(timeoutMillis = 500) { false }
            assertTrue("$label: stays deleted", state.boards.first().none { it.url.startsWith(imgUrl) || it.id == imgKey })
            assertTrue("$label: its tab went with it", compat.tabs.first().none { it.boardKey == imgKey })
        } finally {
            state.close()
            compat.closeForTest()
            context.deleteDatabase(name)
        }
    }
}
