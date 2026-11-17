package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FtutterCatalogLookTest {
    @Test fun theImageZoomsAndTheTitleOpensTheMenu() {
        assertEquals(FtutterCatalogPress.ZOOM, ftutterCatalogPress(50f, 100f, useZoom = true, legacy = false))
        assertEquals(FtutterCatalogPress.MENU, ftutterCatalogPress(120f, 100f, useZoom = true, legacy = false))
        assertEquals(FtutterCatalogPress.ZOOM, ftutterCatalogPress(120f, null, useZoom = true, legacy = false))
    }

    @Test fun theLegacyPressFollowsTheZoomSetting() {
        assertEquals(FtutterCatalogPress.ZOOM, ftutterCatalogPress(120f, 100f, useZoom = true, legacy = true))
        assertEquals(FtutterCatalogPress.MENU, ftutterCatalogPress(10f, 100f, useZoom = false, legacy = true))
        assertFalse(ftutterLegacyCatalogPress(emptyMap()))
    }

    @Test fun narrowPanesShrinkButtonsToTheIcon() {
        assertEquals(FTUTTER_TOOLBAR_MIN_BUTTON_WIDTH, ftutterToolbarButtonWidth(200f, FtutterToolbarMetrics(), 13, false, false))
    }

    @Test fun aCellInATabIsFramedLikeTheOriginal() {
        assertEquals(FtutterCatalogTabFrame(inset = 1.5f, width = 1f), ftutterCatalogTabFrame(inTab = true))
        assertEquals(null, ftutterCatalogTabFrame(inTab = false))
    }
}
