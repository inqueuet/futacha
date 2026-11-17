package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.shared.SharedKeys
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md): the Futaba canonical addresses and the keys hashed from them, byte for
 * byte as they are before あいもげ support. Tabs, posts read and NG rules are stored under these hashes, so a later batch
 * that adds あいもげ must leave every row unchanged. あいもげ addresses give null today and must keep giving null from
 * these two functions (あいもげ goes through new functions).
 */
class CanonicalKeyGoldenTest {
    private val boardGolden: List<Triple<String, String?, String?>> = listOf(
        Triple("https://may.2chan.net/b/", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://may.2chan.net/b", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("http://may.2chan.net/b/", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://may.2chan.net/b/futaba.php", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://may.2chan.net/b/futaba.php?mode=cat&sort=1", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://may.2chan.net/b/futaba.htm", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://MAY.2chan.net/b//", "https://may.2chan.net/b/", "compat_board_093561aba2d78ad8"),
        Triple("https://img.2chan.net/b/", "https://img.2chan.net/b/", "compat_board_da703a0736ae6f0a"),
        Triple("https://dat.2chan.net/img2/futaba.php", "https://dat.2chan.net/img2/", "compat_board_0daa70c887a39f49"),
        Triple("https://www.2chan.net/hinan/", "https://www.2chan.net/hinan/", "compat_board_e6827498d41a52dc"),
        Triple("https://jun.2chan.net/jun/", "https://jun.2chan.net/jun/", "compat_board_9c05ba54dadd4c51"),
        Triple("https://may.2chan.net/b/res/1234.htm", null, null),
        Triple("https://example.com/b/", null, null),
        Triple("https://nijiurachan.net/b/aimoge/", null, null),
        Triple("https://nijiurachan.net/b/aimoge", null, null),
        Triple("https://nijiurachan.net/b/aimoge/futaba.php", null, null),
        Triple("https://api.nijiurachan.net/b/aimoge/", null, null),
        Triple("https://nijiurachan.net/", null, null),
    )

    private val threadGolden: List<List<String?>> = listOf(
        listOf("https://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/", "b", "1439323740", "compat_tab_198a20e9a8b73254"),
        listOf("http://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/", "b", "1439323740", "compat_tab_198a20e9a8b73254"),
        listOf("https://may.2chan.net/b/res/1439323740.htm/", "https://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/", "b", "1439323740", "compat_tab_198a20e9a8b73254"),
        listOf("https://may.2chan.net//b//res/1439323740.htm", "https://may.2chan.net/b/res/1439323740.htm", "https://may.2chan.net/b/", "b", "1439323740", "compat_tab_198a20e9a8b73254"),
        listOf("https://img.2chan.net/b/res/1474754356.htm?x=1#r2", "https://img.2chan.net/b/res/1474754356.htm", "https://img.2chan.net/b/", "b", "1474754356", "compat_tab_67be127ed1713228"),
        listOf("https://dat.2chan.net/img2/res/123.htm", "https://dat.2chan.net/img2/res/123.htm", "https://dat.2chan.net/img2/", "img2", "123", "compat_tab_97cec5d03cfeb967"),
        listOf("https://www.2chan.net/hinan/res/9.htm", "https://www.2chan.net/hinan/res/9.htm", "https://www.2chan.net/hinan/", "hinan", "9", "compat_tab_b1a04866cfdfe817"),
        listOf("https://may.2chan.net/b/", null, null, null, null, null),
        listOf("https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f", null, null, null, null, null),
        listOf("https://nijiurachan.net/b/aimoge/res/123.htm", null, null, null, null, null),
        listOf("https://api.nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f", null, null, null, null, null),
    )

    private val sharedKeysGolden: Map<String, String> = mapOf(
        "https://may.2chan.net/b/futaba.php" to "SharedThreadKeys(board=SharedBoardKeys(boardId=may-b, boardUrl=https://may.2chan.net/b/futaba.php, canonicalBoardUrl=https://may.2chan.net/b/, compatBoardKey=compat_board_093561aba2d78ad8, futaberStoredBoardId=compat_board_093561aba2d78ad8, ftutterBoardKey=may.2chan.net/b/), threadId=1234567, threadUrl=https://may.2chan.net/b/res/1234567.htm, canonicalThreadUrl=https://may.2chan.net/b/res/1234567.htm, compatTabKey=compat_tab_9f729d322f649664, appStateHistoryKey=may-b::1234567, appStateSelfPostKey=may-b::1234567, futaberTabEntry=compat_board_093561aba2d78ad8\n1234567, futaberReplyDraftKey=r:compat_board_093561aba2d78ad8:1234567, ftutterThreadUrl=https://may.2chan.net/b/res/1234567.htm, ftutterReplyDraftKey=res:may.2chan.net/b/res/1234567.htm)",
        "https://img.2chan.net/b/" to "SharedThreadKeys(board=SharedBoardKeys(boardId=img-b, boardUrl=https://img.2chan.net/b/, canonicalBoardUrl=https://img.2chan.net/b/, compatBoardKey=compat_board_da703a0736ae6f0a, futaberStoredBoardId=compat_board_da703a0736ae6f0a, ftutterBoardKey=img.2chan.net/b/), threadId=1234567, threadUrl=https://img.2chan.net/b/res/1234567.htm, canonicalThreadUrl=https://img.2chan.net/b/res/1234567.htm, compatTabKey=compat_tab_5f3245a6ee2b7d12, appStateHistoryKey=img-b::1234567, appStateSelfPostKey=img-b::1234567, futaberTabEntry=compat_board_da703a0736ae6f0a\n1234567, futaberReplyDraftKey=r:compat_board_da703a0736ae6f0a:1234567, ftutterThreadUrl=https://img.2chan.net/b/res/1234567.htm, ftutterReplyDraftKey=res:img.2chan.net/b/res/1234567.htm)",
        "https://example.com/tutorial/" to "SharedThreadKeys(board=SharedBoardKeys(boardId=tutorial, boardUrl=https://example.com/tutorial/, canonicalBoardUrl=null, compatBoardKey=null, futaberStoredBoardId=tutorial, ftutterBoardKey=example.com/tutorial/), threadId=1234567, threadUrl=https://example.com/tutorial/res/1234567.htm, canonicalThreadUrl=null, compatTabKey=null, appStateHistoryKey=tutorial::1234567, appStateSelfPostKey=tutorial::1234567, futaberTabEntry=tutorial\n1234567, futaberReplyDraftKey=r:tutorial:1234567, ftutterThreadUrl=https://example.com/tutorial/res/1234567.htm, ftutterReplyDraftKey=res:example.com/tutorial/res/1234567.htm)",
        "https://nijiurachan.net/b/aimoge/" to "SharedThreadKeys(board=SharedBoardKeys(boardId=aimoge, boardUrl=https://nijiurachan.net/b/aimoge/, canonicalBoardUrl=null, compatBoardKey=null, futaberStoredBoardId=aimoge, ftutterBoardKey=nijiurachan.net/b/aimoge/), threadId=1234567, threadUrl=https://nijiurachan.net/b/aimoge/res/1234567.htm, canonicalThreadUrl=null, compatTabKey=null, appStateHistoryKey=aimoge::1234567, appStateSelfPostKey=aimoge::1234567, futaberTabEntry=aimoge\n1234567, futaberReplyDraftKey=r:aimoge:1234567, ftutterThreadUrl=https://nijiurachan.net/b/aimoge/res/1234567.htm, ftutterReplyDraftKey=res:nijiurachan.net/b/aimoge/res/1234567.htm)",
    )

    @Test
    fun boardAddressesKeepTheirCanonicalFormAndKey() {
        assertEquals(CanonicalKeyGoldenInputs.boards, boardGolden.map { it.first })
        boardGolden.forEach { (url, canonical, key) ->
            val actual = canonicalizeBoardUrl(url)
            assertEquals(canonical, actual, url)
            assertEquals(key, actual?.let(::compatBoardKey), url)
        }
    }

    @Test
    fun threadAddressesKeepTheirCanonicalFormAndKey() {
        assertEquals(CanonicalKeyGoldenInputs.threads, threadGolden.map { it[0] })
        threadGolden.forEach { row ->
            val url = row[0]!!
            val actual = canonicalizeThreadUrl(url)
            assertEquals(row.drop(1), listOf(actual?.canonicalUrl, actual?.canonicalBoardUrl, actual?.boardPath, actual?.threadNo, actual?.canonicalUrl?.let(::compatTabKey)), url)
        }
    }

    @Test
    fun sharedKeysOfEachModeStayTheSame() {
        CanonicalKeyGoldenInputs.summaries.forEach { (id, url) ->
            val board = BoardSummary(id = id, name = id, category = "", url = url, description = "")
            assertEquals(sharedKeysGolden.getValue(url), SharedKeys.thread(board, "1234567").toString(), url)
        }
    }
}
