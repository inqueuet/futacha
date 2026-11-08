@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(coil3.annotation.DelicateCoilApi::class)
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.*
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Every remote thumbnail must use the injected loader, including on composition removal. */
class CompatThumbnailCancellationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val started = AtomicInteger()
    private val cancelled = AtomicInteger()
    private val singletonStarted = AtomicInteger()
    private var loader: ImageLoader? = null
    private var singleton: ImageLoader? = null
    private var store: AndroidCompatibilityStore? = null
    private val database = "thumbnail-cancel-${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    // .invalid plus a terminal interceptor ensures neither loader can access a server.
    private val imageUrl = "https://thumbnail-cancellation.invalid/thumb/123.jpg"
    private val threadUrl = "${boardUrl}res/123.htm"
    private fun tab() = CompatTab(threadUrl, threadUrl, threadUrl, boardKey, "b", "123", "fixture",
        imageUrl, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)

    private fun host(content: @Composable () -> Unit) {
        val context = rule.activity
        singleton = ImageLoader.Builder(context).memoryCache(null).diskCache(null).components {
            add(Interceptor { singletonStarted.incrementAndGet(); awaitCancellation() })
        }.build().also { SingletonImageLoader.setUnsafe(it) }
        val images = ImageLoader.Builder(context).memoryCache(null).diskCache(null).components {
            add(Interceptor {
                started.incrementAndGet()
                try { awaitCancellation() } finally { cancelled.incrementAndGet() }
            })
        }.build().also { loader = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                MaterialTheme { content() }
            }
        }
    }

    private fun assertInjectedRequestAndCancel() {
        rule.waitUntil(10_000) { started.get() + singletonStarted.get() > 0 }
        assertEquals("Coil singleton bypasses Android's safe response close", 0, singletonStarted.get())
        assertEquals(1, started.get())
        // AsyncImagePainter.onForgotten cancels its job on the UI thread, as in the crash.
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitUntil(5_000) { cancelled.get() == 1 }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader?.shutdown()
        singleton?.shutdown()
        SingletonImageLoader.reset()
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    @Test fun drawerTabUsesInjectedLoaderAndCancelsWhenRemoved() {
        host { CompatThreadMetadataRow(tab(), {}, {}) }
        assertInjectedRequestAndCancel()
    }

    @Test fun tabSelectorUsesInjectedLoaderAndCancelsWhenRemoved() {
        host { CompatTabSelector(listOf(tab()), threadUrl, true, {}, {}) }
        assertInjectedRequestAndCancel()
    }

    @Test fun droppedCatalogUsesInjectedLoaderAndCancelsWhenRemoved() {
        val item = CatalogItem("123", threadUrl, "fixture", imageUrl, null, replyCount = 1)
        host { CompatDroppedCatalogScreen("b", listOf(CompatDroppedCatalogItem(boardKey, item, 1)), {}, {}, {}) }
        assertInjectedRequestAndCancel()
    }

    @Test fun watcherDrawerUsesInjectedLoaderAndCancelsWhenRemoved() {
        val fixture = runBlocking {
            AndroidCompatibilityStore(rule.activity, databaseName = database).also {
                store = it
                it.initialize()
                it.savePreference("compat.commonUsedVersion", "999.0")
                it.upsertBoard(CompatBoard(boardKey, "b", boardUrl, boardUrl, 0))
                val entry = CompatHistoryEntry(threadUrl, threadUrl, boardKey, "b", "123", "fixture", imageUrl,
                    contentUpdatedAtEpochMillis = 1)
                it.savePreference("compat.watcher.result.0", Json.encodeToString(
                    CompatWatchResult(entry, "fixture", System.currentTimeMillis())))
            }
        }
        host { CompatibilityApp(store = fixture, repository = null, imageLoader = loader!!,
            catalogImageLoader = loader!!, onExitApplication = {}) }
        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("巡回結果").performClick()
        assertInjectedRequestAndCancel()
    }

    private fun productionLoader(client: HttpClient?) = buildFutachaImageLoader(
        rule.activity, Dispatchers.IO, Dispatchers.Default,
        MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build(), null,
        AdaptiveImageRequestGate(ImageMemoryPressurePolicy(ImageMemoryPressureLevel.NORMAL, 2, 1024 * 1024L, 1)),
        client
    ).also { loader = it }

    private fun png(): ByteArray = java.io.ByteArrayOutputStream().use { output ->
        val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        } finally { bitmap.recycle() }
    }

    @Test fun startupWithoutHttpClientHasNoImplicitNetworkFetcherButStillDecodesLocalImages() = runBlocking {
        val images = productionLoader(null)
        val remote = images.execute(ImageRequest.Builder(rule.activity).data(imageUrl).build())
        assertTrue(remote.toString(), remote is ErrorResult)
        assertTrue((remote as ErrorResult).throwable.toString(),
            remote.throwable.message.orEmpty().contains("Unable to create a fetcher"))
        val local = images.execute(ImageRequest.Builder(rule.activity).data(png()).build())
        assertTrue(local.toString(), local is SuccessResult)
    }

    @Test fun explicitlyConfiguredNetworkFetcherStillDownloadsAndDecodesImages() = runBlocking {
        val requests = AtomicInteger()
        val bytes = png()
        val client = HttpClient(MockEngine {
            requests.incrementAndGet()
            respond(bytes, headers = headersOf("Content-Type", "image/png"))
        })
        try {
            val images = productionLoader(client)
            val result = images.execute(ImageRequest.Builder(rule.activity).data(imageUrl).build())
            assertTrue(result.toString(), result is SuccessResult)
            assertEquals(1, requests.get())
        } finally { loader?.shutdown(); client.close() }
    }
}
