package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.buildSelfPostStorageKey
import com.valoser.futacha.shared.state.historyEntryIdentity
import com.valoser.futacha.shared.ui.ftutter.FtutterPostTarget
import com.valoser.futacha.shared.ui.ftutter.ftutterCanonicalThreadUrl
import com.valoser.futacha.shared.ui.ftutter.ftutterDraftKey
import com.valoser.futacha.shared.ui.ftutter.ftutterNgBoardKey
import com.valoser.futacha.shared.ui.ftutter.ftutterThreadUrl
import com.valoser.futacha.shared.ui.futaber.FutaberPostTarget
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatThreadUrl
import com.valoser.futacha.shared.ui.futaber.futaberDraftStoreKey
import com.valoser.futacha.shared.ui.futaber.futaberHistoryThreadUrl
import com.valoser.futacha.shared.ui.futaber.futaberStoredBoardId
import com.valoser.futacha.shared.ui.futaber.futaberStoredBoardIndex
import com.valoser.futacha.shared.ui.futaber.futaberTabsFromStore
import com.valoser.futacha.shared.ui.futaber.futaberTabsToStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SharedKeys] makes the same keys each mode makes today (D-B0): nothing reads through it yet, so a later batch can move a
 * mode onto it without changing a single stored key.
 */
class SharedKeysTest {
    private val may = BoardSummary("b", "may", "", "https://may.2chan.net/b/futaba.php", "")
    private val imgHttp = BoardSummary("img", "img", "", "http://img.2chan.net/b/futaba.php", "")
    private val dec = BoardSummary("b3", "dec55", "", "https://dec.2chan.net/55/", "")
    private val jun = BoardSummary("jun", "jun", "", "https://jun.2chan.net/jun/futaba.htm", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private val boards = listOf(may, imgHttp, dec, jun, tutorial)
    private val official = listOf(may, imgHttp, dec, jun)
    private val threadId = "1444338343"

    @Test fun theBoardKeysAreTheOnesEachModeWritesToday() {
        boards.forEach { board ->
            val keys = SharedKeys.board(board)
            assertEquals(board.id, keys.boardId)
            assertEquals(canonicalizeBoardUrl(board.url), keys.canonicalBoardUrl, board.url)
            assertEquals(futaberStoredBoardId(board), keys.futaberStoredBoardId, board.url)
            assertEquals(ftutterNgBoardKey(board.url), keys.ftutterBoardKey, board.url)
        }
        official.forEach { board ->
            val keys = SharedKeys.board(board)
            assertTrue(keys.isOfficial)
            // ふたばー風 and ふたちゃ / としあき(仮) (compatBoardKey of the canonical address) make the same key.
            assertEquals(futaberCompatBoardKey(board), keys.compatBoardKey)
            assertEquals(compatBoardKey(canonicalizeBoardUrl(board.url)!!), keys.compatBoardKey)
        }
        assertFalse(SharedKeys.board(tutorial).isOfficial)
        assertNull(SharedKeys.board(tutorial).compatBoardKey)
        assertEquals("t", SharedKeys.board(tutorial).futaberStoredBoardId)
        assertEquals("https://img.2chan.net/b/", SharedKeys.board(imgHttp).canonicalBoardUrl)
        assertEquals("img.2chan.net/b/", SharedKeys.board(imgHttp).ftutterBoardKey)
    }

    @Test fun theThreadKeysAreTheOnesEachModeWritesToday() {
        boards.forEach { board ->
            val keys = SharedKeys.thread(board, threadId)
            // ふたばー風 / ふたったー風's history address, and ふたったー風's stored thread address (over https).
            assertEquals(futaberHistoryThreadUrl(board, threadId), keys.threadUrl, board.url)
            assertEquals(ftutterCanonicalThreadUrl(ftutterThreadUrl(board, threadId)), keys.ftutterThreadUrl, board.url)
            // AppState's history row and own post numbers.
            assertEquals(historyEntryIdentity(threadId, board.id, keys.threadUrl), keys.appStateHistoryKey)
            assertEquals(buildSelfPostStorageKey(threadId, board.id), keys.appStateSelfPostKey)
            // ふたばー風's tab entry as it is stored, and its drafts.
            val storedTabs = Json.decodeFromString(ListSerializer(String.serializer()),
                futaberTabsToStore(listOf(FutaberTabKey(board.id, threadId)), boards))
            assertEquals(listOf(keys.futaberTabEntry), storedTabs, board.url)
            assertEquals(futaberDraftStoreKey(FutaberPostTarget.Reply(board.id, threadId, "title"), boards), keys.futaberReplyDraftKey)
            assertEquals(futaberDraftStoreKey(FutaberPostTarget.CreateThread(board.id), boards), SharedKeys.futaberBuildDraftKey(board))
            // ふたったー風's drafts.
            assertEquals(ftutterDraftKey(FtutterPostTarget.Reply(board.id, threadId), board.url), keys.ftutterReplyDraftKey)
            assertEquals(ftutterDraftKey(FtutterPostTarget.Create(board.id), board.url), SharedKeys.ftutterBuildDraftKey(board))
        }
        official.forEach { board ->
            val keys = SharedKeys.thread(board, threadId)
            // ふたばー風 (its NG rules, image viewer tab) and ふたちゃ (`${canonicalBoard}res/N.htm`, FutachaThreadFeatures).
            assertEquals(futaberCompatThreadUrl(board, threadId), keys.canonicalThreadUrl)
            assertEquals(futaberCompatTabKey(board, threadId), keys.compatTabKey)
            assertEquals(compatTabKey("${canonicalizeBoardUrl(board.url)}res/$threadId.htm"), keys.compatTabKey)
            // としあき(仮) keys a tab by the canonical form of the thread's address.
            assertEquals(compatTabKey(canonicalizeThreadUrl(keys.threadUrl)!!.canonicalUrl), keys.compatTabKey)
            // A history row shared from AppState lands on the same compatibility row.
            val row = ThreadHistoryEntry(threadId, board.id, "t", "", board.name, keys.threadUrl, 1L, 3)
            assertEquals(keys.canonicalThreadUrl, row.toCompatHistoryEntry()!!.canonicalUrl)
            assertEquals(keys.board.compatBoardKey, row.toCompatHistoryEntry()!!.boardKey)
        }
        val tutorialKeys = SharedKeys.thread(tutorial, threadId)
        assertNull(tutorialKeys.canonicalThreadUrl)
        assertNull(tutorialKeys.compatTabKey)
        assertEquals("t::$threadId", tutorialKeys.appStateHistoryKey)
        assertEquals("t\n$threadId", tutorialKeys.futaberTabEntry)
        assertEquals("https://img.2chan.net/b/res/$threadId.htm", SharedKeys.thread(imgHttp, threadId).ftutterThreadUrl)
    }

    @Test fun aStoredFormLeadsBackToTheRegisteredBoardAndThread() {
        // ふたばー風: the shared key of an official board and an older value's board id both name the board (M3).
        val index = futaberStoredBoardIndex(boards)
        boards.forEach { board ->
            val keys = SharedKeys.board(board)
            assertEquals(index[keys.futaberStoredBoardId], SharedKeys.boardOfFutaberStoredId(keys.futaberStoredBoardId, boards)?.id)
            assertEquals(board.id, SharedKeys.boardOfFutaberStoredId(board.id, boards)?.id)
            keys.compatBoardKey?.let { assertEquals(board.id, SharedKeys.boardOfCompatKey(it, boards)?.id) }
        }
        val storedTabs = futaberTabsToStore(boards.map { FutaberTabKey(it.id, threadId) }, boards)
        val fromStore = futaberTabsFromStore(storedTabs, boards)
        val entries = Json.decodeFromString(ListSerializer(String.serializer()), storedTabs)
        assertEquals(fromStore.map { it.boardId to it.threadId },
            entries.map { entry -> SharedKeys.threadOfFutaberTabEntry(entry, boards)!!.let { it.board.boardId to it.threadId } })
        // AppState's `boardId::threadId`.
        boards.forEach { board ->
            assertEquals(SharedKeys.thread(board, threadId), SharedKeys.threadOfAppStateKey(SharedKeys.thread(board, threadId).appStateHistoryKey, boards))
        }
        assertNull(SharedKeys.threadOfAppStateKey("gone::$threadId", boards))
        assertNull(SharedKeys.threadOfAppStateKey(threadId, boards))
        // Any of the modes' thread addresses.
        listOf(
            "https://may.2chan.net/b/res/$threadId.htm" to may,
            "http://may.2chan.net/b/res/$threadId.htm" to may,
            "https://IMG.2chan.net/b/res/$threadId.htm" to imgHttp,
            SharedKeys.thread(imgHttp, threadId).ftutterThreadUrl to imgHttp,
            "https://dec.2chan.net/55/res/$threadId.htm" to dec,
            SharedKeys.thread(tutorial, threadId).ftutterThreadUrl to tutorial
        ).forEach { (url, board) ->
            assertEquals(SharedKeys.thread(board, threadId), SharedKeys.threadOfUrl(url, boards), url)
        }
        assertNull(SharedKeys.threadOfUrl("https://www.2chan.net/b/res/$threadId.htm", boards), "a board that is not registered")
        assertNull(SharedKeys.threadOfUrl("https://may.2chan.net/b/", boards), "a board address is not a thread")
        assertEquals("https://may.2chan.net/b/res/$threadId.htm", SharedKeys.canonicalThreadUrlOf("http://may.2chan.net//b/res/$threadId.htm"))
    }
}
