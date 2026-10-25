@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import org.junit.*
import org.junit.Assert.*

/** Real app navigation, local cached thread and MockEngine images only. */
class CompatMediaChildReturnInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m43_media_return.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/100.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "test board", "100", "Media return",
        replyCount = 80, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1)
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var client: HttpClient
    private lateinit var loader: ImageLoader
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize(); store.upsertBoard(CompatBoard(boardKey, "test board", boardUrl, boardUrl, 0)); store.openTab(tab)
            store.savePreferences(mapOf("compat.viewer.galleryGridViewPortraitClmNum" to "4",
                "compat.viewer.galleryGridViewLandscapeClmNum" to "4"))
            store.saveThreadSnapshot(CompatThreadSnapshot(tab.key, 1, 1, posts = (1..80).map { number ->
                CompatPostSnapshot(number - 1, "$number", timestamp = "", messageHtml = "body-$number",
                    imageUrl = "https://may.2chan.net/b/src/$number.jpg", thumbnailUrl = "https://may.2chan.net/b/thumb/${number}s.jpg")
            }))
        }
        val bytes = ByteArrayOutputStream().also { output -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.JPEG, 90, output); recycle()
        } }.toByteArray()
        client = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg")) })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
        if (::client.isInitialized) client.close()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }
    private fun openGallery() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                MaterialTheme { CompatibilityApp(store, repository = null, httpClient = client, imageLoader = loader, catalogImageLoader = loader,
                    initialThreadDeepLink = threadUrl, onExitApplication = {}) }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("画像一覧").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("画像一覧").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-gallery-item-1").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun child(label: String) {
        // The viewer also has a bottom-toolbar overflow; only the top overflow owns these destinations.
        val topOverflow = hasContentDescription("その他") and SemanticsMatcher("visible top overflow") { node ->
            val bounds = node.boundsInRoot
            bounds.width > 1f && bounds.height > 1f && bounds.right > 0f && bounds.left < rule.activity.window.decorView.width &&
                bounds.top >= 0f && bounds.bottom < rule.activity.window.decorView.height / 3f
        }
        rule.onNode(topOverflow).performClick()
        rule.onNode(hasText(label) and visibleOnScreen()).performClick()
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
    }
    private fun waitCounter(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun visibleOnScreen() = SemanticsMatcher("visible in the activity window") { node ->
        val bounds = node.boundsInRoot
        bounds.width > 1f && bounds.height > 1f && bounds.right > 0f && bounds.bottom > 0f &&
            bounds.left < rule.activity.window.decorView.width && bounds.top < rule.activity.window.decorView.height
    }
    private fun galleryGrid(): SemanticsNodeInteraction {
        val galleryCell = SemanticsMatcher("gallery cell") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("compat-gallery-item-") == true
        }
        // The closed board drawer retains off-screen lazy lists in the semantics tree.
        val grid = hasScrollAction() and hasAnyDescendant(galleryCell) and visibleOnScreen()
        rule.waitUntil(10_000) { rule.onAllNodes(grid).fetchSemanticsNodes().size == 1 }
        return rule.onNode(grid)
    }
    @Test fun viewerReturnsToItsCurrentPageAfterSettingsHelpAndToolbarEditing() {
        openGallery(); rule.onNodeWithTag("compat-gallery-item-1").performClick(); waitCounter("1/80")
        repeat(2) { number ->
            rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { swipeLeft(durationMillis = 400) }
            waitCounter("${number + 2}/80")
        }
        for (label in listOf("表示オプション", "設定", "ヘルプ", "ツールバー編集")) {
            child(label); waitCounter("3/80")
            rule.onNodeWithText("3/80").assertIsDisplayed()
            rule.onNodeWithTag("compat-viewer-image-page").assert(hasContentDescription("No.3の画像"))
        }
    }
    @Test fun galleryReturnsToItsScrolledItemAndPixelOffsetAfterSettingsAndHelp() {
        openGallery()
        galleryGrid().performScrollToIndex(48)
        galleryGrid().performTouchInput { swipeUp(durationMillis = 400) }
        rule.waitForIdle()
        val cells = SemanticsMatcher("gallery cell") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("compat-gallery-item-") == true
        }
        fun anchor(): Pair<String, Float>? = rule.onAllNodes(cells).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 1f }.minByOrNull { it.boundsInRoot.top }?.let {
                val tag = it.config[SemanticsProperties.TestTag]
                tag to (rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value * rule.density.density)
            }
        val before = checkNotNull(anchor())
        assertFalse("Scroll must leave the initial row", before.first == "compat-gallery-item-1")
        for (label in listOf("表示オプション", "設定", "ヘルプ")) {
            child(label)
            rule.waitUntil(10_000) { anchor()?.first == before.first }
            val after = checkNotNull(anchor())
            assertEquals(before.first, after.first)
            assertEquals("Gallery pixel offset after $label", before.second, after.second, 2f)
        }
    }
    @Test fun aFreshViewerEntryUsesItsNewLaunchImageInsteadOfThePreviousCurrentPage() {
        openGallery(); rule.onNodeWithTag("compat-gallery-item-1").performClick(); waitCounter("1/80")
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { swipeLeft(durationMillis = 400) }; waitCounter("2/80")
        child("表示オプション"); waitCounter("2/80")
        Espresso.pressBack(); rule.waitForIdle()
        galleryGrid().performScrollToIndex(0)
        rule.onNodeWithTag("compat-gallery-item-1").performClick(); waitCounter("1/80")
    }
}
