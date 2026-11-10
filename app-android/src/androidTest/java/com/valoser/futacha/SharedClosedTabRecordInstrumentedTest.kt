package com.valoser.futacha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.COMPAT_UNDO_NOTICE_MILLIS
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatTabPlacement
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The shared closed-tab record (plan B6) in Android's SQLite store: 20 records, the marks' cascade and their return. */
@RunWith(AndroidJUnit4::class)
class SharedClosedTabRecordInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_closed_record_${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "may", boardUrl, boardUrl, 0)
    private val fileSystem = createFileSystem(context)
    private val futaber = ExperienceProfile.FUTABER.persistedValue
    private val ftutter = ExperienceProfile.FTUTTER.persistedValue
    private val futacha = ExperienceProfile.FUTACHA.persistedValue
    private val now = System.currentTimeMillis()
    private lateinit var store: AndroidCompatibilityStore

    private fun url(no: Int) = "${boardUrl}res/$no.htm"
    private fun key(no: Int) = compatTabKey(url(no))
    private fun tab(no: Int, favorite: Boolean = false) =
        CompatTab(key(no), url(no), url(no), boardKey, "may", "$no", "スレ$no", favorite = favorite, insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())

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

    @Test fun twentyRecordsOutliveTheNoticeAndARelaunch() = runBlocking {
        (1..25).forEach { no ->
            store.openTab(tab(no))
            assertNotNull(store.closeTabs(setOf(key(no)), now + no, closedBy = futacha))
        }
        assertEquals(20, store.countClosedTabBatches(futacha, now + 30))
        assertNull(store.loadPendingClosedTabs(now + 25 + COMPAT_UNDO_NOTICE_MILLIS))
        store.closeForTest()
        store = newStore()
        store.initialize()
        assertEquals(20, store.countClosedTabBatches(futacha, now + 60_000))
        assertEquals(key(25), store.popClosedTabBatch(futacha, now + 60_000)?.tabs?.single()?.tab?.key)
        assertTrue(store.tabs.first().any { it.key == key(25) })
        assertEquals(19, store.countClosedTabBatches(futacha, now + 60_000))
    }

    @Test fun theMarksGoWithTheTabAndComeBackWithIt() = runBlocking {
        (1..3).forEach { store.openTab(tab(it, favorite = it == 2)) }
        store.replaceTabViews(futaber, (1..3).map { CompatSharedTabView(key(it), futaber, it - 1, 5L) })
        assertEquals(3, store.tabViews.first().size)
        store.closeTabs(setOf(key(2)), now, closedBy = ftutter)
        assertEquals(listOf(key(1), key(3)), store.loadTabViews(futaber).map { it.tabKey })
        assertEquals(listOf(key(1), key(3)), store.tabViews.first().sortedBy { it.ordinal }.map { it.tabKey })
        assertNull(store.popClosedTabBatch(ExperienceProfile.TOSHIAKI_COMPAT.persistedValue, now + 1))
        assertNotNull(store.popClosedTabBatch(futaber, now + 1))
        assertEquals(listOf(key(1), key(2), key(3)), store.loadTabViews(futaber).map { it.tabKey })
        assertFalse("ふたったー風's close drops the star", store.tabs.first().single { it.key == key(2) }.favorite)
    }

    @Test fun openingASharedTabKeepsTheStarAndAddsTheMark() = runBlocking {
        assertTrue(store.openSharedTab(tab(1, favorite = true), futaber))
        assertTrue(store.openSharedTab(tab(2), futaber, CompatTabPlacement.FIRST, select = true))
        assertTrue(store.openSharedTab(tab(1).copy(insertedAtEpochMillis = 99), ftutter))
        val one = store.tabs.first().single { it.key == key(1) }
        assertTrue(one.favorite)
        assertEquals(1L, one.insertedAtEpochMillis)
        assertEquals(listOf(key(2), key(1)), store.loadTabViews(futaber).map { it.tabKey })
        assertEquals(listOf(key(1)), store.loadTabViews(ftutter).map { it.tabKey })
        assertEquals(key(2), store.workspace.first().activeTabKey)
    }
}
