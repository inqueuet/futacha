package com.valoser.futacha.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuConfigTest {
    @Test
    fun normalizeThreadMenuConfig_keepsSettingsEnabled_and_restoresRefreshWhenNeeded() {
        val normalized = normalizeThreadMenuConfig(
            listOf(
                ThreadMenuItemConfig(ThreadMenuItemId.Reply, isEnabled = false, order = 5),
                ThreadMenuItemConfig(ThreadMenuItemId.Refresh, isEnabled = false, order = 4),
                ThreadMenuItemConfig(ThreadMenuItemId.Settings, isEnabled = false, order = 0)
            )
        )

        assertTrue(normalized.first { it.id == ThreadMenuItemId.Settings }.isEnabled)
        assertTrue(normalized.first { it.id == ThreadMenuItemId.Refresh }.isEnabled)
        assertEquals(
            ThreadMenuItemId.entries.toSet(),
            normalized.map { it.id }.toSet()
        )
        assertEquals(
            normalized.indices.toList(),
            normalized.map { it.order }
        )
    }

    @Test
    fun normalizeThreadSettingsMenuConfig_restoresNgManagementWhenAllDisabled() {
        val normalized = normalizeThreadSettingsMenuConfig(
            ThreadSettingsMenuItemId.entries.map {
                ThreadSettingsMenuItemConfig(it, isEnabled = false, order = 10 - it.defaultOrder)
            }
        )

        assertTrue(normalized.first { it.id == ThreadSettingsMenuItemId.NgManagement }.isEnabled)
        assertEquals(
            normalized.indices.toList(),
            normalized.map { it.order }
        )
    }

    @Test
    fun normalizeThreadMenuEntries_forcesSettingsIntoBar_and_movesOverflowToSheet() {
        val configs = listOf(
            ThreadMenuEntryConfig(ThreadMenuEntryId.Reply, ThreadMenuEntryPlacement.BAR, 0),
            ThreadMenuEntryConfig(ThreadMenuEntryId.ScrollToTop, ThreadMenuEntryPlacement.BAR, 1),
            ThreadMenuEntryConfig(ThreadMenuEntryId.ScrollToBottom, ThreadMenuEntryPlacement.BAR, 2),
            ThreadMenuEntryConfig(ThreadMenuEntryId.Refresh, ThreadMenuEntryPlacement.BAR, 3),
            ThreadMenuEntryConfig(ThreadMenuEntryId.Gallery, ThreadMenuEntryPlacement.BAR, 4),
            ThreadMenuEntryConfig(ThreadMenuEntryId.Save, ThreadMenuEntryPlacement.BAR, 5),
            ThreadMenuEntryConfig(ThreadMenuEntryId.Filter, ThreadMenuEntryPlacement.BAR, 6),
            ThreadMenuEntryConfig(ThreadMenuEntryId.Settings, ThreadMenuEntryPlacement.HIDDEN, 7),
            ThreadMenuEntryConfig(ThreadMenuEntryId.NgManagement, ThreadMenuEntryPlacement.SHEET, 0)
        )

        val normalized = normalizeThreadMenuEntries(configs, maxBarItems = 4)

        assertEquals(
            ThreadMenuEntryPlacement.BAR,
            normalized.first { it.id == ThreadMenuEntryId.Settings }.placement
        )
        assertEquals(
            4,
            normalized.count { it.placement == ThreadMenuEntryPlacement.BAR }
        )
        assertTrue(
            normalized.any { it.id == ThreadMenuEntryId.Filter && it.placement == ThreadMenuEntryPlacement.SHEET }
        )
    }

    @Test
    fun normalizeThreadMenuEntries_ensuresAtLeastOneBarItem() {
        val normalized = normalizeThreadMenuEntries(
            ThreadMenuEntryId.entries.map {
                ThreadMenuEntryConfig(it, ThreadMenuEntryPlacement.HIDDEN, it.defaultOrder)
            }
        )

        assertEquals(
            ThreadMenuEntryPlacement.BAR,
            normalized.first { it.id == ThreadMenuEntryId.Settings }.placement
        )
    }

    @Test
    fun normalizeCatalogNavEntries_keepsPastThreadSearchHidden_and_restoresSettingsBar() {
        val normalized = normalizeCatalogNavEntries(
            CatalogNavEntryId.entries.map {
                CatalogNavEntryConfig(it, CatalogNavEntryPlacement.HIDDEN, 10 - it.defaultOrder)
            },
            maxBarItems = 2
        )

        assertEquals(
            CatalogNavEntryPlacement.HIDDEN,
            normalized.first { it.id == CatalogNavEntryId.PastThreadSearch }.placement
        )
        assertEquals(
            CatalogNavEntryPlacement.BAR,
            normalized.first { it.id == CatalogNavEntryId.Settings }.placement
        )
    }

    @Test
    fun normalizeCatalogNavEntries_movesOverflowOutOfBar() {
        val normalized = normalizeCatalogNavEntries(
            CatalogNavEntryId.entries.map {
                CatalogNavEntryConfig(it, CatalogNavEntryPlacement.BAR, it.defaultOrder)
            },
            maxBarItems = 3
        )

        assertEquals(
            3,
            normalized.count { it.placement == CatalogNavEntryPlacement.BAR }
        )
        assertTrue(
            normalized.any { it.id == CatalogNavEntryId.Mode && it.placement == CatalogNavEntryPlacement.HIDDEN }
        )
    }
}
