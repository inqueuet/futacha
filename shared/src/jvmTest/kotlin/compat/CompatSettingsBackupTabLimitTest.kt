package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** P4-3 / P4-4 of the 2026-10-02 round-4 audit. */
class CompatSettingsBackupTabLimitTest {
    private val board = CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)

    private fun tab(no: Int, favorite: Boolean = false, insertedAt: Long = no.toLong()): CompatTab {
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
            insertedAtEpochMillis = insertedAt,
            contentUpdatedAtEpochMillis = insertedAt
        )
    }

    private fun <T> withStore(block: suspend (DesktopCompatibilityStore) -> T): T = runBlocking {
        val directory = Files.createTempDirectory("compat-backup-tab-limit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoard(board)
            block(store)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun moreThanAHundredFavouritesCanBeBackedUpAndRestored() = withStore { store ->
        (1..120).forEach { no -> store.openTab(tab(no, favorite = true)) }
        assertEquals(120, store.tabs.first().size)

        val payload = store.exportSettingsBackup()
        assertEquals(120, decodeCompatSettingsBackup(payload).tabs.size)

        withStore { target ->
            val report = target.importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)
            assertEquals(120, target.tabs.first().size)
            assertEquals(120, report.tabsImported)
        }
    }

    @Test
    fun theBackupLimitStillRejectsAbsurdFiles() {
        val backup = CompatSettingsBackup(
            exportedAtEpochMillis = 1L,
            boards = listOf(board),
            tabs = (1..MAX_COMPAT_BACKUP_TABS + 1).map { tab(it) }
        )
        assertFailsWith<IllegalArgumentException> { validateCompatSettingsBackup(backup) }
    }

    @Test
    fun theReportCountsOnlyRestoredTabsKeptByTheLimit() = withStore { store ->
        // The device's own tabs are newer than every restored one.
        // 990 of them: with the restored ten the largest limit is passed, so raising it (audit M1) cannot keep them all.
        store.importSettingsBackup(
            encodeCompatSettingsBackup(
                CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = listOf(board), tabs = (1..990).map { tab(it, insertedAt = 10_000L + it) })
            ),
            restoreUserSettings = true,
            restoreNgRules = false
        )
        val backup = CompatSettingsBackup(
            exportedAtEpochMillis = 1L,
            boards = listOf(board),
            tabs = (1_001..1_010).map { tab(it, favorite = it == 1_001, insertedAt = it.toLong()) }
        )
        val report = store.importSettingsBackup(
            encodeCompatSettingsBackup(backup),
            restoreUserSettings = true,
            restoreNgRules = false
        )
        val keys = store.tabs.first().map(CompatTab::key).toSet()
        val keptRestored = backup.tabs.count { it.key in keys }
        // Only the favourite survives: the 109 trimmed tabs include the nine other restored ones (the oldest).
        assertEquals(1, keptRestored)
        assertTrue("tab-1001" in keys)
        assertEquals(keptRestored, report.tabsImported)
    }

    @Test
    fun keptCountIgnoresDuplicatesAndMissingKeys() {
        assertEquals(2, countCompatRestoredKept(listOf("a", "a", "b", "c"), setOf("a", "b", "z")))
    }
}
