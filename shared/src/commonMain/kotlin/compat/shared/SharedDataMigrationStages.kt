package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.boardSettingKeys
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.migrateFtutterLegacyTabs
import com.valoser.futacha.shared.ui.futaber.FUTABER_TABS_STAGE_ID
import com.valoser.futacha.shared.ui.futaber.FutaberPreferenceKeys
import com.valoser.futacha.shared.ui.futaber.migrateFutaberLegacyTabs
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
 * `tabs-ftutter-v1` (plan B7a): ふたったー風's old tab rows (`compat.ftutter.tabs` / `protected`) go to the shared tabs
 * with this mode's marks ([migrateFtutterLegacyTabs]). The rows stay (the screen keeps showing the threads with no
 * shared place from them), and the record of what was taken in keeps a re-run from adding a tab closed since.
 */
internal class FtutterTabsStage(
    private val stateStore: AppStateStore,
    private val compat: CompatibilityStore
) : SharedDataMigrationStage {
    override val id: String = "tabs-ftutter-v1"

    override suspend fun readLegacy(): String =
        listOf(FtutterKeys.TABS, FtutterKeys.PROTECTED).map { compat.loadPreference(it).orEmpty() }.joinToString("\n")
            .takeIf { it.isNotBlank() }.orEmpty()

    override suspend fun migrate(legacy: String) {
        migrateFtutterLegacyTabs(compat, stateStore.boards.first(), stateStore.history.first(), Clock.System.now().toEpochMilliseconds())
    }
}

/**
 * `tabs-futaber-v1` (plan B8a): ふたばー風's old tab row (`compat.futaber.tabs`) goes to the shared tabs with this mode's
 * marks ([migrateFutaberLegacyTabs]). The row stays (the strip keeps showing the threads with no shared place from it,
 * and the reconcile reads it until B8b), and the record of what was taken in keeps a re-run from adding a tab closed since.
 */
internal class FutaberTabsStage(
    private val stateStore: AppStateStore,
    private val compat: CompatibilityStore
) : SharedDataMigrationStage {
    override val id: String = FUTABER_TABS_STAGE_ID

    override suspend fun readLegacy(): String = compat.loadPreference(FutaberPreferenceKeys.TABS).orEmpty()

    override suspend fun migrate(legacy: String) {
        migrateFutaberLegacyTabs(compat, stateStore.boards.first(), stateStore.history.first(), Clock.System.now().toEpochMilliseconds())
    }
}

/**
 * Runs the registered migration stages once both stores are loaded (each host calls it at start, before or beside the
 * first screen; the screens read the legacy places too until a stage ran). A failure is logged and the stage runs again
 * at the next start.
 */
suspend fun runSharedDataMigrations(stateStore: AppStateStore, compat: CompatibilityStore): SharedMigrationRun? = try {
    compat.setSharedMigrationsSettled(false)
    awaitCompatibilityLoaded(compat)
    SharedDataMigrationRunner(CompatSharedMigrationMarkStore(compat)) { Clock.System.now().toEpochMilliseconds() }
        .run(SharedDataMigrations.stagesFor(stateStore, compat))
        .also { run ->
            run.failure?.let { error -> Logger.e("SharedDataMigrations", "Stage ${run.failedStage} failed", error) }
        }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Logger.e("SharedDataMigrations", "Failed to run the migration stages", error)
    null
} finally {
    compat.setSharedMigrationsSettled(true)
}
