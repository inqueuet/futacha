package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutaberPostLayoutTest {
    @Test fun theGapAndTheOneLineHeaderStartAsFutaberHasThemAndTakeTheirSettings() {
        val none = FutaberDisplaySettings.from(emptyMap())
        assertEquals(10, none.postGapDp)
        assertFalse(none.headerOneLine)
        val set = FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.POST_GAP to "2", FutaberSettingKeys.HEADER_ONE_LINE to "ON"))
        assertEquals(2, set.postGapDp)
        assertTrue(set.headerOneLine)
        assertEquals(0, FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.POST_GAP to "0")).postGapDp)
        assertEquals(10, FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.POST_GAP to "99")).postGapDp)
    }

    @Test fun theLayoutRowsSitBeforeTheLegacySwitches() {
        val extras = futaberThreadSettingsExtras(services = true)
        assertTrue(extras.indexOf(FutaberThreadSettingsExtra.PostLayout) < extras.indexOf(FutaberThreadSettingsExtra.Legacy))
    }
}
