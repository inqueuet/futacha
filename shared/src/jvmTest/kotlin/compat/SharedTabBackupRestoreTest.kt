package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/** Audit M3 (2026-11-09): restoring a settings backup must not close the tabs open now. */
class SharedTabBackupRestoreTest {
    private val board = BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")
    private fun url(no: Int) = "https://may.2chan.net/b/res/$no.htm"
    private fun tab(no: Int) = CompatTab(
        compatTabKey(url(no)), url(no), url(no), compatBoardKey(board.url), "may", no.toString(), "題$no",
        insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong()
    )

    private fun <T> withStore(block: suspend (DesktopCompatibilityStore) -> T): T = runBlocking {
        val root = Files.createTempDirectory("shared-tab-backup").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try { store.initialize(); store.importModernBoards(listOf(board)); block(store) }
        finally { store.close(); root.deleteRecursively() }
    }

    private fun modePrefs(vararg nos: Int) = mapOf(
        FutaberPreferenceKeys.TABS to futaberTabsToStore(nos.map { FutaberTabKey(board.id, it.toString()) }, listOf(board)),
        FtutterKeys.TABS to nos.joinToString("\n") { url(it) },
        FtutterKeys.PROTECTED to nos.joinToString("\n") { url(it) },
        FtutterKeys.OPENING to (nos.firstOrNull()?.let { url(it) } ?: "")
    )

    @Test fun restoringAnOldBackupKeepsTheModeTabListsAndTheOpenSharedTabs() {
        val oldBackup = withStore { old ->
            old.openTab(tab(1))
            old.savePreferences(modePrefs(1))
            old.exportSettingsBackup()
        }
        withStore { store ->
            store.openTab(tab(2))
            store.savePreferences(modePrefs(2))
            val before = modePrefs(2).keys.associateWith { store.preferences.first()[it] }

            store.importSettingsBackup(oldBackup, restoreUserSettings = true, restoreNgRules = false)

            val after = store.preferences.first()
            before.forEach { (key, value) -> assertEquals(value, after[key], key) }
            val open = store.tabs.first().map { it.threadNo }.toSet()
            assertTrue("2" in open, "the tab open now stays open: $open")
            assertTrue("1" in open, "the backup's own tabs are still restored: $open")
        }
    }

    @Test fun theModeTabListKeysAreNotRestorable() {
        val restored = compatBackupRestorePreferences(modePrefs(1) + ("compat.other" to "x"))
        assertEquals(setOf("compat.other"), restored.keys)
        assertEquals(COMPAT_BACKUP_MODE_TAB_LIST_KEYS, modePrefs(1).keys)
    }
}
