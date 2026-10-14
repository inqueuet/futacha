package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FutachaUnregisteredBoardSupportTest {
    private fun entry(url: String, name: String = "二次元裏 may", boardId: String = "unknown") = ThreadHistoryEntry(
        threadId = "1234", boardId = boardId, title = "t", titleImageUrl = "", boardName = name,
        boardUrl = url, lastVisitedEpochMillis = 0L, replyCount = 3
    )

    @Test fun officialThreadAndBoardUrlsCanBeAdded() {
        assertEquals("https://may.2chan.net/b/", resolveFutachaAddableBoardUrl(entry("https://may.2chan.net/b/res/1234.htm")))
        assertEquals("https://img.2chan.net/b/", resolveFutachaAddableBoardUrl(entry("http://img.2chan.net/b/futaba.php")))
    }

    @Test fun tutorialAndUnofficialUrlsAreNotOffered() {
        assertNull(resolveFutachaAddableBoardUrl(entry("https://example.com/b/res/1.htm")))
        assertNull(resolveFutachaAddableBoardUrl(entry("")))
        assertNull(FutachaUnregisteredBoardRequest("x").addableBoardUrl)
    }

    @Test fun addedBoardOpensTheSameEntryAndDuplicatesAreKept() {
        val e = entry("https://may.2chan.net/b/res/1234.htm")
        val request = FutachaUnregisteredBoardRequest(e.boardName, e)
        val url = assertNotNull(request.addableBoardUrl)
        assertNull(resolveHistoryEntrySelection(e, emptyList()))
        val boards = addFutachaBoardIfMissing(emptyList(), futachaAddedBoardName(request, url), url)
        assertEquals(1, boards.size)
        assertEquals("二次元裏 may", boards.single().name)
        val selection = assertNotNull(resolveHistoryEntrySelection(e, boards))
        assertEquals("1234", selection.threadId)
        assertEquals(boards.single().id, selection.boardId)
        assertSame(boards, addFutachaBoardIfMissing(boards, "別名", url))
    }

    @Test fun blankBoardNameFallsBackToTheUrl() {
        val e = entry("https://may.2chan.net/b/res/1234.htm", name = " ")
        assertEquals("may.2chan.net/b", futachaAddedBoardName(FutachaUnregisteredBoardRequest(" ", e), "https://may.2chan.net/b/"))
    }

    @Test fun historySelectionPassesTheEntryForUnregisteredBoards() {
        var request: FutachaUnregisteredBoardRequest? = null
        val callbacks = buildFutachaNavigationCallbacks(
            currentBoards = { emptyList<BoardSummary>() },
            currentNavigationState = { FutachaNavigationState() },
            setNavigationState = {},
            onUnregisteredBoard = { request = it }
        )
        val e = entry("https://may.2chan.net/b/res/1234.htm")
        callbacks.onHistoryEntrySelected(e)
        assertSame(e, request?.historyEntry)
        assertEquals("https://may.2chan.net/b/", request?.addableBoardUrl)
    }
}
