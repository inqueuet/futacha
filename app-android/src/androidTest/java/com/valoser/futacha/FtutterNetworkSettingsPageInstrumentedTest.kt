@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** M77/M48: ネットワーク・書き込み follows the original — two sections, クッキーの再取得 with the original's confirmation, the key shown. */
class FtutterNetworkSettingsPageInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val prefs = mutableStateMapOf<String, String>()
    private var reacquired = 0

    private fun page() = rule.setContent {
        MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterNetworkSettingsPage(prefs, save = { k, v -> prefs[k] = v }, deleteKey = "abc123",
                onOpenCookies = {}, onOpenSharedNetwork = {}, onBack = {}, onReacquireCookies = { reacquired++ })
        } }
    }

    @Test fun theSectionsAndTheKeyAreTheOriginals() {
        page()
        rule.onNodeWithText("ネットワーク").assertExists()
        rule.onNodeWithText("書き込み").assertExists()
        rule.onNodeWithText("abc123").assertExists()
        val list = rule.onNodeWithTag("ftutter-settings-page-network-list")
        list.performScrollToNode(hasTestTag("ftutter-settings-cookies"))
        rule.onNodeWithText(FTUTTER_MORE_LABEL).assertExists()
    }

    @Test fun reacquireAsksFirstAndCancelDoesNothing() {
        page()
        rule.onNodeWithTag("ftutter-settings-cookie-reacquire").performClick()
        rule.onNodeWithText(FTUTTER_COOKIE_REACQUIRE_TEXT).assertExists()
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitForIdle()
        assertEquals(0, reacquired)
        rule.onNodeWithTag("ftutter-settings-cookie-reacquire").performClick()
        rule.onNodeWithTag("ftutter-cookie-reacquire-ok").performClick()
        rule.waitForIdle()
        assertEquals(1, reacquired)
        rule.onNodeWithText(FTUTTER_COOKIE_REACQUIRE_TEXT).assertDoesNotExist()
    }
}
