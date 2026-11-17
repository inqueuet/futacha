package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 参考アプリ 18-20's third toolbar state 「非表示」, taken into としあき(仮)'s thread toolbar. */
class CompatToolbarHiddenStateTest {
    private fun item(key: String, active: Boolean, hidden: Boolean = false, position: Int = 0) =
        CompatToolbarItem(key, position, active, hidden)

    @Test
    fun theStatesFollowTheOriginalsOrderAndLabels() {
        val onBar = item("close", active = true)
        val inMenu = onBar.nextCompatToolbarState()
        val hidden = inMenu.nextCompatToolbarState()
        assertEquals(listOf("ツールバーに表示", "メニューに表示", "非表示"), listOf(onBar, inMenu, hidden).map(::compatToolbarStateLabel))
        assertEquals(onBar, hidden.nextCompatToolbarState())
    }

    @Test
    fun aHiddenCommandLeavesTheOtherMenuToo() {
        val items = listOf(item("close", active = false, hidden = true), item("check", active = false), item("post", active = true))
        assertEquals(listOf("check"), compatToolbarMenuItems(items).map(CompatToolbarItem::key))
        val menu = compatThreadOtherMenu(
            route = CompatOtherMenuRoute.THREAD_ROOT,
            ngEnabled = true,
            canUndoClose = false,
            ngCount = 0,
            activeToolbarKeys = compatToolbarKeysOutOfMenu(items)
        ).map(CompatOtherMenuItem::key)
        assertFalse("close" in menu)
        assertTrue("check" in menu)
    }

    @Test
    fun onlyTheThreadToolbarKeepsTheHiddenState() {
        val thread = reconcileCompatToolbar(
            CompatToolbarSurface.THREAD,
            compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { i, m ->
                item(m.key, active = false, hidden = m.key == "close", position = i)
            }
        )
        assertTrue(thread.single { it.key == "close" }.hidden)
        val catalog = reconcileCompatToolbar(
            CompatToolbarSurface.CATALOG,
            compatToolbarMaster(CompatToolbarSurface.CATALOG).mapIndexed { i, m -> item(m.key, active = false, hidden = true, position = i) }
        )
        assertTrue(catalog.none(CompatToolbarItem::hidden))
        // A command on the toolbar is never also hidden.
        val both = reconcileCompatToolbar(
            CompatToolbarSurface.THREAD,
            compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { i, m -> item(m.key, active = true, hidden = true, position = i) }
        )
        assertTrue(both.none(CompatToolbarItem::hidden))
    }

    @Test
    fun the1820BackupValueTwoIsHiddenOnTheThreadToolbarAndAMenuEntryElsewhere() {
        val decoded = decodeCompatLegacyBackup(
            """{"strFileType":"setting",
              "arrThreadToolbarList":[["close","2","0"],["check","0","1"],["post","1","2"]],
              "arrCatalogToolbarList":[["privacy","2","0"]]}"""
        )
        val thread = decoded.toolbars.getValue(CompatToolbarSurface.THREAD)
        assertTrue(thread.single { it.key == "close" }.hidden)
        assertFalse(thread.single { it.key == "check" }.hidden)
        val catalog = decoded.toolbars.getValue(CompatToolbarSurface.CATALOG)
        assertFalse(catalog.single { it.key == "privacy" }.let { it.active || it.hidden })
    }

    @Test
    fun theSettingsBackupKeepsTheHiddenStateAndOldBackupsReadAsMenu() {
        val backup = CompatSettingsBackup(
            exportedAtEpochMillis = 1L,
            toolbars = listOf(CompatToolbarBackup("THREAD", listOf(CompatToolbarBackupItem("close", 0, active = false, hidden = true))))
        )
        val round = decodeCompatSettingsBackup(encodeCompatSettingsBackup(backup))
        assertTrue(round.toolbars.single().items.single().hidden)
        assertFalse(CompatToolbarBackupItem("close", 0, active = false).hidden)
    }
}
