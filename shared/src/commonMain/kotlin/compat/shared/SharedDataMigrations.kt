package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * How the four modes' own records move to the shared places, one stage at a time (D-B0,
 * docs/futaber-evolution-data-2026-10-31.md §3.1). The stages are registered by the batch that moves their data
 * ([SharedDataMigrations.stagesFor]; D-B1: `boardkeyed-maps-v1`), and each host runs them once both stores are loaded
 * ([runSharedDataMigrations]).
 *
 * - A stage reads the legacy values it moves (one text), moves them, and is then marked done with the hash of the legacy
 *   values as they are after the move (the same text, unless the stage took them in where they were, as a map's entry
 *   moved to its new key). The legacy storage keys are never deleted (read both, write one).
 * - A stage runs again when its legacy values changed after it was marked (an older backup restored, an older version of
 *   the app written to them after a downgrade), and takes them in again. A stage is therefore written to be idempotent:
 *   running it twice gives the same result (a union, or the newer of two by time).
 * - The stages run in order. A failed stage is not marked and the run stops there (the later stages may need it); the
 *   next start tries again.
 * - The marks are kept away from the settings rows (`compat_preference`): not in the settings backup, and not counted
 *   against Android's 4,096-row read of those rows. [SharedMigrationMarkStore] is where they go:
 *   [CompatSharedMigrationMarkStore] keeps them in the compatibility store's shared rows (Android `compat_metadata`, the
 *   iOS / desktop state, [com.valoser.futacha.shared.compat.CompatibilityStore.loadSharedData]).
 */

/** That stage [stageId] finished, with the hash of the legacy values it read then. */
data class SharedMigrationMark(
    val stageId: String,
    val legacyHash: String,
    val completedAtEpochMillis: Long
)

/** Where the marks are kept. */
interface SharedMigrationMarkStore {
    suspend fun loadMarks(): Map<String, SharedMigrationMark>
    suspend fun saveMark(mark: SharedMigrationMark)
}

/** One stage of the move. */
interface SharedDataMigrationStage {
    /** Unique, stable: the mark is kept under it. */
    val id: String

    /** The legacy values this stage moves, as one text; the stage runs again whenever it changes. */
    suspend fun readLegacy(): String

    /** Moves [legacy] to the shared place. Never deletes the legacy values; running it twice gives the same result. */
    suspend fun migrate(legacy: String)
}

/** What one run did. */
data class SharedMigrationRun(
    /** The stages that moved their values and were marked, in order. */
    val ran: List<String>,
    /** The stages already marked with the same legacy values. */
    val skipped: List<String>,
    /** The stage that failed (not marked); the stages after it did not run. */
    val failedStage: String? = null,
    val failure: Throwable? = null
)

object SharedDataMigrations {
    /** Key prefix of a mark kept as a key / value row (Android `compat_metadata`). */
    const val MARK_KEY_PREFIX: String = "shared.migration."
    private const val MARK_FORMAT = "1"

    /**
     * The stages planned in §3.1, in the order they will run. Not registered yet: each comes with the batch that moves its
     * data (B1 boards … B8 own posts).
     */
    val PLANNED_STAGE_IDS: List<String> = listOf(
        "boards-tombstone-v1",
        "boardkeyed-maps-v1",
        "history-count-unit-v1",
        "readstate-futaber-v1",
        "readstate-ftutter-v1",
        "tabs-futaber-v1",
        "favorites-futaber-v1",
        "tabs-ftutter-v1",
        "drafts-v1",
        "deletekey-v1",
        "ownpost-v1",
        AIMOGE_BOARDS_STAGE_ID
    )

    /** The registered stages, in the order of [PLANNED_STAGE_IDS]. */
    fun stagesFor(stateStore: AppStateStore, compat: CompatibilityStore? = null): List<SharedDataMigrationStage> =
        listOfNotNull(
            BoardKeyedMapsStage(stateStore), compat?.let { FtutterTabsStage(stateStore, it) }, compat?.let { FutaberTabsStage(stateStore, it) },
            // AM-B5: only while あいもげ is on; off, the earlier-form あいもげ boards are kept as they are.
            AimogeBoardsStage(stateStore).takeIf { com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled }
        )

    fun markKey(stageId: String): String = MARK_KEY_PREFIX + stageId

    /** A mark as the value of a key / value row: `1|hash|time`. */
    fun encodeMark(mark: SharedMigrationMark): String =
        "$MARK_FORMAT|${mark.legacyHash}|${mark.completedAtEpochMillis}"

    /** The mark of a key / value row, or null for another key or a value it cannot read (the stage then runs again). */
    fun decodeMark(key: String, value: String): SharedMigrationMark? {
        if (!key.startsWith(MARK_KEY_PREFIX)) return null
        val stageId = key.removePrefix(MARK_KEY_PREFIX).takeIf { it.isNotBlank() } ?: return null
        val parts = value.split('|')
        if (parts.size != 3 || parts[0] != MARK_FORMAT || parts[1].isBlank()) return null
        val time = parts[2].toLongOrNull() ?: return null
        return SharedMigrationMark(stageId, parts[1], time)
    }

    /**
     * The hash recorded with a mark: FNV-1a 64 of the UTF-8 bytes, with the length. Any length (a setting holds up to
     * 20,000 characters, more than [com.valoser.futacha.shared.compat.compatTabKey]'s hash takes).
     */
    fun legacyHash(value: String): String {
        val bytes = value.encodeToByteArray()
        var hash = 0xcbf29ce484222325UL
        bytes.forEach { byte -> hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL }
        return "fnv1a64:${bytes.size}:${hash.toString(16).padStart(16, '0')}"
    }
}

/**
 * Runs the stages in order, one run at a time. [now] stamps the marks. Cancellation is passed on (nothing is marked for
 * the stage it stopped).
 */
class SharedDataMigrationRunner(
    private val marks: SharedMigrationMarkStore,
    private val now: () -> Long
) {
    private val lock = Mutex()

    suspend fun run(stages: List<SharedDataMigrationStage>): SharedMigrationRun = lock.withLock {
        val ids = stages.map { it.id }
        require(ids.none { it.isBlank() }) { "A migration stage has no id" }
        require(ids.size == ids.toSet().size) { "Two migration stages share an id" }
        if (stages.isEmpty()) return@withLock SharedMigrationRun(emptyList(), emptyList())
        val recorded = marks.loadMarks()
        val ran = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (stage in stages) {
            try {
                val legacy = stage.readLegacy()
                val hash = SharedDataMigrations.legacyHash(legacy)
                if (recorded[stage.id]?.legacyHash == hash) {
                    skipped += stage.id
                    continue
                }
                stage.migrate(legacy)
                // What is left of the legacy values after the move: a stage that took them in where they were reads
                // less now, and only a later change of that runs it again.
                val after = stage.readLegacy()
                marks.saveMark(SharedMigrationMark(stage.id, if (after == legacy) hash else SharedDataMigrations.legacyHash(after), now()))
                ran += stage.id
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return@withLock SharedMigrationRun(ran, skipped, stage.id, failure)
            }
        }
        SharedMigrationRun(ran, skipped)
    }
}
