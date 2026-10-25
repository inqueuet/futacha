@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.ui.compat.persistCompatPostAttachment
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What a write leaves when its commit fails (L92) or its caller is cancelled after the commit (L93). */
@RunWith(AndroidJUnit4::class)
class CompatStoreCommitInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_commit_${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "may", boardUrl, boardUrl, 0)
    private val fileSystem = createFileSystem(context)
    private lateinit var store: AndroidCompatibilityStore

    @Before fun prepare() = runBlocking {
        store = AndroidCompatibilityStore(context, attachmentFileSystem = fileSystem, databaseName = databaseName)
        store.initialize()
    }

    @After fun cleanUp() {
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test fun aWriteWhoseCommitFailsIsNotShown() = runBlocking {
        // A row breaking a deferred foreign key makes the COMMIT itself fail, as a full disk would.
        store.beforeCommitForTest = { db ->
            db.execSQL("PRAGMA defer_foreign_keys=ON")
            db.execSQL("INSERT INTO compat_catalog_dropped(board_key, thread_id, item_json, dropped_at) VALUES('missing', '1', '{}', 0)")
        }
        val failure = runCatching { store.upsertBoard(board) }.exceptionOrNull()
        store.beforeCommitForTest = null

        assertTrue("The commit failed: $failure", failure is SQLiteException)
        assertTrue("A board not committed is not shown", store.boards.first().none { it.key == boardKey })
    }

    @Test fun aDeletedBoardsDraftAttachmentGoesEvenWhenTheCallerIsCancelledAfterTheCommit() = runBlocking {
        store.upsertBoard(board)
        val locator = persistCompatPostAttachment(fileSystem, "build:$boardKey", ImageData(byteArrayOf(1, 2, 3), "a.png")).getOrThrow()
        store.saveBuildDraft(CompatBuildDraft(boardKey, comment = "draft", attachmentUri = locator, updatedAtEpochMillis = 1))
        assertTrue(fileSystem.readBytes(locator).isSuccess)

        lateinit var deleting: Job
        store.beforeCommitForTest = { deleting.cancel() }
        deleting = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { store.deleteBoard(boardKey) }
        deleting.start()
        deleting.join()
        store.beforeCommitForTest = null

        assertTrue("The deletion committed", store.boards.first().none { it.key == boardKey })
        assertTrue("Its draft's attachment is gone with it", fileSystem.readBytes(locator).isFailure)
    }
}
