package com.valoser.futacha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The shared tab limit in Android's SQLite store (audits of 2026-11-09, rounds 4 and 5). */
@RunWith(AndroidJUnit4::class)
class SharedTabLimitInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_tab_limit_${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "may", boardUrl, boardUrl, 0)
    private val fileSystem = createFileSystem(context)
    private lateinit var store: AndroidCompatibilityStore

    private fun url(no: Int) = "${boardUrl}res/$no.htm"
    private fun key(no: Int) = compatTabKey(url(no))
    private fun tab(no: Int) =
        CompatTab(key(no), url(no), url(no), boardKey, "may", "$no", "スレ$no", insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())

    private fun newStore() = AndroidCompatibilityStore(context, attachmentFileSystem = fileSystem, databaseName = databaseName)

    @Before fun prepare() = runBlocking {
        store = newStore()
        store.initialize()
        store.upsertBoard(board)
    }

    @After fun cleanUp() {
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    /** L3: as on iOS and desktop, a limit lowered earlier is applied at the next start. */
    @Test fun aLoweredLimitTrimsAtTheNextStart() = runBlocking {
        (1..30).forEach { store.openTab(tab(it)) }
        store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        assertEquals(30, store.tabs.first().size)
        store.closeForTest()
        store = newStore()
        store.initialize()
        val tabs = store.tabs.first()
        assertTrue("trimmed at the start: ${tabs.size}", tabs.size <= 20)
        assertTrue("the newest stays", tabs.any { it.key == key(30) })
    }

    /** L4: a tab brought back is not trimmed at once and the limit holds, as on iOS and desktop. */
    @Test fun aTabBroughtBackAtTheLimitStays() = runBlocking {
        val futacha = ExperienceProfile.FUTACHA.persistedValue
        store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        (1..20).forEach { store.openTab(tab(it)) }
        val now = System.currentTimeMillis()
        store.closeTabs(setOf(key(1)), now, closedBy = futacha)
        store.openTab(tab(21))
        assertEquals(key(1), store.popClosedTabBatch(futacha, now)?.tabs?.single()?.tab?.key)
        val tabs = store.tabs.first()
        assertTrue(tabs.any { it.key == key(1) })
        assertTrue("the limit holds: ${tabs.size}", tabs.size <= 20)
    }

    /** L6: a copy of the history read before a clear of everything adds nothing; opening the thread lifts it. */
    @Test fun aCopyReadBeforeAClearAllAddsNothing() = runBlocking {
        val copy = listOf(ThreadHistoryEntry("1", "may", "スレ", "", "may", url(1), System.currentTimeMillis() - 5_000, 3))
        store.clearHistory()
        store.importModernHistory(copy)
        assertTrue(store.history.first().isEmpty())
        store.forgetHistoryTombstones(listOf(url(1)))
        store.importModernHistory(copy)
        assertEquals(1, store.history.first().size)
    }

    /** M1: the limit is not restored, so the restore raises it instead of closing what it just brought back. */
    @Test fun aRestoreRaisesTheLimitInsteadOfClosingTheTabs() = runBlocking {
        val backup = CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = listOf(board), tabs = (1..300).map(::tab))
        store.importSettingsBackup(encodeCompatSettingsBackup(backup), restoreUserSettings = true, restoreNgRules = false)
        assertEquals(300, store.tabs.first().size)
        assertEquals("300", store.preferences.first()[COMPAT_SHARED_TAB_LIMIT_KEY])
    }
}
