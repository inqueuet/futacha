package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtutterToolbarLayoutTest {
    @Test fun legacyWidthPreferencesAreClampedWithoutRewritingThem() {
        assertEquals(40, parseFtutterCatalogToolbarMetrics(mapOf(FtutterKeys.CATALOG_ICON_WIDTH to "30")).iconWidth)
        assertEquals(40, parseFtutterThreadToolbarMetrics(mapOf(FtutterKeys.THREAD_ICON_WIDTH to "30")).iconWidth)
    }

    @Test fun crowdedButtonsHaveAUsableMinimumEvenWhenThePreferenceIsThirty() {
        assertEquals(40f, ftutterToolbarButtonWidth(82f, FtutterToolbarMetrics(), 13, true, false))
        assertEquals(40f, ftutterToolbarButtonWidth(411f, FtutterToolbarMetrics(leftSpace = 100, rightSpace = 100), 13, true, false))
        assertEquals(40f, ftutterToolbarButtonWidth(411f, FtutterToolbarMetrics(iconWidth = 30), 3, false, false))
        assertEquals(80f, ftutterToolbarButtonWidth(1000f, FtutterToolbarMetrics(iconWidth = 80), 3, true, false))
    }

    @Test fun largeMarginsCannotHideTheFirstButtonBeforeScrolling() {
        val adjusted = ftutterToolbarMetricsForWidth(82f, FtutterToolbarMetrics(leftSpace = 100, rightSpace = 100))
        assertEquals(21, adjusted.leftSpace)
        assertEquals(21, adjusted.rightSpace)
        assertTrue(adjusted.leftSpace + 40 <= 82)
        assertEquals(FtutterToolbarMetrics(), ftutterToolbarMetricsForWidth(411f, FtutterToolbarMetrics()))
    }

}
