@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.state.createPlatformStateStorage
import com.valoser.futacha.shared.ui.FutachaApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppLockCommandsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun relockedAppDefersPlatformBridgeAndDeepLinkCommandsUntilPasswordIsEntered() {
        val folder = File(rule.activity.cacheDir, "lock-command-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(rule.activity.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = folder
        }
        val store = createAppStateStore(context)
        // The Android delegate intentionally shares one process-wide DataStore.
        // A ContextWrapper filesDir does not isolate it from earlier test runs.
        val storage = createPlatformStateStorage(context)
        val previousPasswordHash = runBlocking { store.appLockPasswordHash.first() }
        val previousAiEnabled = runBlocking { store.isAiCommandEnabled.first() }
        val platformCommand = mutableStateOf<FutachaAiCommand?>(null)
        val aiDeepLink = mutableStateOf<String?>(null)
        var platformConsumed = false
        var deepLinkConsumed = false
        try {
            runBlocking { store.clearAppLockPassword(); store.setAiCommandEnabled(true) }
            rule.setContent {
                FutachaApp(
                    stateStore = store,
                    platformAiCommand = platformCommand.value,
                    onPlatformAiCommandConsumed = { platformConsumed = true; platformCommand.value = null },
                    platformAiDeepLink = aiDeepLink.value,
                    onPlatformAiDeepLinkConsumed = { deepLinkConsumed = true; aiDeepLink.value = null }
                )
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("メニュー").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { store.setAppLockPassword("audit-password") }
            rule.waitForIdle()
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText("解除").assertIsDisplayed()
            rule.runOnUiThread {
                platformCommand.value = FutachaAiCommand(FutachaAiAction.ClearHistory)
                aiDeepLink.value = "futacha://ai?action=open_gallery"
                FutachaAiCommandBridge.enqueue(FutachaAiCommand(FutachaAiAction.ClearSavedThreads))
            }
            rule.waitForIdle()
            rule.onNodeWithText("解除").assertIsDisplayed()
            rule.onNodeWithText("AI操作の確認").assertDoesNotExist()
            org.junit.Assert.assertFalse(platformConsumed)
            org.junit.Assert.assertFalse(deepLinkConsumed)
            rule.onNodeWithText("パスワード").performTextInput("audit-password")
            rule.onNodeWithText("解除").performClick()
            rule.waitUntil(10_000) { platformConsumed && deepLinkConsumed }
            rule.onNodeWithText("AI操作の確認").assertIsDisplayed()
        } finally {
            rule.runOnUiThread { platformCommand.value = null; aiDeepLink.value = null }
            runBlocking {
                storage.updateAppLockPasswordHash(previousPasswordHash.orEmpty())
                store.setAiCommandEnabled(previousAiEnabled)
            }
            store.close()
        }
    }
}
