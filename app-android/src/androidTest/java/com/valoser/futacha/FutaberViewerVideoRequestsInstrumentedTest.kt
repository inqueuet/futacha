@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.size.Size
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Coil is intercepted before fetching; the player only receives nonexistent local files and never plays. */
class FutaberViewerVideoRequestsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m56_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private data class Request(val url: String, val size: Size, val fallback: FutabaExtensionFallbackPolicy)
    private val requests = CopyOnWriteArrayList<Request>()
    private var failedSource: String? = null
    private val thumbnail = "https://may.2chan.net/b/thumb/audit-m56s.jpg"

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize() }
        val image = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }.asImage()
        loader = ImageLoader.Builder(rule.activity).diskCache(null).memoryCache(null).components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString()
                val size = chain.size
                requests += Request(url, size, chain.request.futabaExtensionFallbackPolicy())
                android.util.Log.i("AuditM56", "Coil URL=$url size=$size")
                if (url == failedSource) ErrorResult(image = null, request = chain.request, throwable = IOException("fixture original failure"))
                else SuccessResult(image = image, request = chain.request, dataSource = DataSource.MEMORY)
            })
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }
    private fun open(url: String, inApp: Boolean = false) {
        val post = CompatPostSnapshot(position = 0, postNo = "56", timestamp = "", messageHtml = "video request fixture",
            imageUrl = url, thumbnailUrl = thumbnail)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberViewerScreen(listOf(post), 0, "may/b", "56", store, emptyMap(), null, null,
                        onShowPost = {}, onOpenGallery = {}, onOpenSettings = {}, playVideoInApp = inApp, onClose = {})
                }
            }
        }
        rule.onNodeWithTag("futaber-viewer-counter").assertTextEquals("1 of 1")
    }
    private fun video(extension: String, inApp: Boolean) {
        val file = File(rule.activity.cacheDir, "audit-m56-missing.$extension").apply { delete() }
        val url = Uri.fromFile(file).toString()
        open(url, inApp)
        rule.waitUntil(10_000) { requests.any { it.url == thumbnail } }
        if (inApp) rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("futaber-video-error").fetchSemanticsNodes().isNotEmpty()
        } else rule.onNodeWithText("動画を開く").assertIsDisplayed()
        rule.waitForIdle()
        assertTrue("Video must only be opened by the player, never Coil: $requests", requests.none { it.url == url })
        assertTrue("The poster must use measured bounds: $requests", requests.all { it.url == thumbnail && it.size != Size.ORIGINAL })
        assertTrue("A missing poster must not fall back to full video: $requests", requests.all {
            it.fallback.maxAttempts == 0 && !it.fallback.allowVideoFallback && it.fallback.maxVideoAttempts == 0
        })
    }
    @Test fun webmWithoutInAppPlaybackOnlyRequestsItsStaticPoster() = video("webm", false)
    @Test fun mp4WithoutInAppPlaybackOnlyRequestsItsStaticPoster() = video("mp4", false)
    @Test fun webmWithInAppPlaybackOnlyRequestsItsStaticPoster() = video("webm", true)
    @Test fun mp4WithInAppPlaybackOnlyRequestsItsStaticPoster() = video("mp4", true)

    @Test fun failedVideoPosterDoesNotTryTheVideoAsAnImage() {
        failedSource = thumbnail
        video("webm", false)
    }

    @Test fun stillImagesKeepOriginalResolutionAndPinchZoom() {
        val url = "https://may.2chan.net/b/src/audit-m56.jpg"
        open(url)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-viewer-source-ready", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(requests.any { it.url == url && it.size == Size.ORIGINAL })
        val page = rule.onNodeWithTag("compat-viewer-image-page")
        page.performTouchInput {
            val c = center
            pinch(c + Offset(-20f, 0f), c + Offset(-200f, 0f), c + Offset(20f, 0f), c + Offset(200f, 0f))
        }
        assertFalse(page.fetchSemanticsNode().config[SemanticsProperties.StateDescription].startsWith("拡大率 100%"))
    }
    @Test fun failedStillOriginalKeepsThumbnailFallback() {
        val url = "https://may.2chan.net/b/src/audit-m56-failed.jpg"
        failedSource = url
        open(url)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-viewer-thumbnail-fallback", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(requests.any { it.url == url && it.size == Size.ORIGINAL })
        assertTrue(requests.any { it.url == thumbnail })
    }
}
