@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.EventListener
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/** Real Coil requests, entirely intercepted by MockEngine; no server or audio. */
class FtutterThumbnailRequestInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val requests = CopyOnWriteArrayList<String>()
    private val successes = CopyOnWriteArrayList<String>()
    private lateinit var client: HttpClient
    private lateinit var loader: ImageLoader
    private val failures = mutableMapOf<String, Int>()
    private data class Case(val name: String, val extension: String = "jpg", val quality: Int = 2) {
        val full get() = "https://may.2chan.net/b/src/$name.$extension?fixture=1"
        val thumb get() = "https://may.2chan.net/b/thumb/${name}s.jpg"
    }
    @Before fun prepare() {
        val bytes = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.JPEG, 90, output); recycle()
            }
        }.toByteArray()
        client = HttpClient(MockEngine { request ->
            val url = request.url.toString(); requests += url
            when (val failure = failures[url]) {
                -1 -> throw IOException("fixture timeout")
                -2 -> respond(byteArrayOf(0, 1, 2), headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
                null -> respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
                else -> respond("fixture failure", HttpStatusCode.fromValue(failure))
            }
        })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).memoryCache(null).eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes += request.data.toString() }
        }).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown(); client.close()
    }
    private fun open(cases: List<Case>) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    Column { cases.forEach { item ->
                        FtutterThumbnail(item.thumb, item.full, 64, item.name, Modifier.size(64.dp), quality = item.quality)
                    } }
                }
            }
        }
    }
    private fun waitImage(item: Case) = rule.waitUntil(10_000) {
        (item.full in successes || item.thumb in successes) &&
            rule.onAllNodesWithContentDescription(item.name).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun wifiAndAlwaysHighQualityVideosRequestOnlyTheirStaticThumbnails() {
        assertTrue("The owned AVD must have Wi-Fi for the Wi-Fi policy check", isCompatWifiConnected(rule.activity))
        val cases = listOf(Case("wifi-webm", "webm", 1), Case("wifi-mp4", "mp4", 1),
            Case("always-webm", "webm"), Case("always-mp4", "mp4"))
        open(cases)
        cases.forEach { waitImage(it) }
        assertEquals(cases.map { it.thumb }.toSet(), requests.toSet())
        assertTrue(requests.none { "/src/" in it })
    }
    @Test fun highQualityStaticImagesStillRequestTheirOriginals() {
        val item = Case("original")
        open(listOf(item)); waitImage(item)
        assertEquals(listOf(item.full), requests.toList())
    }
    @Test fun failedStaticOriginalsReturnToThumbnailAfterServerNetworkOrDecodeFailure() {
        val cases = listOf(Case("server"), Case("network"), Case("decode"))
        failures[cases[0].full] = 500; failures[cases[1].full] = -1; failures[cases[2].full] = -2
        open(cases)
        cases.forEach { item -> rule.waitUntil(10_000) { item.thumb in requests }; waitImage(item) }
        cases.forEach { item ->
            assertTrue("Original must be attempted first", requests.indexOf(item.full) in 0 until requests.indexOf(item.thumb))
        }
        assertEquals(cases.flatMap { listOf(it.full, it.thumb) }.toSet(), requests.toSet())
    }
}
