@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.board.ThreadTopBar
import com.valoser.futacha.shared.ui.privacy.*
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyModeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val databaseName = "privacy_mode_ui_test.db"
    private lateinit var store: AndroidCompatibilityStore
    private val appState = AppStateStore(BaseInMemoryPlatformStateStorage())

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(databaseName)
        appState.close()
    }

    @Test fun futachaFilterPersistsDrawsMeshAndHidesOnlyTheTitle() = verify(compat = false)
    @Test fun compatibilityFilterUsesItsExistingToggleAndKeepsSettingsReadable() = verify(compat = true)

    private fun verify(compat: Boolean) {
        store = AndroidCompatibilityStore(rule.activity, databaseName = databaseName)
        runBlocking { store.initialize() }
        rule.setContent {
            FutachaTheme {
                PrivacyModeHost(store, appState, compatibilityMode = compat) {
                    var settings by remember { mutableStateOf(false) }
                    if (settings) {
                        PrivacySettingsExemption()
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Button(onClick = { settings = false }) { Text("設定を閉じる") }
                                PrivacySettingsControls()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            ThreadTopBar("板", "周囲に見せたくないタイトル", 1, null, false,
                                remember { mutableStateOf("") }, 0, 0, {}, {}, {}, {}, {}, {}, {}, {}, {})
                            Box(Modifier.fillMaxWidth().height(160.dp).background(Color.White).testTag("privacy-pixels")) {
                                Text("周囲に見せたくない本文", color = Color.Black)
                            }
                            Button(onClick = { settings = true }) { Text("プライバシー設定を開く") }
                        }
                    }
                }
            }
        }
        val before = rule.onNodeWithTag("privacy-pixels").captureToImage().toPixelMap()
        assertEquals(1f, before[before.width / 2, before.height / 2].red, 0.01f)
        rule.onNodeWithText("プライバシー設定を開く").performClick()
        rule.onNodeWithTag("privacy-mode-toggle").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                if (compat) store.preferences.first()["compat.common.commonPrivacy"] == "ON"
                else appState.isPrivacyFilterEnabled.first()
            }
        }
        rule.onNodeWithText("暗くする＋網目").performScrollTo().performClick()
        rule.onNodeWithText("タイトルを隠す").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { store.preferences.first()[PRIVACY_TITLE_KEY] == "HIDDEN" } }
        rule.onNodeWithText("設定を閉じる").performScrollTo().performClick()
        rule.onNodeWithTag("privacy-screen-filter").assertExists()
        rule.onNodeWithText("周囲に見せたくないタイトル").assertDoesNotExist()
        rule.onNodeWithText("スレッド").assertIsDisplayed()
        rule.onNodeWithText("周囲に見せたくない本文").assertIsDisplayed()
        val after = rule.onNodeWithTag("privacy-pixels").captureToImage().toPixelMap()
        // Capture from the root so the ancestor's filter is included.
        val root = rule.onRoot().captureToImage().toPixelMap()
        val bounds = rule.onNodeWithTag("privacy-pixels").fetchSemanticsNode().boundsInRoot
        val row = bounds.center.y.toInt()
        val values = (bounds.left.toInt() + 10 until bounds.right.toInt() - 10).map { root[it, row].red }
        assertTrue("The screen must darken body content", values.max() < 0.6f)
        assertTrue("The mesh must add visible lines", values.max() - values.min() > 0.1f)
        assertEquals(before.width, after.width)
        rule.onNodeWithText("プライバシー設定を開く").performClick()
        rule.onNodeWithText("タイトルを隠す").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("privacy-mode-toggle").performScrollTo().performClick()
        rule.onNodeWithText("設定を閉じる").performScrollTo().performClick()
        rule.onNodeWithTag("privacy-screen-filter").assertDoesNotExist()
        rule.onNodeWithText("スレッド").assertIsDisplayed()
    }
}
