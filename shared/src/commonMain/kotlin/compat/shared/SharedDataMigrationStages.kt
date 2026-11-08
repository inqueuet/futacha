package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.boardSettingKeys
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** The marks in the compatibility store's shared rows ([CompatibilityStore.loadSharedData]). */
class CompatSharedMigrationMarkStore(private val compat: CompatibilityStore) : SharedMigrationMarkStore {
    override suspend fun loadMarks(): Map<String, SharedMigrationMark> =
        compat.loadSharedData()
            .mapNotNull { (key, value) -> SharedDataMigrations.decodeMark(key, value) }
            .associateBy(SharedMigrationMark::stageId)

    override suspend fun saveMark(mark: SharedMigrationMark) {
        compat.saveSharedData(mapOf(SharedDataMigrations.markKey(mark.stageId) to SharedDataMigrations.encodeMark(mark)))
    }
}

/**
 * `boardkeyed-maps-v1` (D-B1, docs/futaber-evolution-data-2026-10-31.md §3.2): AppState's catalog modes and board watch
 * words were kept under the board's id in the AppState list, a slug that depends on the order the boards were added
 * and differs between devices and after a restore. They move to the board's shared key
 * ([com.valoser.futacha.shared.state.BoardSettingKeys]), the compatibility store's key of the official board. A key no
 * listed board has (a board removed earlier), and a board that is not an official one, stay as they are. The screens
 * read both keys meanwhile, so a value is never lost before or after the move.
 */
internal class BoardKeyedMapsStage(private val stateStore: AppStateStore) : SharedDataMigrationStage {
    override val id: String = "boardkeyed-maps-v1"

    /** Earlier key to shared key, for each listed official board with a value under its earlier key. */
    private suspend fun moves(): Map<String, String> {
        val catalogModes = stateStore.catalogModes.first()
        val boardWatchWords = stateStore.boardWatchWords.first()
        return buildMap {
            stateStore.boards.first().forEach { board ->
                val keys = boardSettingKeys(board) ?: return@forEach
                val legacy = keys.legacy ?: return@forEach
                if (legacy in catalogModes || legacy in boardWatchWords) {
                    if (legacy !in this) put(legacy, keys.key)
                }
            }
        }
    }

    override suspend fun readLegacy(): String {
        val moves = moves()
        if (moves.isEmpty()) return ""
        val catalogModes = stateStore.catalogModes.first()
        val boardWatchWords = stateStore.boardWatchWords.first()
        return moves.entries.sortedBy { it.key }.joinToString("\n") { (from, to) ->
            "$from>$to|${catalogModes[from]?.name.orEmpty()}|${boardWatchWords[from]?.joinToString("\u0001").orEmpty()}"
        }
    }

    override suspend fun migrate(legacy: String) {
        stateStore.moveBoardKeyedSettings(moves())
    }
}

/**
 * Runs the registered migration stages once both stores are loaded (each host calls it at start, before or beside the
 * first screen; the screens read the legacy places too until a stage ran). A failure is logged and the stage runs again
 * at the next start.
 */
suspend fun runSharedDataMigrations(stateStore: AppStateStore, compat: CompatibilityStore): SharedMigrationRun? = try {
    awaitCompatibilityLoaded(compat)
    SharedDataMigrationRunner(CompatSharedMigrationMarkStore(compat)) { Clock.System.now().toEpochMilliseconds() }
        .run(SharedDataMigrations.stagesFor(stateStore))
        .also { run ->
            run.failure?.let { error -> Logger.e("SharedDataMigrations", "Stage ${run.failedStage} failed", error) }
        }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Logger.e("SharedDataMigrations", "Failed to run the migration stages", error)
    null
}
