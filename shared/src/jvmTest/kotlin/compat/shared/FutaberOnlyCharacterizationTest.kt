package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.post
import com.valoser.futacha.shared.compat.shared.SingleMode.tutorial
import com.valoser.futacha.shared.compat.shared.SingleMode.withStores
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.futaber.FUTABER_DRAFT_MAX_COMMENT_CHARS
import com.valoser.futacha.shared.ui.futaber.FUTABER_DRAFT_MAX_COUNT
import com.valoser.futacha.shared.ui.futaber.FutaberPostTarget
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.FutaberThreadRef
import com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey
import com.valoser.futacha.shared.ui.futaber.futaberDiscardDraft
import com.valoser.futacha.shared.ui.futaber.futaberDraftForTarget
import com.valoser.futacha.shared.ui.futaber.futaberDraftsWithText
import com.valoser.futacha.shared.ui.futaber.futaberFavoriteFor
import com.valoser.futacha.shared.ui.futaber.futaberFavoriteViews
import com.valoser.futacha.shared.ui.futaber.futaberFavoritesFromStore
import com.valoser.futacha.shared.ui.futaber.futaberFavoritesToStore
import com.valoser.futacha.shared.ui.futaber.futaberFirstNewIndex
import com.valoser.futacha.shared.ui.futaber.futaberNgHiddenIds
import com.valoser.futacha.shared.ui.futaber.futaberPruneFavorites
import com.valoser.futacha.shared.ui.futaber.futaberPruneTabsOfRemovedBoards
import com.valoser.futacha.shared.ui.futaber.futaberRefWithHistory
import com.valoser.futacha.shared.ui.futaber.futaberResumeIndex
import com.valoser.futacha.shared.ui.futaber.futaberSeenCountForBoard
import com.valoser.futacha.shared.ui.futaber.futaberSeenCountsAfterForBoard
import com.valoser.futacha.shared.ui.futaber.futaberTabViews
import com.valoser.futacha.shared.ui.futaber.futaberTabsFromStore
import com.valoser.futacha.shared.ui.futaber.futaberTabsToStore
import com.valoser.futacha.shared.ui.futaber.futaberToggleFavorite
import com.valoser.futacha.shared.ui.futaber.futaberToggleTab
import com.valoser.futacha.shared.ui.futaber.encodeFutaberTabs
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ふたばー風 used alone (D-B0, docs/futaber-evolution-data-2026-10-31.md §5): what it stores and shows today. Each name is
 * a promise the batches sharing the data keep. Its tabs, favourites, posts seen and drafts are its own settings rows
 * (`compat.futaber.*`); the history is AppState's.
 */
class FutaberOnlyCharacterizationTest {
    private fun ref(board: BoardSummary, threadId: String, replies: Int = 3) =
        FutaberThreadRef(board.id, threadId, "スレ$threadId", "", replies)

    /** The history after ふたばー風 opened [threads] in order (the latest first). */
    private suspend fun opened(state: com.valoser.futacha.shared.state.AppStateStore, vararg threads: Pair<BoardSummary, String>): List<ThreadHistoryEntry> {
        threads.forEachIndexed { index, (board, threadId) ->
            val existing = state.history.first().firstOrNull { it.boardId == board.id && it.threadId == threadId }
            state.prependOrReplaceHistoryEntry(buildFutaberOpenHistoryEntry(existing, board, ref(board, threadId), nowEpochMillis = 1_000L + index))
        }
        return state.history.first()
    }

    @Test fun futaberOnlyStripShowsOnlyRegisteredTabs() = withStores { compat, state ->
        val history = opened(state, may to "1", may to "2", img to "3")
        assertEquals(3, history.size, "opening a thread writes the history")
        val tabs = futaberToggleTab(emptyList(), FutaberTabKey(may.id, "2"))
        assertEquals(listOf("2"), futaberTabViews(tabs, history).map { it.key.threadId }, "opening never adds a tab")
        // A registered tab whose thread has no history row is kept but not shown.
        val withMissing = futaberToggleTab(tabs, FutaberTabKey(img.id, "9"))
        assertEquals(listOf("2"), futaberTabViews(withMissing, history).map { it.key.threadId })
        assertEquals(2, withMissing.size)
        assertTrue(compat.tabs.first().isEmpty(), "the other modes' tab list is not written")
    }

    @Test fun futaberOnlyTabsAreStoredUnderTheSharedBoardKeyAndOlderValuesStillRead() {
        val boards = SingleMode.boards
        val tabs = listOf(FutaberTabKey(may.id, "1"), FutaberTabKey(tutorial.id, "2"))
        val stored = futaberTabsToStore(tabs, boards)
        assertEquals(encodeFutaberTabs(listOf(FutaberTabKey(futaberCompatBoardKey(may), "1"), FutaberTabKey("t", "2"))), stored,
            "an official board under the shared key, the tutorial board under its id")
        assertEquals(tabs, futaberTabsFromStore(stored, boards))
        assertEquals(tabs, futaberTabsFromStore(encodeFutaberTabs(tabs), boards), "an older value with the board ids reads the same")
    }

    /** Plan B8a (Q3b): the 50-tab cap is gone; the strip takes the 51st (the shared limit trims, not this mode). */
    @Test fun futaberOnlyStripTakesTheFiftyFirstTab() {
        val full = (1..50).map { FutaberTabKey(may.id, "$it") }
        val next = FutaberTabKey(may.id, "999")
        assertEquals(full + next, futaberToggleTab(full, next))
    }

    @Test fun futaberOnlyFavoritesOutliveTheHistoryButGoWithTheirBoard() {
        val boards = SingleMode.boards
        var favorites = futaberToggleFavorite(emptyList(), futaberFavoriteFor(may, ref(may, "1", replies = 7), nowEpochMillis = 1))
        favorites = futaberToggleFavorite(favorites, futaberFavoriteFor(img, ref(img, "2"), nowEpochMillis = 2))
        assertEquals(listOf("2", "1"), favorites.map { it.threadId }, "the newest star first")
        val views = futaberFavoriteViews(favorites, history = emptyList())
        assertEquals(listOf("スレ2", "スレ1"), views.map { it.favorite.title }, "shown with no history at all")
        assertEquals(7, views.last().replyCount)
        assertEquals(favorites, futaberFavoritesFromStore(futaberFavoritesToStore(favorites, boards), boards))
        assertEquals(listOf("1"), futaberPruneFavorites(favorites, listOf(may, tutorial)).map { it.threadId }, "a removed board takes its favourites")
        assertEquals(favorites, futaberPruneFavorites(favorites, emptyList()), "an empty board list is not every board removed")
        assertEquals(listOf("1"), futaberToggleFavorite(favorites, favorites.first()).map { it.threadId }, "a second star removes it")
    }

    @Test fun futaberOnlyRemovingABoardDropsItsTabsAndNothingElse() {
        val tabs = listOf(FutaberTabKey(may.id, "1"), FutaberTabKey(img.id, "2"), FutaberTabKey(may.id, "no-history"))
        assertEquals(listOf("1", "no-history"), futaberPruneTabsOfRemovedBoards(tabs, listOf(may, tutorial)).map { it.threadId })
        assertEquals(tabs, futaberPruneTabsOfRemovedBoards(tabs, SingleMode.boards), "a thread missing from the history keeps its tab")
        assertEquals(tabs, futaberPruneTabsOfRemovedBoards(tabs, emptyList()))
    }

    @Test fun futaberOnlyNewPostsStartAfterTheLastSeenCount() = withStores { _, state ->
        val stored = futaberSeenCountsAfterForBoard(null, may, "1", seen = 5, lastPostId = "105")
        assertEquals(5, futaberSeenCountForBoard(stored, may, "1"))
        // The record is the number of posts the page had when the reading position settled (FutaberThread's
        // onScrollSettled), and the latest one replaces it: a smaller one too (it is not the larger of the two).
        assertEquals(3, futaberSeenCountForBoard(futaberSeenCountsAfterForBoard(stored, may, "1", seen = 3), may, "1"))
        val history = opened(state, may to "1")
        val reopened = futaberRefWithHistory(ref(may, "1", replies = 9), history.single(), seenCount = futaberSeenCountForBoard(stored, may, "1"))
        assertEquals(5, reopened.seenCount)
        assertEquals(5, futaberFirstNewIndex(reopened.seenCount, total = 9), "the 6th post is the first new one")
        assertNull(futaberFirstNewIndex(9, total = 9), "nothing new")
        assertNull(futaberFirstNewIndex(0, total = 9), "a thread never seen marks nothing")
    }

    @Test fun futaberOnlyOpenPositionPrefersTheRememberedPost() {
        val posts = (1..10).map { post("10$it", "本文") }
        assertEquals(6, futaberResumeIndex(posts, ref(may, "1").copy(resumePostId = "107", resumeIndex = 2)))
        assertEquals(2, futaberResumeIndex(posts, ref(may, "1").copy(resumePostId = "gone", resumeIndex = 2)), "else the remembered row")
        assertNull(futaberResumeIndex(posts, ref(may, "1")), "a thread never read opens at the top")
    }

    @Test fun futaberOnlyDraftsKeepSixUnderTheSharedBoardKeyAndOutliveTheTab() {
        val boards = SingleMode.boards
        var drafts = emptyList<com.valoser.futacha.shared.ui.futaber.FutaberDraft>()
        (1..7).forEach { drafts = futaberDraftsWithText(drafts, FutaberPostTarget.Reply(may.id, "$it", "t"), boards, "", "本文$it", nowEpochMillis = it.toLong()) }
        assertEquals(FUTABER_DRAFT_MAX_COUNT, drafts.size)
        assertEquals((7 downTo 2).map { "r:${futaberCompatBoardKey(may)}:$it" }, drafts.map { it.key }, "the newest first, the oldest goes")
        val long = futaberDraftsWithText(drafts, FutaberPostTarget.CreateThread(may.id), boards, "題", "あ".repeat(3_000), nowEpochMillis = 8)
        assertEquals(FUTABER_DRAFT_MAX_COMMENT_CHARS, futaberDraftForTarget(long, FutaberPostTarget.CreateThread(may.id), boards)!!.comment.length)
        val sent = futaberDiscardDraft(drafts, FutaberPostTarget.Reply(may.id, "7", "t"), boards)
        assertNull(futaberDraftForTarget(sent, FutaberPostTarget.Reply(may.id, "7", "t"), boards), "a sent post leaves no draft")
        assertEquals(5, sent.size)
    }

    @Test fun futaberOnlyNgHidesByTheSharedListsAndTheThreadsRules() = withStores { _, state ->
        state.setNgWords(listOf("荒らし"))
        state.setNgHeaders(listOf("ID:abc"))
        val tabKey = futaberCompatTabKey(may, "1")
        val rule = CompatNgRule("post-4", CompatNgKind.THREAD_POST_NO, tabKey, "4", createdAtEpochMillis = 1)
        val posts = listOf(post("1", "スレ"), post("2", "荒らしです"), post("3", "普通", posterId = "ID:abc"), post("4", "普通"), post("5", "普通"))
        assertEquals(setOf("2", "3", "4"), futaberNgHiddenIds(posts, state.ngWords.first(), state.ngHeaders.first(), listOf(rule), tabKey, futaberCompatBoardKey(may)))
    }
}
