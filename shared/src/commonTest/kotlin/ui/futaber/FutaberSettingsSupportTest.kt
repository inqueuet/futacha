package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FutaberSettingsSupportTest {
    @Test
    fun noStoredValuesGiveTheDefaults() {
        val settings = FutaberDisplaySettings.from(emptyMap())
        assertEquals(14, settings.fontSize)
        assertEquals(3, settings.manyRepliesThreshold)
        assertFalse(settings.smallImages)
        assertTrue(settings.scrollCatalogToTopOnRefresh)
    }

    @Test
    fun storedValuesAreRead() {
        val settings = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.FONT_SIZE to "18",
                FutaberSettingKeys.MANY_REPLIES to "7",
                FutaberSettingKeys.SMALL_IMAGES to "ON",
                FutaberSettingKeys.CATALOG_SCROLL_TOP to "OFF"
            )
        )
        assertEquals(18, settings.fontSize)
        assertEquals(7, settings.manyRepliesThreshold)
        assertTrue(settings.smallImages)
        assertFalse(settings.scrollCatalogToTopOnRefresh)
    }

    @Test
    fun damagedOrOutOfRangeValuesFallBackToTheDefaults() {
        val settings = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.FONT_SIZE to "huge",
                FutaberSettingKeys.MANY_REPLIES to "1",
                FutaberSettingKeys.SMALL_IMAGES to "maybe",
                FutaberSettingKeys.CATALOG_SCROLL_TOP to "???"
            )
        )
        assertEquals(14, settings.fontSize)
        assertEquals(3, settings.manyRepliesThreshold)
        assertFalse(settings.smallImages)
        // Only an explicit OFF turns the scroll off.
        assertTrue(settings.scrollCatalogToTopOnRefresh)
        assertEquals(14, FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.FONT_SIZE to "99")).fontSize)
        assertEquals(14, FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.FONT_SIZE to "9")).fontSize)
    }

    @Test
    fun theStepperStaysInsideItsRange() {
        assertEquals(15, futaberStep(14, 1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX))
        assertEquals(FUTABER_FONT_SIZE_MAX, futaberStep(FUTABER_FONT_SIZE_MAX, 1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX))
        assertEquals(FUTABER_FONT_SIZE_MIN, futaberStep(FUTABER_FONT_SIZE_MIN, -1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX))
        assertEquals(FUTABER_MANY_REPLIES_MIN, futaberStep(FUTABER_MANY_REPLIES_MIN, -1, FUTABER_MANY_REPLIES_MIN, FUTABER_MANY_REPLIES_MAX))
    }

    @Test
    fun textSizesScaleWithTheFontSizeAndStayTheDefaultAtFourteen() {
        val normal = FutaberDisplaySettings()
        assertEquals(12f, normal.sp(12f).value, 0.001f)
        assertEquals(14f, normal.sp(14f).value, 0.001f)
        val larger = FutaberDisplaySettings(fontSize = 21)
        assertEquals(18f, larger.sp(12f).value, 0.001f)
        assertEquals(21f, larger.sp(14f).value, 0.001f)
    }

    @Test
    fun rowWidthTitleNameAndViewerSwipeAreOffUntilTurnedOn() {
        val off = FutaberDisplaySettings.from(emptyMap())
        assertFalse(off.catalogRowWide)
        assertFalse(off.alwaysShowTitleAndName)
        assertFalse(off.viewerSwipeClose)
        val on = FutaberDisplaySettings.from(
            mapOf(
                FutaberSettingKeys.CATALOG_ROW_WIDE to "ON",
                FutaberSettingKeys.TITLE_NAME_ALWAYS to "ON",
                FutaberSettingKeys.VIEWER_SWIPE_CLOSE to "ON"
            )
        )
        assertTrue(on.catalogRowWide)
        assertTrue(on.alwaysShowTitleAndName)
        assertTrue(on.viewerSwipeClose)
    }

    @Test
    fun patrolSwitchFollowsTheSharedKeyAndIsOnUntilItIsTurnedOff() {
        assertTrue(FutaberDisplaySettings.from(emptyMap()).patrolEnabled)
        assertFalse(FutaberDisplaySettings.from(mapOf("compat.watcher.enabled" to "OFF")).patrolEnabled)
        assertTrue(FutaberDisplaySettings.from(mapOf("compat.watcher.enabled" to "ON")).patrolEnabled)
        assertEquals(com.valoser.futacha.shared.compat.COMPAT_WATCH_ENABLED_KEY, FutaberSettingKeys.PATROL_ENABLED)
    }
}
