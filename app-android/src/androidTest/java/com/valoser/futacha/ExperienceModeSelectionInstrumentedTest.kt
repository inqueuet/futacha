@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ExperienceProfileAvailability
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.ui.board.GlobalSettingsModeSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The mode choice in the settings of a build that is not debuggable (preview modes off): ふたばー風モード is
 * offered next to the two older modes, and choosing it asks for the switch.
 */
class ExperienceModeSelectionInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var savedPreview = false
    private var savedMobile = false

    @Before
    fun simulateAReleaseBuild() {
        savedPreview = ExperienceProfileAvailability.previewModesEnabled
        savedMobile = ExperienceProfileAvailability.mobileModesEnabled
        ExperienceProfileAvailability.previewModesEnabled = false
        ExperienceProfileAvailability.mobileModesEnabled = true
    }

    @After
    fun restore() {
        ExperienceProfileAvailability.previewModesEnabled = savedPreview
        ExperienceProfileAvailability.mobileModesEnabled = savedMobile
    }

    @Test
    fun futaberIsOfferedInSettingsOfAReleaseBuildAndAsksBeforeSwitching() {
        var requested: ExperienceProfile? = null
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalExperienceProfileUiController provides ExperienceProfileUiController(
                        isAvailable = true,
                        activeProfile = ExperienceProfile.FUTACHA,
                        requestSwitch = { requested = it }
                    )
                ) { GlobalSettingsModeSection() }
            }
        }
        rule.onNodeWithText("モード").performClick()
        rule.onNodeWithText("ふたちゃモード").assertIsDisplayed()
        rule.onNodeWithText("としあき(仮)モード").assertIsDisplayed()
        rule.onNodeWithText("ふたばー風モード").assertIsDisplayed()
        // The development-only notice is gone.
        assertEquals(0, rule.onAllNodesWithText("開発版のみ", substring = true).fetchSemanticsNodes().size)

        rule.onNodeWithText("ふたばー風モード").performClick()
        rule.onNodeWithText("ふたばー風モードへ切り替えますか？").assertIsDisplayed()
        rule.onNodeWithText("切り替える").performClick()
        rule.waitForIdle()
        assertEquals(ExperienceProfile.FUTABER, requested)
    }
}
