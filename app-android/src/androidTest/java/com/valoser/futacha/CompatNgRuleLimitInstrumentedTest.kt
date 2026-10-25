package com.valoser.futacha

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_RULES
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The NG rules past the 10,000 read for the screens (L79, L91), and the board a deleted board leaves (L105). */
@RunWith(AndroidJUnit4::class)
class CompatNgRuleLimitInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_ng_limit_${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val otherUrl = "https://img.2chan.net/b/"
    private val otherKey = compatBoardKey(otherUrl)
    private lateinit var store: AndroidCompatibilityStore

    @Before fun prepare() = runBlocking {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        store.initialize()
        store.upsertBoard(CompatBoard(boardKey, "may", boardUrl, boardUrl, 0))
        store.upsertBoard(CompatBoard(otherKey, "img", otherUrl, otherUrl, 1))
    }

    @After fun cleanUp() {
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    /** Rows written beside the store's own connection, as a settings restore before L91 left them. */
    private fun insertRows(rows: List<Triple<String, String, String>>, createdAt: (Int) -> Long) {
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                rows.forEachIndexed { index, (id, kind, scope) ->
                    db.insertOrThrow("compat_ng_rule", null, ContentValues().apply {
                        put("rule_id", id); put("kind", kind); put("scope_key", scope)
                        put("normalized_value", "w$index"); put("payload_json", "{}"); put("created_at", createdAt(index))
                    })
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    private fun countScoped(scope: String): Long = count("SELECT COUNT(*) FROM compat_ng_rule WHERE scope_key=?", scope)

    private fun count(sql: String, vararg args: String): Long =
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            DatabaseUtils.longForQuery(db, sql, args)
        }

    @Test fun deletingABoardRemovesItsRulesPastTheReadLimitAndOfUnknownKinds() = runBlocking {
        // 10,000 newer rules of another board fill the read; the board's own are older, or of a kind not known here.
        insertRows(List(10_000) { Triple("other-$it", "THREAD_WORD", otherKey) }) { 1_000_000L + it }
        insertRows(List(40) { Triple("old-$it", "THREAD_WORD", boardKey) } +
            List(5) { Triple("future-$it", "SOME_LATER_KIND", boardKey) }) { 1L + it }
        assertEquals(45, countScoped(boardKey))

        store.deleteBoard(boardKey)

        assertEquals("No rule of the deleted board is left", 0, countScoped(boardKey))
        assertEquals("The other board's rules stay", 10_000, countScoped(otherKey))
    }

    @Test fun aRestorePastTheLimitKeepsTheRulesThatApplyAndLeavesRoomToAddAgain() = runBlocking {
        insertRows(List(MAX_COMPAT_NG_RULES) { Triple("held-$it", "THREAD_WORD", otherKey) }) { 1_000_000L + it }
        val restored = List(50) { CompatNgRule("restored-$it", CompatNgKind.CATALOG_WORD, boardKey, "r$it", 1L + it) }
        val report = store.importSettingsBackup(
            encodeCompatSettingsBackup(CompatSettingsBackup(exportedAtEpochMillis = 1, ngRules = restored, workspace = null)),
            restoreUserSettings = false,
            restoreNgRules = true
        )

        assertEquals("No more than the limit is stored", MAX_COMPAT_NG_RULES.toLong(), count("SELECT COUNT(*) FROM compat_ng_rule"))
        assertEquals("The rules read (the newest) are the ones kept", 0, countScoped(boardKey))
        assertEquals("The report counts what was kept", 0, report.ngRulesImported)
        // A rule taken off makes room for a new one, as it does below the limit.
        store.deleteNgRule("held-0")
        assertTrue(store.upsertNgRule(CompatNgRule("new", CompatNgKind.CATALOG_WORD, boardKey, "new", 2_000_000L)))
    }

    @Test fun rulesPastTheLimitLeftByAnEarlierRestoreAreRemovedAtStart() = runBlocking {
        insertRows(List(MAX_COMPAT_NG_RULES + 50) { Triple("left-$it", "THREAD_WORD", otherKey) }) { 1L + it }
        store.closeForTest()
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        store.initialize()

        assertEquals(MAX_COMPAT_NG_RULES.toLong(), count("SELECT COUNT(*) FROM compat_ng_rule"))
        assertEquals("The oldest 50 (never read) are the ones removed", 0,
            count("SELECT COUNT(*) FROM compat_ng_rule WHERE created_at <= 50"))
        assertEquals(MAX_COMPAT_NG_RULES, store.ngRules.first().size)
    }

    @Test fun deletingTheBoardTheCatalogIsShownForClearsItFromTheWorkspace() = runBlocking {
        store.updateWorkspace(CompatWorkspaceRecord(catalogHostBoardKey = boardKey))
        store.deleteBoard(boardKey)
        assertNull(store.workspace.first().catalogHostBoardKey)
        store.updateWorkspace(CompatWorkspaceRecord(catalogHostBoardKey = otherKey))
        store.deleteBoard(boardKey)
        assertEquals("Another board's is kept", otherKey, store.workspace.first().catalogHostBoardKey)
    }
}
