@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.graphics.Bitmap
import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.privacy.*
import com.valoser.futacha.shared.util.createFileSystem
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** M58: actual separate media Dialog, actual PNG/Coil pixels, shared privacy host; isolated DB, no network or audio. */
@SdkSuppress(minSdkVersion = 29)
class FutaberMediaPrivacyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "futaber_media_privacy_test.db"
    private lateinit var store: AndroidCompatibilityStore
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())
    private lateinit var loader: ImageLoader
    private lateinit var image: File
    private val loaded = AtomicInteger()
    private val dialogFilter = hasTestTag("privacy-screen-filter") and hasAnyAncestor(hasTestTag("futaber-media"))

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize(); state.setPrivacyFilterEnabled(false) }
        image = File(rule.activity.cacheDir, "m58-white.png")
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        loader = ImageLoader.Builder(rule.activity).diskCache(null).memoryCache(null).eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        state.close()
        rule.activity.deleteDatabase(database)
        if (::image.isInitialized) image.delete()
    }

    private fun host(selectSave: Boolean = false, initialViewer: Boolean = false) {
        val url = image.toURI().toString()
        val board = BoardSummary("m58", "模擬板", "", "https://may.2chan.net/b/futaba.php", "")
        val page = ThreadPage("58", "模擬板", null, null, listOf(Post(
            id = "58", author = null, subject = null, timestamp = "", messageHtml = "M58",
            imageUrl = url, thumbnailUrl = url, thumbnailWidth = 256, thumbnailHeight = 256
        )))
        runBlocking {
            store.savePreferences(mapOf("compat.futaber.ext.gallerySelectSave" to if (selectSave) "ON" else "OFF",
                PRIVACY_SCOPE_KEY to "SCREEN", PRIVACY_FILTER_KEY to "DIM", PRIVACY_STRENGTH_KEY to "50"))
        }
        val services = FutaberMediaServices(store, null, createFileSystem(rule.activity), null)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    PrivacyModeHost(store, state, compatibilityMode = false) {
                        Box(Modifier.fillMaxSize()) {
                            FutaberMediaOverlay(board, FutaberThreadRef("m58", "58", "M58", url, 1), page, services,
                                initialPostNo = if (initialViewer) "58" else null, onShowPost = {}, onClose = {})
                        }
                    }
                }
            }
        }
        if (selectSave && !initialViewer) waitTag("futaber-select-save-gallery")
        val target = if (initialViewer) "compat-viewer-image-page" else if (selectSave) "compat-gallery-image-58" else "futaber-gallery-item"
        waitTag(target)
        rule.waitUntil(5_000) { loaded.get() > 0 }
        rule.waitUntil(5_000) { samples(target).min() > .9f }
    }

    private fun waitTag(tag: String) {
        // The shared gallery's clickable cell merges its image semantics; inspect the child in the unmerged tree.
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Sample the actual displayed window, including its ancestor filter, with the Dialog's screen offset. */
    private fun samples(tag: String): List<Float> {
        val target = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val offset = IntArray(2)
        rule.runOnIdle {
            val dialog = WindowInspector.getGlobalWindowViews().filter {
                it !== rule.activity.window.decorView && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
            }.single()
            val screen = IntArray(2)
            val window = IntArray(2)
            dialog.getLocationOnScreen(screen)
            dialog.getLocationInWindow(window)
            offset[0] = screen[0] - window[0]
            offset[1] = screen[1] - window[1]
        }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val x = target.center.x.toInt() + offset[0]
            val y = target.center.y.toInt() + offset[1]
            assertTrue("Bitmap sample must be inside the actual Dialog on screen", x - 24 >= 0 && x + 24 < screenshot.width && y in 0 until screenshot.height)
            return (-24..24).map { delta -> android.graphics.Color.red(screenshot.getPixel(x + delta, y)) / 255f }
        } finally { screenshot.recycle() }
    }

    private fun enable(filter: String = "DIM") {
        runBlocking { store.savePreference(PRIVACY_FILTER_KEY, filter); state.setPrivacyFilterEnabled(true) }
        // The host's original window proves the real flow has recomposed even on the unfixed product.
        val description = if (filter == "MESH") "暗くする＋網目 50%" else "暗くする 50%"
        rule.waitUntil(5_000) { rule.onAllNodes(hasStateDescription(description), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun assertDimmed(tag: String, mesh: Boolean = false) {
        // A black loading frame must never count as successful privacy over a displayed white image.
        rule.waitUntil(5_000) { samples(tag).min() > .15f }
        val pixels = samples(tag)
        assertTrue("Separate media window must darken the white bitmap: $pixels", pixels.max() < .6f)
        if (mesh) assertTrue("Mesh lines must be drawn in this window", pixels.max() - pixels.min() > .1f)
        else assertTrue("DIM must not add mesh lines", pixels.max() - pixels.min() < .04f)
        rule.onNode(dialogFilter, useUnmergedTree = true).assertExists()
    }

    @Test fun screenDimCoversGalleryViewerAndReturnWhileSettingsStayReadable() {
        host()
        enable()
        assertDimmed("futaber-gallery-item")
        rule.onNodeWithTag("futaber-gallery-settings").performClick()
        waitTag("compat-settings-list-viewer")
        rule.onNode(dialogFilter, useUnmergedTree = true).assertDoesNotExist()
        Espresso.pressBack()
        waitTag("futaber-gallery-item")
        assertDimmed("futaber-gallery-item")
        rule.onNodeWithTag("futaber-gallery-item").performClick()
        waitTag("compat-viewer-image-page")
        assertDimmed("compat-viewer-image-page")
        Espresso.pressBack()
        waitTag("futaber-gallery-item")
        assertDimmed("futaber-gallery-item")
        runBlocking { state.setPrivacyFilterEnabled(false) }
        rule.waitUntil(5_000) { samples("futaber-gallery-item").min() > .9f }
        rule.onNode(dialogFilter, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun screenMeshCoversAnInitiallyOpenedViewerAndImagesScopeKeepsItsOwnFilter() {
        host(initialViewer = true)
        enable("MESH")
        assertDimmed("compat-viewer-image-page", mesh = true)
        runBlocking { store.savePreferences(mapOf(PRIVACY_SCOPE_KEY to "IMAGES", PRIVACY_IMAGE_STRENGTH_KEY to "50")) }
        rule.waitUntil(5_000) { rule.onAllNodes(dialogFilter, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        // Semantics can update before the OS compositor presents the corresponding image-only frame.
        var latest = emptyList<Float>()
        try {
            rule.waitUntil(5_000) {
                latest = samples("compat-viewer-image-page")
                val minimum = latest.min()
                val maximum = latest.max()
                android.util.Log.i("AuditM58", "IMAGES frame min=$minimum max=$maximum spread=${maximum - minimum}")
                minimum > .4f && maximum < .6f && maximum - minimum < .04f
            }
        } catch (failure: Throwable) {
            runCatching {
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val evidence = File(rule.activity.filesDir, "audit-m58-images-scope-frame.png")
                screenshot?.let { bitmap ->
                    evidence.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                android.util.Log.e("AuditM58", "IMAGES did not present a flat dimmed frame; last=$latest screenshot=${evidence.absolutePath}")
            }
            throw failure
        }
        val imagesOnly = samples("compat-viewer-image-page")
        assertTrue("Image-only privacy must still dim the bitmap", imagesOnly.max() < .6f)
        assertTrue("Screen mesh must not leak into image-only mode", imagesOnly.max() - imagesOnly.min() < .04f)
        rule.onNodeWithTag("futaber-viewer-counter").assertIsDisplayed()
        runBlocking { state.setPrivacyFilterEnabled(false) }
        rule.waitUntil(5_000) { samples("compat-viewer-image-page").min() > .9f }
    }

    @Test fun screenFilterAlsoCoversTheSelectionSaveGallery() {
        host(selectSave = true)
        enable("MESH")
        assertDimmed("compat-gallery-image-58", mesh = true)
        rule.onNodeWithTag("futaber-select-save-gallery").assertIsDisplayed()
        rule.onNodeWithTag("compat-gallery-item-58").performClick()
        waitTag("compat-viewer-image-page")
        assertDimmed("compat-viewer-image-page", mesh = true)
    }
}
