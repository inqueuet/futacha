package com.valoser.futacha

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.media3.common.Player
import org.hamcrest.CoreMatchers.equalTo
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import okio.BufferedSink
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real normal/compat thread -> native player -> generation details, with counted HTTP requests. */
class VideoPromptViewerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "video-prompt-ui-${System.nanoTime()}")
    private val database = "video_prompt_${System.nanoTime()}.db"
    private var compatStore: AndroidCompatibilityStore? = null
    private val gets = AtomicInteger()
    private val boardUrl = "https://may.2chan.net/b/"
    private val positive = "青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye."
    private val movie = VideoEditFixtures.bytes("portrait")
    private val thumbnail = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).let { bitmap ->
        bitmap.eraseColor(0xff2468ac.toInt())
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray().also { bitmap.recycle() }
    }
    private val client = HttpClient(MockEngine { request ->
        val video = request.url.encodedPath.endsWith(".mp4")
        if (video) gets.incrementAndGet()
        val bytes = if (video) movie else thumbnail
        respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf(if (video) "video/mp4" else "image/png"),
            HttpHeaders.ContentLength to listOf(bytes.size.toString())))
    })
    private val session = createOriginalMediaSession(client).apply {
        configure(OriginalMediaCacheConfiguration(directory.path.toPath(), 1024 * 1024))
    }
    private val gate = MediaFeatureGate()
    private val settings = mutableStateOf(MediaFeatureSettings.Disabled)
    private val prompts = PromptMediaSource(session, gate)
    private val images = ImageLoader.Builder(context).diskCache(null).components {
        addOriginalMediaSupport(prompts)
        add(KtorNetworkFetcherFactory(httpClient = client))
    }.build()
    private val page = ThreadPage("1001", "動画生成情報確認", "END", null, (0..2).map { index ->
        Post((1001 + index).toString(), index, "としあき", null, "09/17 12:00", messageHtml = "VIDEO-PROMPT-BODY-$index",
            imageUrl = "${boardUrl}src/prompt-$index.mp4", thumbnailUrl = "${boardUrl}thumb/prompt-$index.png")
    })
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page
        override suspend fun getThreadByUrl(threadUrl: String) = page
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
    }

    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        images.shutdown(); prompts.close()
        runBlocking { withTimeout(5_000) { session.closeAndAwait() }; compatStore?.closeForTest() }
        client.close(); context.deleteDatabase(database); directory.deleteRecursively()
    }

    @Test fun normalVideoViewerCopiesOriginalPromptAndReturnsToCachedLabelWithOneGet() = verify(false)
    @Test fun compatVideoViewerDoesNotDownloadOffscreenPagesAndCopiesPromptWithOneGet() = verify(true)

    @Test fun inactiveViewerNeverFetchesAndLeavingDuringTransferReleasesTheNativeReader() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val failures = AtomicInteger()
        val active = mutableStateOf(false)
        val originals = OriginalMediaStore("partial-viewer", {
            DiskCache.Builder().directory(File(directory, "partial").path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink) = download(request, sink) {}
            override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
                calls.incrementAndGet()
                try {
                    onHeaders(OriginalMediaInfo("video/mp4", movie.size.toLong(), resolvedUrl = request.url))
                    sink.write(movie, 0, 512).emit()
                    started.complete(Unit)
                    awaitCancellation()
                } finally { cancelled.complete(Unit) }
            }
        })
        try {
            rule.runOnUiThread { rule.activity.setContent {
                CompositionLocalProvider(LocalOriginalMediaSource provides originals) {
                    PlatformVideoPlayer("${boardUrl}src/partial.mp4", isActive = active.value,
                        onPlaybackError = { failures.incrementAndGet() })
                }
            } }
            rule.waitForIdle()
            assertEquals(0, calls.get())
            rule.runOnIdle { active.value = true }
            rule.waitUntil(10_000) { started.isCompleted }
            // Let the native player open its data source and wait for the missing tail.
            rule.waitUntil(5_000) {
                runCatching { onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, failure ->
                    if (failure != null) throw failure
                    assertNotNull(view.javaClass.getMethod("getPlayer").invoke(view))
                } }.isSuccess
            }
            rule.runOnIdle { active.value = false }
            rule.waitUntil(5_000) { cancelled.isCompleted }
            assertEquals(1, calls.get())
            assertEquals("A disposed viewer must not report a late download failure", 0, failures.get())
            runBlocking {
                assertTrue(runCatching { originals.acquire(OriginalMediaRequest("${boardUrl}src/partial.mp4", allowNetwork = false)).close() }.isFailure)
            }
        } finally {
            rule.runOnUiThread { rule.activity.setContent {} }
            rule.waitForIdle()
            runBlocking { withTimeout(5_000) { originals.closeAndAwait() } }
        }
    }

    private fun verify(compat: Boolean) {
        if (compat) compatStore = runBlocking { AndroidCompatibilityStore(context, databaseName = database).also {
            it.initialize(); it.savePreference("compat.commonUsedVersion", "1.0")
            it.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "動画生成情報確認", boardUrl, boardUrl, 0))
        } }
        rule.runOnUiThread { rule.activity.setContent {
            SideEffect { gate.update(settings.value) }
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalOriginalMediaSource provides prompts,
                LocalMediaFeatureSettings provides settings.value, LocalMediaFeatureGate provides gate) {
                MaterialTheme {
                    if (compat) CompatibilityApp(store = compatStore!!, repository = repository, imageLoader = images, catalogImageLoader = images,
                        initialThreadDeepLink = "${boardUrl}res/1001.htm", onExitApplication = {})
                    else ThreadScreen(board = BoardSummary("video-prompt", "動画生成情報確認", "test", boardUrl, ""),
                        history = emptyList(), threadId = "1001", threadTitle = "動画生成情報確認", initialReplyCount = 3,
                        repository = repository, preferencesState = ScreenPreferencesState("test"), onBack = {})
                }
            }
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("VIDEO-PROMPT-BODY-0").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Thumbnails/AI labels must not fetch the video original", 0, gets.get())
        rule.onAllNodesWithContentDescription(if (compat) "No.1001の画像" else "添付画像", useUnmergedTree = true)[0].performClick()
        rule.waitUntil(10_000) { gets.get() == 1 }
        rule.waitUntil(10_000) {
            var ready = false
            runCatching {
                onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, failure ->
                    if (failure != null) throw failure
                    val player = view.javaClass.getMethod("getPlayer").invoke(view) as? Player
                    ready = player?.playbackState == Player.STATE_READY && player.videoSize.width == 240 && player.videoSize.height == 320
                }
            }
            ready
        }
        onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, _ ->
            (view.javaClass.getMethod("getPlayer").invoke(view) as Player).play()
        }
        rule.waitUntil(5_000) {
            var advanced = false
            onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, _ ->
                val player = view.javaClass.getMethod("getPlayer").invoke(view) as Player
                advanced = player.currentPosition > 0 && player.playerError == null
                if (advanced) player.pause()
            }
            advanced
        }
        rule.runOnIdle { settings.value = MediaFeatureSettings(promptDisplayEnabled = true); gate.update(settings.value) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("viewer-prompt-toggle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        val viewerLabel = hasTestTag("prompt-ai-label") and hasAnyAncestor(hasTestTag("viewer-prompt-panel"))
        rule.onNode(viewerLabel, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
        rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
        rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onNode(viewerLabel, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
        // Hiding playback controls must preserve the expanded prompt and AI label.
        onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, _ ->
            view.javaClass.getMethod("hideController").invoke(view)
        }
        rule.mainClock.advanceTimeBy(4_500)
        rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(viewerLabel, useUnmergedTree = true).assertIsDisplayed()
        val info = hasText("生成情報") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        rule.waitUntil(10_000) { rule.onAllNodes(info).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(info).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(positive, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("プロンプト をコピー", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("コピーしました", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            assertEquals(positive, (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())
        }
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
        onView(withClassName(equalTo("androidx.media3.ui.PlayerView"))).check { view, _ ->
            view.javaClass.getMethod("showController").invoke(view)
        }
        rule.waitForIdle()
        // The normal viewer is a separate Dialog window. Send Back to the
        // focused window instead of Espresso's cached Activity root.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("viewer-prompt-panel").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, gets.get())
        rule.onAllNodesWithContentDescription(if (compat) "No.1001の画像" else "添付画像", useUnmergedTree = true)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("viewer-prompt-toggle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        rule.onNode(viewerLabel, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("viewer-prompt-toggle").performTouchInput { click() }
        rule.onNodeWithTag("viewer-prompt-inline-text", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { settings.value = MediaFeatureSettings.Disabled; gate.update(settings.value) }
        rule.onAllNodesWithTag("viewer-prompt-panel").assertCountEquals(0)
        rule.onAllNodesWithTag("prompt-ai-label", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("prompt-inline-text", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(1, gets.get())
    }
}
