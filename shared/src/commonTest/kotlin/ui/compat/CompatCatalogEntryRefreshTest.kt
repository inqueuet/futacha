package com.valoser.futacha.shared.ui.compat

import kotlin.test.*

class CompatCatalogEntryRefreshTest {
    @Test fun entryOnlySeparatesExplicitOpeningFromReturningToACachedCatalog() {
        assertTrue(compatCatalogShouldReload(true, false, true, true, false))
        assertFalse(compatCatalogShouldReload(true, true, true, false, false))
        assertTrue(compatCatalogShouldReload(false, false, true, false, false))
        assertTrue(compatCatalogShouldReload(true, false, true, false, true))
        assertFalse(compatCatalogShouldReload(true, false, false, true, false))
        assertTrue(compatCatalogShouldReload(true, true, false, false, false))
    }
}
