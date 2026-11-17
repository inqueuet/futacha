@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.disk.DiskCache
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.media.prompt.readLocalGenerationMetadata
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

/** Exercises real post cards and Android selection/clipboard without external board traffic. */
class PromptDisplayInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = java.io.File(context.cacheDir, "prompt-test-${System.nanoTime()}")
    private val database = "prompt_${System.nanoTime()}.db"
    private var compatStore: AndroidCompatibilityStore? = null
    private val calls = AtomicInteger()
    private val localReads = AtomicInteger()
    private var expectedCalls = 1
    private val localFile = java.io.File(context.cacheDir, "compat_post_preview/saved prompt.png")
    private val boardUrl = "https://may.2chan.net/b/"
    private val mediaUrl = "${boardUrl}src/prompt.png"
    private val positive = "  cat\r\n" + "detailed fur, natural light, ".repeat(35) + "\r\n  tail  "
    private val bytes by lazy { png("$positive\r\nNegative prompt: blurry\r\nSteps: 20, Sampler: Euler, Seed: 42") }
    private val gate = MediaFeatureGate()
    private val settings = mutableStateOf(MediaFeatureSettings.Disabled)
    private val privacy = mutableStateOf(false)
    private val originals = OriginalMediaStore("prompt-ui", {
        DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
    }, { request, sink ->
        calls.incrementAndGet()
        sink.write(bytes)
        OriginalMediaInfo("image/png", bytes.size.toLong(), resolvedUrl = request.url)
    })
    private val prompts = PromptMediaSource(originals, gate, readLocalMetadata = { url ->
        localReads.incrementAndGet(); readLocalGenerationMetadata(url, context)
    })
    private val images = ImageLoader.Builder(context).diskCache(null).components { addOriginalMediaSupport(prompts) }.build()
    private var page = ThreadPage("1001", "生成情報確認", "END", null,
        listOf(Post("1001", 0, "としあき", null, "09/17 12:00", messageHtml = "PROMPT-POST-BODY",
            imageUrl = mediaUrl, thumbnailUrl = mediaUrl)))
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page
        override suspend fun getThreadByUrl(threadUrl: String) = page
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
    }

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        images.shutdown()
        prompts.close()
        runBlocking { withTimeout(5_000) { originals.closeAndAwait() }; compatStore?.closeForTest() }
        context.deleteDatabase(database)
        directory.deleteRecursively()
        localFile.delete()
    }

    private fun open(compat: Boolean) {
        if (compat) compatStore = runBlocking {
            AndroidCompatibilityStore(context, databaseName = database).also {
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "1.0")
                it.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "生成情報確認", boardUrl, boardUrl, 0))
            }
        }
        rule.runOnUiThread {
            rule.activity.setContent {
                SideEffect { gate.update(settings.value) }
                CompositionLocalProvider(
                    LocalFutachaImageLoader provides images,
                    LocalOriginalMediaSource provides prompts,
                    LocalMediaFeatureSettings provides settings.value,
                    LocalMediaFeatureGate provides gate,
                    LocalPromptContentVisible provides !privacy.value
                ) {
                    MaterialTheme {
                        if (compatStore != null) {
                            CompatibilityApp(store = compatStore!!, repository = repository, imageLoader = images, catalogImageLoader = images,
                                initialThreadDeepLink = "${boardUrl}res/1001.htm", onExitApplication = {})
                        } else {
                            ThreadScreen(board = BoardSummary("prompt", "生成情報確認", "test", boardUrl, ""),
                                history = emptyList(), threadId = "1001", threadTitle = "生成情報確認", initialReplyCount = 1,
                                repository = repository, preferencesState = ScreenPreferencesState("test"), onBack = {})
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("PROMPT-POST-BODY").fetchSemanticsNodes().isNotEmpty() && calls.get() == expectedCalls }
        rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.runOnIdle { settings.value = MediaFeatureSettings(promptDisplayEnabled = true); gate.update(settings.value) }
        expandInitiallyCollapsedInlinePrompt()
        val capture = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.filesDir, "prompt-${if (compat) "compat" else "futacha"}.png").outputStream().use {
            capture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun expandInitiallyCollapsedInlinePrompt() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("prompt-inline-toggle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("prompt-inline-toggle").performTouchInput { click() }
        rule.onNodeWithTag("prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun futachaCopiesFullPositiveAndOffHidesAllPromptUi() = copyAndDisable(false)
    @Test fun compatCopiesFullPositiveAndOffHidesAllPromptUi() = copyAndDisable(true)

    @Test fun savedFilePromptCopiesWithoutAnyHttpAcquisition() {
        savedOriginal(contentUri = false)
        copyAndDisable(false)
        assertTrue(localReads.get() > 0)
    }

    @Test fun savedContentUriPromptCopiesInCompatWithoutAnyHttpAcquisition() {
        savedOriginal(contentUri = true)
        copyAndDisable(true)
        assertTrue(localReads.get() > 0)
    }

    private fun savedOriginal(contentUri: Boolean) {
        localFile.parentFile!!.mkdirs(); localFile.writeBytes(bytes)
        val url = if (contentUri) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile).toString()
            else localFile.toURI().toASCIIString().replace("file:/", "file:///")
        page = page.copy(posts = page.posts.map { it.copy(imageUrl = url, thumbnailUrl = url) })
        expectedCalls = 0
    }

    private fun copyAndDisable(compat: Boolean) {
        open(compat)
        rule.onNodeWithTag("prompt-ai-label", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("prompt-inline-toggle").performTouchInput { click() }
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("prompt-ai-label", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("prompt-inline-toggle").performTouchInput { click() }
        rule.onNodeWithText("コピー", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("コピーしました", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(positive, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        // Consumed long press opens generation information, not the parent post action sheet.
        rule.onNodeWithTag("prompt-inline-text", useUnmergedTree = true).performTouchInput { longClick() }
        rule.onNodeWithText("プロンプト", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(positive, useUnmergedTree = true).assertExists()
        rule.runOnIdle { settings.value = MediaFeatureSettings.Disabled; gate.update(settings.value) }
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("プロンプト", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(expectedCalls, calls.get())
    }

    @Test fun placementAndPrivacySuppressOnlyTheChosenSurfaces() {
        open(false)
        rule.runOnIdle { settings.value = settings.value.copy(promptPlacement = PromptPlacement.LABELS_ONLY); gate.update(settings.value) }
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("prompt-ai-label", useUnmergedTree = true).assertExists()
        rule.runOnIdle { settings.value = settings.value.copy(promptPlacement = PromptPlacement.INLINE_ONLY); gate.update(settings.value) }
        expandInitiallyCollapsedInlinePrompt()
        rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).assertCountEquals(0)
        rule.runOnIdle { privacy.value = true }
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(1, calls.get())
    }

    @Test fun futachaViewerOpensGenerationInfoWithoutAnotherDownload() = viewerInfo(false)
    @Test fun compatViewerOpensGenerationInfoWithoutAnotherDownload() = viewerInfo(true)

    private fun viewerInfo(compat: Boolean) {
        open(compat)
        repeat(2) {
            rule.onNodeWithContentDescription(if (compat) "No.1001の画像" else "添付画像", useUnmergedTree = true)
                .performTouchInput { click() }
            val inViewer = hasAnyAncestor(hasTestTag("viewer-prompt-panel"))
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasTestTag("viewer-prompt-toggle") and inViewer, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(hasTestTag("prompt-ai-label") and inViewer, useUnmergedTree = true).assertIsDisplayed()
            rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
            rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
            rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
            rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
            rule.onNode(hasTestTag("prompt-ai-label") and inViewer, useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
            rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
            val infoButton = hasText("生成情報") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            rule.onNode(infoButton).performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText(positive, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(positive, useUnmergedTree = true).assertExists()
            rule.onNodeWithText("閉じる").performClick()
            rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("viewer-prompt-panel").fetchSemanticsNodes().isEmpty() }
        }
        assertEquals(1, calls.get())
    }

    @Test fun metadataObservationWaitsForPermissionAndRecoversWhenReopened() {
        runBlocking { prompts.acquire(OriginalMediaRequest(mediaUrl)).close() }
        var visible by mutableStateOf(true)
        val enabled = MediaFeatureSettings(promptDisplayEnabled = true)
        rule.runOnUiThread { rule.activity.setContent {
            CompositionLocalProvider(LocalOriginalMediaSource provides prompts,
                LocalMediaFeatureSettings provides enabled, LocalMediaFeatureGate provides gate) {
                MaterialTheme {
                    if (visible) {
                        val metadata = rememberGenerationMetadata(mediaUrl)
                        Column { PromptAiBadge(metadata); InlinePrompt(metadata) }
                    }
                }
            }
        } }
        repeat(2) { visit ->
            if (visit > 0) rule.runOnIdle { visible = true }
            rule.waitForIdle()
            // The persisted setting reaches Compose before the corresponding gate update.
            // Publishing permission alone must start observing an already cached original.
            gate.update(enabled)
            expandInitiallyCollapsedInlinePrompt()
            rule.onNodeWithTag("prompt-ai-label", useUnmergedTree = true).assertIsDisplayed()
            rule.runOnIdle { visible = false }
            val revision = prompts.changes.value
            gate.update(MediaFeatureSettings.Disabled)
            runBlocking { withTimeout(5_000) { prompts.changes.first { it > revision } } }
        }
        assertEquals(1, calls.get())
    }

    @Test fun parsedMetadataAppearsWithoutInteractionWhileCacheProbeIsStillPending() {
        val probeStarted = CompletableDeferred<Unit>()
        val finishProbe = CompletableDeferred<Unit>()
        val delayedCache = object : OriginalMediaSource by originals {
            override suspend fun acquire(request: OriginalMediaRequest): OriginalMediaStore.Lease {
                if (!request.allowNetwork) {
                    probeStarted.complete(Unit)
                    finishProbe.await()
                }
                return originals.acquire(request)
            }
        }
        val source = PromptMediaSource(delayedCache, gate)
        val enabled = MediaFeatureSettings(promptDisplayEnabled = true)
        gate.update(enabled)
        try {
            rule.runOnUiThread { rule.activity.setContent {
                CompositionLocalProvider(LocalOriginalMediaSource provides source,
                    LocalMediaFeatureSettings provides enabled, LocalMediaFeatureGate provides gate) {
                    MaterialTheme {
                        val metadata = rememberGenerationMetadata(mediaUrl)
                        Column { PromptAiBadge(metadata); InlinePrompt(metadata); PromptInfoAction(metadata) }
                    }
                }
            } }
            rule.waitForIdle()
            runBlocking { withTimeout(5_000) {
                probeStarted.await()
                // The visible media finishes independently of an earlier cache-only lookup.
                source.acquire(OriginalMediaRequest(mediaUrl)).close()
                source.changes.first { source.metadata(mediaUrl)?.hasAiEvidence == true }
            } }
            assertFalse(finishProbe.isCompleted)
            // Do not tap, resize, change settings, or otherwise invalidate the composition.
            rule.waitUntil(5_000) {
                rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).fetchSemanticsNodes().size == 2
            }
            rule.onNodeWithTag("prompt-inline-toggle").assertIsDisplayed()
            rule.onNodeWithTag("viewer-prompt-toggle").assertIsDisplayed()
            rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
            rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
            assertEquals(1, calls.get())
        } finally {
            finishProbe.complete(Unit)
            rule.runOnUiThread { rule.activity.setContent {} }
            source.close()
        }
    }

    private fun png(text: String): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff2468ac.toInt()) }
        val encoded = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val payload = "parameters\u0000$text".toByteArray()
        val type = "tEXt".toByteArray()
        val chunk = ByteArrayOutputStream().also { output -> DataOutputStream(output).use {
            it.writeInt(payload.size); it.write(type); it.write(payload)
            it.writeInt(CRC32().apply { update(type); update(payload) }.value.toInt())
        } }.toByteArray()
        return encoded.copyOfRange(0, encoded.size - 12) + chunk + encoded.copyOfRange(encoded.size - 12, encoded.size)
    }
}
