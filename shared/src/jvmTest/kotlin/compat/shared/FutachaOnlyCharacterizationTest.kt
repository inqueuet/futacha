package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatThreadStatusFlags
import com.valoser.futacha.shared.compat.bridgeProfileBoardsAndHistory
import com.valoser.futacha.shared.compat.catalogReplyCountsByUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatReplyCount
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.updatedCatalogReplyCount
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.page
import com.valoser.futacha.shared.compat.shared.SingleMode.post
import com.valoser.futacha.shared.compat.shared.SingleMode.withStores
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.FutachaThreadHistoryContext
import com.valoser.futacha.shared.ui.board.FutachaDraftFields
import com.valoser.futacha.shared.ui.board.FutachaDraftOwner
import com.valoser.futacha.shared.ui.board.FutachaDraftSession
import com.valoser.futacha.shared.ui.board.FutachaDraftWrite
import com.valoser.futacha.shared.ui.board.FutachaThreadTabWrite
import com.valoser.futacha.shared.ui.board.applyNgFilters
import com.valoser.futacha.shared.ui.board.persistFutachaDraft
import com.valoser.futacha.shared.ui.board.toggleFutachaTabFavorite
import com.valoser.futacha.shared.ui.board.writeFutachaThreadTab
import com.valoser.futacha.shared.ui.compat.FUTACHA_DRAFT_PERSIST_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.futachaDraftPersistEnabled
import com.valoser.futacha.shared.ui.dismissHistoryEntry
import com.valoser.futacha.shared.ui.persistFutachaThreadScrollPositionImmediately
import com.valoser.futacha.shared.ui.recordFutachaVisitedThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ふたちゃ used alone (D-B0, docs/futaber-evolution-data-2026-10-31.md §5): what it stores and shows today. Each name is a
 * promise the batches sharing the data keep.
 */
class FutachaOnlyCharacterizationTest {
    private fun pageWrite(board: com.valoser.futacha.shared.model.BoardSummary, threadId: String, posts: Int, now: Long): FutachaThreadTabWrite {
        val url = SingleMode.threadUrl(board, threadId)
        val replies = page(threadId, (1..posts).map { post("$it", "本文$it") }).compatReplyCount()
        return FutachaThreadTabWrite(
            tabKey = compatTabKey(url), sourceUrl = url, boardKey = SharedKeys.board(board).compatBoardKey!!, boardName = board.name,
            threadId = threadId, title = "スレ$threadId", replyCount = replies, thumbnailUrl = null, snapshotChanged = true,
            snapshotRevision = now, storedSnapshotRevision = null, confirmedGone = false, confirmedStatus = CompatThreadStatusFlags(),
            nowEpochMillis = now
        )
    }

    @Test fun futachaOnlyOpeningAThreadMakesATabThatCountsRepliesWithoutTheOp() = withStores { compat, _ ->
        writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 6, now = 10))
        val opened = compat.tabs.first().single()
        assertEquals(5, opened.replyCount, "6 posts are 5 replies")
        assertEquals(5, opened.checkedReplyCount, "what was loaded is read")
        assertEquals(0, opened.unreadCount)
        writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 9, now = 20))
        val again = compat.tabs.first().single()
        assertEquals(8 to 8, again.replyCount to again.checkedReplyCount)
        assertEquals(10, again.insertedAtEpochMillis, "the tab keeps its place")
    }

    @Test fun futachaOnlyCatalogNewCountIsTheTabsRepliesBeyondTheReadOnes() = withStores { compat, _ ->
        writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 6, now = 10))
        val tab = compat.tabs.first().single()
        val catalog = listOf(CatalogItem("100", SingleMode.threadUrl(may, "100"), "スレ100", null, null, replyCount = 8))
        val counts = catalogReplyCountsByUrl(catalog)
        val probed = updatedCatalogReplyCount(tab, counts, fetchedAt = 30)
        assertEquals(8, probed.replyCount)
        assertEquals(5, probed.checkedReplyCount, "the catalog never moves what was read")
        assertEquals(3, probed.unreadCount, "新着 +3")
        assertEquals(probed, updatedCatalogReplyCount(probed, catalogReplyCountsByUrl(catalog.map { it.copy(replyCount = 4) }), 40),
            "a smaller count never lowers it")
    }

    @Test fun futachaOnlyStarProtectsATabFromTheTrimAndGoesWithTheTab() = withStores { compat, _ ->
        compat.openTab(SingleMode.tab(may, "1", insertedAt = 1))
        assertTrue(toggleFutachaTabFavorite(compat, SingleMode.tab(may, "1", 1).key))
        (2..201).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = it.toLong())) }
        val kept = compat.tabs.first()
        // B4 (decision Q3): the shared limit is 200; the star and the active tab stay, the 199 others go back to 178.
        assertEquals(180, kept.size, "200 -> 180 after the 201st")
        assertTrue(kept.any { it.threadNo == "1" && it.favorite }, "the starred oldest tab stays")
        assertTrue(kept.none { it.threadNo == "2" }, "the oldest one without a star goes")
        compat.closeTabs(setOf(SingleMode.tab(may, "1", 1).key), 200)
        writeFutachaThreadTab(compat, pageWrite(may, "1", posts = 2, now = 300))
        assertFalse(compat.tabs.first().first { it.threadNo == "1" }.favorite, "closing the tab takes its star")
    }

    @Test fun futachaOnlyReadPositionIsTheHistoryRowsPost() = withStores { _, state ->
        val context = FutachaThreadHistoryContext("スレ100", "https://may.2chan.net/b/res/100.htm", 6, "")
        recordFutachaVisitedThread(state, state.history.first(), "100", may, context, currentTimeMillis = 1_000)
        val visited = state.history.first().single()
        assertEquals(6, visited.replyCount, "the count the thread was opened with (posts with the op)")
        assertEquals("https://may.2chan.net/b/res/100.htm", visited.boardUrl, "the row holds the thread's address")
        persistFutachaThreadScrollPositionImmediately(state, "100", index = 4, offset = 12, postId = "104", board = may, context = context)
        val read = state.history.first().single()
        assertEquals(Triple(4, 12, "104"), Triple(read.lastReadItemIndex, read.lastReadItemOffset, read.lastReadPostId))
        assertEquals(1_000, read.lastVisitedEpochMillis, "scrolling never moves the visit")
    }

    @Test fun futachaOnlyNgListsHideTheMatchingPosts() = withStores { _, state ->
        state.setNgWords(listOf("荒らし"))
        state.setNgHeaders(listOf("ID:abc"))
        val thread = page("100", listOf(post("1", "スレ"), post("2", "荒らしです"), post("3", "普通", posterId = "ID:abc"), post("4", "普通")))
        val shown = applyNgFilters(thread, state.ngHeaders.first(), state.ngWords.first(), enabled = true)
        assertEquals(listOf("1", "4"), shown.posts.map { it.id })
        assertEquals(thread, applyNgFilters(thread, state.ngHeaders.first(), state.ngWords.first(), enabled = false), "NG off shows all")
    }

    @Test fun futachaDraftSwitchOffKeepsNothing() = withStores { compat, _ ->
        assertFalse(futachaDraftPersistEnabled(emptyMap()), "the switch is off until it is turned on")
        assertFalse(futachaDraftPersistEnabled(mapOf(FUTACHA_DRAFT_PERSIST_STORAGE_KEY to "OFF")))
        assertTrue(futachaDraftPersistEnabled(mapOf(FUTACHA_DRAFT_PERSIST_STORAGE_KEY to "ON")))
        // With the switch on: written under the tab, without the delete key; no tab, nothing written; sent, deleted.
        val tabKey = compatTabKey(SingleMode.threadUrl(may, "100"))
        val typed = FutachaDraftFields(name = "名無し", comment = "書きかけ")
        assertEquals(FutachaDraftWrite.NoOwner, persistFutachaDraft(compat, null, FutachaDraftOwner.Reply(tabKey), typed, FutachaDraftSession()))
        assertNull(compat.loadDraft(tabKey))
        writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 2, now = 10))
        val session = FutachaDraftSession()
        assertEquals(FutachaDraftWrite.Saved, persistFutachaDraft(compat, null, FutachaDraftOwner.Reply(tabKey), typed, session, nowEpochMillis = 20))
        val stored = compat.loadDraft(tabKey)!!
        assertEquals("書きかけ" to "", stored.comment to stored.deleteKey)
        assertEquals(FutachaDraftWrite.Deleted, persistFutachaDraft(compat, null, FutachaDraftOwner.Reply(tabKey), FutachaDraftFields(name = "名無し"), session))
        assertNull(compat.loadDraft(tabKey), "the sent form leaves no draft")
    }

    @Test fun futachaOnlyRemovingABoardTakesItsTabsAndDraftsThroughTheSync() = withStores { compat, state -> coroutineScope {
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val bridge = launch(Dispatchers.Default) {
            bridgeProfileBoardsAndHistory(state, compat, usesAppStateData = true) { _, failure -> failures += failure }
        }
        try {
            writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 2, now = 10))
            writeFutachaThreadTab(compat, pageWrite(img, "200", posts = 2, now = 20))
            val imgTab = compatTabKey(SingleMode.threadUrl(img, "200"))
            compat.saveDraft(CompatReplyDraft(imgTab, comment = "img", updatedAtEpochMillis = 30))
            // The person removes the board after the synchronization saw it (as in the app, where the first pass runs
            // when the screen is shown and its record is kept across starts). Removed before any pass, the board would
            // be one only the compatibility store has, which D-B1 keeps (D1).
            val imgKey = compatBoardKey("https://img.2chan.net/b/")
            withTimeout(10_000) {
                while (imgKey !in decodeSharedBoardsRecord(compat.loadSharedData()[SHARED_BOARDS_RECORD_KEY]).seen) delay(20)
            }
            state.setBoards(listOf(may, SingleMode.tutorial))
            withTimeout(10_000) { while (compat.boards.first().any { it.key == imgKey }) delay(20) }
            assertEquals(listOf("100"), compat.tabs.first().map { it.threadNo })
            assertNull(compat.loadDraft(imgTab))
            assertTrue(failures.isEmpty(), failures.toString())
        } finally { bridge.cancel() }
    } }

    @Test fun futachaOnlyDismissingAHistoryRowKeepsItsTab() = withStores { compat, state ->
        val context = FutachaThreadHistoryContext("スレ100", "https://may.2chan.net/b/res/100.htm", 2, "")
        recordFutachaVisitedThread(state, state.history.first(), "100", may, context, currentTimeMillis = 1_000)
        writeFutachaThreadTab(compat, pageWrite(may, "100", posts = 2, now = 10))
        state.addSelfPostIdentifier("100", "101", boardId = may.id)
        dismissHistoryEntry(state, autoSavedThreadRepository = null, compatibilityStore = compat, entry = state.history.first().single())
        assertTrue(state.history.first().isEmpty())
        assertTrue(state.selfPostIdentifiersByThread.first().isEmpty(), "the own post numbers go with the row")
        assertEquals(listOf("100"), compat.tabs.first().map { it.threadNo }, "the tab stays")
    }
}
