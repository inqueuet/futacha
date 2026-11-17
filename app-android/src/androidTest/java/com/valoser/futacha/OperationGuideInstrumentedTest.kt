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

    @Test fun modeSpecificHelpSearchExpandsTheActualWebDocument() {
        rule.setContent { MaterialTheme { CompatHelpScreen(onBack = {}, appVersion = "13.3") } }
        for ((word, id) in listOf("ペーストボード" to "futaber-help", "スレッドを先頭から開く" to "ftutter-opening-help")) {
            rule.onNodeWithTag("help-search-field").performTextReplacement(word)
            val quoted = org.json.JSONObject.quote(word)
            rule.waitUntil(15_000) {
                helpDocumentScript("""
                    (function() {
                        const section = document.getElementById('$id');
                        return !!section && section.checked && section.nextElementSibling.offsetHeight > 0 &&
                            Array.from(document.querySelectorAll('mark')).some(mark => mark.textContent === $quoted);
                    })()
                """.trimIndent()) == "true"
            }
        }
    }

    @Test fun currentSpeechHelpSearchExpandsTheActualWebDocument() {
        rule.setContent { MaterialTheme { CompatHelpScreen(onBack = {}, appVersion = "13.3") } }
        rule.onNodeWithTag("help-search-field").performTextInput("ずんだもん")
        rule.waitUntil(15_000) {
            helpDocumentScript("""
                (function() {
                    const section = document.getElementById('speech-help');
                    return !!section && section.checked && section.nextElementSibling.offsetHeight > 0 &&
                        document.body.textContent.includes('83MB') &&
                        document.body.textContent.includes('iOS 16') &&
                        Array.from(document.querySelectorAll('mark')).some(mark => mark.textContent === 'ずんだもん');
                })()
            """.trimIndent()) == "true"
        }
    }

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
            val extraSteps = when (current) {
                ExperienceProfile.FUTABER -> listOf("kana", "pasteboard", "gallery", "keyboard")
                ExperienceProfile.FTUTTER -> listOf("opening")
                else -> emptyList()
            }
            extraSteps.forEach { rule.onNodeWithTag("operation-guide-$it").performScrollTo().assertIsDisplayed() }
            rule.onNodeWithTag("operation-guide-close").performClick()
            rule.onNodeWithTag("help-operation-guide").assertIsDisplayed()
        }
    }
}
