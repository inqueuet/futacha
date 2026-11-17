package com.valoser.futacha.shared.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiKeychainInstallSupportTest {
    private val installedAt = 800_000_000.0

    @Test
    fun keyLeftByAnEarlierInstallIsDiscarded() {
        // Saved a month before this install's container exists.
        assertTrue(isAiKeychainItemFromEarlierInstall(false, installedAt - 2_592_000.0, installedAt))
    }

    @Test
    fun existingUsersKeepTheirKeyAcrossAnUpdate() {
        // The key was saved after the install; the build just has no marker yet.
        assertFalse(isAiKeychainItemFromEarlierInstall(false, installedAt + 86_400.0, installedAt))
        // Created at the same moment (within the margin) is not "earlier".
        assertFalse(isAiKeychainItemFromEarlierInstall(false, installedAt - 0.5, installedAt))
    }

    @Test
    fun nothingIsDiscardedWithoutBothDatesOrOnceTheInstallWasChecked() {
        assertFalse(isAiKeychainItemFromEarlierInstall(false, null, installedAt))
        assertFalse(isAiKeychainItemFromEarlierInstall(false, installedAt - 100.0, null))
        assertFalse(isAiKeychainItemFromEarlierInstall(true, installedAt - 100.0, installedAt))
    }
}
