package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.distinctBoardsById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * 2026-10-13 全体検証 7.6 の2: on iOS/desktop the tutorial imported from the shared store (id `compat_board_…`) was not
 * recognized as the tutorial, so every synchronization kept it and appended the store's copy again; ふたちゃ then crashed
 * on the duplicated LazyColumn key. Runs on the JVM and on iOS Native.
 */
class CompatTutorialDuplicateSyncTest {
    private val tutorialUrl = "https://www.example.com/t/futaba.php"
    private val tutorialKey = compatBoardKey("https://img.2chan.net/t/")
    private val tutorialCompat = CompatBoard(tutorialKey, "チュートリアル＠ふたちゃ", "https://img.2chan.net/t/", tutorialUrl, 0)
    private val mayUrl = "https://may.2chan.net/b/"
    private val mayCompat = CompatBoard(compatBoardKey(mayUrl), "may", mayUrl, mayUrl, 1)
    private val fixture = BoardSummary("t", "チュートリアル＠ふたちゃ", "チュートリアル", tutorialUrl, "チュートリアル")
    private val importedTutorial = tutorialCompat.toBoardSummary()

    @Test
    fun repeatedSynchronizationWithTheStoreTutorialDoesNotAddCopies() {
        // The AppState lost its "t" while the store still holds the tutorial (the Simulator state).
        var boards = emptyList<BoardSummary>()
        repeat(4) { round ->
            boards = synchronizeModernBoardsFromCompatibility(boards, listOf(tutorialCompat, mayCompat))
            assertEquals(listOf(tutorialKey, mayCompat.key), boards.map(BoardSummary::id), "round $round")
        }
    }

    @Test
    fun alreadySavedCopiesAreCollapsedBySynchronization() {
        val saved = List(6) { importedTutorial } + mayCompat.toBoardSummary()
        val synchronized = synchronizeModernBoardsFromCompatibility(saved, listOf(tutorialCompat, mayCompat))
        assertEquals(listOf(tutorialKey, mayCompat.key), synchronized.map(BoardSummary::id))
    }

    @Test
    fun theBundledFixtureIdIsKeptWhenACopyIsAlsoSaved() {
        val synchronized = synchronizeModernBoardsFromCompatibility(
            listOf(importedTutorial, fixture, mayCompat.toBoardSummary()), listOf(tutorialCompat, mayCompat)
        )
        assertEquals(listOf("t", mayCompat.key), synchronized.map(BoardSummary::id))
        assertEquals(fixture, synchronized.first())
    }

    @Test
    fun theTutorialIsRecognizedByItsUrlSoADeletionInToshiakiStillWins() {
        // Deleted in としあき(仮) on a store that holds the tutorial: the copy with a compat id goes too.
        assertEquals(
            listOf(mayCompat.key),
            synchronizeModernBoardsFromCompatibility(listOf(importedTutorial), listOf(mayCompat.copy(sortOrder = 0)))
                .map(BoardSummary::id)
        )
        val bareUrl = fixture.copy(id = "tutorial-bare", url = "https://www.example.com/t/")
        assertEquals(
            listOf(mayCompat.key),
            synchronizeModernBoardsFromCompatibility(listOf(bareUrl), listOf(mayCompat.copy(sortOrder = 0)))
                .map(BoardSummary::id)
        )
    }

    @Test
    fun androidLikeStoreKeepsOneTutorialInPlace() {
        // The Android store never holds the tutorial: it stays where it is, once.
        val synchronized = synchronizeModernBoardsFromCompatibility(
            listOf(fixture, importedTutorial), listOf(mayCompat.copy(sortOrder = 0)), compatibilityHoldsTutorial = false
        )
        assertEquals(listOf("t", mayCompat.key), synchronized.map(BoardSummary::id))
    }

    @Test
    fun aNonFutabaBoardTheStoreAlsoHoldsIsNotDuplicated() {
        // The iOS/desktop stores accept any example.com address (mapped onto img.2chan.net), not only the tutorial.
        val customUrl = "https://www.example.com/x/futaba.php"
        val customCompat = CompatBoard(compatBoardKey("https://img.2chan.net/x/"), "x", "https://img.2chan.net/x/", customUrl, 1)
        val custom = BoardSummary("x-local", "x", "外部", customUrl, "")
        var boards = listOf(mayCompat.toBoardSummary(), custom)
        repeat(3) {
            boards = synchronizeModernBoardsFromCompatibility(boards, listOf(mayCompat.copy(sortOrder = 0), customCompat))
            assertEquals(listOf(mayCompat.key, "x-local"), boards.map(BoardSummary::id))
        }
        assertEquals("外部", boards.last().category)
    }

    @Test
    fun savedDuplicatesAreRemovedKeepingTheFirstPositionAndTheFixture() {
        val may = BoardSummary("may", "may", "二次元", mayUrl, "", pinned = true)
        val boards = listOf(importedTutorial, may, fixture, importedTutorial, may.copy(id = "may2", url = "${mayUrl}futaba.php"))
        assertEquals(listOf(fixture, may), removeDuplicateBoardSummaries(boards))
        // The same id under another address is a broken copy too: the first one stays.
        val sameId = listOf(may, BoardSummary("may", "img", "", "https://img.2chan.net/b/", ""))
        assertEquals(listOf(may), removeDuplicateBoardSummaries(sameId))
        val clean = listOf(fixture, may)
        assertSame(clean, removeDuplicateBoardSummaries(clean))
    }

    @Test
    fun theBoardListShowsEachIdOnce() {
        val boards = List(6) { importedTutorial } + mayCompat.toBoardSummary()
        assertEquals(listOf(tutorialKey, mayCompat.key), distinctBoardsById(boards).map(BoardSummary::id))
        val clean = listOf(fixture, mayCompat.toBoardSummary())
        assertSame(clean, distinctBoardsById(clean))
    }
}
