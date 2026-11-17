@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.compat

import cnames.structs.sqlite3
import com.valoser.futacha.shared.sqlite.SQLITE_OK
import com.valoser.futacha.shared.sqlite.SQLITE_OPEN_READWRITE
import com.valoser.futacha.shared.sqlite.SQLITE_ROW
import com.valoser.futacha.shared.sqlite.sqlite3_close_v2
import com.valoser.futacha.shared.sqlite.sqlite3_column_int64
import com.valoser.futacha.shared.sqlite.sqlite3_column_text
import com.valoser.futacha.shared.sqlite.sqlite3_finalize
import com.valoser.futacha.shared.sqlite.sqlite3_open_v2
import com.valoser.futacha.shared.sqlite.sqlite3_prepare_v2
import com.valoser.futacha.shared.sqlite.sqlite3_step
import cnames.structs.sqlite3_stmt
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.open
import platform.posix.pwrite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L95: when the cache table can be neither read nor cleared, the database is rebuilt without it, but only after
 * the profile, its scroll overlay and every state record have been read and a copy of the original kept. Before,
 * the whole database was deleted first and rewritten later, so the latest read positions were lost and a failure
 * in between left an empty profile. Mirrors Desktop's DesktopCompatibilityCacheRecoveryTest (M8).
 */
class IosCompatibilityCacheRecoveryTest {
    @Test fun aCorruptCacheTableIsRebuiltWithoutLosingProfileOrScrollOverlay() = verifyRecovery(Fault.NONE)
    @Test fun aFailedBackupPreservesTheOriginalDatabaseAndAllowsRetry() = verifyRecovery(Fault.BACKUP)
    @Test fun aFailedReplacementWritePreservesTheOriginalDatabaseAndAllowsRetry() = verifyRecovery(Fault.REPLACEMENT)

    private enum class Fault { NONE, BACKUP, REPLACEMENT }

    private fun verifyRecovery(fault: Fault) = runBlocking {
        val delegate = createFileSystem()
        delegate.deleteRecursively("compatibility").getOrThrow()
        var failReplacement = false
        val fs = object : FileSystem by delegate {
            override fun resolveAbsolutePath(relativePath: String): String {
                if (failReplacement && relativePath.startsWith("compatibility/cache-recovery-")) {
                    throw IllegalStateException("injected replacement write failure")
                }
                return delegate.resolveAbsolutePath(relativePath)
            }
        }
        var store = IosCompatibilityStore(fs, nowMillis = { 1_000L })
        val boardUrl = "https://may.2chan.net/b/"
        val url = "${boardUrl}res/123456.htm"
        val board = CompatBoard(compatBoardKey(boardUrl), "残す板", boardUrl, boardUrl, 0)
        val tab = CompatTab(compatTabKey(url), url, url, board.key, board.name, "123456", "残すスレ",
            insertedAtEpochMillis = 1_000L, contentUpdatedAtEpochMillis = 1_000L)
        val history = CompatHistoryEntry(url, url, board.key, board.name, tab.threadNo, tab.title,
            contentUpdatedAtEpochMillis = 1_000L, lastVisitedEpochMillis = 1_000L)
        val draft = CompatReplyDraft(tab.key, comment = "残す返信下書き", updatedAtEpochMillis = 1_000L)
        val buildDraft = CompatBuildDraft(board.key, comment = "残すスレ立て下書き", updatedAtEpochMillis = 1_000L)
        val ng = CompatNgRule("kept-rule", CompatNgKind.CATALOG_WORD, board.key, "残すng", 1_000L)
        val anchor = ScrollAnchor(postNo = "123460", offsetPx = 12, fallbackIndex = 4)
        val databasePath = delegate.resolveAbsolutePath("compatibility/compatibility.db")
        val backupPath = "compatibility/unreadable_compatibility_database-3000.db"
        try {
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, history)
            store.saveDraft(draft)
            store.saveBuildDraft(buildDraft)
            store.savePreference("compat.test.kept", "残す設定")
            assertTrue(store.upsertNgRule(ng))
            store.saveThreadSnapshot(CompatThreadSnapshot(tab.key, 1L, 1_000L,
                posts = listOf(CompatPostSnapshot(0, "123456", timestamp = "", messageHtml = "捨てるキャッシュ"))))
            store.updateScrollAnchor(tab.key, anchor) // The latest position exists only in the overlay table.
            store.close()

            val rootPage = withDatabase(databasePath) { db ->
                queryLong(db, "SELECT rootpage FROM sqlite_master WHERE name='compat_cache_record'")
            }
            val pageSize = withDatabase(databasePath) { db -> queryLong(db, "PRAGMA page_size") }
            // Invalid b-tree page type; the profile and its overlays stay readable.
            corruptByte(databasePath, (rootPage - 1) * pageSize)
            val check = IosCompatibilityDatabase(delegate)
            try {
                assertTrue(requireNotNull(check.readPayload()).contains(draft.comment))
                assertTrue(assertFails { check.readCacheRecords() }.isRecoverableIosCompatibilityDatabaseCorruption())
                assertTrue(assertFails { check.clearCacheRecords() }.isRecoverableIosCompatibilityDatabaseCorruption())
            } finally {
                check.close()
            }

            store = IosCompatibilityStore(fs, nowMillis = { 3_000L })
            if (fault != Fault.NONE) {
                if (fault == Fault.BACKUP) {
                    delegate.createDirectory("$backupPath/block-replacement").getOrThrow()
                } else {
                    failReplacement = true
                }
                assertFails { store.initialize() }
                val raw = withDatabase(databasePath) { db -> queryText(db, "SELECT payload FROM compat_state") }
                assertTrue(raw.contains(draft.comment), "The original profile must survive a failed recovery")
                assertEquals(1L, withDatabase(databasePath) { db ->
                    queryLong(db, "SELECT COUNT(*) FROM compat_scroll_anchor")
                })
                if (fault == Fault.BACKUP) delegate.deleteRecursively(backupPath).getOrThrow() else failReplacement = false
            }
            store.initialize()
            assertEquals(listOf(board.key), store.boards.first().map(CompatBoard::key))
            assertEquals(tab.key, store.tabs.first().single().key)
            assertEquals(anchor, store.tabs.first().single().scrollAnchor)
            assertEquals(anchor, store.history.first().single().scrollAnchor)
            assertEquals(draft, store.loadDraft(tab.key))
            assertEquals(buildDraft, store.loadBuildDraft(board.key))
            assertEquals("残す設定", store.loadPreference("compat.test.kept"))
            assertEquals(listOf("kept-rule"), store.ngRules.first().map(CompatNgRule::id))
            assertNull(store.loadThreadSnapshot(tab.key))
            assertTrue(delegate.exists(backupPath), "The damaged original is kept")
            store.savePreference("compat.test.afterRecovery", "保存できる")
            store.close()

            store = IosCompatibilityStore(fs)
            store.initialize()
            assertEquals(anchor, store.tabs.first().single().scrollAnchor)
            assertEquals(draft, store.loadDraft(tab.key))
            assertEquals("残す設定", store.loadPreference("compat.test.kept"))
            assertEquals("保存できる", store.loadPreference("compat.test.afterRecovery"))
            store.close()
            assertEquals("ok", withDatabase(databasePath) { db -> queryText(db, "PRAGMA integrity_check") })
        } finally {
            store.close()
            delegate.deleteRecursively("compatibility").getOrThrow()
        }
    }

    private fun <T> withDatabase(path: String, block: (CPointer<sqlite3>) -> T): T {
        val db = memScoped {
            val out = alloc<CPointerVarOf<CPointer<sqlite3>>>()
            check(sqlite3_open_v2(path, out.ptr, SQLITE_OPEN_READWRITE, null) == SQLITE_OK) { "open $path" }
            requireNotNull(out.value)
        }
        return try { block(db) } finally { sqlite3_close_v2(db) }
    }

    private fun <T> query(db: CPointer<sqlite3>, sql: String, read: (CPointer<sqlite3_stmt>) -> T): T {
        val statement = memScoped {
            val out = alloc<CPointerVarOf<CPointer<sqlite3_stmt>>>()
            check(sqlite3_prepare_v2(db, sql, -1, out.ptr, null) == SQLITE_OK) { "prepare $sql" }
            requireNotNull(out.value)
        }
        return try {
            check(sqlite3_step(statement) == SQLITE_ROW) { "step $sql" }
            read(statement)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    private fun queryLong(db: CPointer<sqlite3>, sql: String): Long = query(db, sql) { sqlite3_column_int64(it, 0) }

    private fun queryText(db: CPointer<sqlite3>, sql: String): String =
        query(db, sql) { requireNotNull(sqlite3_column_text(it, 0)).reinterpret<ByteVar>().toKString() }

    private fun corruptByte(path: String, offset: Long) {
        val fd = open(path, O_RDWR)
        check(fd >= 0) { "open $path for writing" }
        try {
            ByteArray(1).usePinned { pinned ->
                check(pwrite(fd, pinned.addressOf(0), 1uL, offset) == 1L) { "write $path" }
            }
        } finally {
            close(fd)
        }
    }
}
