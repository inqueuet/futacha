@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import org.junit.Rule
import org.junit.Test

class OperationGuideInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyModeCanOpenReadAndCloseItsOwnGuideFromHelp() {
        val profile = mutableStateOf(ExperienceProfile.FUTACHA)
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalExperienceProfileUiController provides ExperienceProfileUiController(activeProfile = profile.value)) {
                CompatHelpScreen(onBack = {}, appVersion = "13.0")
            }
        } }
        ExperienceProfile.entries.forEach { current ->
            rule.runOnIdle { profile.value = current }
            rule.onNodeWithTag("help-operation-guide").performClick()
            rule.onNodeWithText("操作ガイド：${current.displayName}").assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-write").assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-attach").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-save").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-back").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-gesture").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("operation-guide-close").performClick()
            rule.onNodeWithTag("help-operation-guide").assertIsDisplayed()
        }
    }
}
