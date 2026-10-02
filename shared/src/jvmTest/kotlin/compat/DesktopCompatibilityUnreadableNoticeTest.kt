package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.state.SettingsRecoveryNotices
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P4-1 of the 2026-10-02 round-4 audit. */
class DesktopCompatibilityUnreadableNoticeTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val directory = Files.createTempDirectory("compat-unreadable-notice").toFile()
    private val fs = JvmFileSystem(directory)

    @AfterTest
    fun tearDown() {
        while (SettingsRecoveryNotices.notice.value != null) SettingsRecoveryNotices.acknowledge()
        directory.deleteRecursively()
    }

    @Test
    fun unreadablePayloadIsReportedUntilAcknowledgedEvenAfterTheRowIsReplaced() = runBlocking {
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.upsertBoard(board)
            store.close()
            DesktopCompatibilityDatabase(fs).apply {
                writePayload("""{"boards":"not a list","tabs":[]}""", 2L)
                close()
            }

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            val notice = assertNotNull(SettingsRecoveryNotices.notice.value)
            assertEquals(UNREADABLE_COMPATIBILITY_NOTICE_TITLE, notice.title)
            val backup = directory.walkTopDown().single { it.name.startsWith("unreadable_compatibility_state-") }
            assertTrue(backup.name in notice.message, notice.message)
            assertTrue(backup.absoluteFile.parent in notice.message, notice.message)
            val marker = File(directory, UNREADABLE_COMPATIBILITY_NOTICE_PATH)
            assertTrue(marker.isFile)

            // The first real write replaces the unreadable row; the next launch
            // (simulated by dropping the in-memory notice) still reports it.
            store.upsertBoard(board)
            store.close()
            SettingsRecoveryNotices.post("stale", key = UNREADABLE_COMPATIBILITY_NOTICE_KEY) {}
            SettingsRecoveryNotices.acknowledge()
            assertNull(SettingsRecoveryNotices.notice.value)

            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(listOf(board.key), store.boards.first().map(CompatBoard::key))
            assertEquals(notice, SettingsRecoveryNotices.notice.value)

            SettingsRecoveryNotices.acknowledge()
            withTimeout(5_000) { while (marker.exists()) delay(10) }
            assertTrue(backup.isFile, "acknowledging must keep the preserved copy")
        } finally {
            store.close()
        }
    }

    @Test
    fun readableDataPostsNoNotice() = runBlocking {
        val store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.upsertBoard(board)
            assertNull(SettingsRecoveryNotices.notice.value)
        } finally {
            store.close()
        }
    }

    @Test
    fun noticesFromDifferentSourcesQueueInsteadOfReplacingEachOther() {
        SettingsRecoveryNotices.post("settings") {}
        SettingsRecoveryNotices.post("compat", title = "t", key = UNREADABLE_COMPATIBILITY_NOTICE_KEY) {}
        SettingsRecoveryNotices.post("settings 2") {}
        assertEquals("settings 2", SettingsRecoveryNotices.message.value)
        SettingsRecoveryNotices.acknowledge()
        assertEquals(SettingsRecoveryNotices.Notice("t", "compat"), SettingsRecoveryNotices.notice.value)
        SettingsRecoveryNotices.acknowledge()
        assertNull(SettingsRecoveryNotices.notice.value)
    }
}
