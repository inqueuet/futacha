package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** AM-B5: while あいもげ is off, a backup holding あいもげ boards is restored whole (kept, not shown) and does not fail. */
class AimogeDesktopHiddenRestoreTest {
    private val fUrl = "https://may.2chan.net/b/"
    private val aUrl = "https://nijiurachan.net/b/aimoge/"
    private val fBoard = CompatBoard(compatBoardKey(fUrl), "may", fUrl, "${fUrl}futaba.php", 0)
    private val aBoard = CompatBoard(compatBoardKey(aUrl), "あいもげ", aUrl, aUrl, 1)
    private val aThread = "${aUrl}thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"

    @Test
    fun aSiteBoardsBackupIsKeptWhileOff() = runBlocking {
        val before = AimogeAvailability.sitesEnabled
        AimogeAvailability.sitesEnabled = false
        try {
            val payload = encodeCompatSettingsBackup(
                CompatSettingsBackup(
                    exportedAtEpochMillis = 1L,
                    boards = listOf(fBoard, aBoard),
                    tabs = listOf(CompatTab(
                        key = compatTabKey(aThread), canonicalUrl = aThread, originalUrl = aThread, boardKey = aBoard.key,
                        boardName = aBoard.name, threadNo = "1", title = "t", insertedAtEpochMillis = 1L,
                        contentUpdatedAtEpochMillis = 1L
                    )),
                    history = listOf(CompatHistoryEntry(
                        canonicalUrl = aThread, originalUrl = aThread, boardKey = aBoard.key, boardName = aBoard.name,
                        threadNo = "1", title = "t", contentUpdatedAtEpochMillis = 1L
                    )),
                    ngRules = listOf(CompatNgRule("a", CompatNgKind.CATALOG_WORD, aBoard.key, "w", 1L))
                )
            )
            assertTrue("siteBoards" in payload)
            val store = DesktopCompatibilityStore(JvmFileSystem(Files.createTempDirectory("aimoge-hidden").toFile()))
            store.importSettingsBackup(payload)
            assertTrue(store.boards.first().any { it.key == aBoard.key }, "board kept")
            assertTrue(store.tabs.first().any { it.canonicalUrl == aThread }, "tab kept")
            assertTrue(store.history.first().any { it.canonicalUrl == aThread }, "history kept")
            assertTrue(store.ngRules.first().any { it.id == "a" }, "rule kept")
            // Writing it back out keeps the あいもげ part too.
            assertTrue("siteBoards" in store.exportSettingsBackup())
        } finally {
            AimogeAvailability.sitesEnabled = before
        }
    }
}
