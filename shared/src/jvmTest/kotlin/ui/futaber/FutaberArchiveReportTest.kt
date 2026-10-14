package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.enqueueViewedThreadArchiveReport
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "閲覧スレ通知" of ふたちゃ and ふたばー風 (W07): a viewed thread is put in the shared outbox (nothing is sent here), only
 * with the shared switch on, only for the boards the outbox takes, and never on the desktop.
 */
class FutaberArchiveReportTest {
    private val may = BoardSummary("may-b", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")

    private fun <T> withStore(block: suspend (DesktopCompatibilityStore) -> T): T = runBlocking {
        val directory = Files.createTempDirectory("futaber-archive-report").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            block(store)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aLiveReadOfAnOfficialThreadIsNotedOnceWithTheSwitchUnset() = withStore { store ->
        val sent = mutableListOf<Int>()
        assertTrue(futaberNoteViewedThread(store, emptyMap(), may, "1001", { sent += it }, nowEpochMillis = 1_000L, desktop = false))
        assertEquals(1, store.archiveReportOutboxStats().pendingOrRetry)
        assertEquals(listOf(1), sent)
        // The same thread again is not a second row (the host is still told what waits, as としあき(仮) does).
        assertFalse(futaberNoteViewedThread(store, emptyMap(), may, "1001", { sent += it }, nowEpochMillis = 2_000L, desktop = false))
        assertEquals(1, store.archiveReportOutboxStats().pendingOrRetry)
    }

    @Test
    fun theSwitchOffTheDesktopAndABoardOutsideTheServiceNoteNothing() = withStore { store ->
        val sent = mutableListOf<Int>()
        val off = mapOf(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY to "OFF")
        assertFalse(futaberNoteViewedThread(store, off, may, "1001", { sent += it }, nowEpochMillis = 1_000L, desktop = false))
        assertFalse(futaberNoteViewedThread(store, emptyMap(), may, "1002", { sent += it }, nowEpochMillis = 1_000L, desktop = true))
        assertFalse(futaberNoteViewedThread(store, emptyMap(), tutorial, "1003", { sent += it }, nowEpochMillis = 1_000L, desktop = false))
        assertFalse(futaberNoteViewedThread(null, emptyMap(), may, "1004", { sent += it }, nowEpochMillis = 1_000L, desktop = false))
        assertEquals(0, store.archiveReportOutboxStats().total)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun ふたちゃNotesThePageOnlyWhenItWasReadFromTheBoardNow() = withStore { store ->
        val url = "https://img.2chan.net/b/res/77.htm"
        assertFalse(enqueueViewedThreadArchiveReport(store, url, null, liveFetch = false, nowEpochMillis = 1L, onEnqueued = null, desktop = false))
        assertEquals(0, store.archiveReportOutboxStats().total)
        assertTrue(enqueueViewedThreadArchiveReport(store, url, null, liveFetch = true, nowEpochMillis = 1L, onEnqueued = null, desktop = false))
        assertEquals(1, store.archiveReportOutboxStats().pendingOrRetry)
    }
}
