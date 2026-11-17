package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.compat.FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY
import com.valoser.futacha.shared.ui.compat.FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY
import com.valoser.futacha.shared.ui.compat.compatParityControlSummary
import com.valoser.futacha.shared.ui.compat.compatSettingsGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FutachaFlatMediaSaveSettingTest {
    @Test
    fun futachaControlPageOffersTheFlatSaveSwitchOnByDefault() {
        val entry = compatSettingsGroups("control", modernPresentation = true, includeAppStateEntries = false)
            .flatMap { it.second }.single { it.preferenceKey == FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY }
        assertEquals("ON", entry.summary)
        assertNotNull(compatParityControlSummary(FUTACHA_FLAT_MEDIA_SAVE_PREFERENCE_KEY))
    }

    @Test
    fun futachaControlPageOffersTheSwipeToCatalogSwitchOnByDefault() {
        val entry = compatSettingsGroups("control", modernPresentation = true, includeAppStateEntries = false)
            .flatMap { it.second }.single { it.preferenceKey == FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY }
        assertEquals("ON", entry.summary)
        assertNotNull(compatParityControlSummary(FUTACHA_SWIPE_TO_CATALOG_PREFERENCE_KEY))
    }
}
