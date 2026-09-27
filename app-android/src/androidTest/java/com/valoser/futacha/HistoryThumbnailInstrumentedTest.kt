@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkResponse
import coil3.request.ErrorResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.board.HistoryDrawerContent
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalHistoryImageRepositories
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class HistoryThumbnailInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = java.io.File(context.cacheDir, "history-image-${System.nanoTime()}")
    private val fs = createFileSystem(context)
    private val repository = SavedThreadRepository(fs, root.absolutePath)
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardId = compatBoardKey(boardUrl)
    private val threadUrl = "${boardUrl}res/123.htm"
    private val remoteUrl = "${boardUrl}thumb/missing.jpg"
    private val networkRequests = AtomicInteger()
    private val images = ImageLoader.Builder(context).components {
        add(Interceptor { chain ->
            if (chain.request.data.toString().startsWith("https://")) {
                networkRequests.incrementAndGet()
                ErrorResult(null, chain.request, HttpException(NetworkResponse(code = 404)))
            } else chain.proceed()
        })
    }.build()
    private val database = "history-image-${System.nanoTime()}.db"
    private var store: AndroidCompatibilityStore? = null

    @Before fun saveRealThumbnail() = runBlocking {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF00CC55.toInt()) }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        fs.createDirectory("${root.absolutePath}/current/thumb").getOrThrow()
        fs.writeBytes("${root.absolutePath}/current/thumb/op.png", bytes).getOrThrow()
        repository.addThreadToIndex(SavedThread(
            threadId = "123", boardId = boardId, boardName = "b", title = "保存画像の確認",
            storageId = "current", thumbnailPath = "thumb/op.png", savedAt = 1,
            postCount = 1, imageCount = 1, videoCount = 0, totalSize = bytes.size.toLong(), status = SaveStatus.COMPLETED
        )).getOrThrow()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        images.shutdown()
        runBlocking { store?.closeForTest() }
        context.deleteDatabase(database)
        root.deleteRecursively()
    }

    @Test fun modernHistoryDisplaysSavedPixelsForMissingRemoteUrl() {
        val entry = ThreadHistoryEntry("123", boardId, "保存画像の確認", remoteUrl, "b", threadUrl, 1, 0, hasAutoSave = true)
        rule.runOnUiThread { rule.activity.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalHistoryImageRepositories provides listOf(repository)) {
                MaterialTheme { HistoryDrawerContent(listOf(entry), {}, {}) }
            }
        } }
        val image = rule.onNodeWithContentDescription("保存画像の確認 のタイトル画像", useUnmergedTree = true)
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("保存画像の確認 のタイトル画像", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertSavedPixels(image)
    }

    @Test fun compatibilityHistoryUsesProvidedLoaderAndSavedPixels() {
        val fixture = runBlocking {
            AndroidCompatibilityStore(context, databaseName = database).also {
                store = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "999.0")
                it.upsertBoard(CompatBoard(boardId, "b", boardUrl, boardUrl, 0))
                it.upsertHistory(CompatHistoryEntry(threadUrl, threadUrl, boardId, "b", "123", "保存画像の確認", remoteUrl, contentUpdatedAtEpochMillis = 1))
            }
        }
        rule.runOnUiThread { rule.activity.setContent {
            MaterialTheme {
                CompatibilityApp(store = fixture, repository = null, historyAutoSavedThreadRepository = repository,
                    imageLoader = images, catalogImageLoader = images, onExitApplication = {})
            }
        } }
        rule.onNodeWithContentDescription("ドロワー").performClick()
        val image = rule.onNodeWithTag("compat-drawer-history-thumb-$threadUrl", useUnmergedTree = true)
        assertSavedPixels(image)
    }

    private fun assertSavedPixels(image: SemanticsNodeInteraction) {
        rule.waitUntil(10_000) {
            runCatching {
                val pixels = image.captureToImage().toPixelMap()
                pixels[pixels.width / 2, pixels.height / 2].toArgb() == 0xFF00CC55.toInt()
            }.getOrDefault(false)
        }
        image.assertIsDisplayed()
        assertEquals(0, networkRequests.get())
    }
}
