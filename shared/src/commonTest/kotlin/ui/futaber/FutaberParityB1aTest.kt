package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The first half of the B1 parity batch of the ふたばー風 mode (pure parts). */
class FutaberParityB1aTest {
    private val may = BoardSummary("may-b", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")

    @Test fun theNewExtensionsAreOffByDefaultAndReadOnlyAnOn() {
        val defaults = FutaberDisplaySettings.from(emptyMap())
        assertFalse(defaults.extTabsManage); assertFalse(defaults.extArchiveMenu); assertFalse(defaults.extSavedDetail)
        assertFalse(defaults.extOtherFavorites); assertFalse(defaults.extScrollMenu); assertFalse(defaults.extSearchNav)
        assertFalse(defaults.tapScroll); assertFalse(defaults.postDestinationConfirm)
        assertEquals(defaults, FutaberDisplaySettings())
        val on = FutaberDisplaySettings.from(
            listOf(
                FutaberSettingKeys.EXT_TABS_MANAGE, FutaberSettingKeys.EXT_ARCHIVE_MENU, FutaberSettingKeys.EXT_SAVED_DETAIL,
                FutaberSettingKeys.EXT_OTHER_FAVORITES, FutaberSettingKeys.EXT_SCROLL_MENU, FutaberSettingKeys.EXT_SEARCH_NAV,
                FutaberSettingKeys.TAP_SCROLL, FutaberSettingKeys.POST_DESTINATION_CONFIRM
            ).associateWith { "ON" }
        )
        assertTrue(on.extTabsManage && on.extArchiveMenu && on.extSavedDetail && on.extOtherFavorites)
        assertTrue(on.extScrollMenu && on.extSearchNav && on.tapScroll && on.postDestinationConfirm)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.TAP_SCROLL to "yes")).tapScroll)
    }

    @Test fun theDestinationConfirmationIsTheSharedKeyAndAlsoReadsTheLegacyTitle() {
        assertEquals("compat.control.controlPostDestinationConfirm", FutaberSettingKeys.POST_DESTINATION_CONFIRM)
        // A value stored by an old version under the Japanese title is read too; the stable key wins.
        assertTrue(FutaberDisplaySettings.from(mapOf("compat.control.板名の誤投稿確認" to "ON")).postDestinationConfirm)
        assertFalse(
            FutaberDisplaySettings.from(
                mapOf("compat.control.板名の誤投稿確認" to "ON", FutaberSettingKeys.POST_DESTINATION_CONFIRM to "OFF")
            ).postDestinationConfirm
        )
        assertFalse(
            FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.POST_DESTINATION_CONFIRM to "OFF")).postDestinationConfirm
        )
    }

    @Test fun theDestinationWarningAsksOnImgAndMayOnlyWhenOn() {
        val img = "https://img.2chan.net/b/futaba.php"
        assertNull(futaberPostDestinationWarning(img, "としあきです", enabled = false))
        assertNotNull(futaberPostDestinationWarning(img, "としあきです", enabled = true))
        assertNotNull(futaberPostDestinationWarning(may.url, "「」", enabled = true))
        assertNull(futaberPostDestinationWarning(may.url, "としあきです", enabled = true))
        assertNull(futaberPostDestinationWarning(tutorial.url, "としあき「」", enabled = true))
    }

    private fun fav(board: String, thread: String) = FutaberFavorite(board, thread, "t", "", "b", 0, 0L)

    @Test fun aBulkCloseKeepsTheStarredTabsInTheirPlacesOnlyWithTheExtension() {
        val tabs = listOf(FutaberTabKey("a", "1"), FutaberTabKey("a", "2"), FutaberTabKey("a", "3"), FutaberTabKey("a", "4"))
        val favorites = listOf(fav("a", "2"), fav("a", "4"), fav("a", "9"))
        // "他のタブを削除" on tab 3.
        val others = futaberKeepOnlyTab(tabs, FutaberTabKey("a", "3"))
        assertEquals(others, futaberKeepFavoriteTabs(tabs, others, favorites, enabled = false))
        assertEquals(
            listOf(FutaberTabKey("a", "2"), FutaberTabKey("a", "3"), FutaberTabKey("a", "4")),
            futaberKeepFavoriteTabs(tabs, others, favorites, enabled = true)
        )
        // "タブをすべて削除": only the starred tabs stay, and a starred thread that was not a tab is not added.
        assertEquals(
            listOf(FutaberTabKey("a", "2"), FutaberTabKey("a", "4")),
            futaberKeepFavoriteTabs(tabs, emptyList(), favorites, enabled = true)
        )
        assertEquals(emptyList(), futaberKeepFavoriteTabs(tabs, emptyList(), emptyList(), enabled = true))
    }

    private fun entry(boardId: String, url: String, thread: String = "123") = ThreadHistoryEntry(
        threadId = thread, boardId = boardId, title = "題", titleImageUrl = "", boardName = "may/b",
        boardUrl = url, lastVisitedEpochMillis = 1L, replyCount = 5
    )

    @Test fun onlyAnOfficialBoardsAddressOffersToAddTheBoard() {
        val fromThread = assertNotNull(futaberBoardAddRequestForHistory(entry("x", "https://may.2chan.net/b/res/123.htm")))
        assertEquals("https://may.2chan.net/b/", fromThread.canonicalBoardUrl)
        assertEquals("https://may.2chan.net/b/futaba.php", fromThread.boardUrl)
        assertEquals("123", fromThread.threadId)
        assertEquals("may/b", fromThread.name)
        assertEquals("「may/b」を板一覧に追加して開きますか？", futaberBoardAddQuestion(fromThread))
        // An old row holding the board's address keeps its own thread number.
        val fromBoard = assertNotNull(futaberBoardAddRequestForHistory(entry("x", "https://img.2chan.net/b/futaba.php", "77")))
        assertEquals("https://img.2chan.net/b/", fromBoard.canonicalBoardUrl)
        assertEquals("77", fromBoard.threadId)
        // The tutorial board and other sites stay as they were.
        assertNull(futaberBoardAddRequestForHistory(entry("x", "https://www.example.com/t/res/1.htm")))
        assertNull(futaberBoardAddRequest("https://evil.example/b/res/1.htm"))
        // A name is made from the address when there is none.
        assertEquals("zip.2chan.net/1", futaberBoardAddRequest("https://zip.2chan.net/1/res/9.htm")?.name)
        // A board link opens the catalog.
        assertNull(futaberBoardAddRequest("https://may.2chan.net/b/")?.threadId)
    }

    @Test fun aPatrolResultAndAnAddedBoardResolveByTheCanonicalAddress() {
        val boardUrl = "https://may.2chan.net/b/"
        val result = CompatWatchResult(
            history = CompatHistoryEntry(
                canonicalUrl = "${boardUrl}res/55.htm", originalUrl = "${boardUrl}res/55.htm", boardKey = compatBoardKey(boardUrl),
                boardName = "may", threadNo = "55", title = "見つけた", replyCount = 3, contentUpdatedAtEpochMillis = 1L
            ),
            keyword = "k", insertedAtEpochMillis = 1L
        )
        val request = assertNotNull(futaberBoardAddRequestForWatchResult(result))
        assertEquals("55", request.threadId)
        assertEquals("見つけた", request.title)
        assertNull(futaberBoardByCanonicalUrl(listOf(tutorial), request.canonicalBoardUrl))
        assertEquals(may, futaberBoardByCanonicalUrl(listOf(tutorial, may), request.canonicalBoardUrl))
        // Adding it gives a board the request finds; adding it again changes nothing.
        val added = futaberAddBoard(listOf(tutorial), request.name, request.boardUrl)
        assertNotNull(futaberBoardByCanonicalUrl(added, request.canonicalBoardUrl))
        assertEquals(added, futaberAddBoard(added, request.name, request.boardUrl))
    }

    @Test fun theArchiveLinksFollowTheSharedBuilders() {
        val mayThread = "https://may.2chan.net/b/res/1234.htm"
        val links = futaberArchiveLinks(mayThread)
        assertTrue(links.ftbucket.startsWith("https://dev2.ftbucket.info/"))
        assertEquals("https://futabaforest.net/b/res/1234.htm", links.forest)
        assertEquals("https://kako.futakuro.com/futa/may_b/1234/", links.futapo)
        val img = futaberArchiveLinks("https://img.2chan.net/b/res/9.htm")
        assertNull(img.forest)
        assertEquals("https://kako.futakuro.com/futa/img_b/9/", img.futapo)
        val other = futaberArchiveLinks("https://www.example.com/t/res/1.htm")
        assertNull(other.forest); assertNull(other.futapo)
    }

    @Test fun theSavedStatusNamesTheCountsTheSizeAndWhatIsMissing() {
        val saved = SavedThread(
            threadId = "1", boardId = "b", boardName = "板", title = "題", thumbnailPath = null, savedAt = 0L,
            postCount = 12, imageCount = 3, videoCount = 1, totalSize = 2048, status = SaveStatus.PARTIAL,
            incompleteMediaCount = 2
        )
        val text = futaberSavedStatusText(saved)
        assertTrue(text.startsWith("一部を保存できませんでした"), text)
        assertTrue("投稿 12件" in text && "画像 3件" in text && "動画 1件" in text, text)
        assertTrue("容量 " in text, text)
        assertTrue("未保存 2件" in text, text)
        val complete = futaberSavedStatusText(saved.copy(status = SaveStatus.COMPLETED, incompleteMediaCount = 0))
        assertTrue(complete.startsWith("スレッドを保存しました") && "未保存 0件" in complete, complete)
        assertTrue("（HTMLなし）" in futaberSavedStatusText(saved.copy(isHtmlMissing = true)))
    }

    private fun tab(board: String, thread: String, favorite: Boolean) = CompatTab(
        key = "k$thread", canonicalUrl = "${board}res/$thread.htm", originalUrl = "${board}res/$thread.htm",
        boardKey = compatBoardKey(board), boardName = "may", threadNo = thread, title = "題$thread",
        replyCount = 4, favorite = favorite, insertedAtEpochMillis = 0L, contentUpdatedAtEpochMillis = 0L
    )

    @Test fun theOtherModesFavouritesAreTheStarredTabsWithTheirBoardsResolvedByKey() {
        val board = "https://may.2chan.net/b/"
        val tabs = listOf(tab(board, "1", true), tab(board, "2", false), tab("https://img.2chan.net/b/", "3", true))
        val rows = futaberOtherModeFavorites(tabs, listOf(may, tutorial))
        assertEquals(listOf("1", "3"), rows.map { it.tab.threadNo })
        assertEquals(FutaberThreadRef("may-b", "1", "題1", "", 4), rows[0].ref)
        // The img board is not registered: no thread to open, but the board can be added.
        assertNull(rows[1].ref)
        assertEquals("https://img.2chan.net/b/", futaberBoardAddRequestForOtherFavorite(rows[1])?.canonicalBoardUrl)
    }

    @Test fun theSearchArrowsWrapAndStartFromTheFirstOrTheLast() {
        assertEquals(0, futaberSearchHitStep(-1, 3, forward = true))
        assertEquals(2, futaberSearchHitStep(-1, 3, forward = false))
        assertEquals(1, futaberSearchHitStep(0, 3, forward = true))
        assertEquals(0, futaberSearchHitStep(2, 3, forward = true))
        assertEquals(2, futaberSearchHitStep(0, 3, forward = false))
        assertEquals(0, futaberSearchHitStep(0, 0, forward = true))
        assertEquals("-/3", futaberSearchHitLabel(-1, 3))
        assertEquals("2/3", futaberSearchHitLabel(1, 3))
        assertEquals("-/0", futaberSearchHitLabel(0, 0))
    }

    @Test fun aTapScrollsUpInTheUpperThirdDownInTheLowerAndNotInTheMiddle() {
        assertEquals(-1, futaberTapScrollDirection(10f, 900f))
        assertEquals(0, futaberTapScrollDirection(450f, 900f))
        assertEquals(1, futaberTapScrollDirection(890f, 900f))
        assertEquals(0, futaberTapScrollDirection(-1f, 900f))
        assertEquals(0, futaberTapScrollDirection(901f, 900f))
        assertEquals(0, futaberTapScrollDirection(10f, 0f))
    }

    @Test fun theTsumanneRegistrationReportsItsResultFromTheScopeItIsGivenAfterTheSheetIsGone() = runBlocking {
        val client = HttpClient(MockEngine { respond("unused") })
        val notices = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val answer = CompletableDeferred<Result<String>>()
        coroutineScope {
            // The scope of the thread screen; the sheet (and anything of its own) is gone once the action is picked.
            val screen = this
            futaberRegisterTsumanne(screen, client, "https://may.2chan.net/b/res/1.htm", "題", { opened += it }, { notices += it }) { _, url, _ ->
                assertEquals("https://may.2chan.net/b/res/1.htm", url)
                answer.await()
            }
            assertEquals(listOf("つまんね。に登録しています…"), notices)
            launch { answer.complete(Result.success("https://tsumanne.net/my/")) }
        }
        assertEquals(listOf("つまんね。に登録しています…", "つまんね。に登録しました"), notices)
        assertEquals(listOf("https://tsumanne.net/my/"), opened)
        client.close()
    }
}
