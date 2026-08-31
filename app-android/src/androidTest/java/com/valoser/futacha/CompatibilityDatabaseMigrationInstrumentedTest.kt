package com.valoser.futacha

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.compat.CompatibilityDatabaseSchema
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityDatabaseMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var openStore: AndroidCompatibilityStore? = null
    private var extraDatabaseName: String? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "compat_migration_${System.currentTimeMillis()}.db"
    }

    @After
    fun tearDown() {
        openStore?.let { runBlocking { it.closeForTest() } }
        openStore = null
        context.deleteDatabase(databaseName)
        extraDatabaseName?.let(context::deleteDatabase)
        extraDatabaseName = null
    }

    @Test
    fun version9SchemaExport_matchesCurrentStatementsAndFreshDatabase() = runBlocking {
        val exportedStatements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/9.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        val expectedStatements = CompatibilityDatabaseSchema.createStatements +
            CompatibilityDatabaseSchema.initialWorkspaceStatement +
            "PRAGMA user_version=9"
        assertEquals(expectedStatements, exportedStatements)

        val production = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = production
        production.initialize()
        production.closeForTest()
        openStore = null

        val exportedDatabaseName = "${databaseName.removeSuffix(".db")}_export.db"
        extraDatabaseName = exportedDatabaseName
        context.openOrCreateDatabase(exportedDatabaseName, Context.MODE_PRIVATE, null).use { db ->
            exportedStatements.forEach(db::execSQL)
        }

        val productionSignature = SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use(SQLiteDatabase::schemaSignature)
        val exportedSignature = SQLiteDatabase.openDatabase(
            context.getDatabasePath(exportedDatabaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_workspace WHERE singleton_id=1"))
            db.schemaSignature()
        }
        assertEquals(productionSignature, exportedSignature)
    }

    @Test
    fun version7To8_addsDurableHistoryTombstones() = runBlocking {
        val version7Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/7.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version7Statements.forEach(db::execSQL)
            assertEquals(7, db.version)
        }

        val store = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = store
        store.initialize()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(
                1,
                db.scalarInt(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_history_tombstone'"
                )
            )
        }
    }

    @Test
    fun version8To9_addsPerBoardNonPriorityVisibilityAndPersistsIt() = runBlocking {
        val version8Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/8.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version8Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) " +
                    "VALUES('may-b','二次元裏','https://may.2chan.net/b/','https://may.2chan.net/b/',0)"
            )
            db.execSQL(
                "INSERT INTO compat_catalog_preference(" +
                    "board_key,sort_mode,layout_mode,reply_priority_enabled,few_replies_delay" +
                    ") VALUES('may-b','CATALOG','GRID',0,5)"
            )
            assertEquals(8, db.version)
        }

        val store = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = store
        store.initialize()

        assertEquals(9, context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { it.version })
        val migrated = store.loadCatalogPreference("may-b")
        assertEquals(false, migrated.replyPriorityEnabled)
        assertEquals(true, migrated.showNonPriority)
        assertEquals(5, migrated.fewRepliesDelay)

        store.saveCatalogPreference(
            CompatCatalogPreference(
                boardKey = "may-b",
                replyPriorityEnabled = true,
                showNonPriority = false,
                fewRepliesDelay = 9
            )
        )
        val saved = store.loadCatalogPreference("may-b")
        assertEquals(true, saved.replyPriorityEnabled)
        assertEquals(false, saved.showNonPriority)
        assertEquals(9, saved.fewRepliesDelay)
    }

    @Test
    fun closedStoreRejectsLateReadWithoutReopeningDatabase() = runBlocking {
        val store = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = store
        store.initialize()
        store.closeForTest()
        openStore = null

        var wasCancelled = false
        try {
            store.loadPreference("late.read")
        } catch (_: CancellationException) {
            wasCancelled = true
        }
        assertTrue(wasCancelled)
    }

    @Test
    fun version6To7_detachesThreadCacheAndKeepsRowsAfterTabClose() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val tabUrl = "${boardUrl}res/777.htm"
        val tabKey = compatTabKey(tabUrl)
        val version6Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/6.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version6Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            db.execSQL(
                """INSERT INTO compat_tab(
                    tab_key,canonical_url,original_url,board_key,board_name,thread_no,title,thumbnail_url,
                    reply_count,checked_reply_count,is_dead,is_isolated,is_exploded,is_old,favorite,inserted_at,
                    content_updated_at,scroll_anchor_json,snapshot_revision
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    tabKey, tabUrl, tabUrl, boardKey, "mayb", "777", "cached thread", null,
                    3, 0, 0, 0, 0, 0, 0, 1L, 2L,
                    "{\"postNo\":null,\"offsetPx\":0,\"fallbackIndex\":0,\"snapshotRevision\":4}", 4L
                )
            )
            db.execSQL(
                "INSERT INTO compat_thread_snapshot(tab_key,revision,fetched_at,board_title,expires_label,deleted_notice) VALUES(?,?,?,?,?,?)",
                arrayOf<Any?>(tabKey, 4L, 10L, "mayb", null, null)
            )
            db.execSQL(
                "INSERT INTO compat_post(tab_key,revision,position,post_json) VALUES(?,?,?,?)",
                arrayOf<Any?>(tabKey, 4L, 0, "{\"postNo\":\"1\"}")
            )
            db.execSQL(
                "INSERT INTO compat_reply_draft(tab_key,name,email,subject,comment,attachment_uri,delete_key,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(tabKey, "", "", "", "draft", null, "", 1L)
            )
            assertEquals(6, db.version)
        }

        val store = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = store
        store.initialize()
        val closed = store.closeTabs(setOf(tabKey), nowEpochMillis = 100L)
        assertNotNull(closed)
        store.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_thread_snapshot WHERE tab_key='$tabKey'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_post WHERE tab_key='$tabKey'"))
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM compat_reply_draft WHERE tab_key='$tabKey'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version1To2_statementFailureRollsBackAndCanRetryWithoutLosingData() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            versionOneStatements().forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            // The first migration statement can succeed, but the second CREATE INDEX
            // must fail because an object of a different type already owns its name.
            db.execSQL("CREATE TABLE compat_closed_batch_expires_idx(blocker INTEGER)")
        }

        val failing = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = failing
        val migrationFailure = runCatching { failing.initialize() }.exceptionOrNull()
        assertNotNull(migrationFailure)
        failing.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            assertEquals(1, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_board WHERE board_key='$boardKey'"))
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_closed_batch'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_closed_batch_expires_idx'"))
            db.execSQL("DROP TABLE compat_closed_batch_expires_idx")
        }

        val retry = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = retry
        retry.initialize()
        assertEquals(listOf(boardKey), retry.boards.first().map { it.key })
        retry.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_closed_batch'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='compat_closed_batch_expires_idx'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version1To2_preservesRowsRepairsCanonicalDuplicateAndCreatesClosedBatchSchema() = runBlocking {
        val canonicalBoardUrl = "https://may.2chan.net/b/"
        val canonicalBoardKey = compatBoardKey(canonicalBoardUrl)
        val currentTabUrl = "https://may.2chan.net/b/res/100.htm"
        val currentTabKey = compatTabKey(currentTabUrl)
        val currentHistoryUrl = "https://may.2chan.net/b/res/101.htm"

        val legacyBoardKey = "legacy-futaba-php-board"
        val legacyBoardUrl = "https://may.2chan.net/b/futaba.php"
        val legacyTabUrl = "https://may.2chan.net/b/res/200.htm"
        val legacyTabKey = compatTabKey(legacyTabUrl)
        val legacyHistoryUrl = "https://may.2chan.net/b/res/201.htm"
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            versionOneStatements().forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(canonicalBoardKey, "mayb", canonicalBoardUrl, canonicalBoardUrl, 0)
            )
            db.execSQL(
                """INSERT INTO compat_tab(
                    tab_key,canonical_url,original_url,board_key,board_name,thread_no,title,thumbnail_url,
                    reply_count,checked_reply_count,is_dead,is_isolated,is_exploded,is_old,favorite,inserted_at,
                    content_updated_at,scroll_anchor_json,snapshot_revision
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    currentTabKey, currentTabUrl, currentTabUrl, canonicalBoardKey, "mayb", "100", "current tab", null,
                    0, 0, 0, 0, 0, 0, 0, 100L, 200L,
                    "{\"postNo\":null,\"offsetPx\":0,\"fallbackIndex\":0,\"snapshotRevision\":0}", 0L
                )
            )
            db.execSQL(
                """INSERT INTO compat_history(
                    canonical_url,original_url,board_key,board_name,thread_no,title,thumbnail_url,reply_count,
                    content_updated_at,scroll_anchor_json
                ) VALUES(?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    currentHistoryUrl, currentHistoryUrl, canonicalBoardKey, "mayb", "101", "current history", null,
                    0, 201L, "{\"postNo\":null,\"offsetPx\":0,\"fallbackIndex\":0,\"snapshotRevision\":0}"
                )
            )
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(legacyBoardKey, "legacy mayb", legacyBoardUrl, legacyBoardUrl, 1)
            )
            db.execSQL(
                """INSERT INTO compat_tab(
                    tab_key,canonical_url,original_url,board_key,board_name,thread_no,title,thumbnail_url,
                    reply_count,checked_reply_count,is_dead,is_isolated,is_exploded,is_old,favorite,inserted_at,
                    content_updated_at,scroll_anchor_json,snapshot_revision
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    legacyTabKey, legacyTabUrl, legacyTabUrl, legacyBoardKey, "legacy mayb", "200", "legacy tab", null,
                    2, 1, 0, 0, 0, 0, 0, 300L, 400L,
                    "{\"postNo\":null,\"offsetPx\":0,\"fallbackIndex\":0,\"snapshotRevision\":0}", 0L
                )
            )
            db.execSQL(
                """INSERT INTO compat_history(
                    canonical_url,original_url,board_key,board_name,thread_no,title,thumbnail_url,reply_count,
                    content_updated_at,scroll_anchor_json
                ) VALUES(?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    legacyHistoryUrl, legacyHistoryUrl, legacyBoardKey, "legacy mayb", "201", "legacy history", null,
                    3, 401L, "{\"postNo\":null,\"offsetPx\":0,\"fallbackIndex\":0,\"snapshotRevision\":0}"
                )
            )
        }

        val migrated = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = migrated
        migrated.initialize()

        val boards = migrated.boards.first()
        val tabs = migrated.tabs.first()
        val history = migrated.history.first()
        assertEquals(listOf(canonicalBoardKey), boards.map { it.key })
        assertEquals(setOf(currentTabKey, legacyTabKey), tabs.map { it.key }.toSet())
        assertTrue(tabs.all { it.boardKey == canonicalBoardKey })
        assertEquals(setOf(currentHistoryUrl, legacyHistoryUrl), history.map { it.canonicalUrl }.toSet())
        assertTrue(history.all { it.boardKey == canonicalBoardKey })

        val closeTime = System.currentTimeMillis()
        val closed = migrated.closeTabs(setOf(legacyTabKey), nowEpochMillis = closeTime)
        assertNotNull(closed)
        assertEquals(legacyTabKey, migrated.loadPendingClosedTabs(closeTime + 1L)?.tabs?.single()?.tab?.key)

        migrated.closeForTest()
        openStore = null
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_closed_batch'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='compat_closed_batch_expires_idx'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version2To3_preservesExistingRowsAndAddsIndependentBuildDraft() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val version2Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/2.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version2Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            assertEquals(2, db.version)
        }

        val migrated = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = migrated
        migrated.initialize()
        val draft = CompatBuildDraft(
            boardKey = boardKey,
            subject = "独立したスレ立て",
            comment = "本文",
            deleteKey = "delete",
            updatedAtEpochMillis = 123L
        )
        migrated.saveBuildDraft(draft)
        assertEquals(draft, migrated.loadBuildDraft(boardKey))
        assertEquals(listOf(boardKey), migrated.boards.first().map { it.key })
        migrated.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_build_draft WHERE board_key='$boardKey'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='archive_report_outbox'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version3To4_statementFailureRollsBackThenPreservesRowsAndCreatesOutbox() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val version3Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/3.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version3Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            // migration3To4 creates the table first, then this index. Occupying the
            // index name with a table verifies the complete SQLite transaction rollback.
            db.execSQL("CREATE TABLE idx_archive_report_outbox_due(blocker INTEGER)")
            assertEquals(3, db.version)
        }

        val failing = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = failing
        assertNotNull(runCatching { failing.initialize() }.exceptionOrNull())
        failing.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            assertEquals(3, db.version)
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='archive_report_outbox'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_board WHERE board_key='$boardKey'"))
            db.execSQL("DROP TABLE idx_archive_report_outbox_due")
        }

        val migrated = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = migrated
        migrated.initialize()
        assertEquals(listOf(boardKey), migrated.boards.first().map { it.key })
        val queued = migrated.enqueueArchiveReport("https://may.2chan.net/b/res/123.htm", 1_000L)
        assertTrue(queued.inserted)
        assertEquals(1, migrated.archiveReportOutboxStats().total)
        migrated.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM archive_report_outbox"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_archive_report_outbox_due'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_archive_report_outbox_batch'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_archive_report_outbox_expiry'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version4To6_statementFailureRollsBackThenCreatesDroppedCatalogSchema() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val version4Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/4.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version4Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            db.execSQL("CREATE TABLE compat_catalog_dropped_recent_idx(blocker INTEGER)")
            assertEquals(4, db.version)
        }

        val failing = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = failing
        assertNotNull(runCatching { failing.initialize() }.exceptionOrNull())
        failing.closeForTest()
        openStore = null
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            assertEquals(4, db.version)
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='compat_catalog_dropped'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_board WHERE board_key='$boardKey'"))
            db.execSQL("DROP TABLE compat_catalog_dropped_recent_idx")
        }

        val migrated = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = migrated
        migrated.initialize()
        fun item(id: String) = CatalogItem(
            id = id,
            threadUrl = "${boardUrl}res/$id.htm",
            title = id,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 0
        )
        assertTrue(
            migrated.saveCatalogSnapshot(
                CompatCatalogSnapshot(boardKey, CompatCatalogSort.CATALOG, 1L, 1_000L, listOf(item("1"))),
                trackDropped = true
            )
        )
        assertTrue(
            migrated.saveCatalogSnapshot(
                CompatCatalogSnapshot(boardKey, CompatCatalogSort.CATALOG, 2L, 2_000L, listOf(item("2"))),
                trackDropped = true,
                requestedThreadCount = 2
            )
        )
        assertEquals("1", migrated.loadDroppedCatalogItems(boardKey).single().item.id)
        migrated.closeForTest()
        openStore = null

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_catalog_dropped"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='compat_catalog_dropped_recent_idx'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }

    @Test
    fun version5To6_classificationMigrationRollsBackAndBackfillsExistingRows() = runBlocking {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val version5Statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compatibility-schema/5.sql")
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .map { it.removeSuffix(";") }
                    .toList()
            }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            version5Statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO compat_board(board_key,name,canonical_url,original_url,sort_order) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(boardKey, "mayb", boardUrl, boardUrl, 0)
            )
            db.execSQL(
                "INSERT INTO compat_catalog_dropped(board_key,thread_id,item_json,dropped_at) VALUES(?,?,?,?)",
                arrayOf<Any?>(boardKey, "123", "{}", 12_345L)
            )
            // migration5To6 first adds last_seen_at, then creates this index. A table
            // occupying its name proves SQLiteOpenHelper rolls the preceding ALTER back.
            db.execSQL("CREATE TABLE compat_catalog_dropped_class_idx(blocker INTEGER)")
            assertEquals(5, db.version)
        }

        val failing = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = failing
        assertNotNull(runCatching { failing.initialize() }.exceptionOrNull())
        failing.closeForTest()
        openStore = null
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            assertEquals(5, db.version)
            assertEquals(0, db.scalarInt("SELECT COUNT(*) FROM pragma_table_info('compat_catalog_dropped') WHERE name='last_seen_at'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_catalog_dropped WHERE thread_id='123'"))
            db.execSQL("DROP TABLE compat_catalog_dropped_class_idx")
        }

        val migrated = AndroidCompatibilityStore(context, databaseName = databaseName)
        openStore = migrated
        migrated.initialize()
        migrated.closeForTest()
        openStore = null
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            assertEquals(9, db.version)
            assertEquals(3, db.scalarInt("SELECT COUNT(*) FROM pragma_table_info('compat_catalog_dropped') WHERE name IN ('last_seen_at','drop_class','inserted_at')"))
            assertEquals(12_345, db.scalarInt("SELECT last_seen_at FROM compat_catalog_dropped WHERE thread_id='123'"))
            assertEquals(12_345, db.scalarInt("SELECT inserted_at FROM compat_catalog_dropped WHERE thread_id='123'"))
            assertEquals(1, db.scalarInt("SELECT COUNT(*) FROM compat_catalog_dropped WHERE thread_id='123' AND drop_class='DIE'"))
            assertEquals(0, db.foreignKeyViolationCount())
        }
    }
}

private fun SQLiteDatabase.scalarInt(sql: String): Int = rawQuery(sql, null).use { cursor ->
    check(cursor.moveToFirst()) { "No scalar row for $sql" }
    cursor.getInt(0)
}

/**
 * Use the PRAGMA form instead of the `pragma_foreign_key_check` table-valued
 * function.  The latter is only available in newer SQLite versions and is
 * absent from the SQLite bundled with Android 8 (API 26), which is still a
 * supported minSdk test target.
 */
private fun SQLiteDatabase.foreignKeyViolationCount(): Int = rawQuery(
    "PRAGMA foreign_key_check",
    null
).use { cursor ->
    var count = 0
    while (cursor.moveToNext()) count += 1
    count
}

private fun versionOneStatements(): List<String> =
    InstrumentationRegistry.getInstrumentation().context.assets
        .open("compatibility-schema/1.sql")
        .bufferedReader()
        .useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("--") }
                .map { it.removeSuffix(";") }
                .toList()
        }

private fun SQLiteDatabase.schemaSignature(): List<String> = rawQuery(
    "SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type,name",
    null
).use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            add(
                listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2).normalizedSql())
                    .joinToString("|")
            )
        }
    }
}

private fun String.normalizedSql(): String = trim()
    .replace(Regex("\\s+"), " ")
    .replace(" IF NOT EXISTS ", " ", ignoreCase = true)
