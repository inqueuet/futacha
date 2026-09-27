@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.ui.board.ImagePreviewDialog
import com.valoser.futacha.shared.ui.board.MediaPreviewEntry
import com.valoser.futacha.shared.ui.board.MediaType
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FutachaImagePreviewGestureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var loader: ImageLoader? = null

    @After fun close() { loader?.shutdown() }

    private fun open() {
        val imageLoader = ImageLoader.Builder(rule.activity).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(Color.GREEN, 256, 256), chain.request) })
        }.build().also { loader = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    ImagePreviewDialog(
                        entry = MediaPreviewEntry("https://image.invalid/green.png", MediaType.Image, "123", "ズーム検証"),
                        currentIndex = 0, totalCount = 1,
                        onDismiss = {}, onNavigateNext = {}, onNavigatePrevious = {}
                    )
                }
            }
        }
        rule.waitUntil(10_000) { centerIsGreen() }
    }

    private fun centerIsGreen(): Boolean {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return false
        return try {
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            Color.green(pixel) > 200 && Color.red(pixel) < 50 && Color.blue(pixel) < 50
        } finally { bitmap.recycle() }
    }

    private fun zoom(start: Float, end: Float) {
        rule.onNodeWithContentDescription("プレビュー画像").performTouchInput {
            pinch(
                start0 = center - Offset(width * start, 0f),
                end0 = center - Offset(width * end, 0f),
                start1 = center + Offset(width * start, 0f),
                end1 = center + Offset(width * end, 0f),
                durationMillis = 300
            )
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue("Bitmap must remain visible after all fingers leave the viewer", centerIsGreen())
    }

    @Test fun releaseAfterSmallPinchKeepsBitmapVisible() {
        open()
        zoom(0.10f, 0.13f) // Does not cross the original-resolution request threshold.
    }

    @Test fun zoomInPanAndZoomOutKeepBitmapVisibleAcrossOriginalReplacement() {
        open()
        zoom(0.10f, 0.20f)
        rule.onNodeWithContentDescription("プレビュー画像").performTouchInput {
            down(center)
            moveTo(center + Offset(40f, 20f), 100)
            up()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue("Bitmap must remain visible after pan release", centerIsGreen())
        zoom(0.20f, 0.15f)
    }
}
