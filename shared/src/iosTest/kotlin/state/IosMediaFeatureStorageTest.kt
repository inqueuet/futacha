package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.media.MEDIA_FEATURE_SETTINGS_KEY
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.PromptPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUserDefaults
import kotlin.test.*

class IosMediaFeatureStorageTest {
    @Test fun defaultsAreOffAndNSUserDefaultsRestoresTheWholeSettingsDocument(): Unit = runBlocking(Dispatchers.Default) {
        val defaults = NSUserDefaults.standardUserDefaults()
        val previous = defaults.stringForKey(MEDIA_FEATURE_SETTINGS_KEY)
        try {
            defaults.removeObjectForKey(MEDIA_FEATURE_SETTINGS_KEY)
            val initial = createAppStateStore()
            assertEquals(MediaFeatureSettings.Disabled, initial.mediaFeatureSettings.first())
            val expected = MediaFeatureSettings(promptDisplayEnabled = true, imageEditorEnabled = true,
                promptPlacement = PromptPlacement.LABELS_ONLY)
            initial.updateMediaFeatureSettings { expected }
            val reopened = createAppStateStore()
            assertEquals(expected, reopened.mediaFeatureSettings.first())
            reopened.updateMediaFeatureSettings { it.copy(promptDisplayEnabled = false) }
            val third = createAppStateStore().mediaFeatureSettings.first()
            assertFalse(third.showAiLabels)
            assertTrue(third.imageEditorEnabled)
        } finally {
            if (previous == null) defaults.removeObjectForKey(MEDIA_FEATURE_SETTINGS_KEY)
            else defaults.setObject(previous, forKey = MEDIA_FEATURE_SETTINGS_KEY)
        }
    }
}
