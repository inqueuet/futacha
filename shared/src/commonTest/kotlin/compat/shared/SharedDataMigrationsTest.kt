package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedDataMigrationsTest {
    private class MemoryMarks : SharedMigrationMarkStore {
        /** The rows as a key / value table keeps them (Android `compat_metadata`). */
        val rows = LinkedHashMap<String, String>()
        var saveFailure: Exception? = null
        override suspend fun loadMarks(): Map<String, SharedMigrationMark> =
            rows.mapNotNull { (key, value) -> SharedDataMigrations.decodeMark(key, value) }.associateBy { it.stageId }
        override suspend fun saveMark(mark: SharedMigrationMark) {
            saveFailure?.let { throw it }
            rows[SharedDataMigrations.markKey(mark.stageId)] = SharedDataMigrations.encodeMark(mark)
        }
    }

    /** Copies a legacy list into a shared one as a union, as the stages will (running twice gives the same result). */
    private class UnionStage(override val id: String, var legacy: List<String>, val shared: MutableSet<String>) : SharedDataMigrationStage {
        var runs = 0
        var failure: Exception? = null
        override suspend fun readLegacy(): String = legacy.joinToString("\n")
        override suspend fun migrate(legacy: String) {
            runs++
            failure?.let { throw it }
            shared += legacy.split("\n").filter { it.isNotEmpty() }
        }
    }

    private var clock = 1_000L
    private fun runner(marks: MemoryMarks) = SharedDataMigrationRunner(marks) { clock }

    @Test fun aRunOfNoStagesMovesNothingAndTheRegisteredOnesFollowThePlan() = runTest {
        val marks = MemoryMarks()
        assertEquals(SharedMigrationRun(emptyList(), emptyList()), runner(marks).run(emptyList()))
        assertTrue(marks.rows.isEmpty(), "no mark is written")
        // The planned stages are listed once each, in the order of §3.1.
        assertEquals(SharedDataMigrations.PLANNED_STAGE_IDS.size, SharedDataMigrations.PLANNED_STAGE_IDS.toSet().size)
        assertEquals("boards-tombstone-v1", SharedDataMigrations.PLANNED_STAGE_IDS.first())
        // D-B1 registers the board-keyed maps (the boards' tombstones need no stage: the record starts empty).
        val store = AppStateStore(FakePlatformStateStorage())
        val registered = try { SharedDataMigrations.stagesFor(store).map { it.id } } finally { store.close() }
        assertEquals(listOf("boardkeyed-maps-v1"), registered)
        assertEquals(registered, SharedDataMigrations.PLANNED_STAGE_IDS.filter { it in registered })
    }

    /** A stage that takes its legacy values in where they are (a map's entry moved to its new key). */
    private class ConsumingStage(override val id: String, var legacy: String) : SharedDataMigrationStage {
        var runs = 0
        override suspend fun readLegacy(): String = legacy
        override suspend fun migrate(legacy: String) {
            runs++
            this.legacy = ""
        }
    }

    @Test fun aStageThatTakesItsValuesInIsMarkedWithWhatIsLeft() = runTest {
        val marks = MemoryMarks()
        val stage = ConsumingStage("boardkeyed-maps-v1", "b>compat_board_1|New|")
        assertEquals(listOf("boardkeyed-maps-v1"), runner(marks).run(listOf(stage)).ran)
        assertEquals(SharedDataMigrations.legacyHash(""), marks.loadMarks().getValue("boardkeyed-maps-v1").legacyHash)
        // Nothing left to move: not run again at the next start; a value written there again later runs it again.
        assertEquals(listOf("boardkeyed-maps-v1"), runner(marks).run(listOf(stage)).skipped)
        stage.legacy = "b>compat_board_1|Old|"
        assertEquals(listOf("boardkeyed-maps-v1"), runner(marks).run(listOf(stage)).ran)
        assertEquals(2, stage.runs)
    }

    @Test fun aStageRunsOnceAndIsMarkedWithTheHashOfWhatItRead() = runTest {
        val marks = MemoryMarks()
        val shared = mutableSetOf<String>()
        val stage = UnionStage("tabs-futaber-v1", listOf("a", "b"), shared)
        assertEquals(listOf("tabs-futaber-v1"), runner(marks).run(listOf(stage)).ran)
        assertEquals(setOf("a", "b"), shared)
        val mark = marks.loadMarks().getValue("tabs-futaber-v1")
        assertEquals(SharedDataMigrations.legacyHash("a\nb"), mark.legacyHash)
        assertEquals(1_000L, mark.completedAtEpochMillis)
        assertEquals("shared.migration.tabs-futaber-v1", marks.rows.keys.single())
        // The same legacy values: skipped, not moved again.
        assertEquals(listOf("tabs-futaber-v1"), runner(marks).run(listOf(stage)).skipped)
        assertEquals(1, stage.runs)
    }

    @Test fun legacyValuesWrittenAfterTheMarkAreTakenInAgain() = runTest {
        val marks = MemoryMarks()
        val shared = mutableSetOf<String>()
        val stage = UnionStage("drafts-v1", listOf("a"), shared)
        runner(marks).run(listOf(stage))
        // An older backup restored, or an older version of the app after a downgrade, wrote the legacy key again.
        stage.legacy = listOf("a", "c")
        clock = 2_000L
        assertEquals(listOf("drafts-v1"), runner(marks).run(listOf(stage)).ran)
        assertEquals(setOf("a", "c"), shared)
        assertEquals(2_000L, marks.loadMarks().getValue("drafts-v1").completedAtEpochMillis)
        assertEquals(2, stage.runs)
    }

    @Test fun aFailedStageIsNotMarkedStopsTheRunAndRunsAgainNextTime() = runTest {
        val marks = MemoryMarks()
        val shared = mutableSetOf<String>()
        val first = UnionStage("boards-tombstone-v1", listOf("x"), shared)
        val failing = UnionStage("tabs-futaber-v1", listOf("a"), shared).apply { failure = IllegalStateException("disk") }
        val after = UnionStage("favorites-futaber-v1", listOf("f"), shared)
        val result = runner(marks).run(listOf(first, failing, after))
        assertEquals(listOf("boards-tombstone-v1"), result.ran)
        assertEquals("tabs-futaber-v1", result.failedStage)
        assertSame(failing.failure, result.failure)
        assertEquals(0, after.runs, "a later stage may need the failed one")
        assertEquals(setOf("boards-tombstone-v1"), marks.loadMarks().keys)
        failing.failure = null
        val retry = runner(marks).run(listOf(first, failing, after))
        assertEquals(listOf("boards-tombstone-v1"), retry.skipped)
        assertEquals(listOf("tabs-futaber-v1", "favorites-futaber-v1"), retry.ran)
        assertNull(retry.failedStage)
    }

    @Test fun aMarkThatCannotBeWrittenLeavesTheStageToRunAgain() = runTest {
        val marks = MemoryMarks().apply { saveFailure = IllegalStateException("full") }
        val shared = mutableSetOf<String>()
        val stage = UnionStage("deletekey-v1", listOf("k"), shared)
        assertEquals("deletekey-v1", runner(marks).run(listOf(stage)).failedStage)
        marks.saveFailure = null
        assertEquals(listOf("deletekey-v1"), runner(marks).run(listOf(stage)).ran)
        assertEquals(setOf("k"), shared, "running it twice gives the same result")
    }

    @Test fun cancellationIsPassedOnWithoutAMark() = runTest {
        val marks = MemoryMarks()
        val stage = UnionStage("ownpost-v1", listOf("p"), mutableSetOf()).apply { failure = CancellationException("left") }
        assertFailsWith<CancellationException> { runner(marks).run(listOf(stage)) }
        assertTrue(marks.rows.isEmpty())
    }

    @Test fun stagesMustHaveDistinctIds() = runTest {
        val shared = mutableSetOf<String>()
        assertFailsWith<IllegalArgumentException> {
            runner(MemoryMarks()).run(listOf(UnionStage("a", emptyList(), shared), UnionStage("a", emptyList(), shared)))
        }
        assertFailsWith<IllegalArgumentException> { runner(MemoryMarks()).run(listOf(UnionStage(" ", emptyList(), shared))) }
    }

    @Test fun marksAndHashesReadBackAndRefuseOtherRows() {
        val mark = SharedMigrationMark("readstate-ftutter-v1", SharedDataMigrations.legacyHash("値"), 42L)
        assertEquals(mark, SharedDataMigrations.decodeMark(SharedDataMigrations.markKey(mark.stageId), SharedDataMigrations.encodeMark(mark)))
        assertNull(SharedDataMigrations.decodeMark("schema.appliedVersion", "13"), "another metadata row")
        assertNull(SharedDataMigrations.decodeMark("shared.migration.x", "2|h|1"), "an unknown format runs the stage again")
        assertNull(SharedDataMigrations.decodeMark("shared.migration.x", "1|h|later"))
        assertNull(SharedDataMigrations.decodeMark("shared.migration.", "1|h|1"))
        // Any length, and every character counts.
        val long = "あ".repeat(20_000)
        assertTrue(SharedDataMigrations.legacyHash(long).startsWith("fnv1a64:60000:"))
        assertNotEquals(SharedDataMigrations.legacyHash("a\nb"), SharedDataMigrations.legacyHash("b\na"))
        assertEquals(SharedDataMigrations.legacyHash(""), SharedDataMigrations.legacyHash(""))
    }
}
