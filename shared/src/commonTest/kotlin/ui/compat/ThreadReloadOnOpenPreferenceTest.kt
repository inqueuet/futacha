package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadReloadOnOpenPreferenceTest {
    @Test fun catalogSettingDefaultsOnAndIsIndependentOfCatalogReload() {
        val entry = compatSettingsGroups("catalog").flatMap { it.second }
            .single { it.preferenceKey == THREAD_RELOAD_ON_OPEN_KEY }
        assertEquals("ON", entry.summary)
        assertTrue(compatIsBooleanPreference("catalog", entry))
        val preferences = mapOf(compatPreferenceStorageKey("catalog", "catalogOpenWithReload") to "OFF")
        assertTrue(preferences.threadReloadOnOpenEnabled())
        val key = compatPreferenceStorageKey("catalog", THREAD_RELOAD_ON_OPEN_KEY)
        assertFalse((preferences + (key to "OFF")).threadReloadOnOpenEnabled())
        assertTrue((preferences + (key to "ON")).threadReloadOnOpenEnabled())
    }
}
