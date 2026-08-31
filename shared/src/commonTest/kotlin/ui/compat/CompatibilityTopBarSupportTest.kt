package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityTopBarSupportTest {
    @Test
    fun explicitNavigationBack_isIosOnlyAndDoesNotCompeteWithAnOpenDrawer() {
        assertTrue(
            shouldShowCompatExplicitNavigationBack(
                isAndroidPlatform = false,
                isDrawerOpen = false
            )
        )
        assertFalse(
            shouldShowCompatExplicitNavigationBack(
                isAndroidPlatform = true,
                isDrawerOpen = false
            )
        )
        assertFalse(
            shouldShowCompatExplicitNavigationBack(
                isAndroidPlatform = false,
                isDrawerOpen = true
            )
        )
    }
}
