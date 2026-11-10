package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit round 4, M1 (plan 1-7): a settings restore raises the shared tab limit once instead of closing the restored tabs. */
class SharedTabRestoreLimitTest {
    private val base = "https://may.2chan.net/b/"
    private val board = CompatBoard(compatBoardKey(base), "may", base, base, 0)
    private fun url(no: Int) = "${base}res/$no.htm"
    private fun tab(no: Int, favorite: Boolean = false) = CompatTab(
        compatTabKey(url(no)), url(no), url(no), board.key, "may", "$no", "スレ$no", favorite = favorite,
        insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong()
    )

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("restore-limit").toFile()
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

    private fun backupOf(tabs: List<CompatTab>) =
        encodeCompatSettingsBackup(CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = listOf(board), tabs = tabs))

    private suspend fun DesktopCompatibilityStore.restore(payload: String) =
        importSettingsBackup(payload, restoreUserSettings = true, restoreNgRules = false)

    @Test fun aRestoreOnADeviceWithTheDefaultLimitKeepsEveryTab() = withStore { store ->
        val report = store.restore(backupOf((1..300).map { tab(it) }))
        assertEquals(300, store.tabs.first().size)
        assertEquals(300, report.tabsImported)
        assertEquals("300", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
    }

    @Test fun theStarsLeaveTenMoreThanThemselves() = withStore { store ->
        store.restore(backupOf((1..195).map { tab(it, favorite = true) }))
        assertEquals("205", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
    }

    @Test fun aHigherLimitIsNotLoweredAndARestoreThatFitsChangesNothing() = withStore { store ->
        store.restore(backupOf((1..20).map { tab(it) }))
        assertEquals(null, store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
        store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "400")
        store.restore(backupOf((21..120).map { tab(it) }))
        assertEquals("400", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
    }

    @Test fun aSettingsRestoreOfNoTabsDoesNotRaiseTheLimit() = withStore { store ->
        store.addTabsIfAbsent((1..150).map { tab(it) })
        store.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "100")
        store.restore(backupOf(emptyList()))
        assertEquals("100", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
    }

    @Test fun anNgOnlyRestoreDoesNotRaiseTheLimit() = withStore { store ->
        store.importSettingsBackup(backupOf((1..300).map { tab(it) }), restoreUserSettings = false, restoreNgRules = true)
        assertEquals(null, store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
        assertEquals(0, store.tabs.first().size)
    }

    @Test fun theLimitStopsAtTheLargestValueAndTheRestIsTrimmedAsBefore() = withStore { store ->
        store.restore(backupOf((1..1_000).map { tab(it) }))
        assertEquals("$COMPAT_SHARED_TAB_LIMIT_MAX", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
        assertEquals(1_000 - (1_000 - COMPAT_SHARED_TAB_LIMIT_MAX * 9 / 10), store.tabs.first().size)
    }
}
