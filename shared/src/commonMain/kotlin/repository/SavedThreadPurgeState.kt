package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark

/**
 * Purge cutoffs of one repository root, shared by every [SavedThreadRepository]
 * instance of that root on the same [FileSystem]: a save running through another
 * instance (the background refresh, the history size-limit cleanup, the screen)
 * must see a deletion made through this one instead of indexing the thread again.
 * Guarded by [mutex].
 */
internal class SavedThreadPurgeState {
    val mutex = Mutex()
    var rootCutoffMillis = Long.MIN_VALUE
    val threadCutoffMillis = LinkedHashMap<String, Long>()

    /**
     * Monotonic marks taken when the cutoffs above were recorded (null for one loaded from the
     * persisted marker). They let a cutoff be re-expressed on the current wall clock after the
     * clock was set back; see [effectivePurgeCutoffMillis].
     */
    var rootCutoffMark: TimeMark? = null
    val threadCutoffMarks = HashMap<String, TimeMark>()

    /**
     * Serializes every read-modify-write of the purge marker file (a new purge, the
     * resume of an interrupted one, the migration from the old location). Taken
     * before [mutex], never while waiting for a repository's delete/mutation locks.
     */
    val markerMutex = Mutex()

    /** Advanced by each whole-root purge; a resume of an older purge never writes over a newer one. */
    var markerGeneration = 0L

    /** Whether this process already loaded the marker of an interrupted whole-root purge. */
    var interruptedPurgeResumeChecked = false

    /** Root children an interrupted purge has not deleted yet, with that purge's cutoff. */
    val pendingLeftovers: MutableSet<String> = HashSet()
    var pendingLeftoverCutoffMillis = Long.MIN_VALUE

    /** Background deletion of [pendingLeftovers], if one was started. */
    var resumeJob: Job? = null
}

internal object SavedThreadPurgeRegistry {
    private val mutex = Mutex()

    // Keyed by file system identity, so separate (test) file systems never share cutoffs.
    private val states = mutableMapOf<FileSystem, MutableMap<String, SavedThreadPurgeState>>()

    suspend fun stateFor(fileSystem: FileSystem, rootKey: String): SavedThreadPurgeState = mutex.withLock {
        states.getOrPut(fileSystem) { mutableMapOf() }.getOrPut(rootKey) { SavedThreadPurgeState() }
    }
}

/**
 * Where the purge marker of the root [baseDirectory] was kept before: a hidden sibling of
 * the root. For a relative root that put it in the public app folder (iOS Documents,
 * which Files shows and backups include; Android's external app folder, which can
 * change with storage availability), with the deleted threads' storage ids in it.
 */
internal fun legacySavedThreadPurgeMarkerPath(baseDirectory: String): String {
    val root = baseDirectory.trim().trimEnd('/')
    val split = root.lastIndexOf('/') + 1
    return "${root.substring(0, split)}.${root.substring(split)}.purge-cutoff"
}

/**
 * The purge marker of [baseDirectory]: the hidden sibling of the root inside the app's
 * private files area (`private/`), which is never shared, backed up through Files, nor
 * moved between storage volumes. A root that is already private or absolute keeps its
 * sibling. Outside the root, so the whole-root delete cannot remove it before it is done.
 */
internal fun savedThreadPurgeMarkerPath(baseDirectory: String): String {
    val root = baseDirectory.trim().trimEnd('/')
    val legacy = legacySavedThreadPurgeMarkerPath(root)
    return if (root.startsWith("/") || root.startsWith("private/")) legacy else "private/$legacy"
}

/** Contents of the purge marker: the cutoff, then the root children present when the purge began. */
internal data class SavedThreadPurgeMarker(val cutoffMillis: Long, val leftovers: List<String>)

internal fun encodeSavedThreadPurgeMarker(marker: SavedThreadPurgeMarker): String =
    buildString {
        append(marker.cutoffMillis)
        marker.leftovers.forEach { append('\n').append(it) }
    }

internal fun decodeSavedThreadPurgeMarker(encoded: String): SavedThreadPurgeMarker? {
    val lines = encoded.split('\n')
    val cutoff = lines.firstOrNull()?.trim()?.toLongOrNull() ?: return null
    return SavedThreadPurgeMarker(
        cutoffMillis = cutoff,
        leftovers = lines.drop(1).map(String::trim).filter(::isRecoverableSavedThreadDirectory).distinct()
    )
}

/** Wall-clock disagreement smaller than this is jitter, not a clock that was set back. */
private const val PURGE_CUTOFF_CLOCK_TOLERANCE_MILLIS = 2_000L

/**
 * The purge cutoff on the clock as it reads now. A save counts as started before a purge when
 * its start time is at or before the cutoff, and both are wall-clock times. If the clock was
 * set back after the purge, every later save would be stamped before the cutoff and silently
 * discarded until the clock caught up. The monotonic [mark] shows how long ago the purge
 * really was, which places it correctly on the new clock. A forward step needs no correction:
 * saves that began before the purge carry the older, smaller stamps.
 */
internal fun effectivePurgeCutoffMillis(cutoffMillis: Long, mark: TimeMark?, nowMillis: Long): Long {
    if (mark == null || cutoffMillis == Long.MIN_VALUE) return cutoffMillis
    val purgedAtOnCurrentClock = nowMillis - mark.elapsedNow().inWholeMilliseconds
    return if (purgedAtOnCurrentClock < cutoffMillis - PURGE_CUTOFF_CLOCK_TOLERANCE_MILLIS) {
        purgedAtOnCurrentClock
    } else {
        cutoffMillis
    }
}
