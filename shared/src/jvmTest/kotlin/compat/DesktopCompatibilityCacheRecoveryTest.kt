package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.SQLException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCompatibilityCacheRecoveryTest {
    @Test fun aCorruptCacheTableIsRebuiltWithoutLosingProfileOrScrollOverlay() = verifyRecovery(Fault.NONE)
    @Test fun aFailedBackupPreservesTheOriginalDatabaseAndAllowsRetry() = verifyRecovery(Fault.BACKUP)
    @Test fun aFailedReplacementWritePreservesTheOriginalDatabaseAndAllowsRetry() = verifyRecovery(Fault.REPLACEMENT)

    private enum class Fault { NONE, BACKUP, REPLACEMENT }

    private fun verifyRecovery(fault: Fault) = runBlocking {
        val root = Files.createTempDirectory("compat-cache-corruption").toFile()
        val delegate = JvmFileSystem(root)
        var failReplacement = false
        val fs = object : FileSystem by delegate {
            override fun resolveAbsolutePath(relativePath: String): String {
                if (failReplacement && relativePath.startsWith("compatibility/cache-recovery-")) {
                    throw IOException("injected replacement write failure")
                }
                return delegate.resolveAbsolutePath(relativePath)
            }
        }
        var store = DesktopCompatibilityStore(fs, nowMillis = { 1_000L })
        val boardUrl = "https://may.2chan.net/b/"
        val url = "${boardUrl}res/123456.htm"
        val board = CompatBoard(compatBoardKey(boardUrl), "残す板", boardUrl, boardUrl, 0)
        val tab = CompatTab(compatTabKey(url), url, url, board.key, board.name, "123456", "残すスレ",
            insertedAtEpochMillis = 1_000L, contentUpdatedAtEpochMillis = 1_000L)
        val history = CompatHistoryEntry(url, url, board.key, board.name, tab.threadNo, tab.title,
            contentUpdatedAtEpochMillis = 1_000L, lastVisitedEpochMillis = 1_000L)
        val draft = CompatReplyDraft(tab.key, comment = "残す返信下書き", updatedAtEpochMillis = 1_000L)
        val buildDraft = CompatBuildDraft(board.key, comment = "残すスレ立て下書き", updatedAtEpochMillis = 1_000L)
        val ng = CompatNgRule("kept-rule", CompatNgKind.THREAD_IGNORE, tab.key, "残すNG", 1_000L)
        val anchor = ScrollAnchor(postNo = "123460", offsetPx = 12, fallbackIndex = 4)
        val databaseFile = File(root, "compatibility/compatibility.db")
        val backupFile = File(databaseFile.parentFile, "unreadable_compatibility_db-3000.db")
        try {
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, history)
            store.saveDraft(draft)
            store.saveBuildDraft(buildDraft)
            store.savePreference("compat.test.kept", "残す設定")
            store.upsertNgRule(ng)
            store.saveThreadSnapshot(CompatThreadSnapshot(tab.key, 1L, 1_000L,
                posts = listOf(CompatPostSnapshot(0, "123456", timestamp = "", messageHtml = "捨てるキャッシュ"))))
            store.updateScrollAnchor(tab.key, anchor) // The latest position exists only in the overlay table.
            store.close()
            val (page, pageSize) = connection(databaseFile) { db ->
                db.createStatement().use { statement ->
                    val page = statement.executeQuery("SELECT rootpage FROM sqlite_master WHERE name='compat_cache_record'")
                        .use { it.next(); it.getLong(1) }
                    val size = statement.executeQuery("PRAGMA page_size").use { it.next(); it.getLong(1) }
                    page to size
                }
            }
            RandomAccessFile(databaseFile, "rw").use { file ->
                file.seek((page - 1) * pageSize)
                file.writeByte(0) // Invalid b-tree page type; the profile and its overlays stay readable.
            }
            connection(databaseFile) { db ->
                db.createStatement().use { statement ->
                    assertNotNull(statement.executeQuery("SELECT payload FROM compat_state").use { it.next(); it.getString(1) })
                    assertEquals(11, assertFailsWith<SQLException> {
                        statement.executeQuery("SELECT key,payload FROM compat_cache_record").close()
                    }.errorCode)
                    assertEquals(11, assertFailsWith<SQLException> {
                        statement.executeUpdate("DELETE FROM compat_cache_record")
                    }.errorCode)
                }
            }
            store = DesktopCompatibilityStore(fs, nowMillis = { 3_000L })
            if (fault != Fault.NONE) {
                if (fault == Fault.BACKUP) {
                    assertTrue(backupFile.mkdirs())
                    File(backupFile, "block-replacement").writeText("test")
                } else failReplacement = true
                assertFailsWith<IOException> { store.initialize() }
                assertTrue(databaseFile.isFile)
                connection(databaseFile) { db ->
                    db.createStatement().use { statement ->
                        val raw = statement.executeQuery("SELECT payload FROM compat_state").use { it.next(); it.getString(1) }
                        assertTrue(raw.contains(draft.comment), "The original profile must survive a failed recovery")
                        assertEquals(1, statement.executeQuery("SELECT COUNT(*) FROM compat_scroll_anchor").use { it.next(); it.getInt(1) })
                    }
                }
                if (fault == Fault.BACKUP) backupFile.deleteRecursively() else failReplacement = false
            }
            store.initialize()
            assertTrue(backupFile.isFile)
            assertEquals(listOf(board), store.boards.first())
            assertEquals(tab.key, store.tabs.first().single().key)
            assertEquals(anchor, store.tabs.first().single().scrollAnchor)
            assertEquals(anchor, store.history.first().single().scrollAnchor)
            assertEquals(draft, store.loadDraft(tab.key))
            assertEquals(buildDraft, store.loadBuildDraft(board.key))
            assertEquals("残す設定", store.loadPreference("compat.test.kept"))
            assertEquals(listOf(ng), store.ngRules.first())
            assertNull(store.loadThreadSnapshot(tab.key))
            store.savePreference("compat.test.afterRecovery", "保存できる")
            store.close()
            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(anchor, store.tabs.first().single().scrollAnchor)
            assertEquals(draft, store.loadDraft(tab.key))
            assertEquals("保存できる", store.loadPreference("compat.test.afterRecovery"))
            connection(databaseFile) { db ->
                db.createStatement().use { statement ->
                    assertEquals("ok", statement.executeQuery("PRAGMA integrity_check").use { it.next(); it.getString(1) })
                }
            }
        } finally { store.close(); root.deleteRecursively() }
    }

    private fun <T> connection(file: File, block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use(block)
}
