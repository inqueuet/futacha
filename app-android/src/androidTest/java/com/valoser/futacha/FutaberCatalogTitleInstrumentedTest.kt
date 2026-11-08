@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.*
import org.junit.Assert.*

/**
 * L-B1 (docs/futaber-evolution-look-2026-10-31.md §5): ふたばー風 shows the whole title as futaber does. The repository
 * answers as the server does, with the titles cut to the letters of the layout the request asked for (the app's 16
 * without one), so a catalog fetched without the whole-title layout loses the end of the title. Local data only: no
 * network, no posting.
 */
class FutaberCatalogTitleInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("catalog-title", "題名", "test", "https://may.2chan.net/b/futaba.php", "")
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        private val appSettings = CatalogFetchSettings(rows = 60)
        override suspend fun getCatalogFetchSettings(): CatalogFetchSettings = appSettings
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = page(appSettings.titleLines)
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) =
            page(settings.titleLines)
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent = ThreadPageContent(
            ThreadPage(threadId, null, null, null, listOf(
                Post("901", author = null, subject = null, timestamp = "", messageHtml = "$LONG_TITLE<br>二行目の本文",
                    imageUrl = null, thumbnailUrl = null),
                Post("902", author = null, subject = null, timestamp = "", messageHtml = "返信です",
                    imageUrl = null, thumbnailUrl = null)
            ))
        )
    }

    private fun page(letters: Int) = CatalogPageContent(listOf(
        CatalogItem("901", "https://may.2chan.net/b/res/901.htm", LONG_TITLE.take(letters), null, null, replyCount = 12),
        CatalogItem("902", "https://may.2chan.net/b/res/902.htm", "短い題名", null, null, replyCount = 3)
    ))

    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown()
    }

    @Composable private fun Host(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
            FutaberTheme(FutaberThemeMode.Light) { content() }
        }
    }

    private fun showCatalog(style: FutaberCatalogDisplayStyle) {
        rule.setContent {
            Host {
                FutaberCatalogScreen(board, repository, style, CatalogMode.Catalog,
                    {}, {}, {}, {}, {}, {}, 0, onOpenThread = {})
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size == 2 }
    }

    private fun descriptions(): List<String> = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()
        .map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty() }

    @Test fun theCatalogSearchFindsAWordFromTheLatterHalfOfATitle() {
        assertFalse(LONG_TITLE.take(16).contains(LATE_WORD))
        showCatalog(FutaberCatalogDisplayStyle.Grid4)
        rule.onNodeWithTag("futaber-search").performClick()
        rule.onNodeWithTag("futaber-search-field").performTextInput(LATE_WORD)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size == 1 }
        assertTrue(descriptions().toString(), descriptions().single().startsWith(LONG_TITLE))
    }

    @Test fun aLongTitleKeepsToOneLineAndEndsInAnEllipsis() {
        showCatalog(FutaberCatalogDisplayStyle.Row1)
        val layouts = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithText(LONG_TITLE, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertTrue("the whole title should be cut with an ellipsis on its row", layout.isLineEllipsized(0))
    }

    @Test fun theThreadsTitleRowIsTheFirstLineOfTheFirstPostOnceItIsRead() {
        // Opened from a catalog that only knew the first 16 letters.
        val ref = FutaberThreadRef(board.id, "901", LONG_TITLE.take(16), "", 12)
        rule.setContent {
            Host {
                FutaberThreadScreen(board, ref, repository, isTabbed = false, onToggleTab = {},
                    onOpenManage = {}, onOpenPost = {}, refreshSignal = 0, quoteMode = false, onQuoteLine = {},
                    onOpenDrawer = {}, drawerGestureEnabled = false, onPageLoaded = {},
                    onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                    ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {})
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { threadTitle() == LONG_TITLE }
    }

    private fun threadTitle(): String = rule.onNodeWithTag("futaber-thread-title").fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    private companion object {
        const val LATE_WORD = "猫じゃらし"
        const val LONG_TITLE = "今日の晩ごはんの話をするスレッドです最後のほうにだけ猫じゃらしの話も出てきますのでよろしくお願いします"
    }
}
