package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatWatchNgRestoreWorkspaceTest {
    @Test
    fun watchAndNgOnlyRestoreKeepsTheOpenTabsAndActiveTab() = runBlocking {
        val directory = Files.createTempDirectory("watch-ng-restore").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoard(CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0))
            val url = "https://may.2chan.net/b/res/1.htm"
            val tab = CompatTab(
                key = "tab-1",
                canonicalUrl = url,
                originalUrl = url,
                boardKey = "may-b",
                boardName = "虹裏",
                threadNo = "1",
                title = "スレ",
                insertedAtEpochMillis = 1L,
                contentUpdatedAtEpochMillis = 1L
            )
            store.openTab(tab)
            store.updateWorkspace(store.workspace.first().copy(threadSelectorOpen = true))
            val before = store.workspace.first()
            assertEquals("tab-1", before.activeTabKey)

            val wordsOnly = CompatSettingsBackup(
                exportedAtEpochMillis = 2L,
                preferences = mapOf(COMPAT_WATCH_WORDS_PREFERENCE_KEY to "foo")
            ).watchAndNgOnly()
            store.importSettingsBackup(
                encodeCompatSettingsBackup(wordsOnly),
                restoreUserSettings = true,
                restoreNgRules = true
            )

            val after = store.workspace.first()
            assertEquals("tab-1", after.activeTabKey)
            assertTrue(after.threadSelectorOpen)
            assertEquals(listOf("tab-1"), store.tabs.first().map { it.key })
            assertEquals("foo", store.preferences.first()[COMPAT_WATCH_WORDS_PREFERENCE_KEY])
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }
}
