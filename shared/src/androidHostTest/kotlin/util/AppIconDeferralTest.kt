package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppIconDeferralTest {
    @Test
    fun aliasChangeFromAVisibleActivityIsHeldOnlyFromApi37() {
        assertTrue(shouldDeferLauncherAliasChange(sdkInt = 37, requestedFromActivity = true))
        assertTrue(shouldDeferLauncherAliasChange(sdkInt = 38, requestedFromActivity = true))
        // Older versions keep changing the icon at once.
        assertFalse(shouldDeferLauncherAliasChange(sdkInt = 36, requestedFromActivity = true))
        assertFalse(shouldDeferLauncherAliasChange(sdkInt = 26, requestedFromActivity = true))
        // The held request itself is applied through the application context.
        assertFalse(shouldDeferLauncherAliasChange(sdkInt = 37, requestedFromActivity = false))
    }

    @Test
    fun heldChangeIsAppliedOnlyWhenNoTaskOfTheAppIsVisible() {
        assertTrue(shouldApplyDeferredIconOnStop(isChangingConfigurations = false, anyAppTaskVisible = false))
        // A picker over the app's task, or a relaunched root, keeps a task visible.
        assertFalse(shouldApplyDeferredIconOnStop(isChangingConfigurations = false, anyAppTaskVisible = true))
        assertFalse(shouldApplyDeferredIconOnStop(isChangingConfigurations = true, anyAppTaskVisible = false))
    }
}
