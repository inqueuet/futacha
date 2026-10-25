package com.valoser.futacha

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityInitializationFailureInstrumentedTest {
    private val boardUrl = "https://may.2chan.net/b/"

    @Test fun unreadableInitializationDoesNotPublishAnEmptyListAndCanBeRetried() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audit_init_failure_${System.nanoTime()}.db"
        val board = CompatBoard(compatBoardKey(boardUrl), "保存した板", boardUrl, boardUrl, 0)
        var store = AndroidCompatibilityStore(context, databaseName = name)
        try {
            store.ensureInitialized()
            store.upsertBoard(board)
            store.savePreference("compat.test.initialization", "保持する設定")
            store.closeForTest()
            // Keep the real rows intact while making both repair and fallback reads fail.
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("ALTER TABLE compat_board RENAME TO audit_saved_board")
                db.execSQL("CREATE VIEW compat_board AS SELECT 1 AS unreadable")
            }
            store = AndroidCompatibilityStore(context, databaseName = name)
            var failed = false
            try { store.ensureInitialized() } catch (_: SQLiteException) { failed = true }
            assertTrue("The test must actually fail initialization", failed)
            assertFalse("An unreadable database must not become authoritative", store.isLoaded.value)
            assertNull(withTimeoutOrNull(250) { store.boards.first() })
            assertNull(withTimeoutOrNull(250) { store.preferences.first() })

            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("DROP VIEW compat_board")
                db.execSQL("ALTER TABLE audit_saved_board RENAME TO compat_board")
            }
            // The same entry point used by a subsequent mode switch retries initialization.
            store.ensureInitialized()
            assertTrue(store.isLoaded.value)
            assertEquals(listOf(board), withTimeout(5_000) { store.boards.first() })
            assertEquals("保持する設定", store.preferences.first()["compat.test.initialization"])
        } finally { store.closeForTest(); context.deleteDatabase(name) }
    }

    @Test fun aSuccessfullyReadEmptyListStillRepresentsTheUsersBoardDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audit_init_empty_${System.nanoTime()}.db"
        val store = AndroidCompatibilityStore(context, databaseName = name)
        try {
            store.ensureInitialized()
            assertTrue(store.isLoaded.value)
            val boards = withTimeout(5_000) { store.boards.first() }
            assertTrue(boards.isEmpty())
            val modern = listOf(BoardSummary("may", "保存した板", "", boardUrl, ""))
            assertTrue(synchronizeModernBoardsFromCompatibility(modern, boards, false).isEmpty())
        } finally { store.closeForTest(); context.deleteDatabase(name) }
    }
}
