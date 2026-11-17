package com.valoser.futacha.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopAppTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothModesOpenAndSettingsRemainAfterRestart() {
        val dir = Files.createTempDirectory("futacha-desktop-ui").toFile()
        val environment = DesktopEnvironment(dir, File(dir, "cache"))
        environment.install()
        val graph = DesktopAppGraph(environment)
        var complete = false
        try {
            compose.setContent { Box(Modifier.requiredSize(1000.dp, 720.dp)) { DesktopFutachaApp(graph) {} } }
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasText("チュートリアル", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            capture("modern-board")
            compose.onNodeWithContentDescription("メニュー").performClick()
            compose.onAllNodesWithText("設定").onFirst().performClick()
            compose.waitForIdle()
            capture("modern-settings")
            compose.runOnIdle { DesktopBackDispatcher.dispatch() }
            compose.waitForIdle()
            compose.onAllNodes(hasText("チュートリアル", substring = true)).onFirst().performClick()
            compose.waitForIdle()
            capture("modern-catalog")
            compose.runOnIdle { DesktopBackDispatcher.dispatch() }
            compose.waitForIdle()
            runBlocking { graph.switchTo(ExperienceProfile.TOSHIAKI_COMPAT) }
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasText("チュートリアル", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            capture("compat-board")
            compose.onNodeWithContentDescription("その他").performClick()
            compose.onAllNodesWithText("設定").onFirst().performClick()
            compose.waitForIdle()
            capture("compat-settings")
            runBlocking { graph.stateStore.setNgWords(listOf("再起動確認")) }
            runBlocking { graph.stateStore.setAppIconVariant(AppIconVariant.Classic) }
            complete = true
        } finally {
            compose.setContent {}
            runBlocking { DesktopOsIntegration.close(); graph.close() }
            if (complete) {
            val secondEnvironment = DesktopEnvironment(dir, File(dir, "cache"))
            secondEnvironment.install()
            val reopened = DesktopAppGraph(secondEnvironment)
            try { runBlocking {
                reopened.initialize()
                assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, reopened.profile)
                assertEquals(listOf("再起動確認"), reopened.stateStore.ngWords.first())
                assertEquals(AppIconVariant.Classic, reopened.stateStore.appIconVariant.first())
            } } finally { runBlocking { reopened.close() } }
            }
            dir.deleteRecursively()
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        val image = org.jetbrains.skia.Image.makeFromBitmap(bitmap)
        try {
            val data = image.encodeToData()!!
            try { File("build/desktop-ui/$name.png").also { it.parentFile.mkdirs() }.writeBytes(data.bytes) }
            finally { data.close() }
        } finally { image.close() }
    }
}
