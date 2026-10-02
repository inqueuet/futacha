package com.valoser.futacha

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.compat.CompatibilityDatabaseSchema
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.compatBoardDefaultNamePreferenceKey
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatWatchRules
import com.valoser.futacha.shared.compat.encodeValidCompatWatchRules
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** G-16 / G-17 / M-5 of the 2026-10-02 round-3 audit. */
@RunWith(AndroidJUnit4::class)
class CompatibilityStoreRepairInstrumentedTest {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()
    private val stores = mutableListOf<AndroidCompatibilityStore>()
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() = runBlocking {
        stores.forEach { it.closeForTest() }
        databaseNames.forEach(context::deleteDatabase)
    }

    private fun newDatabaseName(): String =
        "compat_repair_${System.nanoTime()}.db".also(databaseNames::add)

    private suspend fun openStore(name: String): AndroidCompatibilityStore =
        AndroidCompatibilityStore(context, databaseName = name).also { store ->
            stores += store
            store.initialize()
        }

    @Test
    fun boardUrlRepairKeepsNewThreadDraftAndDroppedRecords() = runBlocking {
        val name = newDatabaseName()
        val legacyKey = "legacy-futaba-php-board"
        val legacyUrl = "https://may.2chan.net/b/futaba.php"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            (CompatibilityDatabaseSchema.createStatements + CompatibilityDatabaseSchema.initialWorkspaceStatement)
                .forEach(db::execSQL)
            db.version = CompatibilityDatabaseSchema.version
            val board = "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)"
            db.execSQL(board, arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0))
            db.execSQL(board, arrayOf<Any?>(legacyKey, "legacy", legacyUrl, legacyUrl, 1))
            val draft = "INSERT INTO compat_build_draft(board_key,name,email,subject,comment,attachment_uri,delete_key,updated_at) VALUES(?,?,?,?,?,?,?,?)"
            db.execSQL(draft, arrayOf<Any?>(boardKey, "", "", "古い", "old", null, "k", 100L))
            db.execSQL(draft, arrayOf<Any?>(legacyKey, "", "", "新しい", "new", null, "k", 200L))
            db.execSQL(
                "INSERT INTO compat_catalog_dropped(board_key,thread_id,item_json,dropped_at,last_seen_at,drop_class,inserted_at) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any?>(legacyKey, "123", "{}", 10L, 10L, "DIE", 10L)
            )
            // P4-2: board keys stored inside settings.
            val preference = "INSERT INTO compat_preference(key,value_json) VALUES(?,?)"
            db.execSQL(preference, arrayOf<Any?>(COMPAT_WATCH_RULES_KEY, encodeValidCompatWatchRules(listOf(CompatWatchRule("猫", boardKey = legacyKey)))))
            db.execSQL(preference, arrayOf<Any?>(compatBoardDefaultNamePreferenceKey(legacyKey), "としあき"))
        }

        val store = openStore(name)
        assertEquals(listOf(boardKey), store.boards.first().map { it.key })
        // The newer of the two drafts survives under the corrected key.
        assertEquals(200L, store.loadBuildDraft(boardKey)?.updatedAtEpochMillis)
        val preferences = store.preferences.first()
        assertEquals(listOf(CompatWatchRule("猫", boardKey = boardKey)), compatWatchRules(preferences))
        assertEquals("としあき", preferences[compatBoardDefaultNamePreferenceKey(boardKey)])
        assertEquals(null, preferences[compatBoardDefaultNamePreferenceKey(legacyKey)])
        store.closeForTest()
        stores -= store

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM compat_catalog_dropped WHERE board_key=?", arrayOf(boardKey)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    @Test
    fun backupRestoreAppliesTheTabLimitImmediately() = runBlocking {
        val board = CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0)
        fun tab(threadNo: Int, insertedAt: Long): CompatTab {
            val url = "${boardUrl}res/$threadNo.htm"
            return CompatTab(
                key = compatTabKey(url), canonicalUrl = url, originalUrl = url, boardKey = boardKey,
                boardName = "mayb", threadNo = threadNo.toString(), title = "スレ$threadNo",
                insertedAtEpochMillis = insertedAt, contentUpdatedAtEpochMillis = insertedAt
            )
        }
        val source = openStore(newDatabaseName())
        source.upsertBoard(board)
        (1..100).forEach { index -> source.openTab(tab(1_000 + index, 10_000L + index)) }
        val payload = source.exportSettingsBackup()

        val target = openStore(newDatabaseName())
        target.upsertBoard(board)
        (1..100).forEach { index -> target.openTab(tab(2_000 + index, index.toLong())) }
        target.importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)

        val tabs = target.tabs.first()
        assertTrue("restored ${tabs.size} tabs", tabs.size <= 100)
        // The newer restored tabs are kept, the oldest local ones trimmed.
        assertTrue(tabs.map { it.threadNo }.containsAll((1_011..1_100).map(Int::toString)))
    }

    @Test
    fun modernHistoryImportKeepsCompatibilityUpdateTime() = runBlocking {
        val store = openStore(newDatabaseName())
        store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        val url = "${boardUrl}res/1.htm"
        store.recordHistoryVisit(
            CompatHistoryEntry(url, url, boardKey, "mayb", "1", "スレ1", contentUpdatedAtEpochMillis = 150L, lastVisitedEpochMillis = 100L)
        )
        val modern = ThreadHistoryEntry(
            threadId = "1", boardId = "may", title = "スレ1", titleImageUrl = "", boardName = "mayb",
            boardUrl = boardUrl, lastVisitedEpochMillis = 500L, replyCount = 3
        )
        assertEquals(1, store.importModernHistory(listOf(modern)))
        val entry = store.history.first().single()
        assertEquals(500L, entry.lastVisitedEpochMillis)
        assertEquals(150L, entry.contentUpdatedAtEpochMillis)
        assertEquals(0, store.importModernHistory(listOf(modern)))
    }
}
