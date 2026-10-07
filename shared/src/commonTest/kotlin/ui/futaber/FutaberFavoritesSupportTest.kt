package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberFavoritesSupportTest {
    private val board = BoardSummary(id = "b1", name = "板1", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val other = BoardSummary(id = "b2", name = "板2", category = "", url = "https://img.2chan.net/b/futaba.php", description = "")

    private fun favorite(thread: String, now: Long = 1L, title: String = "題名$thread", board: BoardSummary = this.board) =
        futaberFavoriteFor(board, FutaberThreadRef(board.id, thread, title, "https://t/$thread.jpg", 5), now)

    @Test
    fun aFavouriteSurvivesTheStoredFormWithEveryField() {
        val list = listOf(favorite("1", now = 10), favorite("2", now = 20, board = other))
        val decoded = decodeFutaberFavorites(encodeFutaberFavorites(list))
        assertEquals(list, decoded)
        assertEquals("板2", decoded[1].boardName)
        assertEquals(20L, decoded[1].addedAtEpochMillis)
    }

    @Test
    fun damagedOrMissingStoredValuesReadAsNoFavourites() {
        assertEquals(emptyList(), decodeFutaberFavorites(null))
        assertEquals(emptyList(), decodeFutaberFavorites(""))
        assertEquals(emptyList(), decodeFutaberFavorites("{not json"))
        // A row with the wrong number of fields is skipped, the others are kept.
        val good = encodeFutaberFavorites(listOf(favorite("1")))
        val mixed = good.dropLast(1) + ",\"broken\"]"
        assertEquals(listOf("1"), decodeFutaberFavorites(mixed).map { it.threadId })
    }

    @Test
    fun aTitleWithLineBreaksOrAnOverlongTitleStillRoundTrips() {
        val fav = favorite("7", title = "一行目\n二行目\r三行目" + "あ".repeat(200))
        assertFalse(fav.title.contains('\n') || fav.title.contains('\r'))
        assertEquals(60, fav.title.length)
        assertEquals(listOf(fav), decodeFutaberFavorites(encodeFutaberFavorites(listOf(fav))))
        assertEquals("(無題)", favorite("8", title = "").title)
    }

    @Test
    fun toggleStarsAtTheTopAndAgainRemovesIt() {
        val first = favorite("1")
        val second = favorite("2")
        val starred = futaberToggleFavorite(futaberToggleFavorite(emptyList(), first), second)
        assertEquals(listOf("2", "1"), starred.map { it.threadId })
        assertTrue(futaberIsFavorite(starred, FutaberTabKey("b1", "1")))
        val unstarred = futaberToggleFavorite(starred, first)
        assertEquals(listOf("2"), unstarred.map { it.threadId })
        assertFalse(futaberIsFavorite(unstarred, FutaberTabKey("b1", "1")))
        // The same thread number on another board is a different thread.
        assertFalse(futaberIsFavorite(starred, FutaberTabKey("b2", "1")))
    }

    @Test
    fun theListIsCappedAndTheStoredTextStaysUnderTheLimit() {
        var list = emptyList<FutaberFavorite>()
        repeat(FUTABER_MAX_FAVORITES + 10) { list = futaberToggleFavorite(list, favorite("$it", now = it.toLong())) }
        assertEquals(FUTABER_MAX_FAVORITES, list.size)
        // The newest is kept and the oldest dropped.
        assertEquals("${FUTABER_MAX_FAVORITES + 9}", list.first().threadId)
        // Worst case: every field at its limit.
        val heavy = (0 until FUTABER_MAX_FAVORITES).map {
            futaberFavoriteFor(board.copy(name = "板".repeat(100)), FutaberThreadRef("b1", "$it", "題".repeat(100), "u".repeat(500), 1), it.toLong())
        }
        val text = encodeFutaberFavorites(heavy)
        assertTrue(text.length <= 18_000, "stored text ${text.length} chars")
        assertTrue(decodeFutaberFavorites(text).isNotEmpty())
    }

    @Test
    fun removingABoardRemovesItsFavouritesButClearingHistoryDoesNot() {
        val list = listOf(favorite("1"), favorite("2", board = other))
        assertEquals(listOf("2"), futaberPruneFavorites(list, listOf(other)).map { it.threadId })
        assertEquals(list, futaberPruneFavorites(list, listOf(board, other)))
        // The row follows the history count when the thread was read since, and keeps its own otherwise.
        val history = listOf(
            ThreadHistoryEntry(
                threadId = "1", boardId = "b1", title = "t", titleImageUrl = "", boardName = "板1", boardUrl = "u",
                lastVisitedEpochMillis = 1, replyCount = 99
            )
        )
        val views = futaberFavoriteViews(list, history)
        assertEquals(99, views[0].replyCount)
        assertEquals(5, views[1].replyCount)
        assertEquals(5, futaberFavoriteViews(list, emptyList())[0].replyCount)
    }

    @Test
    fun aFavouriteOpensOnlyWhileItsBoardIsRegistered() {
        val view = futaberFavoriteViews(listOf(favorite("3")), emptyList()).single()
        val ref = futaberRefForFavorite(view, listOf(board))
        assertEquals("3", ref?.threadId)
        assertEquals("題名3", ref?.title)
        assertNull(futaberRefForFavorite(view, listOf(other)))
    }
}
