@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Product gallery cells, intercepted requests and rendered pixels; no external transport or audio. */
class FtutterMediaThumbnailSafetyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val original = "https://may.2chan.net/b/src/fixture.png"
    private val thumbnail = "https://may.2chan.net/b/thumb/fixtures.jpg"
    private data class Seen(val request: ImageRequest, val width: Int, val height: Int)
    private val seen = CopyOnWriteArrayList<Seen>()
    private var loader: ImageLoader? = null

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader?.shutdown()
    }

    private fun show(mode: HighQualityThumbnailMode, sizeBytes: Long? = 1024, reduce: Boolean = false) {
        val images = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain ->
                val request = chain.request
                seen.add(Seen(request, (chain.size.width as Dimension.Pixels).px, (chain.size.height as Dimension.Pixels).px))
                val isOriginal = request.data.toString() == original
                SuccessResult(ColorImage(if (isOriginal) Color.BLUE else Color.GREEN, 250, 250), request)
            })
        }.build().also { loader = it }
        val post = CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "", imageUrl = original,
            thumbnailUrl = thumbnail, thumbnailWidth = 250, thumbnailHeight = 250, imageFileSizeBytes = sizeBytes)
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                CompositionLocalProvider(LocalFutachaImageLoader provides images,
                    LocalFutachaCatalogImageLoader provides images, LocalHighQualityThumbnailMode provides mode) {
                    FtutterGalleryScreen(media = listOf(post), columns = 1,
                        settings = FtutterMediaSettings(reduceThumbnail = reduce, thumbnailPixels = 200),
                        privacyAlpha = 1f,
                        tools = FtutterMediaSaveTools("test", {}, {}, { _, _ -> FtutterBatchSaveResult(0, 0) }, false),
                        onColumns = {}, onOpenViewer = {}, onBackToPost = {}, onClose = {})
                }
            }
        }
    }

    private fun awaitColor(color: Int) {
        rule.waitUntil(5_000) {
            val nodes = rule.onAllNodesWithContentDescription("No.1", useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) false else {
                val pixels = rule.onNodeWithContentDescription("No.1", useUnmergedTree = true).captureToImage().toPixelMap()
                pixels[pixels.width / 2, pixels.height / 2].toArgb() == color
            }
        }
    }

    @Test fun sharedOffShowsTheThumbnailWithoutFetchingTheOriginal() {
        show(HighQualityThumbnailMode.OFF)
        awaitColor(Color.GREEN)
        rule.runOnIdle {
            assertTrue(seen.isNotEmpty())
            assertTrue(seen.all { it.request.data.toString() == thumbnail })
        }
    }

    @Test fun cachedHighQualityUpgradesVisiblePixelsWithCacheOnlyAndSlotSizedRequests() {
        show(HighQualityThumbnailMode.CACHED)
        awaitColor(Color.BLUE)
        verifyOriginalRequest(CachePolicy.DISABLED)
    }

    @Test fun allowedSmallOriginalUsesTheSharedNetworkAndBoundedDecodeRequest() {
        show(HighQualityThumbnailMode.ALWAYS)
        awaitColor(Color.BLUE)
        verifyOriginalRequest(CachePolicy.ENABLED)
    }

    @Test fun unknownSizeOriginalStaysCacheOnlyEvenWhenNetworkUpgradesAreEnabled() {
        show(HighQualityThumbnailMode.ALWAYS, sizeBytes = null)
        awaitColor(Color.BLUE)
        verifyOriginalRequest(CachePolicy.DISABLED)
    }

    @Test fun reducedSettingKeepsItsExistingThumbnailRequestAndPixels() {
        show(HighQualityThumbnailMode.ALWAYS, reduce = true)
        awaitColor(Color.GREEN)
        rule.runOnIdle {
            assertTrue(seen.all { it.request.data.toString() == thumbnail })
            assertTrue(seen.all { it.width == 200 && it.height == 200 })
        }
    }

    private fun verifyOriginalRequest(expectedNetwork: CachePolicy) {
        val cell = rule.onNodeWithTag("ftutter-gallery-cell").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            assertEquals(thumbnail, seen.first().request.data.toString())
            val originalRequest = seen.last { it.request.data.toString() == original }
            assertTrue("decode width ${originalRequest.width} exceeds slot ${cell.width}", originalRequest.width <= cell.width + 1)
            assertTrue("decode height ${originalRequest.height} exceeds slot ${cell.height}", originalRequest.height <= cell.height + 1)
            assertEquals(expectedNetwork, originalRequest.request.networkCachePolicy)
            assertEquals(coil3.size.Precision.INEXACT, originalRequest.request.precision)
            assertEquals(0, originalRequest.request.futabaExtensionFallbackPolicy().maxAttempts)
        }
    }
}
