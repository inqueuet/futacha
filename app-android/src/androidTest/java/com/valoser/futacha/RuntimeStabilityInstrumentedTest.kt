// Exercise shared internal UI/decoder boundaries from the Android host's real
// device test target without adding them to the production public API.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.BitmapImage
import coil3.decode.ImageSource
import coil3.request.Options
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatFastScrollbar
import com.valoser.futacha.shared.ui.compat.CompatPostScreen
import com.valoser.futacha.shared.ui.compat.CompatViewerScreen
import com.valoser.futacha.shared.ui.compat.CompatSettingsScreen
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import com.valoser.futacha.shared.ui.image.CompatFallbackGifDecoder
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RuntimeStabilityInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "stability_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var imageLoader: ImageLoader
    private val board = CompatBoard("board", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val tab = CompatTab("tab", "https://may.2chan.net/b/res/123.htm", "https://may.2chan.net/b/res/123.htm",
        "board", "虹裏", "123", "test", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)

    @Before fun prepare() {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        runBlocking {
            store.initialize()
            store.upsertBoard(board)
            store.openTab(tab, null)
        }
        imageLoader = ImageLoader.Builder(context).build()
    }

    @After fun cleanup() {
        imageLoader.shutdown()
        runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test fun unreadableDraftDoesNotCrashOrOverwriteStoredDraft() {
        val writes = AtomicInteger()
        val failed = AtomicBoolean()
        val faultyStore = object : CompatibilityStore by store {
            override suspend fun loadDraft(tabKey: String): CompatReplyDraft? {
                failed.set(true)
                throw IOException("draft read failed")
            }
            override suspend fun saveDraft(draft: CompatReplyDraft) { writes.incrementAndGet() }
            override suspend fun deleteDraft(tabKey: String) { writes.incrementAndGet() }
        }
        rule.setContent { Post(faultyStore) }
        rule.waitUntil(5_000) { failed.get() }
        rule.onNodeWithText("下書きを読み込めませんでした").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertEquals(0, writes.get())
        rule.onNodeWithTag("compat-post-comment-field").assertIsDisplayed()
    }

    @Test fun acceptedPostStillCompletesWhenLocalSettingsWriteFails() {
        runBlocking { store.saveDraft(CompatReplyDraft(tab.key, comment = "投稿本文", deleteKey = "1234", updatedAtEpochMillis = 1L)) }
        val accepted = AtomicInteger()
        val failedWrites = AtomicInteger()
        val faultyStore = object : CompatibilityStore by store {
            override suspend fun savePreference(key: String, value: String) {
                failedWrites.incrementAndGet()
                throw IOException("disk full")
            }
        }
        rule.setContent { Post(faultyStore, onPostSent = { accepted.incrementAndGet() }) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("投稿本文").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-post-toolbar-icon-send", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { accepted.get() == 1 }
        assertTrue(failedWrites.get() > 0)
        assertEquals(null, runBlocking { store.loadDraft(tab.key) })
    }

    @Test fun unreadableViewerCacheShowsErrorAndAllowsBack() {
        val returned = AtomicBoolean()
        val faultyStore = object : CompatibilityStore by store {
            override suspend fun loadThreadSnapshot(tabKey: String): CompatThreadSnapshot? = throw IOException("cache read failed")
        }
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                    CompatViewerScreen(tab, 0, store = faultyStore, preferences = emptyMap(), ngRules = emptyList(),
                        httpClient = null, fileSystem = null, onToolbarEdit = {}, onShowSourcePost = {}, onOpenGallery = { _, _ -> },
                        onOpenSettings = {}, onOpenCommonSettings = {}, onBack = { returned.set(true) })
                }
            }
        }
        rule.onNodeWithText("画像一覧を読み込めませんでした").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        assertTrue(returned.get())
    }

    @Test fun settingsWriteFailureIsVisibleAndScreenRemainsUsable() {
        val returned = AtomicBoolean()
        val faultyStore = object : CompatibilityStore by store {
            override suspend fun savePreference(key: String, value: String) = throw IOException("disk full")
        }
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalFutachaImageLoader provides imageLoader,
                    LocalFutachaCatalogImageLoader provides imageLoader
                ) {
                    CompatSettingsScreen("control", faultyStore, emptyMap(), null,
                        onNavigate = {}, onBack = { returned.set(true) })
                }
            }
        }
        rule.onNodeWithText("送信時の確認").performScrollTo().performClick()
        rule.onNodeWithText("操作に失敗しました: disk full").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        assertTrue(returned.get())
    }

    @Test fun unreadableToolbarOffersBackInsteadOfCrashing() {
        val returned = AtomicBoolean()
        val faultyStore = object : CompatibilityStore by store {
            override suspend fun loadToolbar(surface: CompatToolbarSurface): List<CompatToolbarItem> = throw IOException("toolbar read failed")
        }
        rule.setContent {
            MaterialTheme {
                CompatToolbarEditorScreen(CompatToolbarSurface.POST, faultyStore) { returned.set(true) }
            }
        }
        rule.onNodeWithText("ツールバーを読み込めませんでした").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.waitUntil(5_000) { returned.get() }
    }

    @Test fun scrollingWithDisabledScrollbarDoesNotRecomposeParent() = checkScrollComposition(enabled = false)
    @Test fun scrollingWithEnabledScrollbarDoesNotRecomposeParent() = checkScrollComposition(enabled = true)
    @Test fun scrollingWithStateOwnedTotalDoesNotRecomposeParent() =
        checkScrollComposition(enabled = true, readTotalInScrollbar = true)

    private fun checkScrollComposition(enabled: Boolean, readTotalInScrollbar: Boolean = false) {
        val compositions = AtomicInteger()
        lateinit var state: LazyListState
        rule.setContent {
            MaterialTheme {
                state = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    SideEffect { compositions.incrementAndGet() }
                    LazyColumn(state = state, modifier = Modifier.testTag("list")) {
                        items(200) { Text("row $it", Modifier.height(50.dp)) }
                    }
                    // The thread screens use the overload that reads the item count itself.
                    if (readTotalInScrollbar) CompatFastScrollbar(enabled, state) else CompatFastScrollbar(enabled, 200, state)
                }
            }
        }
        rule.waitForIdle()
        val before = compositions.get()
        repeat(12) { index ->
            runBlocking { state.scrollToItem(index + 1, 3) }
            rule.waitForIdle()
        }
        assertEquals(12, state.firstVisibleItemIndex)
        assertEquals("Scrolling must invalidate only the scrollbar, not the screen", before, compositions.get())
    }

    @Test fun gifDecoderStopsReadingOversizedInputAndClosesSource() = runBlocking {
        var emitted = 0L
        var closed = false
        val chunk = ByteArray(8192)
        val oversized = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = minOf(byteCount, chunk.size.toLong())
                sink.write(chunk, 0, count.toInt())
                emitted += count
                return count
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }
        val error = runCatching {
            CompatFallbackGifDecoder(ImageSource(oversized.buffer(), FileSystem.SYSTEM), Options(context)).decode()
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException && error.message.orEmpty().contains("Animated image exceeds"))
        assertTrue(emitted <= 32L * 1024 * 1024 + 8192)
        assertTrue(closed)
    }

    @Test fun boundedGifDecoderStillDecodesStaticAndAnimatedImages() = runBlocking {
        fun gif(frames: Int): Buffer = Buffer().apply {
            writeUtf8("GIF89a")
            write(byteArrayOf(1, 0, 1, 0, 0x80.toByte(), 0, 0))
            write(byteArrayOf(0, 0, 0, 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
            repeat(frames) {
                write(byteArrayOf(0x21, 0xf9.toByte(), 4, 0, 10, 0, 0, 0))
                write(byteArrayOf(0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0))
            }
            writeByte(0x3b)
        }
        val single = CompatFallbackGifDecoder(ImageSource(gif(1), FileSystem.SYSTEM), Options(context)).decode()
        assertEquals(1, single.image.width)
        assertEquals(1, single.image.height)
        val animation = CompatFallbackGifDecoder(ImageSource(gif(2), FileSystem.SYSTEM), Options(context)).decode()
        assertTrue("Multiple GIF frames must remain animated", animation.image !is BitmapImage)
    }

    @Composable private fun Post(store: CompatibilityStore, onPostSent: () -> Unit = {}) {
        MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                CompatPostScreen(tab, board, FakeBoardRepository(), store = store,
                    preferences = mapOf("compat.control.controlPostConfirm" to "OFF"), appVersion = "10.8",
                    fileSystem = null, onToolbarEdit = {}, onBack = {}, onPostSent = onPostSent)
            }
        }
    }
}
