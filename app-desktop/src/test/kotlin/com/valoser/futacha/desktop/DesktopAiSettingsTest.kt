package com.valoser.futacha.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ai.*
import com.valoser.futacha.shared.desktop.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class DesktopAiSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compatModeHasSharedAiSettingsWithNgStyleHidingAndDefaultsOff() {
        val directory = Files.createTempDirectory("futacha-compat-ai-ui").toFile()
        File(directory, "profile.txt").writeText("toshiaki_compat\n0")
        val environment = DesktopEnvironment(directory, File(directory, "cache"))
        environment.install()
        val graph = DesktopAppGraph(environment)
        try {
            compose.setContent { Box(Modifier.requiredSize(1000.dp, 800.dp)) { DesktopFutachaApp(graph) {} } }
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("その他").performClick()
            compose.onNodeWithText("設定").performClick()
            compose.onNodeWithTag("compat-settings-list-root").performScrollToNode(hasText("AI・補助機能"))
            compose.onNodeWithText("AI・補助機能").performClick()
            compose.onNodeWithTag("compat-ai-summary-enabled").performScrollTo().assertIsOff()
            compose.onNodeWithTag("compat-ai-moderation-enabled").performScrollTo().assertIsOff()
            compose.onNodeWithTag("openai-api-key").assertDoesNotExist()
            compose.onNodeWithTag("ai-moderation-OPENAI").performScrollTo().performClick()
            compose.onNodeWithTag("openai-api-key").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("閾値以上の候補をNGと同様に非表示にする").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("ai-summary-OPENAI").assertDoesNotExist()
            compose.onNodeWithTag("ai-moderation-DEVICE").performScrollTo().performClick()
            compose.onNodeWithTag("openai-api-key").assertDoesNotExist()
            assertFalse(runBlocking { graph.stateStore.isThreadSummaryModeEnabled.first() })
            assertFalse(runBlocking { graph.stateStore.isAiPostFilterEnabled.first() })
        } finally {
            compose.setContent {}
            runBlocking { DesktopOsIntegration.close(); graph.close() }
            directory.deleteRecursively()
        }
    }

    @Test fun openAiSettingsPersistWithoutEnablingEitherFeature() {
        val directory = Files.createTempDirectory("futacha-ai-ui").toFile()
        val environment = DesktopEnvironment(directory, File(directory, "cache"))
        environment.install()
        val graph = DesktopAppGraph(environment)
        val store = getAiConnectionStore()
        try {
            compose.setContent { Box(Modifier.requiredSize(1000.dp, 800.dp)) { DesktopFutachaApp(graph) {} } }
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("チュートリアル", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(runBlocking { graph.stateStore.isThreadSummaryModeEnabled.first() })
            assertFalse(runBlocking { graph.stateStore.isAiPostFilterEnabled.first() })
            compose.onNodeWithContentDescription("メニュー").performClick()
            compose.onAllNodesWithText("設定").onLast().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("global-settings-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("global-settings-list").performScrollToNode(hasText("AI・補助機能"))
            compose.onNodeWithText("AI・補助機能").performClick()
            compose.onNodeWithTag("openai-api-key").assertDoesNotExist()
            compose.onNodeWithTag("ai-moderation-OPENAI").performScrollTo().performClick()
            compose.onNodeWithTag("ai-summary-OPENAI").assertDoesNotExist()
            compose.onNodeWithTag("openai-api-key").performScrollTo().performTextInput("test-only-desktop-ui-key")
            compose.onNodeWithTag("openai-moderation-threshold").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.85f) }
            compose.onNodeWithText("判定カテゴリを選択（4 / 13）").performScrollTo().performClick()
            compose.onNodeWithTag("openai-category-sexual").performScrollTo().performClick()
            compose.onNodeWithText("閉じる").performClick()
            compose.onNodeWithTag("openai-moderation-auto-hide").performScrollTo().performClick()
            compose.onNodeWithText("AI設定を保存").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("ai-cloud-consent").performScrollTo().performClick()
            compose.onNodeWithText("AI設定を保存").performScrollTo().performClick()
            compose.waitUntil(10_000) { store.state.value.hasApiKey }
            assertEquals(AiProvider.OPENAI, store.state.value.moderationProvider)
            assertEquals(AiProvider.DEVICE, store.state.value.summaryProvider)
            assertEquals(0.85f, store.state.value.moderationThreshold)
            assertFalse(store.state.value.moderationAutoHide)
            assertTrue("sexual" in store.state.value.moderationCategories)
            assertFalse(runBlocking { graph.stateStore.isThreadSummaryModeEnabled.first() })
            assertFalse(runBlocking { graph.stateStore.isAiPostFilterEnabled.first() })
            compose.onNodeWithTag("ai-moderation-enabled").performScrollTo().assertIsEnabled().assertIsOff()
            compose.onNodeWithTag("ai-summary-enabled").performScrollTo().assertIsOff()
            compose.onNodeWithText("APIキーを削除").performScrollTo().performClick()
            compose.waitUntil(10_000) { !store.state.value.hasApiKey }
            assertEquals(AiProvider.DEVICE, store.state.value.moderationProvider)
            compose.onNodeWithTag("openai-api-key").assertDoesNotExist()
            assertFalse(directory.walkTopDown().filter { it.isFile }.any { it.readBytes().decodeToString().contains("test-only-desktop-ui-key") })
        } finally {
            compose.setContent {}
            runBlocking { DesktopOsIntegration.close(); graph.close() }
            if (DesktopPlatform.isMac) {
                val account = MessageDigest.getInstance("SHA-256").digest(directory.canonicalPath.toByteArray()).joinToString("") { "%02x".format(it) }
                ProcessBuilder("/usr/bin/security", "delete-generic-password", "-s", "com.valoser.futacha.desktop.openai", "-a", account)
                    .redirectErrorStream(true).start().apply { inputStream.readBytes(); waitFor() }
            }
            directory.deleteRecursively()
        }
    }
}
