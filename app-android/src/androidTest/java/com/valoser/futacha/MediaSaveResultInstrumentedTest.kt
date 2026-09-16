package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class MediaSaveResultInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = createFileSystem(context)
    private val root = "private/media_save_ui_${System.nanoTime()}"
    private var loader: ImageLoader? = null
    private var client: HttpClient? = null

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader?.shutdown()
        client?.close()
        runBlocking { fs.deleteRecursively(root) }
    }

    private fun openPreview(failSave: Boolean, requireFolder: Boolean = false) {
        val image = ByteArrayOutputStream().also {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE)
                compress(Bitmap.CompressFormat.PNG, 100, it)
                recycle()
            }
        }.toByteArray()
        runBlocking { fs.writeBytes("$root/source.png", image).getOrThrow() }
        val source = fs.resolveAbsolutePath("$root/source.png")
        val mediaUrl = if (failSave) "https://save-test.invalid/failure.png" else source
        val page = ThreadPage("123", "保存テスト", null, null, listOf(
            Post("123", 0, null, null, "test", messageHtml = "保存結果の表示テスト", imageUrl = mediaUrl, thumbnailUrl = source)
        ))
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThread(board: String, threadId: String) = page
            override suspend fun getThreadByUrl(threadUrl: String) = page
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
            override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
        }
        val http = HttpClient(MockEngine { respond("missing", HttpStatusCode.NotFound) }).also { client = it }
        val images = ImageLoader(context).also { loader = it }
        val destination = if (requireFolder) "Documents" else fs.resolveAbsolutePath("$root/export")
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                    MaterialTheme {
                        ThreadScreen(
                            board = BoardSummary("save-ui-test", "保存テスト", "test", "https://save-test.invalid/b/", ""),
                            history = emptyList(), threadId = "123", threadTitle = "保存テスト", initialReplyCount = 0,
                            repository = repository, httpClient = http, fileSystem = fs,
                            preferencesState = ScreenPreferencesState("test", manualSaveDirectory = destination,
                                manualSaveLocation = SaveLocation.Path(destination), resolvedManualSaveDirectory = destination),
                            onBack = {}
                        )
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("添付画像").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("添付画像").performClick()
        rule.onNodeWithContentDescription("プレビューを閉じる").assertIsDisplayed()
        rule.onNodeWithText("保存", useUnmergedTree = true).performClick()
    }

    @Test fun firstSaveSelectsFolderAndContinuesWithoutAnotherSaveTap() {
        openPreview(false, requireFolder = true)
        // Drive the real system picker through its accessibility tree, including its grant dialog.
        clickSystemText(android.os.Build.MODEL)
        clickSystemText("Documents")
        clickSystemText("USE THIS FOLDER", "このフォルダを使用")
        clickSystemText("ALLOW", "許可")
        rule.waitUntil(15_000) { rule.onAllNodesWithText("保存結果").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("画像を保存しました", substring = true).assertIsDisplayed()
        rule.onNodeWithText("共有").assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
    }

    private fun clickSystemText(vararg labels: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            val node = labels.asSequence().flatMap { root?.findAccessibilityNodeInfosByText(it).orEmpty().asSequence() }
                .firstOrNull { it.text?.toString() in labels && it.isVisibleToUser }
            if (node != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                automation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use { command ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
                }
                android.os.SystemClock.sleep(300)
                return
            }
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("System picker control missing: ${labels.toList()}")
    }

    @Test fun localImageSaveShowsDestinationAndShareAboveThePreview() {
        openPreview(false)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存結果").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("保存結果").assertIsDisplayed()
        rule.onNodeWithText("保存先:", substring = true).assertIsDisplayed()
        rule.onNodeWithText("共有").assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithContentDescription("プレビューを閉じる").assertIsDisplayed()
    }

    @Test fun remoteFailureIsVisibleAboveThePreviewAndCanBeDismissed() {
        openPreview(true)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存結果").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("HTTP 404", substring = true).assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithContentDescription("プレビューを閉じる").assertIsDisplayed()
    }
}
