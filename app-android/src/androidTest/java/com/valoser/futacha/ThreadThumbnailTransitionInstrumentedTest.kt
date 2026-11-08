@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.CachePolicy
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ThreadPostCard
import com.valoser.futacha.shared.ui.compat.CompatPostRow
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real post rows: inspect transition pixels, fixed geometry and a cached re-entry with the preview blocked. */
class ThreadThumbnailTransitionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var loader: ImageLoader? = null
    private val original = "https://may.2chan.net/b/src/transition.png"
    private val thumbnail = "https://may.2chan.net/b/thumb/transitions.jpg"
    private val release = CompletableDeferred<Unit>()
    private val visible = mutableStateOf(true)
    @Volatile private var blockPreview = false
    private var description = ""
    @Volatile private var diskReady = false
    private var failedPreview = false
    /** A storage lookup for the original that never answers, as on a busy disk. */
    @Volatile private var holdStorageLookup = false
    private val originalRequests = java.util.concurrent.atomic.AtomicInteger()
    private val networkOriginalRequests = java.util.concurrent.atomic.AtomicInteger()

    @After fun close() {
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread { rule.activity.setContent {} }
        loader?.shutdown()
    }
    private fun show(toshiaki: Boolean, diskOnly: Boolean = false) {
        description = if (toshiaki) "No.1の画像" else "添付画像"
        val images = ImageLoader.Builder(rule.activity).apply { if (diskOnly) memoryCache(null) }.diskCache(null).components {
            add(Interceptor { chain ->
                val source = chain.request.data.toString() == original
                if (source) {
                    originalRequests.incrementAndGet()
                    if (chain.request.networkCachePolicy != CachePolicy.DISABLED) networkOriginalRequests.incrementAndGet()
                    if (chain.request.networkCachePolicy == CachePolicy.DISABLED && holdStorageLookup) awaitCancellation()
                    if (chain.request.networkCachePolicy == CachePolicy.DISABLED && !diskReady)
                        return@Interceptor ErrorResult(null, chain.request, java.io.FileNotFoundException("cache miss"))
                    if (!diskReady) release.await()
                    diskReady = true
                } else if (blockPreview) {
                    if (failedPreview) return@Interceptor ErrorResult(null, chain.request, java.io.FileNotFoundException("missing preview"))
                    awaitCancellation()
                }
                val image = ColorImage(if (source) Color.BLUE else Color.GREEN,
                    if (source) 1000 else 250, if (source) 500 else 125)
                if (source) chain.request.memoryCacheKey?.let { key ->
                    loader?.memoryCache?.set(MemoryCache.Key(key), MemoryCache.Value(image))
                }
                SuccessResult(image, chain.request)
            })
        }.build().also { loader = it }
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFutachaImageLoader provides images,
                    LocalHighQualityThumbnailMode provides HighQualityThumbnailMode.ALWAYS) {
                    if (visible.value) {
                        if (toshiaki) CompatPostRow(CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "test",
                            imageUrl = original, thumbnailUrl = thumbnail, thumbnailWidth = 250,
                            thumbnailHeight = 125, imageFileSizeBytes = 1024), fontSize = 14, thumbnailSize = 250)
                        else ThreadPostCard(Post("1", 0, null, null, "", messageHtml = "test",
                            imageUrl = original, thumbnailUrl = thumbnail, thumbnailWidth = 250,
                            thumbnailHeight = 125, imageFileSizeBytes = 1024), isOp = true,
                            posterIdLabel = null, posterIdValue = null, saidaneLabelOverride = null,
                            onQuoteClick = {}, onUrlClick = {})
                    }
                }
            }
        }
    }
    private fun pixel(): Int {
        val pixels = rule.onNodeWithContentDescription(description, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2].toArgb()
    }
    private fun verify(toshiaki: Boolean, diskOnly: Boolean = false, previewFails: Boolean = false) {
        failedPreview = previewFails
        show(toshiaki, diskOnly)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() && pixel() == Color.GREEN
        }
        val before = rule.onNodeWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Drive one frame at a time so the pixel assertions also observe the
        // middle of the product animation, not just its final frame.
        rule.mainClock.autoAdvance = false
        release.complete(Unit)
        var sawBlend = false
        rule.waitUntil(5_000) {
            rule.mainClock.advanceTimeByFrame()
            val color = pixel()
            if (Color.blue(color) in 1..254 && Color.green(color) > 0) {
                sawBlend = true
                assertTrue("Opaque pictures must not dim during blending",
                    kotlin.math.abs(Color.blue(color) + Color.green(color) - 255) <= 2)
            }
            color == Color.BLUE
        }
        rule.mainClock.autoAdvance = true
        assertTrue("The original must fade in instead of replacing the preview in one frame", sawBlend)
        assertEquals(before, rule.onNodeWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        // Dispose the row as a LazyColumn does. Its cached original must display
        // even while the low-quality preview is unavailable on re-entry.
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        blockPreview = true
        rule.runOnIdle { visible.value = true }
        rule.waitUntil(5_000) {
            if (rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) false
            else {
                val reentryPixel = pixel()
                assertNotEquals("Cached re-entry must not expose the low-quality preview", Color.GREEN, reentryPixel)
                reentryPixel == Color.BLUE
            }
        }
        rule.onAllNodesWithText("画像読込エラー").assertCountEquals(0)
        rule.onAllNodesWithTag("compat-thread-thumbnail-1-placeholder").assertCountEquals(0)
        assertEquals(before, rule.onNodeWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }
    @Test fun transparentOriginalFadesToTheBackgroundWithoutLeavingThePreview() {
        description = "transparent transition"
        val originalPainter = mutableStateOf<Painter?>(null)
        val preview = ColorPainter(androidx.compose.ui.graphics.Color.White)
        rule.setContent {
            Image(rememberThreadThumbnailPainter(preview, originalPainter.value), description,
                Modifier.size(64.dp).background(androidx.compose.ui.graphics.Color.Red))
        }
        assertEquals(Color.WHITE, pixel())
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { originalPainter.value = ColorPainter(androidx.compose.ui.graphics.Color.Transparent) }
        rule.mainClock.advanceTimeBy(80)
        val halfway = pixel()
        assertEquals(255, Color.red(halfway))
        assertTrue("The thumbnail matte should fade away", Color.green(halfway) in 1..254)
        rule.mainClock.advanceTimeBy(240)
        assertEquals(Color.RED, pixel())
        rule.mainClock.autoAdvance = true
    }
    @Test fun futachaDiskOriginalSurvivesPreviewFailure() = verify(false, true, true)
    @Test fun toshiakiDiskOriginalSurvivesPreviewFailure() = verify(true, true, true)
    @Test fun futachaMemoryOriginalSurvivesPreviewFailure() = verify(false, false, true)
    @Test fun toshiakiMemoryOriginalSurvivesPreviewFailure() = verify(true, false, true)
    @Test fun futachaTransitionIsSmoothAndReentryUsesTheCachedOriginal() = verify(false)
    @Test fun toshiakiTransitionIsSmoothAndReentryUsesTheCachedOriginal() = verify(true)

    /** Looking for a stored original must not blank a preview that is already there. */
    private fun previewStaysWhileStorageIsChecked(toshiaki: Boolean) {
        holdStorageLookup = true
        show(toshiaki, diskOnly = true)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                pixel() == Color.GREEN
        }
    }
    @Test fun futachaPreviewStaysVisibleWhileStoredOriginalsAreChecked() = previewStaysWhileStorageIsChecked(false)
    @Test fun toshiakiPreviewStaysVisibleWhileStoredOriginalsAreChecked() = previewStaysWhileStorageIsChecked(true)

    /** A fling composes and leaves rows within a few frames; they must not read or decode originals. */
    private fun briefRowRequestsNoOriginal(toshiaki: Boolean) {
        rule.mainClock.autoAdvance = false
        show(toshiaki, diskOnly = true)
        rule.mainClock.advanceTimeBy(HIGH_QUALITY_THUMBNAIL_SETTLE_MILLIS / 2)
        rule.runOnUiThread { visible.value = false }
        rule.mainClock.advanceTimeBy(HIGH_QUALITY_THUMBNAIL_SETTLE_MILLIS * 2)
        assertEquals("A row shown for less than the settle time must not request its original", 0, originalRequests.get())
        rule.mainClock.autoAdvance = true
    }
    @Test fun futachaBriefRowRequestsNoOriginal() = briefRowRequestsNoOriginal(false)
    @Test fun toshiakiBriefRowRequestsNoOriginal() = briefRowRequestsNoOriginal(true)

    /** Without a stored original a failed preview still ends with the fallback icon, and never downloads. */
    @Test fun futachaFailedPreviewWithoutStoredOriginalShowsTheFallback() {
        blockPreview = true
        failedPreview = true
        show(false, diskOnly = true)
        rule.waitUntil(5_000) {
            originalRequests.get() > 0 &&
                rule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals("A failed preview must not start an original download", 0, networkOriginalRequests.get())
    }
}
