package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberThreadSupportTest {
    private val board = BoardSummary("may-b", "may/b", "may", "https://may.2chan.net/b/", "")
    private val ref = FutaberThreadRef("may-b", "123", "題名", "https://may.2chan.net/b/cat/1s.jpg", 7)

    private fun post(subject: String? = null, author: String? = null) = Post(
        id = "1", author = author, subject = subject, timestamp = "26/10/05(月)12:00:00 ID:abcd1234",
        messageHtml = "", imageUrl = null, thumbnailUrl = null
    )

    @Test
    fun catalogItemBecomesAThreadRefWithItsOwnBoard() {
        val item = CatalogItem("55", "https://may.2chan.net/b/res/55.htm", null, "t.jpg", null, replyCount = 3)
        val result = item.toFutaberThreadRef("may-b")
        assertEquals(FutaberThreadRef("may-b", "55", "(無題)", "t.jpg", 3), result)
    }

    @Test
    fun openingANewThreadWritesTheOpenTimeAndCatalogData() {
        val entry = buildFutaberOpenHistoryEntry(null, board, ref, nowEpochMillis = 1_000L)
        assertEquals("123", entry.threadId)
        assertEquals("may-b", entry.boardId)
        assertEquals("題名", entry.title)
        assertEquals("https://may.2chan.net/b/cat/1s.jpg", entry.titleImageUrl)
        assertEquals(1_000L, entry.lastVisitedEpochMillis)
        // Same field use as the ふたちゃ visit record: the thread's own address.
        assertEquals("https://may.2chan.net/b/res/123.htm", entry.boardUrl)
        // History counts posts including the first; the catalog counted 7 replies.
        assertEquals(8, entry.replyCount)
        assertEquals(0, entry.lastReadItemIndex)
    }

    @Test
    fun reopeningKeepsTheReadPositionAndTheStoredPostCount() {
        val existing = ThreadHistoryEntry(
            threadId = "123", boardId = "may-b", title = "古い題名", titleImageUrl = "old.jpg",
            boardName = "may/b", boardUrl = "https://may.2chan.net/b/", lastVisitedEpochMillis = 10L,
            replyCount = 40, lastReadItemIndex = 12, lastReadItemOffset = 34, lastReadPostId = "99",
            hasAutoSave = true, hasSelfPost = true
        )
        val entry = buildFutaberOpenHistoryEntry(existing, board, ref, nowEpochMillis = 2_000L)
        assertEquals(2_000L, entry.lastVisitedEpochMillis)
        // The stored count is what the last visit saw; opening must not overwrite it.
        assertEquals(40, entry.replyCount)
        assertEquals(12, entry.lastReadItemIndex)
        assertEquals(34, entry.lastReadItemOffset)
        assertEquals("99", entry.lastReadPostId)
        assertTrue(entry.hasAutoSave)
        assertTrue(entry.hasSelfPost)
        assertEquals("題名", entry.title)
    }

    @Test
    fun aMissingCatalogThumbnailKeepsTheStoredOne() {
        val existing = ThreadHistoryEntry(
            "123", "may-b", "題名", "old.jpg", "may/b", "https://may.2chan.net/b/", 10L, 1
        )
        val entry = buildFutaberOpenHistoryEntry(existing, board, ref.copy(thumbnailUrl = ""), 5L)
        assertEquals("old.jpg", entry.titleImageUrl)
    }

    @Test
    fun historyLookupMatchesBoardIdOrFallsBackToTheBoardUrl() {
        val byId = ThreadHistoryEntry("123", "may-b", "a", "", "n", "https://x/", 1L, 0)
        val legacy = ThreadHistoryEntry("123", "", "b", "", "n", "https://may.2chan.net/b/", 1L, 0)
        val other = ThreadHistoryEntry("123", "img-b", "c", "", "n", "https://img.2chan.net/b/", 1L, 0)
        assertEquals(byId, findFutaberHistoryEntry(listOf(other, byId), board, "123"))
        assertEquals(legacy, findFutaberHistoryEntry(listOf(other, legacy), board, "123"))
        assertNull(findFutaberHistoryEntry(listOf(other), board, "123"))
        assertNull(findFutaberHistoryEntry(listOf(byId), board, "999"))
    }

    @Test
    fun theDateColumnDropsTheTrailingId() {
        assertEquals("26/10/05(月)12:00:00", futaberTimestampWithoutId("26/10/05(月)12:00:00 ID:abcd1234"))
        assertEquals("26/10/05(月)12:00:00", futaberTimestampWithoutId(" 26/10/05(月)12:00:00 "))
    }

    @Test
    fun subjectAndNameAppearOnlyWhenTheyDifferFromTheDefaults() {
        assertNull(futaberPostTitleLine(post()))
        assertNull(futaberPostTitleLine(post("無題", "としあき")))
        assertEquals("題名", futaberPostTitleLine(post("題名", "としあき")))
        // "常に表示" shows the board defaults too.
        assertEquals("無題  としあき", futaberPostTitleLine(post("無題", "としあき"), always = true))
        assertEquals("名前", futaberPostTitleLine(post("無題", "名前")))
        assertEquals("題名  名前", futaberPostTitleLine(post("題名", "名前")))
    }

    @Test
    fun quoteLinesStartWithAGreaterThanSignOfEitherWidth() {
        assertTrue(isFutaberQuoteLine(">引用"))
        assertTrue(isFutaberQuoteLine("＞全角の引用"))
        assertTrue(isFutaberQuoteLine("  >字下げ"))
        assertFalse(isFutaberQuoteLine("本文>途中"))
        assertFalse(isFutaberQuoteLine(""))
    }

    @Test
    fun thumbnailRatioFallsBackToASquareAndIsBounded() {
        assertEquals(1f, futaberThumbnailAspectRatio(null, null))
        assertEquals(1f, futaberThumbnailAspectRatio(0, 100))
        assertEquals(2f, futaberThumbnailAspectRatio(200, 100))
        assertEquals(4f, futaberThumbnailAspectRatio(1000, 10))
        assertEquals(0.25f, futaberThumbnailAspectRatio(10, 1000))
    }

    private fun postWith(id: String, thumb: String? = null) = post().copy(id = id, thumbnailUrl = thumb)

    @Test
    fun theRefCarriesWhatTheHistorySaidBeforeThisVisit() {
        assertEquals(ref, futaberRefWithHistory(ref, null))
        val existing = ThreadHistoryEntry(
            "123", "may-b", "題", "", "n", "u", 1L, replyCount = 40,
            lastReadItemIndex = 12, lastReadItemOffset = 34, lastReadPostId = "99"
        )
        val carried = futaberRefWithHistory(ref, existing)
        assertEquals(40, carried.seenCount)
        assertEquals("99", carried.resumePostId)
        assertEquals(12, carried.resumeIndex)
        assertEquals(34, carried.resumeOffset)
    }

    @Test
    fun resumePrefersTheRememberedPostThenTheRowAndStaysInRange() {
        val posts = listOf(postWith("1"), postWith("2"), postWith("3"), postWith("4"))
        assertEquals(2, futaberResumeIndex(posts, ref.copy(resumePostId = "3", resumeIndex = 0)))
        // A post that disappeared falls back to the row, clamped to the list.
        assertEquals(3, futaberResumeIndex(posts, ref.copy(resumePostId = "gone", resumeIndex = 99)))
        assertEquals(1, futaberResumeIndex(posts, ref.copy(resumeIndex = 1)))
        // Nothing remembered (first visit) leaves the list at the top.
        assertNull(futaberResumeIndex(posts, ref))
        assertNull(futaberResumeIndex(emptyList(), ref.copy(resumeIndex = 3)))
    }

    @Test
    fun firstNewPostFollowsTheSeenCountOnlyWhenSomethingArrived() {
        assertEquals(10, futaberFirstNewIndex(seenCount = 10, total = 14))
        assertNull(futaberFirstNewIndex(seenCount = 14, total = 14))
        assertNull(futaberFirstNewIndex(seenCount = 20, total = 14))
        assertNull(futaberFirstNewIndex(seenCount = 0, total = 14))
    }

    @Test
    fun aLoadedPageRaisesTheCountAndFillsAMissingThumbnailWithoutTouchingTheVisit() {
        val mine = ThreadHistoryEntry("123", "may-b", "題", "", "n", "u", 5L, 3, 7, 8, "55")
        val other = ThreadHistoryEntry("124", "may-b", "他", "", "n", "u", 6L, 1)
        val posts = listOf(postWith("1", "op.jpg"), postWith("2"), postWith("3"), postWith("4"), postWith("5"))
        val result = futaberHistoryAfterLoad(listOf(other, mine), "may-b", "123", posts)
        assertEquals(other, result[0])
        assertEquals(5, result[1].replyCount)
        assertEquals("op.jpg", result[1].titleImageUrl)
        assertEquals(5L, result[1].lastVisitedEpochMillis)
        assertEquals(7, result[1].lastReadItemIndex)
        assertEquals("55", result[1].lastReadPostId)
        // Posts that disappeared never lower the count the last visit saw.
        assertEquals(3, futaberHistoryAfterLoad(listOf(mine), "may-b", "123", posts.take(2))[0].replyCount)
    }

    @Test
    fun theSaidaneCountReadsTheDigitsAndHidesZero() {
        assertEquals(3, futaberSaidaneCount("そうだね×3"))
        assertEquals(12, futaberSaidaneCount("+12"))
        assertEquals(null, futaberSaidaneCount("そうだね"))
        assertEquals(null, futaberSaidaneCount("そうだね×0"))
        assertEquals(null, futaberSaidaneCount(null))
    }

    @Test
    fun remoteActionsAskFirstExceptSaidaneAndAreBlockedWhereTheyCannotRun() {
        assertEquals(null, FutaberRemoteAction.Saidane.confirmTitle)
        assertTrue(FutaberRemoteAction.DeleteOwn.confirmTitle != null)
        assertTrue(FutaberRemoteAction.Report.confirmTitle != null)
        // A saved copy sends nothing; deleting needs the delete key; the others need nothing else.
        assertTrue(futaberRemoteActionBlockedReason(FutaberRemoteAction.Saidane, offline = true, deleteKey = "k") != null)
        assertTrue(futaberRemoteActionBlockedReason(FutaberRemoteAction.DeleteOwn, offline = false, deleteKey = " ") != null)
        assertEquals(null, futaberRemoteActionBlockedReason(FutaberRemoteAction.DeleteOwn, offline = false, deleteKey = "k"))
        assertEquals(null, futaberRemoteActionBlockedReason(FutaberRemoteAction.Report, offline = false, deleteKey = ""))
    }

    @Test
    fun aLoadedPageWritesTheRealTitleOverTheCatalogsShortOne() {
        val mine = ThreadHistoryEntry("123", "may-b", "短い", "", "n", "u", 5L, 3)
        val first = post(subject = "件名").copy(id = "1", messageHtml = "本文の一行目<br>二行目")
        // The first line of the body, as ふたちゃ records it; the visit time and read position stay.
        val written = futaberHistoryAfterLoad(listOf(mine), "may-b", "123", listOf(first))[0]
        assertEquals("本文の一行目", written.title)
        assertEquals(5L, written.lastVisitedEpochMillis)
        // Without a body the subject is used; with no posts the title already there stays.
        assertEquals("件名", futaberHistoryAfterLoad(listOf(mine), "may-b", "123", listOf(post(subject = "件名")))[0].title)
        assertEquals("短い", futaberHistoryAfterLoad(listOf(mine), "may-b", "123", emptyList())[0].title)
    }

    @Test
    fun readingTheLivePageMarksTheThreadAliveAndLiftsTheFallenMark() {
        val fallen = ThreadHistoryEntry(
            "123", "may-b", "題", "", "n", "u", 5L, 3, isAutoRefreshDisabled = true, lastConfirmedAliveEpochMillis = 10L
        )
        val other = ThreadHistoryEntry("124", "may-b", "他", "", "n", "u", 6L, 1, isAutoRefreshDisabled = true)
        val result = futaberHistoryMarkAlive(listOf(other, fallen), "may-b", "123", nowEpochMillis = 99L)
        assertEquals(other, result[0])
        assertEquals(99L, result[1].lastConfirmedAliveEpochMillis)
        assertFalse(result[1].isAutoRefreshDisabled)
        // Only that: the visit, position and title are the entry's own.
        assertEquals(fallen.copy(lastConfirmedAliveEpochMillis = 99L, isAutoRefreshDisabled = false), result[1])
    }

    @Test
    fun aThreadOpenedFromTheTabsStartsAtItsOwnPlaceNotTheLastThreadsOne() {
        val posts = listOf(postWith("1"), postWith("2"), postWith("3"))
        val rows = futaberVisibleRows(posts, FutaberViewFilter(), futaberReplyIndex(posts))
        // Never read: stays at the top (the list state is new for each thread, so there is nothing to scroll from).
        assertNull(futaberRestoreRowPosition(posts, rows, ref))
        assertEquals(2, futaberRestoreRowPosition(posts, rows, ref.copy(resumePostId = "3")))
        // A remembered post that NG hides lands on the next listed row.
        val hiddenSecond = futaberVisibleRows(posts, FutaberViewFilter(), futaberReplyIndex(posts), hidden = setOf("2"))
        assertEquals(1, futaberRestoreRowPosition(posts, hiddenSecond, ref.copy(resumePostId = "2")))
        assertNull(futaberRestoreRowPosition(emptyList(), emptyList(), ref.copy(resumePostId = "2")))
    }
}
