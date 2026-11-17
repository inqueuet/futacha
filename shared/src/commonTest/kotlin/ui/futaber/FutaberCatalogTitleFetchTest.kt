package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.MAX_COMPAT_CATALOG_TITLE_LINES
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L-B1 (docs/futaber-evolution-look-2026-10-31.md §5): the ふたばー風 catalog is fetched with the whole title, as futaber
 * shows it, instead of the 16 letters of the app's default layout. The rest of the layout (rows, columns, the visited
 * mark) stays the app's own, so the number of threads fetched does not change.
 */
class FutaberCatalogTitleFetchTest {
    /**
     * Answers as the server does: the titles come cut to the letters of the layout the request was made with. Without
     * settings the layout is the app's ([appSettings], 16 letters), as with [com.valoser.futacha.shared.repo.DefaultBoardRepository].
     */
    private class TitleCuttingRepository(
        private val appSettings: CatalogFetchSettings = CatalogFetchSettings(columns = 7, rows = 100, showVisitedHistory = false)
    ) : BoardRepository by FakeBoardRepository() {
        val plainFetches = mutableListOf<CatalogMode>()
        val settingsFetches = mutableListOf<Pair<CatalogMode, CatalogFetchSettings>>()

        override suspend fun getCatalogFetchSettings(): CatalogFetchSettings = appSettings

        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
            plainFetches += mode
            return page(appSettings.titleLines)
        }

        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
            settingsFetches += mode to settings
            return page(settings.titleLines)
        }

        private fun page(letters: Int) = CatalogPageContent(
            listOf(
                CatalogItem("101", "https://may.2chan.net/b/res/101.htm", LONG_TITLE.take(letters), null, null, replyCount = 12),
                CatalogItem("102", "https://may.2chan.net/b/res/102.htm", SHORT_TITLE.take(letters), null, null, replyCount = 3)
            )
        )
    }

    private val board = "https://may.2chan.net/b/futaba.php"

    @Test fun theFixturesWordIsOnlyInTheLatterHalfOfTheTitle() {
        assertTrue(LONG_TITLE.length > 16)
        assertFalse(LONG_TITLE.take(16).contains(LATE_WORD))
        assertTrue(LONG_TITLE.contains(LATE_WORD))
    }

    @Test fun theCatalogIsFetchedWithTheWholeTitleAndTheAppsOwnLayout() = runBlocking {
        val repository = TitleCuttingRepository()
        val page = futaberFetchCatalogPage(repository, board, CatalogMode.New, emptyList())

        assertEquals(emptyList(), repository.plainFetches, "the catalog must not be fetched with the app's 16-letter layout")
        val (mode, settings) = repository.settingsFetches.single()
        assertEquals(CatalogMode.New, mode)
        assertEquals(MAX_COMPAT_CATALOG_TITLE_LINES, settings.titleLines)
        // Only the title's letters change: the same threads are fetched as before.
        assertEquals(7, settings.columns)
        assertEquals(100, settings.rows)
        assertFalse(settings.showVisitedHistory)
        assertEquals(LONG_TITLE, page.items.first { it.id == "101" }.title)
    }

    @Test fun everyCatalogTheWatchSortReadsIsFetchedWithTheWholeTitle() = runBlocking {
        val repository = TitleCuttingRepository()
        val watched = futaberFetchCatalogPage(repository, board, CatalogMode.WatchWords, listOf(LATE_WORD)).items

        assertEquals(emptyList(), repository.plainFetches)
        assertTrue(repository.settingsFetches.isNotEmpty())
        assertTrue(repository.settingsFetches.all { (_, settings) -> settings.titleLines == MAX_COMPAT_CATALOG_TITLE_LINES && settings.rows == 100 })
        // The word sits past the 16th letter, so only the whole title finds the thread.
        assertEquals(listOf("101"), watched.map { it.id })
    }

    @Test fun theSearchAndTheCatalogNgWordsReachTheEndOfTheTitle() = runBlocking {
        val items = futaberFetchCatalogPage(TitleCuttingRepository(), board, CatalogMode.Catalog, emptyList()).items

        val found = filterFutaberCatalogByKeys(items, futaberCatalogTitleKeys(items), LATE_WORD)
        assertEquals(listOf("101"), found.map { it.id })
        assertEquals(listOf("102"), futaberApplyCatalogNg(items, listOf(LATE_WORD)).map { it.id })
    }

    @Test fun theThreadsTitleRowIsTheFirstPostsFirstLineOnceThePageIsRead() {
        val opened = LONG_TITLE.take(16)
        // Before the page is read: the title it was opened with.
        assertEquals(opened, futaberThreadTitle(opened, null))
        // Read: the first line of the first post, whole (the history's rule).
        val first = Post("101", author = null, subject = "件名", timestamp = "", messageHtml = "$LONG_TITLE<br>二行目",
            imageUrl = null, thumbnailUrl = null)
        assertEquals(LONG_TITLE, futaberThreadTitle(opened, first))
        // A first post without a body line: its subject, else the opened title.
        assertEquals("件名", futaberThreadTitle(opened, first.copy(messageHtml = "")))
        assertEquals(opened, futaberThreadTitle(opened, first.copy(messageHtml = "", subject = null)))
    }

    private companion object {
        const val LATE_WORD = "猫じゃらし"
        const val LONG_TITLE = "今日の晩ごはんの話をするスレッドです最後のほうに猫じゃらしの話も"
        const val SHORT_TITLE = "短い題名"
    }
}
