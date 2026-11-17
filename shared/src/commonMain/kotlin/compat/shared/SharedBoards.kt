package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.COMPAT_SHARED_DATA_KEY_PREFIX
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.removeDuplicateBoardSummaries
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.compat.withOfficialHttps
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/*
 * The boards of the four modes as one list (D-B1, docs/futaber-evolution-data-2026-10-31.md §2.1, §4.1 D1).
 *
 * Two stores list the boards: the AppState (ふたちゃ・ふたばー風・ふたったー風 edit it) and the compatibility store
 * (としあき(仮) edits it; it is also the parent of the tabs, drafts, board NG rules and catalog settings). The
 * synchronization used to copy the list of the mode in use over the other one, deleting from the other store every
 * board the mode's list did not have. A board that only reached the other store (a settings backup or the old toshiaki
 * reference app's files restored into the compatibility store, a `.ftt` imported into the AppState) was deleted that way, and the
 * compatibility store's cascade took its tabs, history, board NG rules and new-thread draft with it (D1).
 *
 * Now the two lists are joined (a union), and only a deletion takes a board away:
 * - A deletion is a board leaving the list of the store the mode in use edits (its "side"), measured against the boards
 *   the last pass saw ([SharedBoardsRecord.seen]): ふたちゃ's "remove from the list", としあき(仮)'s board deletion, an AI
 *   command, any screen. It leaves a tombstone, and the board goes from both stores (with the compatibility store's
 *   cascade, as before). No screen calls anything new, so no way of deleting a board can be missed.
 * - A board appearing in that list is an addition: it lifts a tombstone (a board deleted and added again).
 * - A board only in the other store is not an event: it is added to the mode's list unless a tombstone says it was
 *   deleted (a stale copy written back by a slow screen). A board missing from the other store is written to it again.
 * - The record keeps the side that made it, so a deletion made just before a mode switch (or before the app was closed)
 *   is still taken from that side by the first pass of the next mode.
 * The tombstones and the record live in the compatibility store's shared rows ([CompatibilityStore.loadSharedData]),
 * away from the settings rows and the settings backup. One lock ([SharedBoardsSync]) orders the passes, the mode
 * switches' passes and the restores.
 */

/** The store the boards are edited in while a mode is in use: the mode's own board list. */
enum class SharedBoardSide {
    /** ふたちゃ・ふたばー風・ふたったー風: the AppState board list. */
    APP_STATE,

    /** としあき(仮): the compatibility store. */
    COMPAT;

    companion object {
        fun of(usesAppStateData: Boolean): SharedBoardSide = if (usesAppStateData) APP_STATE else COMPAT
    }
}

/** What the board synchronization keeps between passes: the compatibility store's board keys. */
@Serializable
internal data class SharedBoardsRecord(
    /** The side whose list [seen] was measured on; null before the first pass. */
    val side: SharedBoardSide? = null,
    /** The boards the last pass saw on that side (and, once it finished, on both). */
    val seen: List<String> = emptyList(),
    /** Deleted boards, by the time of the deletion. */
    val tombstones: Map<String, Long> = emptyMap()
)

internal const val SHARED_BOARDS_RECORD_KEY = "${COMPAT_SHARED_DATA_KEY_PREFIX}boards"
internal const val MAX_SHARED_BOARD_TOMBSTONES = 1_000
internal const val SHARED_BOARD_TOMBSTONE_MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000

private val sharedBoardsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

internal fun decodeSharedBoardsRecord(raw: String?): SharedBoardsRecord =
    raw?.let { runCatching { sharedBoardsJson.decodeFromString(SharedBoardsRecord.serializer(), it) }.getOrNull() }
        ?: SharedBoardsRecord()

internal fun encodeSharedBoardsRecord(record: SharedBoardsRecord): String =
    sharedBoardsJson.encodeToString(SharedBoardsRecord.serializer(), record)

private val anyHostRegex = Regex("^https?://[^/]+", RegexOption.IGNORE_CASE)

/**
 * The compatibility store's key of the board at [url], or null when the store cannot hold it. An official board's key
 * comes from its canonical address. The iOS and desktop stores ([compatHoldsNonOfficial]) also keep an `example.com`
 * board (the bundled tutorial) under the img.2chan.net path, as their `importModernBoards` does; Android keeps official
 * boards only, so there the tutorial stays in the AppState alone.
 */
fun sharedBoardKey(url: String, compatHoldsNonOfficial: Boolean): String? = runCatching {
    com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(url)?.let { return@runCatching compatBoardKey(it) }
    if (!compatHoldsNonOfficial || !url.contains("example.com", ignoreCase = true)) return@runCatching null
    canonicalizeBoardUrl(url.trim().replace(anyHostRegex, "https://img.2chan.net"))?.let(::compatBoardKey)
}.getOrNull()

/** [board] as the compatibility store keeps it after `importModernBoards` (see [sharedBoardKey]). */
private fun sharedCompatBoardOf(board: BoardSummary, compatHoldsNonOfficial: Boolean, sortOrder: Int): CompatBoard? {
    val canonical = com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(board.url)
        ?: board.url.takeIf { compatHoldsNonOfficial && it.contains("example.com", ignoreCase = true) }
            ?.let { canonicalizeBoardUrl(it.trim().replace(anyHostRegex, "https://img.2chan.net")) }
        ?: return null
    return CompatBoard(
        key = runCatching { compatBoardKey(canonical) }.getOrNull() ?: return null,
        name = board.name.ifBlank { canonical.substringAfter("//").substringBefore('/') },
        canonicalUrl = canonical,
        originalUrl = board.url,
        sortOrder = sortOrder
    )
}

/** What one pass writes; computed by [planSharedBoards] from what it read. */
internal data class SharedBoardsPlan(
    val side: SharedBoardSide,
    /** AppState boards to take away (deleted), by key. */
    val removeFromAppState: Set<String>,
    /** Boards only the compatibility store has, appended to the AppState list (the AppState side). */
    val addToAppState: List<BoardSummary>,
    /** Compatibility store boards to delete (deleted), by key. */
    val compatDeletes: List<String>,
    /** AppState boards the compatibility store lacks, given to its `importModernBoards`. */
    val compatImports: List<BoardSummary>,
    /** The AppState side: the official boards in the AppState order, written as before (also repairs a reset store). */
    val compatUpserts: List<CompatBoard>,
    /** The compatibility side: the compatibility list after the writes, which orders the AppState list. */
    val compatAfter: List<CompatBoard>,
    /** The record once the deletions and additions are taken (written before anything else). */
    val eventsRecord: SharedBoardsRecord,
    /** The record once both stores are written. */
    val finalRecord: SharedBoardsRecord
)

/**
 * Plans one pass for [side]: takes its deletions and additions against [record], then joins the two lists without the
 * deleted boards. Pure; [reconcileSharedBoards] runs it.
 */
internal fun planSharedBoards(
    side: SharedBoardSide,
    appState: List<BoardSummary>,
    compat: List<CompatBoard>,
    record: SharedBoardsRecord,
    compatHoldsNonOfficial: Boolean,
    nowEpochMillis: Long
): SharedBoardsPlan {
    // While あいもげ is off, an あいもげ board in the compatibility store (restored, or kept from a build with it on) is
    // hidden: left out of the pass entirely, so it is never taken as deleted.
    val hiddenKeys: Set<String> = if (com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled) emptySet() else
        compat.filter { com.valoser.futacha.shared.compat.aimogeBoardOnlyShapeOf(it.canonicalUrl) != null }
            .mapTo(LinkedHashSet(), CompatBoard::key)
    if (hiddenKeys.isNotEmpty()) {
        val plan = planSharedBoards(
            side, appState, compat.filter { it.key !in hiddenKeys },
            record.copy(seen = record.seen.filter { it !in hiddenKeys }, tombstones = record.tombstones - hiddenKeys),
            compatHoldsNonOfficial, nowEpochMillis
        )
        // Left out of `seen` (so turning あいもげ on again finds the board "only in the store" and adds it to the list, never
        // "gone from the list" and deleted); a tombstone it had is kept.
        val keptTombstones = record.tombstones.filterKeys { it in hiddenKeys }
        fun SharedBoardsRecord.withHidden() = copy(tombstones = tombstones + keptTombstones)
        return plan.copy(
            eventsRecord = plan.eventsRecord.withHidden(),
            finalRecord = plan.finalRecord.withHidden()
        )
    }
    fun keyOf(board: BoardSummary) = sharedBoardKey(board.url, compatHoldsNonOfficial)
    val appStateKeys = appState.mapNotNullTo(LinkedHashSet(), ::keyOf)
    val compatKeys = compat.mapTo(LinkedHashSet(), CompatBoard::key)
    val seen = record.seen.toSet()
    val tombstones = record.tombstones.toMutableMap()
    fun takeEvents(keys: Set<String>) {
        (seen - keys).forEach { key -> if (key !in tombstones) tombstones[key] = nowEpochMillis }
        (keys - seen).forEach(tombstones::remove)
    }
    // The other side made the record (a switch, or the app closed, before a pass of this mode): its changes since then
    // are its user's. An empty list there is not taken as "all deleted" (a store not loaded, or reset).
    if (record.side != null && record.side != side) {
        val earlier = if (record.side == SharedBoardSide.APP_STATE) appStateKeys else compatKeys
        if (earlier.isNotEmpty()) takeEvents(earlier)
    }
    takeEvents(if (side == SharedBoardSide.APP_STATE) appStateKeys else compatKeys)
    val trimmed = tombstones.entries
        .filter { (_, at) -> nowEpochMillis - at <= SHARED_BOARD_TOMBSTONE_MAX_AGE_MILLIS }
        .sortedByDescending { it.value }
        .take(MAX_SHARED_BOARD_TOMBSTONES)
        .associate { it.key to it.value }
    val deleted = trimmed.keys
    val removeFromAppState = appStateKeys.filterTo(LinkedHashSet()) { it in deleted }
    val compatDeletes = compatKeys.filter { it in deleted }
    return when (side) {
        SharedBoardSide.APP_STATE -> {
            val keptKeys = appStateKeys - deleted
            val addToAppState = compat
                .filter { board -> board.key !in deleted && board.key !in keptKeys }
                .distinctBy(CompatBoard::key)
                .map(CompatBoard::toBoardSummary)
            val finalAppState = removeDuplicateBoardSummaries(
                appState.filterNot { board -> keyOf(board) in removeFromAppState } + addToAppState
            )
            val compatImports = if (compatHoldsNonOfficial) {
                finalAppState.filter { board ->
                    com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(board.url) == null && keyOf(board).let { it != null && it !in compatKeys }
                }
            } else emptyList()
            SharedBoardsPlan(
                side = side,
                removeFromAppState = removeFromAppState,
                addToAppState = addToAppState,
                compatDeletes = compatDeletes,
                compatImports = compatImports,
                compatUpserts = modernBoardsToCompatibility(finalAppState),
                compatAfter = emptyList(),
                eventsRecord = SharedBoardsRecord(side, keptKeys.toList(), trimmed),
                finalRecord = SharedBoardsRecord(side, finalAppState.mapNotNull(::keyOf).distinct(), trimmed)
            )
        }
        SharedBoardSide.COMPAT -> {
            val kept = compat.filter { board -> board.key !in deleted }
            val keptKeys = kept.mapTo(LinkedHashSet(), CompatBoard::key)
            val appStateOnly = appState
                .filter { board -> keyOf(board).let { it != null && it !in deleted && it !in keptKeys } }
                .distinctBy(::keyOf)
            var nextOrder = (kept.maxOfOrNull(CompatBoard::sortOrder) ?: -1) + 1
            val compatAfter = kept + appStateOnly.mapNotNull { board ->
                sharedCompatBoardOf(board, compatHoldsNonOfficial, nextOrder)?.also { nextOrder++ }
            }
            SharedBoardsPlan(
                side = side,
                removeFromAppState = removeFromAppState,
                addToAppState = emptyList(),
                compatDeletes = compatDeletes,
                compatImports = appStateOnly,
                compatUpserts = emptyList(),
                compatAfter = compatAfter,
                eventsRecord = SharedBoardsRecord(side, keptKeys.toList(), trimmed),
                finalRecord = SharedBoardsRecord(side, compatAfter.map(CompatBoard::key).distinct(), trimmed)
            )
        }
    }
}

/** Orders the board passes, the mode switches' passes and the restores (one shared dataset per process). */
internal object SharedBoardsSync {
    val lock = Mutex()
}

/**
 * One pass of the board synchronization for [side] (the mode in use, or the mode being left at a switch).
 * [appStateBoards] / [compatBoards] are the lists a collector was given; a list not given is read. Fails (to the
 * caller) when a write fails; the next pass takes it up again from the record.
 */
suspend fun reconcileSharedBoards(
    stateStore: AppStateStore,
    compat: CompatibilityStore,
    side: SharedBoardSide,
    appStateBoards: List<BoardSummary>? = null,
    compatBoards: List<CompatBoard>? = null,
    nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): Unit {
    // Waited for before the lock: a load that never ends then holds up only this pass, not a restore or the other
    // passes waiting for the lock (audit D3). A store once loaded stays loaded.
    awaitCompatibilityLoaded(compat)
    SharedBoardsSync.lock.withLock { reconcileSharedBoardsLocked(stateStore, compat, side, appStateBoards, compatBoards, nowEpochMillis) }
}

private suspend fun reconcileSharedBoardsLocked(
    stateStore: AppStateStore,
    compat: CompatibilityStore,
    side: SharedBoardSide,
    appStateBoards: List<BoardSummary>?,
    compatBoards: List<CompatBoard>?,
    nowEpochMillis: () -> Long
) {
    val appState = appStateBoards ?: stateStore.boards.first()
    // Existing boards need their legacy ids recorded too, even when nothing has to be imported.
    compat.rememberModernBoardAliases(appState)
    val compatList = compatBoards ?: compat.boards.first()
    val stored = compat.loadSharedData()[SHARED_BOARDS_RECORD_KEY]
    val record = decodeSharedBoardsRecord(stored)
    val holds = compat.holdsBundledTutorialBoard
    val plan = planSharedBoards(side, appState, compatList, record, holds, nowEpochMillis())
    if (plan.eventsRecord != record) compat.saveSharedData(mapOf(SHARED_BOARDS_RECORD_KEY to encodeSharedBoardsRecord(plan.eventsRecord)))
    fun keyOf(board: BoardSummary) = sharedBoardKey(board.url, holds)
    when (side) {
        SharedBoardSide.APP_STATE -> {
            if (plan.removeFromAppState.isNotEmpty() || plan.addToAppState.isNotEmpty()) {
                stateStore.updateBoards { current ->
                    // The additions were measured on the list this pass read: a board the person deleted since then is
                    // not added back here (the next pass takes the deletion).
                    removeDuplicateBoardSummaries(
                        current.filterNot { board -> keyOf(board) in plan.removeFromAppState } +
                            plan.addToAppState.filter { added -> current.none { keyOf(it) == keyOf(added) } }
                    )
                }
            }
            plan.compatDeletes.forEach { key -> compat.deleteBoard(key) }
            if (plan.compatImports.isNotEmpty()) compat.importModernBoards(plan.compatImports)
            // Written only when a board or the order changes (audit D5: every pass wrote the whole list).
            if (plan.compatDeletes.isNotEmpty() || plan.compatImports.isNotEmpty() ||
                sharedBoardsUpsertsNeeded(compatList, plan.compatUpserts)) compat.upsertBoards(plan.compatUpserts)
        }
        SharedBoardSide.COMPAT -> {
            plan.compatDeletes.forEach { key -> compat.deleteBoard(key) }
            if (plan.compatImports.isNotEmpty()) compat.importModernBoards(plan.compatImports)
            stateStore.updateBoards { current ->
                synchronizeModernBoardsFromCompatibility(
                    current.filterNot { board -> keyOf(board) in plan.removeFromAppState },
                    plan.compatAfter,
                    holds
                )
            }
        }
    }
    if (plan.finalRecord != plan.eventsRecord) {
        compat.saveSharedData(mapOf(SHARED_BOARDS_RECORD_KEY to encodeSharedBoardsRecord(plan.finalRecord)))
    }
}

/**
 * A store that loads asynchronously (Android) shows an empty placeholder first; a pass must not take that for the
 * person's boards. Waits for the load, or fails when it failed (the caller tells it; the next pass tries again).
 */
internal suspend fun awaitCompatibilityLoaded(compat: CompatibilityStore) {
    if (compat.isLoaded.value) return
    val loaded = combine(compat.isLoaded, compat.loadFailed) { loaded, failed -> loaded to failed }
        .first { (loaded, failed) -> loaded || failed }.first
    check(loaded) { "The compatibility store could not be loaded" }
}

/**
 * Keeps the boards of the two stores one list while a mode's screens are shown: each change of [side]'s list (the
 * mode's own) runs [reconcileSharedBoards]. A failed pass is told through [onFailure]; the next change runs again.
 * An empty list is not taken for the person's boards until that list was not empty once (an unloaded placeholder).
 */
suspend fun synchronizeSharedBoards(
    stateStore: AppStateStore,
    compat: CompatibilityStore,
    side: SharedBoardSide,
    appStateBoards: Flow<List<BoardSummary>> = stateStore.observedBoards,
    onFailure: (Throwable) -> Unit
) {
    suspend fun pass(block: suspend () -> Unit) {
        runSuspendCatchingPreservingCancellation { block() }.onFailure(onFailure)
    }
    var hasObservedBoards = false
    when (side) {
        SharedBoardSide.APP_STATE -> appStateBoards.collect { boards ->
            if (boards.isNotEmpty()) hasObservedBoards = true
            if (!hasObservedBoards) return@collect
            pass { reconcileSharedBoards(stateStore, compat, side, appStateBoards = boards) }
        }
        SharedBoardSide.COMPAT -> compat.boards.collect { boards ->
            if (boards.isNotEmpty()) hasObservedBoards = true
            if (!hasObservedBoards) return@collect
            pass { reconcileSharedBoards(stateStore, compat, side, compatBoards = boards) }
        }
    }
}

/**
 * Runs [block] (a settings backup or the old toshiaki reference app's files restored into the compatibility store) with the
 * board synchronization held. When [keep] says it restored something, every board the compatibility store then holds
 * is taken as restored: a tombstone of it is lifted (a board deleted earlier and restored now stays, with what was
 * restored under it), and in the AppState modes [appStateStore] gets the boards at once
 * ([mergeRestoredBoardsIntoAppState], M2). No pass can run between the restore and that, so the restored board is
 * never deleted again in between. [historyStore] (every mode's AppState store, the AppState modes' by default) gets the
 * restored history at once ([mergeRestoredHistoryIntoAppState], plan B3).
 */
suspend fun <T> withSharedBoardsRestore(
    compat: CompatibilityStore,
    appStateStore: AppStateStore?,
    keep: (T) -> Boolean,
    historyStore: AppStateStore? = appStateStore,
    block: suspend () -> T
): T = SharedBoardsSync.lock.withLock {
    val result = block()
    if (keep(result)) {
        runSuspendCatchingPreservingCancellation {
            val restored = compat.boards.first().mapTo(HashSet(), CompatBoard::key)
            val record = decodeSharedBoardsRecord(compat.loadSharedData()[SHARED_BOARDS_RECORD_KEY])
            if (record.tombstones.keys.any { it in restored }) {
                compat.saveSharedData(
                    mapOf(SHARED_BOARDS_RECORD_KEY to encodeSharedBoardsRecord(record.copy(tombstones = record.tombstones - restored)))
                )
            }
        }.onFailure { error -> Logger.e("SharedBoards", "Failed to lift the tombstones of the restored boards", error) }
        com.valoser.futacha.shared.ui.compat.mergeRestoredBoardsIntoAppState(compat, appStateStore)
        // The restored history in every mode at once, a row deleted earlier included (plan B3). In としあき(仮) the boards
        // are not merged here ([appStateStore] is null) but the history still is ([historyStore]).
        if (historyStore != null) {
            runSuspendCatchingPreservingCancellation { mergeRestoredHistoryIntoAppState(compat, historyStore) }
                .onFailure { error -> Logger.e("SharedBoards", "Failed to merge the restored history", error) }
        }
        // AM-B5: an earlier-form あいもげ board brought back by the restore is moved now, not at the next start.
        val stageStore = appStateStore ?: historyStore
        if (stageStore != null && com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled) {
            runSuspendCatchingPreservingCancellation {
                SharedDataMigrationRunner(CompatSharedMigrationMarkStore(compat)) {
                    kotlin.time.Clock.System.now().toEpochMilliseconds()
                }.run(listOf(AimogeBoardsStage(stageStore)))
            }.onFailure { error -> Logger.e("SharedBoards", "Failed to move the restored あいもげ boards", error) }
        }
    }
    result
}

/**
 * Whether writing [upserts] changes [stored]: a board missing or different (name, address), or the upserted boards in
 * another order. Equal ones are not written again (audit D5).
 */
internal fun sharedBoardsUpsertsNeeded(stored: List<CompatBoard>, upserts: List<CompatBoard>): Boolean {
    if (upserts.isEmpty()) return false
    val byKey = stored.associateBy(CompatBoard::key)
    val changed = upserts.any { up ->
        val existing = byKey[up.key] ?: return@any true
        val wanted = up.withOfficialHttps()
        existing.name != wanted.name || existing.canonicalUrl != wanted.canonicalUrl || existing.originalUrl != wanted.originalUrl
    }
    if (changed) return true
    val keys = upserts.mapTo(HashSet(), CompatBoard::key)
    val storedOrder = stored.sortedBy(CompatBoard::sortOrder).map(CompatBoard::key).filter { it in keys }
    return storedOrder != upserts.map(CompatBoard::key)
}
