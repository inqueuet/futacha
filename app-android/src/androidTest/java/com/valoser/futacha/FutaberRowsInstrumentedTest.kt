@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import org.junit.*
import org.junit.Assert.*

/**
 * L-B3「カタログとスレッドの行」(docs/futaber-evolution-look-2026-10-31.md §1.3) on the screens themselves, with local pages
 * (no network, no posting): the catalog's read rows in both themes and the wide rows (V12・V27), the post header's
 * colours and its breaking inside a word (V08・V09・V25), the picture's size and corners (V11) and the grey footer with
 * "…頃消えます" (F22). The tutorial board of the real host has one thread and no IPs or names, so these use their own.
 */
class FutaberRowsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("rows", "行", "test", "https://may.2chan.net/27/futaba.php", "")
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient

    private val items = (901..906).map { CatalogItem("$it", "https://may.2chan.net/27/res/$it.htm", "スレ$it", null, null, replyCount = it - 900) }
    private val ip = "IP:106.73.*(enabler.ne.jp)"
    private val posts = listOf(
        Post("1001", author = "ひー坊", subject = "無題", timestamp = "26/10/16(金)21:19:42 $ip", messageHtml = "本文",
            imageUrl = "https://may.2chan.net/27/src/1001.jpg", thumbnailUrl = "https://may.2chan.net/27/thumb/1001s.jpg",
            saidaneLabel = "そうだねx45", thumbnailWidth = 250, thumbnailHeight = 188),
        Post("1002", author = "としあき", subject = "無題", timestamp = "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)",
            messageHtml = "&gt;本文<br>返信", imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx2",
            quoteReferences = listOf(QuoteReference(">本文", listOf("1001")))),
        Post("1003", author = "としあき", subject = "無題", mail = "sage", timestamp = "26/10/25(日)17:59:39 $ip",
            messageHtml = "下げ", imageUrl = null, thumbnailUrl = null),
        // A long name, so the header fills its first line and goes on to a second.
        Post("1364612475", author = "とても長い名前のとしあきさんがここにいますとても長い名前です", subject = "無題",
            timestamp = "26/10/25(日)21:08:03 IP:106.128.*(au-net.ne.jp)", messageHtml = "長い見出し", imageUrl = null, thumbnailUrl = null)
    ) + (1100..1130).map { Post("$it", author = null, subject = null, timestamp = "26/10/26(月)00:00:00", messageHtml = "body-$it", imageUrl = null, thumbnailUrl = null) }
    private val page = ThreadPage("1001", null, "28年5月頃消えます", null, posts)

    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(items)
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = CatalogPageContent(items)
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
    }

    @Before fun prepare() {
        // Every picture is a blue square: its corners show whether it is clipped.
        val bytes = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(250, 188, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.PNG, 100, out); recycle()
        } }.toByteArray()
        client = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png")) })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown(); client.close()
    }

    private val density get() = rule.density.density

    // The test rule takes its content once; the screens and the theme change through state.
    private var shownTheme by androidx.compose.runtime.mutableStateOf(FutaberThemeMode.Light)
    private var shownDisplay by androidx.compose.runtime.mutableStateOf(FutaberDisplaySettings())
    private var shown by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    private var started = false

    private fun host(theme: FutaberThemeMode, display: FutaberDisplaySettings = FutaberDisplaySettings(), content: @androidx.compose.runtime.Composable () -> Unit) {
        if (!started) {
            started = true
            rule.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader,
                    LocalFutaberDisplaySettings provides shownDisplay) {
                    FutaberTheme(shownTheme) { shown?.invoke() }
                }
            }
        }
        rule.runOnUiThread { shownTheme = theme; shownDisplay = display; shown = content }
        rule.waitForIdle()
    }

    private fun showCatalog(theme: FutaberThemeMode, style: FutaberCatalogDisplayStyle, read: Set<String>, display: FutaberDisplaySettings = FutaberDisplaySettings()) {
        host(theme, display) {
            FutaberCatalogScreen(board, repository, style, CatalogMode.Catalog, {}, {}, {}, {}, {}, {}, 0,
                onOpenThread = {}, readThreadIds = read)
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size == items.size }
        rule.waitForIdle()
    }

    private fun showThread(theme: FutaberThemeMode) {
        host(theme) {
            FutaberThreadScreen(board, FutaberThreadRef(board.id, "1001", "行", "", posts.size), repository,
                isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {})
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post-header").fetchSemanticsNodes().size >= 3 }
        rule.waitForIdle()
    }

    private fun screen(): PixelMap = rule.onRoot().captureToImage().toPixelMap()

    private data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun distance(other: Rgb) = abs(r - other.r) + abs(g - other.g) + abs(b - other.b)
        override fun toString() = "#%02X%02X%02X".format(r, g, b)
    }

    private fun PixelMap.at(x: Float, y: Float): Rgb {
        val c = this[x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1)]
        return Rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }

    private fun rgb(hex: Long) = Rgb(((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())

    private fun assertColour(label: String, expected: Rgb, actual: Rgb, tolerance: Int = 6) {
        assertTrue("$label: expected $expected, was $actual", expected.distance(actual) <= tolerance)
    }

    /** The colour of the letters in [area]: the pixels farthest from the ground, averaged. */
    private fun PixelMap.inkIn(area: Rect, ground: Rgb): Rgb {
        val pixels = mutableListOf<Rgb>()
        for (y in area.top.toInt() until area.bottom.toInt().coerceAtMost(height)) for (x in area.left.toInt() until area.right.toInt().coerceAtMost(width)) {
            pixels += at(x.toFloat(), y.toFloat())
        }
        val far = pixels.maxOf { it.distance(ground) }
        val ink = pixels.filter { it.distance(ground) >= far * 0.85 }
        assertTrue("no ink in $area", far > 100 && ink.isNotEmpty())
        return Rgb(ink.sumOf { it.r } / ink.size, ink.sumOf { it.g } / ink.size, ink.sumOf { it.b } / ink.size)
    }

    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** Where [word] is drawn in the header [node] (its first line), in root pixels. */
    private fun wordBox(node: SemanticsNode, word: String): Rect {
        val layout = layoutOf(node)
        val text = layout.layoutInput.text.text
        // The drawn text has a zero-width space between letters; find the word with them.
        val pattern = Regex(word.toList().joinToString("​?") { Regex.escape(it.toString()) })
        val match = pattern.find(text) ?: error("$word not in $text")
        val first = layout.getBoundingBox(match.range.first)
        val last = layout.getBoundingBox(match.range.last)
        val origin = node.boundsInRoot.topLeft
        return Rect(origin.x + first.left, origin.y + first.top, origin.x + maxOf(first.right, if (last.top == first.top) last.right else first.right), origin.y + first.bottom)
    }

    /** V12・V27: read rows are #EEEEEE (light) / #263941 (dark), the others the page; rows 40dp; the picture box white. */
    @Test fun readRowsAreGreyInBothThemesAndRowsAreFortyHigh() {
        listOf(
            FutaberThemeMode.Light to (rgb(0xEEEEEE) to rgb(0xFFFFFF)),
            FutaberThemeMode.Dark to (rgb(0x263941) to rgb(0x1C2123))
        ).forEach { (theme, grounds) ->
            showCatalog(theme, FutaberCatalogDisplayStyle.Row1, setOf("901", "903"))
            val image = screen()
            val rows = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()
            val counts = rule.onAllNodesWithTag("futaber-catalog-item-count", useUnmergedTree = true).fetchSemanticsNodes()
            rows.forEach { row ->
                val b = row.boundsInRoot
                val count = counts.first { b.contains(it.boundsInRoot.center) }.boundsInRoot
                val read = description(row).endsWith("既読")
                val expected = listOf("スレ901、", "スレ903、").any { description(row).startsWith(it) }
                assertEquals("$theme: ${description(row)}", expected, read)
                assertEquals("$theme row height", 40f * density, b.height, 1.5f * density)
                assertColour("$theme ${description(row)} ground", if (read) grounds.first else grounds.second, image.at(count.left - 4f * density, b.top + 2f * density))
                // The picture box (no picture here: the placeholder in the middle) keeps the page's ground at its corner.
                assertColour("$theme picture box", grounds.second, image.at(b.left + 5f * density, b.top + 1.5f * density), tolerance = 12)
            }
            // The count is red / cyan, 12sp, on the read row.
            val readRow = rows.first { description(it).startsWith("スレ901") }.boundsInRoot
            val countInk = image.inkIn(counts.first { readRow.contains(it.boundsInRoot.center) }.boundsInRoot, grounds.first)
            if (theme == FutaberThemeMode.Light) assertTrue("count red: $countInk", countInk.r > 150 && countInk.g < 90)
            else assertTrue("count cyan: $countInk", countInk.r < 90 && countInk.g > 150)
            shot("rows-catalog-${theme.persistedValue}")
            // The grid: the read cell's strip under the picture is grey, the other white.
            showCatalog(theme, FutaberCatalogDisplayStyle.Grid4, setOf("901"))
            val grid = screen()
            rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().take(2).forEach { cell ->
                val read = description(cell).endsWith("既読")
                assertColour("$theme grid ${description(cell)}", if (read) grounds.first else grounds.second,
                    grid.at(cell.boundsInRoot.left + 2f * density, cell.boundsInRoot.bottom - 1.5f * density))
            }
            shot("rows-grid-${theme.persistedValue}")
        }
    }

    /** V27 「やや広め」 (provisional): a 56 row with the picture 56 and the title two lines. */
    @Test fun wideRowsAreFiftySixHigh() {
        showCatalog(FutaberThemeMode.Light, FutaberCatalogDisplayStyle.Row1, emptySet(), FutaberDisplaySettings(catalogRowWide = true))
        rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().forEach {
            assertEquals(56f * density, it.boundsInRoot.height, 1.5f * density)
        }
        val row = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().first().boundsInRoot
        val title = rule.onAllNodesWithTag("futaber-catalog-item-title", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertEquals("title after the 56 picture", (4f + 56f + 12f) * density, title.left - row.left, 1.5f * density)
    }

    /**
     * V08・V09・V25 and §8.1's handoff: the number blue, the name green (#327800), the IP of a poster who posts again in the
     * poster's colour, "[sage]" futaber's blue (#2F70E1), ×45 red and ×2 the darkened yellow (#936F00).
     */
    @Test fun theHeaderTakesTheOriginalsColours() {
        showThread(FutaberThemeMode.Light)
        val white = rgb(0xFFFFFF)
        val image = screen()
        val headers = rule.onAllNodesWithTag("futaber-post-header").fetchSemanticsNodes()
        fun header(no: String) = headers.first { description(it).endsWith("No.$no") }
        val first = header("1001")
        val ordinal = image.inkIn(wordBox(first, "0"), white)
        assertTrue("number blue: $ordinal", ordinal.b > 130 && ordinal.r < 60)
        val name = image.inkIn(wordBox(first, "ひー坊"), white)
        assertTrue("name green: $name", name.g > name.r + 40 && name.g > name.b + 40)
        val ipInk = image.inkIn(wordBox(first, ip), white)
        val poster = futaberPosterColor(ip, false)
        assertTrue("IP in the poster's colour: $ipInk", rgb(poster.toRgbLong()).distance(ipInk) < 120)
        val mail = image.inkIn(wordBox(header("1003"), "[sage]"), white)
        assertTrue("[sage] blue: $mail", mail.b > 170 && mail.r < 90)
        val saidane = rule.onAllNodesWithTag("futaber-saidane-count").fetchSemanticsNodes()
        val many = saidane.first { headers.first { h -> description(h).endsWith("No.1001") }.boundsInRoot.contains(it.boundsInRoot.center) }
        val few = saidane.first { header("1002").boundsInRoot.contains(it.boundsInRoot.center) }
        val manyInk = image.inkIn(many.boundsInRoot, white)
        val fewInk = image.inkIn(few.boundsInRoot, white)
        assertTrue("×45 red: $manyInk", manyInk.r > 160 && manyInk.g < 80)
        assertTrue("×2 yellow-brown: $fewInk", fewInk.r > 110 && fewInk.g > 80 && fewInk.b < 60)
        // The count sits at the right end of the header, over it.
        assertTrue("×45 at the right", headers.first().boundsInRoot.right - many.boundsInRoot.right < 2f * density)
        shot("rows-thread-light")
    }

    /** V08: one flowing text; a long header fills its first line and breaks inside a word, as futaber's does. */
    @Test fun aLongHeaderBreaksAnywhereLikeTheOriginal() {
        showThread(FutaberThemeMode.Light)
        val node = rule.onAllNodesWithTag("futaber-post-header").fetchSemanticsNodes().first { description(it).endsWith("No.1364612475") }
        val layout = layoutOf(node)
        assertTrue("two lines or more", layout.lineCount >= 2)
        val text = layout.layoutInput.text.text
        val end = layout.getLineEnd(0)
        // A greedy break between letters: the first line is filled and the break is not at a space.
        assertTrue("first line filled: ${layout.getLineRight(0)} of ${layout.size.width}", layout.getLineRight(0) > layout.size.width * 0.9f)
        val before = text.substring(0, end).trimEnd('​').last()
        val after = text.substring(end).trimStart('​').first()
        assertTrue("broken inside a word: '$before|$after'", !before.isWhitespace() && !after.isWhitespace())
    }

    /**
     * V10: the reply mark opens the replies' bubble, and its widened touch area (and the file name's) does not take the
     * post's long press: a long press 12dp under the mark, and one on the file name, open the post's sheet, not a bubble.
     */
    @Test fun theReplyMarkOpensTheBubbleAndLeavesTheLongPressToThePost() {
        showThread(FutaberThemeMode.Light)
        val mark = rule.onAllNodesWithTag("futaber-reply-count").fetchSemanticsNodes().first().boundsInRoot
        fun longPressAt(point: Offset) {
            rule.onNodeWithTag("futaber-thread-list").performTouchInput {
                longClick(point - rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().boundsInRoot.topLeft)
            }
            rule.waitUntil(5_000) { rule.onAllNodesWithText("レス:0").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("レス:0").assertIsDisplayed()
            assertTrue("no bubble opened", rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag("futaber-action-cancel").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("レス:0").fetchSemanticsNodes().isEmpty() }
        }
        longPressAt(Offset(mark.center.x, mark.bottom + 12f * density))
        val file = rule.onAllNodesWithTag("futaber-post-file", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        longPressAt(file.center)
        rule.onAllNodesWithTag("futaber-reply-count")[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().isNotEmpty() }
    }

    /** V11: the picture is 0.7 of its 250×188 thumbnail (175×132) with square corners. */
    @Test fun thePictureIsSevenTenthsOfItsThumbnailWithSquareCorners() {
        showThread(FutaberThemeMode.Light)
        val picture = rule.onAllNodesWithTag("futaber-post-image").fetchSemanticsNodes().first().boundsInRoot
        assertEquals(175f * density, picture.width, 1.5f * density)
        assertEquals(131.6f * density, picture.height, 1.5f * density)
        val blue = rgb(0x0000FF)
        rule.waitUntil(10_000) { screen().at(picture.center.x, picture.center.y).distance(blue) < 30 }
        val image = screen()
        listOf(picture.topLeft, Offset(picture.right - 1f, picture.top), Offset(picture.left, picture.bottom - 1f), Offset(picture.right - 1f, picture.bottom - 1f))
            .forEach { corner -> assertColour("corner $corner", blue, image.at(corner.x, corner.y), tolerance = 30) }
    }

    /** F22: after the last post, futaber's grey footer with "…頃消えます" at the right, in both themes. */
    @Test fun theThreadEndsWithTheExpiryOnItsFooter() {
        listOf(FutaberThemeMode.Light to rgb(0xF2F2F2), FutaberThemeMode.Dark to rgb(0x14191C)).forEach { (theme, ground) ->
            showThread(theme)
            rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-thread-expiry"))
            rule.waitForIdle()
            rule.onNodeWithTag("futaber-thread-expiry").assertTextEquals("28年5月頃消えます")
            val footer = rule.onNodeWithTag("futaber-thread-end").fetchSemanticsNode().boundsInRoot
            val label = rule.onNodeWithTag("futaber-thread-expiry").fetchSemanticsNode().boundsInRoot
            val image = screen()
            assertColour("$theme footer", ground, image.at(footer.left + 3f * density, footer.top + 2f * density))
            val ink = image.inkIn(Rect(label.center.x, label.top, label.right, label.bottom), ground)
            assertTrue("$theme: grey letters at the right half: $ink", abs(ink.r - ink.g) < 20 && ink.distance(ground) > 150)
            shot("rows-thread-end-${theme.persistedValue}")
        }
    }

    private fun androidx.compose.ui.graphics.Color.toRgbLong(): Long =
        ((red * 255).toLong() shl 16) or ((green * 255).toLong() shl 8) or (blue * 255).toLong()

    private fun shot(name: String) {
        rule.waitForIdle()
        val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-look-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }
}
