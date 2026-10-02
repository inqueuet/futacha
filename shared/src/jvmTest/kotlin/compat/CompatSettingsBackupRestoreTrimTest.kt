package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatSettingsBackupRestoreTrimTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)

    private fun tab(no: Int, favorite: Boolean = false): CompatTab {
        val url = "https://may.2chan.net/b/res/$no.htm"
        return CompatTab(
            key = "tab-$no",
            canonicalUrl = url,
            originalUrl = url,
            boardKey = board.key,
            boardName = board.name,
            threadNo = no.toString(),
            title = "スレ$no",
            favorite = favorite,
            insertedAtEpochMillis = no.toLong(),
            contentUpdatedAtEpochMillis = no.toLong()
        )
    }

    @Test
    fun restoreTrimKeepsFavouritesAndTheRestoredActiveTabLikeAndroid() = runBlocking {
        val directory = Files.createTempDirectory("compat-restore-trim").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoard(board)
            // The ten oldest tabs; one favourite and the one the backup selects.
            (1..10).forEach { no -> store.openTab(tab(no, favorite = no == 1)) }
            val backup = CompatSettingsBackup(
                exportedAtEpochMillis = 1_000L,
                boards = listOf(board),
                tabs = (11..105).map(::tab),
                workspace = CompatWorkspaceRecord(activeTabKey = "tab-2")
            )
            store.importSettingsBackup(encodeCompatSettingsBackup(backup), restoreUserSettings = true, restoreNgRules = false)

            val keys = store.tabs.first().map(CompatTab::key)
            assertEquals(COMPAT_TAB_LIMIT_AFTER_TRIM, keys.size)
            assertTrue("tab-1" in keys, "favourite tab must survive the trim")
            assertTrue("tab-2" in keys, "restored active tab must survive the trim")
            assertFalse("tab-3" in keys)
            assertEquals("tab-2", store.workspace.first().activeTabKey)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun exportLeavesPostDeleteKeysOutButOldBackupsStillRestoreThem() = runBlocking {
        val directory = Files.createTempDirectory("compat-backup-delete-key").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoard(board)
            val oldBackup = CompatSettingsBackup(
                exportedAtEpochMillis = 1L,
                boards = listOf(board),
                preferences = mapOf(
                    "compat.common.commonPostDeleteKey" to "pass1234",
                    "compat.lastDeleteKey" to "legacy",
                    "compat.thread.threadFontSize" to "18"
                )
            )
            store.importSettingsBackup(
                encodeCompatSettingsBackup(oldBackup.settingsOnly()),
                restoreUserSettings = true,
                restoreNgRules = false
            )
            assertEquals("pass1234", store.preferences.first()["compat.common.commonPostDeleteKey"])

            val exported = decodeCompatSettingsBackup(store.exportSettingsBackup())
            assertFalse("compat.common.commonPostDeleteKey" in exported.preferences)
            assertFalse("compat.lastDeleteKey" in exported.preferences)
            assertFalse("pass1234" in store.exportSettingsBackup())
            assertEquals("18", exported.preferences["compat.thread.threadFontSize"])

            // Restoring a new backup (without the key) keeps the device's delete key.
            store.importSettingsBackup(
                encodeCompatSettingsBackup(exported.settingsOnly()),
                restoreUserSettings = true,
                restoreNgRules = false
            )
            assertEquals("pass1234", store.preferences.first()["compat.common.commonPostDeleteKey"])
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }
}
