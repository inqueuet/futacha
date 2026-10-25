@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.board.LocalFutachaAppVersion
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import com.valoser.futacha.shared.ui.futaber.FutaberTheme
import com.valoser.futacha.shared.ui.futaber.FutaberThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * L35: a bug report opened from a help screen that was given no version (as the drawing screen's help) still carries the
 * app's version, and names the colour scheme of the mode on screen rather than matching としあき(仮)'s six.
 */
class BugReportContextInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} } }

    @Test fun theReportCarriesTheVersionAndTheModesScheme() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaAppVersion provides "13.1") {
                FutaberTheme(FutaberThemeMode.Futaba) { CompatHelpScreen(onBack = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("help-report-bug").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("help-report-bug").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("版: ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("版: 13.1", substring = true).assertExists()
        rule.onNodeWithText("配色: ふたば", substring = true).assertExists()
    }
}
