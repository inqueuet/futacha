package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatCatalogFetchSettingsTest {
    @Test
    fun mapsLegacyThreadChoicesToTwentyFiveRows() {
        val settings = compatCatalogFetchSettings(800)

        assertEquals(32, settings.columns)
        assertEquals(25, settings.rows)
        assertEquals(256, settings.titleLines)
        assertEquals(800, settings.approximateThreadCount)
    }

    @Test
    fun mapsTheFullLegacyRangeWithoutFallingBackToFiveColumns() {
        val settings = compatCatalogFetchSettings(3_000)

        assertEquals(120, settings.columns)
        assertEquals(25, settings.rows)
        assertEquals(3_000, settings.approximateThreadCount)
    }

    @Test
    fun preservesReferenceIntegerDivisionForNonMultipleOfTwentyFive() {
        val settings = compatCatalogFetchSettings(801)

        assertEquals(32, settings.columns)
        assertEquals(25, settings.rows)
        assertEquals(800, settings.approximateThreadCount)
    }

    @Test
    fun clampsInvalidPreferenceValuesToTheLegacyRange() {
        assertEquals(50, compatCatalogFetchSettings(1).approximateThreadCount)
        assertEquals(3_000, compatCatalogFetchSettings(9_999).approximateThreadCount)
    }
}
