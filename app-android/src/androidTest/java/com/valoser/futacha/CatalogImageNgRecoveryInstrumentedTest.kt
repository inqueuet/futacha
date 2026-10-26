@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.futaber.futaberPhashHiddenIds
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

/** Actual registration dialog, Android bitmap hash, SQLite rule/hash persistence and catalog predicate. No live GET. */
class CatalogImageNgRecoveryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "catalog-ng-recovery-${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient
    private val token = System.nanoTime()
    private val guessed = "https://www.example.com/b/src/$token.jpg"
    private val confirmed = "https://www.example.com/b/src/$token.png"
    private val thumbnail = "https://www.example.com/b/thumb/${token}s.jpg"
    private var originalDeleted = false
    private val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val boardKey = compatBoardKey("https://may.2chan.net/b/")
    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize()
            store.upsertBoard(CompatBoard(boardKey, "fixture", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0))
        }
        loader = ImageLoader.Builder(rule.activity).components { add(TutorialImageFetcherFactory()) }.build()
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        image.setPixels(IntArray(1024) { i -> if (i % 32 / 4 == i / 32 / 4) -1 else -16777216 }, 0, 32, 0, 0, 32, 32)
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if ("/thumb/" !in request.url.encodedPath && (originalDeleted || request.url.encodedPath.endsWith(".jpg"))) respondError(HttpStatusCode.NotFound)
            else respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png"))
        })
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        client.close(); loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    @Test fun aMissingGuessedJpegRegistersTheActualPngAndStaysHiddenAfterTheStoreReopens() = registerAndReopen(false, false)
    @Test fun aDeletedPngRegistersItsThumbnailAndStaysHiddenAfterTheStoreReopens() = registerAndReopen(true, false)
    @Test fun aDeletedJpegRegistersItsThumbnailAndStaysHiddenAfterTheStoreReopens() = registerAndReopen(true, true)

    private fun registerAndReopen(deleted: Boolean, jpeg: Boolean) {
        originalDeleted = deleted
        val resolved = if (jpeg) guessed else confirmed
        var finished by mutableStateOf(false)
        val features = FutachaSharedFeatures(store, mutableStateOf(emptyMap()), client, null, null, null, "test", {})
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                if (!finished) FutachaImageNgRegistration(features, boardKey, CompatImageNgSource.CATALOG,
                    guessed, "fixture", { finished = true }, resolveMissingImageUrl = { resolved }, previewImageUrl = thumbnail)
            }
        } }
        rule.onNodeWithText("登録する").performClick()
        rule.waitUntil(10_000) { finished }
        val rules = runBlocking { store.ngRules.first { it.isNotEmpty() } }
        assertEquals(CompatNgKind.CATALOG_IMAGE_PHASH, rules.single().kind)
        assertEquals(thumbnail, rules.single().imageUrl)
        val query = "$guessed?catalog=reopened"
        val hashes = mutableMapOf<String, String>()
        runBlocking {
            collectCompatImagePhashesConcurrently(client, listOf("123" to query),
                resolveMissingUrl = { resolved }, fallbackImageUrl = { thumbnail }, onComputed = { id, hash ->
                    hashes[id] = hash
                    store.saveImagePhashes(mapOf(compatImagePhashCachePreferenceKey(query) to hash))
                }, onPublish = {})
        }
        assertEquals(setOf("123"), futaberPhashHiddenIds(hashes, rules, 0))
        if (deleted) assertTrue("Registration must actually recover through the thumbnail", requests.contains(thumbnail))
        runBlocking { store.closeForTest() }
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize()
            val loadedRules = store.ngRules.first { it.isNotEmpty() }
            val loadedHash = store.loadImagePhashes(listOf(compatImagePhashCachePreferenceKey(query))).getValue(compatImagePhashCachePreferenceKey(query))
            assertEquals(setOf("123"), futaberPhashHiddenIds(mapOf("123" to loadedHash), loadedRules, 0))
        }
    }
}
