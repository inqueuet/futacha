package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.*

/** Plan B10: the settings backup carries each mode's marks on the shared tabs. */
class SharedTabBackupViewsTest {
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private val board = CompatBoard(boardKey, "may", boardUrl, boardUrl, 0)
    private val futaber = ExperienceProfile.FUTABER.persistedValue
    private val ftutter = ExperienceProfile.FTUTTER.persistedValue
    private fun url(no: Int) = "${boardUrl}res/$no.htm"
    private fun tab(no: Int, title: String = "スレ$no") = CompatTab(
        compatTabKey(url(no)), url(no), url(no), boardKey, "may", "$no", title,
        insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong()
    )

    private fun <T> withStore(block: suspend (DesktopCompatibilityStore) -> T): T = runBlocking {
        val root = Files.createTempDirectory("shared-tab-backup-views").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try { store.initialize(); store.upsertBoard(board); block(store) }
        finally { store.close(); root.deleteRecursively() }
    }

    private suspend fun DesktopCompatibilityStore.order(profile: String) =
        loadTabViews(profile).map { view -> view.tabKey }

    private suspend fun DesktopCompatibilityStore.restore(payload: String) =
        importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)

    @Test fun theMarksComeBackInEachModesOrder() {
        val payload = withStore { source ->
            source.openSharedTab(tab(1), futaber)
            source.openSharedTab(tab(2), futaber)
            source.openSharedTab(tab(2), ftutter)
            source.openSharedTab(tab(3), ftutter, CompatTabPlacement.FIRST)
            source.exportSettingsBackup()
        }
        withStore { target ->
            target.restore(payload)
            assertEquals(listOf(tab(1).key, tab(2).key), target.order(futaber))
            assertEquals(listOf(tab(3).key, tab(2).key), target.order(ftutter))
        }
    }

    @Test fun aRestoreAddsTheMarksAfterTheOnesHereNow() {
        val payload = withStore { source ->
            source.openSharedTab(tab(1), futaber)
            source.openSharedTab(tab(9), futaber)
            source.exportSettingsBackup()
        }
        withStore { target ->
            target.openSharedTab(tab(9), futaber)
            target.openSharedTab(tab(8), futaber)
            target.restore(payload)
            assertEquals(listOf(tab(9).key, tab(8).key, tab(1).key), target.order(futaber))
        }
    }

    @Test fun anOlderFileWithoutMarksRestoresItsTabsAndLeavesTheMarksHere() {
        val payload = withStore { source ->
            source.openSharedTab(tab(1), futaber)
            source.exportSettingsBackup()
        }
        val parsed = Json.parseToJsonElement(payload).jsonObject
        assertTrue("tabViews" in parsed)
        val older = JsonObject(parsed - "tabViews").toString()
        withStore { target ->
            target.openSharedTab(tab(2), ftutter)
            target.restore(older)
            assertEquals(setOf(tab(1).key, tab(2).key), target.tabs.first().map { it.key }.toSet())
            assertEquals(emptyList(), target.order(futaber))
            assertEquals(listOf(tab(2).key), target.order(ftutter))
        }
    }

    @Test fun aRestoreOfOnlyTheNgRulesAppliesNoMarks() {
        val payload = withStore { source ->
            source.openSharedTab(tab(1), futaber)
            source.exportSettingsBackup()
        }
        withStore { target ->
            target.importSettingsBackup(payload, restoreUserSettings = false, restoreNgRules = true)
            assertEquals(emptyList(), target.order(futaber))
        }
    }

    @Test fun aMarkThatCannotApplyIsDroppedAndFailsNothing() {
        val restored = listOf(
            CompatSharedTabView("kept", futaber, 5, 1L),
            CompatSharedTabView("no-such-tab", futaber, 6, 1L),
            CompatSharedTabView("kept", "a-later-mode", 0, 1L),
            CompatSharedTabView("kept", futaber, 7, 1L)
        )
        assertEquals(
            listOf(CompatSharedTabView("kept", futaber, 0, 1L)),
            compatMergedBackupTabViews(emptyList(), restored, setOf("kept"))
        )
    }

    @Test fun onlyTheBackedUpTabsCarryMarks() {
        val views = listOf(CompatSharedTabView(tab(1).key, futaber, 0, 1L), CompatSharedTabView("gone", futaber, 1, 1L))
        assertEquals(listOf(views.first()), compatBackupTabViews(views, listOf(tab(1))))
    }

    @Test fun theTrimAfterARestoreCountsTheRestoredMarks() = withStore { store ->
        val base = decodeCompatSettingsBackup(store.exportSettingsBackup())
        val tabs = (1..1_000).map { tab(it) }
        val views = tabs.mapIndexed { index, item ->
            CompatSharedTabView(item.key, if (index < 500) futaber else ftutter, index, 1L)
        }
        store.restore(encodeCompatSettingsBackup(base.copy(tabs = tabs, tabViews = views)))
        val kept = store.tabs.first().map { it.key }.toSet()
        assertEquals(891, kept.size)
        assertTrue(tab(1).key in kept, "a tab another mode lists stays")
        assertFalse(tab(501).key in kept, "the oldest tab only ふたったー風 lists goes first")
        assertEquals(kept.size, store.loadTabViews(futaber).size + store.loadTabViews(ftutter).size, "a mark goes with its tab")
    }

    @Test fun theLargestTabListWithMarksFitsTheFile() {
        val tabs = (1..990).map { tab(100_000_000 + it, "題".repeat(200)) }
        val views = tabs.flatMapIndexed { index, item ->
            listOf(CompatSharedTabView(item.key, futaber, index, 1_794_000_000_000L), CompatSharedTabView(item.key, ftutter, index, 1_794_000_000_000L))
        }
        val encoded = encodeCompatSettingsBackup(CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = listOf(board), tabs = tabs, tabViews = views))
        assertTrue(encoded.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES)
        assertEquals(1_980, decodeCompatSettingsBackup(encoded).tabViews.size)
    }

    @Test fun marksNeverMakeABackupThatFitsFail() {
        val value = "x".repeat(MAX_COMPAT_PREFERENCE_VALUE_CHARS)
        val count = (MAX_COMPAT_SETTINGS_BACKUP_BYTES - 40_000) / (value.length + 40)
        val preferences = (1..count).associate { "compat.test.$it" to value }
        val views = (0 until 3_000).map { CompatSharedTabView("key$it", futaber, it, 1L) }
        val encoded = encodeCompatSettingsBackup(CompatSettingsBackup(exportedAtEpochMillis = 1L, preferences = preferences, tabViews = views))
        val decoded = decodeCompatSettingsBackup(encoded)
        assertEquals(emptyList(), decoded.tabViews)
        assertEquals(count, decoded.preferences.size)
    }

    @Test fun absurdlyManyMarksAreRefused() {
        val views = (0..MAX_COMPAT_BACKUP_TAB_VIEWS).map { CompatSharedTabView("key$it", futaber, it, 1L) }
        assertFailsWith<IllegalArgumentException> {
            validateCompatSettingsBackup(CompatSettingsBackup(exportedAtEpochMillis = 1L, tabViews = views))
        }
    }
}
