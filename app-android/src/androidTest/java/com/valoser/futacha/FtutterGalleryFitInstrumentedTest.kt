@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.*
import org.junit.Assert.*
import kotlin.math.abs

/** M85：原作の画像一覧は画像を切り抜かずに出し、行の高さは行で一番高い画像に合わせる。通信なし。 */
class FtutterGalleryFitInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() { loader.shutdown() }

    private fun show(settings: FtutterMediaSettings) {
        // 1行目：横長2枚（250×125）、2行目：縦長（125×250）と横長。
        val sizes = listOf(250 to 125, 250 to 125, 125 to 250, 250 to 125)
        val posts = sizes.mapIndexed { i, (w, h) ->
            CompatPostSnapshot(i, "${100 + i}", timestamp = "", messageHtml = "", imageUrl = "file:///nonexistent/m85-$i.jpg",
                thumbnailWidth = w, thumbnailHeight = h)
        }
        val tools = FtutterMediaSaveTools("test folder", {}, {}, { _, _ -> FtutterBatchSaveResult(0, 0) }, false)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterGalleryScreen(media = posts, columns = 2, settings = settings, privacyAlpha = 1f, tools = tools,
                        onColumns = {}, onOpenViewer = {}, onBackToPost = {}, onClose = {})
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test fun cellsShowTheWholeImageAndARowTakesItsTallestImage() {
        show(FtutterMediaSettings())
        val cells = bounds("ftutter-gallery-cell")
        val images = bounds("ftutter-gallery-image")
        assertEquals(4, images.size)
        // 1行目は横長だけ：セルの高さは幅の半分ほど。2行目は縦長があるので正方形。
        assertTrue("row 1 is half height: ${cells[0]}", abs(cells[0].height - cells[0].width / 2) < cells[0].width * 0.1f)
        assertTrue("row 2 is square: ${cells[2]}", abs(cells[2].height - cells[2].width) < cells[2].width * 0.1f)
        // 横長の画像は幅いっぱいで2:1、縦長は1:2で横の中央。
        assertEquals(2f, images[0].width / images[0].height, 0.1f)
        assertEquals(0.5f, images[2].width / images[2].height, 0.05f)
        assertEquals(cells[2].center.x, images[2].center.x, 2f)
        // 2行目の横長は、正方形の行の縦の中央に置く。
        assertEquals(cells[3].center.y, images[3].center.y, 2f)
        // 番号のタグは画像の左上。
        val cards = bounds("ftutter-index-card")
        assertEquals(images[2].left, cards[2].left, 2f)
        assertEquals(images[2].top, cards[2].top, 2f)
    }

    @Test fun theLegacySwitchKeepsTheSquareCrop() {
        show(FtutterMediaSettings(legacyGallerySquare = true))
        bounds("ftutter-gallery-cell").forEach { assertEquals(it.width, it.height, 2f) }
        bounds("ftutter-gallery-image").forEach { assertEquals(it.width, it.height, 2f) }
    }
}
