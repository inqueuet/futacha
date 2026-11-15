package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.shared.SharedKeys
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.ftutter.ftutterIsThreadUrl
import com.valoser.futacha.shared.ui.ftutter.ftutterThreadIdFromUrl
import com.valoser.futacha.shared.ui.ftutter.ftutterThreadUrl
import com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatThreadUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AimogeThreadKeysTest {
    private val board = BoardSummary(id = "aimoge-aimg", name = "aimg", category = "", url = "https://nijiurachan.net/b/aimg/", description = "")
    private val futaba = BoardSummary(id = "b", name = "b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val a = "01a0f76f-0000-7000-8000-000000000001"
    private val b = "01a0f76f-0000-7000-8000-000000000002"

    @Test fun twoThreadsOfOneBoardGetTwoKeysInEveryMode() = withAimoge {
        assertEquals("https://nijiurachan.net/b/aimg/thread/$a", futaberCompatThreadUrl(board, a))
        assertNotEquals(futaberCompatTabKey(board, a), futaberCompatTabKey(board, b))
        assertNotEquals(ftutterThreadUrl(board, a), ftutterThreadUrl(board, b))
        assertEquals("https://nijiurachan.net/b/aimg/thread/$a", SharedKeys.thread(board, a).canonicalThreadUrl)
        assertNotEquals(SharedKeys.thread(board, a).compatTabKey, SharedKeys.thread(board, b).compatTabKey)
        val url = "https://nijiurachan.net/b/aimg/thread/$a"
        assertEquals(a, ftutterThreadIdFromUrl(url))
        assertEquals(true, ftutterIsThreadUrl(url))
        assertEquals(false, ftutterIsThreadUrl("https://nijiurachan.net/b/aimg/app-auth?requestId=x"))
        val history = ThreadHistoryEntry(threadId = a, boardId = board.id, title = "t", titleImageUrl = "", boardName = "aimg",
            boardUrl = url, lastVisitedEpochMillis = 0, replyCount = 1)
        assertEquals(url, history.toCompatHistoryEntry()?.canonicalUrl)
    }

    @Test fun whileOffNothingIsMadeForAnAimogeThreadAndFutabaKeysStay() = withAimoge(enabled = false) {
        assertNull(ftutterThreadIdFromUrl("https://nijiurachan.net/b/aimg/thread/$a"))
        assertEquals(false, ftutterIsThreadUrl("https://nijiurachan.net/b/aimg/thread/$a"))
        assertEquals("https://may.2chan.net/b/res/123.htm", futaberCompatThreadUrl(futaba, "123"))
        assertEquals("https://may.2chan.net/b/res/123.htm", SharedKeys.thread(futaba, "123").canonicalThreadUrl)
    }
}
