@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import platform.FileProvider.NSFileProviderManager
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L75: the preferred-provider folder lookup called the iOS 16 `getUserVisibleURLForItemIdentifier` with no check,
 * and on iOS 15 (the app's minimum is 15.1) the unrecognized selector ends the app. The call is made only when
 * the manager has it; otherwise the folder picker opens at its default location.
 */
class IosFileProviderRootLookupTest {
    @Test
    fun aManagerWithoutTheIos16MethodTakesTheDefaultPicker() {
        // An object without the method stands for the iOS 15 manager (no iOS 15 runtime is installed here).
        assertFalse(fileProviderRootUrlLookupAvailable(NSObject()))
    }

    @Test
    fun theCheckedSelectorIsTheOneTheIos16ManagerHas() {
        // On this iOS 26 simulator the manager has it, so the lookup still runs where it can.
        assertTrue(NSFileProviderManager.instancesRespondToSelector(NSSelectorFromString(FILE_PROVIDER_USER_VISIBLE_URL_SELECTOR)))
    }
}
