package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutaberSavedBoxSupportTest {
    private val may = BoardSummary(id = "may-b", name = "may/b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val img = BoardSummary(id = "img-b", name = "img/b", category = "", url = "https://img.2chan.net/b/futaba.php", description = "")

    private fun saved(boardId: String, thread: String, title: String = "題名", savedAt: Long = 1L, size: Long = 2048L,
        incomplete: Int = 0, htmlMissing: Boolean = false) = SavedThread(
        threadId = thread, boardId = boardId, boardName = "板", title = title, thumbnailPath = null, savedAt = savedAt,
        postCount = 7, imageCount = 0, videoCount = 0, totalSize = size, status = SaveStatus.COMPLETED,
        incompleteMediaCount = incomplete, isHtmlMissing = htmlMissing
    )

    private fun page(id: String) = ThreadPage(threadId = id, boardTitle = null, expiresAtLabel = null, deletedNotice = null, posts = emptyList())

    @Test
    fun theBoardIsFoundByThisAppsIdOrBySharedBoardKey() {
        // The shared page save stores the compatibility board key; other saves store the board id.
        assertEquals(may, futaberBoardForSaved(saved(futaberCompatBoardKey(may), "1"), listOf(img, may)))
        assertEquals(img, futaberBoardForSaved(saved("img-b", "1"), listOf(img, may)))
        assertNull(futaberBoardForSaved(saved("other", "1"), listOf(img, may)))
    }

    @Test
    fun theRefCarriesTheSavedTitleAndPostCount() {
        val ref = futaberRefForSaved(saved("may-b", "55", title = ""), may)
        assertEquals("may-b", ref.boardId)
        assertEquals("55", ref.threadId)
        assertEquals("(無題)", ref.title)
        assertEquals(7, ref.replyCount)
    }

    @Test
    fun theRowShowsTheBoardOnTheLeftAndTheTimeAndSizeOnTheRight() {
        val complete = saved("may-b", "1", size = 2048)
        assertEquals("板", futaberSavedBoardLine(complete, openable = true))
        assertTrue(futaberSavedDetail(complete).endsWith("2 KB"), futaberSavedDetail(complete))
        assertEquals("板（一部未保存）", futaberSavedBoardLine(saved("may-b", "1", incomplete = 2), true))
        assertEquals("板（一部未保存）", futaberSavedBoardLine(saved("may-b", "1", htmlMissing = true), true))
        assertEquals("板（板が未登録）", futaberSavedBoardLine(saved("may-b", "1"), openable = false))
    }

    @Test
    fun theNewestSaveIsListedFirst() {
        val ordered = futaberSavedOrder(listOf(saved("b", "1", savedAt = 10), saved("b", "2", savedAt = 30), saved("b", "3", savedAt = 20)))
        assertEquals(listOf("2", "3", "1"), ordered.map { it.threadId })
    }

    @Test
    fun theOfflineRepositoryServesOnlyItsThreadAndLeavesTheRestToTheRealOne() {
        runBlocking { offlineRepositoryChecks() }
    }

    private suspend fun offlineRepositoryChecks() {
        val saved = page("123")
        val repository = FutaberOfflineRepository(FakeBoardRepository(), "123", saved)
        assertSame(saved, repository.getThread("https://may.2chan.net/b/futaba.php", "123"))
        assertSame(saved, repository.getThreadContent("https://may.2chan.net/b/futaba.php", "123").page)
        // Another thread number is not answered from the saved copy.
        val other = runCatching { repository.getThread("https://example.com/t/futaba.php", "999") }
        other.getOrNull()?.let { assertNotSame(saved, it) }
    }
}
