@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatViewerScreen
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*

/** L56: the viewer's information of the next image is read even while that of the previous one is still being read. */
class CompatViewerInfoInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l56-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/56.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tab = CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, boardKey, "L56", "56", "L56",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1)
    private val first = "${boardUrl}src/561.jpg"
    private val second = "${boardUrl}src/562.jpg"
    private val firstInfo = CompletableDeferred<Unit>()
    private val heads: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var client: HttpClient
    private lateinit var loader: ImageLoader

    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize(); store.upsertBoard(CompatBoard(boardKey, "L56", boardUrl, boardUrl, 0)); store.openTab(tab)
            store.saveThreadSnapshot(CompatThreadSnapshot(tab.key, 1, 1, posts = listOf(first, second).mapIndexed { index, url ->
                CompatPostSnapshot(index, "56${index + 1}", timestamp = "", messageHtml = "body", imageUrl = url,
                    thumbnailUrl = url.replace("/src/", "/thumb/").replace(".jpg", "s.jpg"))
            }))
        }
        val bytes = ByteArrayOutputStream().also { output -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.JPEG, 90, output); recycle()
        } }.toByteArray()
        client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Head) {
                heads += request.url.toString()
                // The first image's information is still being read.
                if (request.url.toString() == first) firstInfo.await()
            }
            respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
        })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }

    @After fun cleanup() {
        firstInfo.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
        if (::client.isInitialized) client.close()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun theNextImagesInformationIsReadWhileThePreviousIsStillRead() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatViewerScreen(tab, 0, store = store, preferences = emptyMap(), ngRules = emptyList(),
                httpClient = client, fileSystem = null, onToolbarEdit = {}, onShowSourcePost = {}, onOpenGallery = { _, _ -> },
                onOpenSettings = {}, onOpenCommonSettings = {}, onBack = {})
        } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-viewer-toolbar-icon-info", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-viewer-toolbar-icon-info", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { first in heads }
        rule.onNodeWithText("閉じる").performClick()

        rule.onNodeWithTag("compat-viewer-toolbar-icon-right", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("compat-viewer-toolbar-icon-info", useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { second in heads }
        // The second image's information comes, while the first one's is still not here.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("形式: image/jpeg").fetchSemanticsNodes().isNotEmpty() }
    }
}
