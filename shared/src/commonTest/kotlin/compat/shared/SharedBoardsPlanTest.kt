package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board synchronization's rules (D-B1, compat/shared/SharedBoards.kt): the two lists are joined, only a deletion in
 * the mode's own list takes a board away, and a tombstone keeps it away. Runs on the JVM and on iOS Native.
 */
class SharedBoardsPlanTest {
    private fun board(path: String, id: String = path) = BoardSummary(id, path, "", "https://may.2chan.net/$path/futaba.php", "")
    private val a = board("a")
    private val b = board("b")
    private val c = board("c")
    private fun key(board: BoardSummary) = compatBoardKey("https://may.2chan.net/${board.name}/")
    private fun compat(vararg boards: BoardSummary): List<CompatBoard> = modernBoardsToCompatibility(boards.toList())
    private val now = 10_000_000_000L

    private fun plan(
        side: SharedBoardSide,
        appState: List<BoardSummary>,
        compat: List<CompatBoard>,
        record: SharedBoardsRecord = SharedBoardsRecord(),
        holds: Boolean = false,
        at: Long = now
    ) = planSharedBoards(side, appState, compat, record, holds, at)

    @Test fun theFirstPassJoinsTheListsAndDeletesNothing() {
        val forAppState = plan(SharedBoardSide.APP_STATE, listOf(a), compat(a, b))
        assertEquals(listOf(key(b)), forAppState.addToAppState.map { it.id }, "a board only the store has is added to the list")
        assertTrue(forAppState.compatDeletes.isEmpty() && forAppState.removeFromAppState.isEmpty())
        assertTrue(forAppState.finalRecord.tombstones.isEmpty())
        assertEquals(setOf(key(a), key(b)), forAppState.finalRecord.seen.toSet())
        val forCompat = plan(SharedBoardSide.COMPAT, listOf(a, c), compat(a))
        assertEquals(listOf(c), forCompat.compatImports, "a board only the list has is added to the store")
        assertEquals(listOf(key(a), key(c)), forCompat.compatAfter.map(CompatBoard::key))
        assertTrue(forCompat.compatDeletes.isEmpty() && forCompat.removeFromAppState.isEmpty())
    }

    @Test fun aDeletionInTheModesListLeavesATombstoneAndGoesFromTheStore() {
        val record = SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(key(a), key(b)))
        val deleted = plan(SharedBoardSide.APP_STATE, listOf(a), compat(a, b), record)
        assertEquals(listOf(key(b)), deleted.compatDeletes)
        assertEquals(mapOf(key(b) to now), deleted.eventsRecord.tombstones)
        assertTrue(deleted.addToAppState.isEmpty(), "the store's copy is not added back")
        // としあき(仮): deleted in the store, it goes from the list.
        val inCompat = plan(SharedBoardSide.COMPAT, listOf(a, b), compat(a), SharedBoardsRecord(SharedBoardSide.COMPAT, listOf(key(a), key(b))))
        assertEquals(setOf(key(b)), inCompat.removeFromAppState)
        assertTrue(inCompat.compatImports.isEmpty(), "the list's copy is not added back")
    }

    @Test fun aStaleCopyDoesNotComeBackAndAddingTheBoardAgainLiftsTheTombstone() {
        val record = SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(key(a)), mapOf(key(b) to now - 1_000))
        // A slow screen wrote the deleted board back into the store.
        val stale = plan(SharedBoardSide.APP_STATE, listOf(a), compat(a, b), record)
        assertEquals(listOf(key(b)), stale.compatDeletes)
        assertTrue(stale.addToAppState.isEmpty())
        // The person added it again.
        val again = plan(SharedBoardSide.APP_STATE, listOf(a, b), compat(a, b), record)
        assertTrue(again.compatDeletes.isEmpty() && again.removeFromAppState.isEmpty())
        assertNull(again.eventsRecord.tombstones[key(b)])
        // The same in としあき(仮).
        val compatRecord = record.copy(side = SharedBoardSide.COMPAT)
        assertEquals(setOf(key(b)), plan(SharedBoardSide.COMPAT, listOf(a, b), compat(a), compatRecord).removeFromAppState)
        assertNull(plan(SharedBoardSide.COMPAT, listOf(a), compat(a, b), compatRecord).eventsRecord.tombstones[key(b)])
    }

    @Test fun aDeletionJustBeforeASwitchIsTakenFromTheModeLeft() {
        // Deleted in ふたちゃ, and the app went to としあき(仮) before ふたちゃ's pass.
        val fromAppState = plan(
            SharedBoardSide.COMPAT, listOf(a), compat(a, b), SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(key(a), key(b)))
        )
        assertEquals(listOf(key(b)), fromAppState.compatDeletes)
        assertTrue(fromAppState.compatImports.isEmpty())
        // Deleted in としあき(仮), and the app went to ふたちゃ.
        val fromCompat = plan(
            SharedBoardSide.APP_STATE, listOf(a, b), compat(a), SharedBoardsRecord(SharedBoardSide.COMPAT, listOf(key(a), key(b)))
        )
        assertEquals(setOf(key(b)), fromCompat.removeFromAppState)
        assertEquals(listOf(key(a)), fromCompat.compatUpserts.map(CompatBoard::key))
    }

    @Test fun anEmptyStoreIsNotTakenForEveryBoardDeleted() {
        // The store was reset (or is not loaded) while the record says the last pass was in としあき(仮).
        val reset = plan(
            SharedBoardSide.APP_STATE, listOf(a, b), emptyList(), SharedBoardsRecord(SharedBoardSide.COMPAT, listOf(key(a), key(b)))
        )
        assertTrue(reset.eventsRecord.tombstones.isEmpty())
        assertTrue(reset.removeFromAppState.isEmpty())
        assertEquals(listOf(key(a), key(b)), reset.compatUpserts.map(CompatBoard::key), "the store gets the boards again")
    }

    @Test fun tombstonesAreKeptForNinetyDaysAndAThousandAtMost() {
        val old = SharedBoardsRecord(SharedBoardSide.APP_STATE, emptyList(), mapOf(key(b) to now - SHARED_BOARD_TOMBSTONE_MAX_AGE_MILLIS - 1))
        assertTrue(plan(SharedBoardSide.APP_STATE, listOf(a), compat(a), old).eventsRecord.tombstones.isEmpty())
        val many = (0 until MAX_SHARED_BOARD_TOMBSTONES + 5).associate { "compat_board_$it" to now - it }
        val kept = plan(SharedBoardSide.APP_STATE, listOf(a), compat(a), SharedBoardsRecord(SharedBoardSide.APP_STATE, emptyList(), many))
            .eventsRecord.tombstones
        assertEquals(MAX_SHARED_BOARD_TOMBSTONES, kept.size)
        assertTrue("compat_board_0" in kept && "compat_board_${MAX_SHARED_BOARD_TOMBSTONES + 4}" !in kept, "the newest stay")
    }

    @Test fun theTutorialStaysInTheListAloneWhereTheStoreCannotHoldIt() {
        val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
        assertNull(sharedBoardKey(tutorial.url, compatHoldsNonOfficial = false))
        // Android: never written to the store, never deleted from the list.
        val android = plan(SharedBoardSide.APP_STATE, listOf(tutorial, a), compat(a), SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(key(a))))
        assertTrue(android.compatImports.isEmpty() && android.removeFromAppState.isEmpty())
        assertEquals(listOf(key(a)), android.finalRecord.seen)
        // iOS / desktop keep it under img.2chan.net/t/, and its deletion sticks like any board's.
        val tutorialKey = compatBoardKey("https://img.2chan.net/t/")
        assertEquals(tutorialKey, sharedBoardKey(tutorial.url, compatHoldsNonOfficial = true))
        val shared = plan(SharedBoardSide.APP_STATE, listOf(tutorial, a), compat(a), holds = true)
        assertEquals(listOf(tutorial), shared.compatImports)
        val deleted = plan(
            SharedBoardSide.APP_STATE, listOf(a), compat(a) + CompatBoard(tutorialKey, "t", "https://img.2chan.net/t/", tutorial.url, 1),
            SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(tutorialKey, key(a))), holds = true
        )
        assertEquals(listOf(tutorialKey), deleted.compatDeletes)
    }

    @Test fun theRecordReadsBackAndAnUnreadableOneStartsEmpty() {
        val record = SharedBoardsRecord(SharedBoardSide.COMPAT, listOf(key(a)), mapOf(key(b) to 42L))
        assertEquals(record, decodeSharedBoardsRecord(encodeSharedBoardsRecord(record)))
        assertEquals(SharedBoardsRecord(), decodeSharedBoardsRecord("{broken"))
        assertEquals(SharedBoardsRecord(), decodeSharedBoardsRecord(null))
        assertTrue(SHARED_BOARDS_RECORD_KEY.startsWith("shared."))
    }
}
