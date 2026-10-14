package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry

/** Fields which can actually change the modern history representation. */
data class CompatHistorySharedMetadata(
    val canonicalUrl: String,
    val boardKey: String,
    val boardName: String,
    val title: String,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val contentUpdatedAtEpochMillis: Long,
    val lastVisitedEpochMillis: Long
)

fun compatibilityHistorySharedMetadata(
    history: List<CompatHistoryEntry>
): List<CompatHistorySharedMetadata> = history.map { entry ->
    CompatHistorySharedMetadata(
        canonicalUrl = entry.canonicalUrl,
        boardKey = entry.boardKey,
        boardName = entry.boardName,
        title = entry.title,
        thumbnailUrl = entry.thumbnailUrl,
        replyCount = entry.replyCount,
        contentUpdatedAtEpochMillis = entry.contentUpdatedAtEpochMillis,
        lastVisitedEpochMillis = entry.lastVisitedEpochMillis
    )
}

/** Shared identity/conversion rules for the modern and compatibility stores. */
fun CompatHistoryEntry.toModernThreadHistoryEntry(): ThreadHistoryEntry? {
    val parsed = canonicalizeThreadUrl(canonicalUrl) ?: return null
    return ThreadHistoryEntry(
        threadId = parsed.threadNo,
        boardId = boardKey,
        title = title,
        titleImageUrl = thumbnailUrl.orEmpty(),
        boardName = boardName,
        boardUrl = parsed.canonicalBoardUrl,
        lastVisitedEpochMillis = lastVisitedEpochMillis,
        replyCount = replyCount,
        lastReadItemIndex = scrollAnchor.fallbackIndex.coerceAtLeast(0),
        lastReadItemOffset = scrollAnchor.offsetPx.coerceAtLeast(0)
    )
}

fun ThreadHistoryEntry.toCompatHistoryEntry(): CompatHistoryEntry? {
    if (!threadId.matches(SHARED_HISTORY_THREAD_NUMBER)) return null
    val parsedThreadUrl = canonicalizeThreadUrl(boardUrl)
    val normalizedBoardUrl = parsedThreadUrl?.canonicalBoardUrl
        ?: canonicalizeBoardUrl(boardUrl)
        ?: return null
    val normalizedThreadId = parsedThreadUrl?.threadNo ?: threadId
    val canonicalUrl = "${normalizedBoardUrl}res/$normalizedThreadId.htm"
    return CompatHistoryEntry(
        canonicalUrl = canonicalUrl,
        originalUrl = canonicalUrl,
        boardKey = compatBoardKey(normalizedBoardUrl),
        boardName = boardName,
        threadNo = normalizedThreadId,
        title = title,
        thumbnailUrl = titleImageUrl.takeIf { it.isNotBlank() },
        replyCount = replyCount.coerceAtLeast(0),
        contentUpdatedAtEpochMillis = lastVisitedEpochMillis,
        lastVisitedEpochMillis = lastVisitedEpochMillis,
        scrollAnchor = ScrollAnchor(
            fallbackIndex = lastReadItemIndex.coerceAtLeast(0),
            offsetPx = lastReadItemOffset.coerceAtLeast(0)
        )
    )
}

/**
 * The modern history has no content-update time: [toCompatHistoryEntry] can
 * only seed it with the view time. Importing therefore shares the visit time
 * and the thread metadata but keeps the compatibility row's own update time
 * (11.4: update and view times are separate fields) and its original URL.
 */
fun mergeImportedModernHistoryEntry(
    imported: CompatHistoryEntry,
    current: CompatHistoryEntry?
): CompatHistoryEntry {
    current ?: return imported
    return mergeCompatHistoryEntry(imported, current, recordVisit = true).copy(
        originalUrl = current.originalUrl,
        contentUpdatedAtEpochMillis = current.contentUpdatedAtEpochMillis
    )
}

/**
 * Plans importing the modern history into the compatibility history.
 *
 * Returns only the merged entries that differ from [current] and are still
 * kept by the store's own history limit ([retain]), so a repeated import
 * writes nothing. [current] must be the whole compatibility history.
 *
 * The compatibility history keeps at most [historyLimit] entries while the
 * modern one can hold ~20,000. Entries are visited newest first; once
 * [historyLimit] entries were accepted, an entry visited strictly earlier
 * cannot raise its visit time above them, so it is trimmed anyway (or, for an
 * entry the compatibility side visited later, keeps that side's fresher
 * metadata) and the rest of the list is not converted.
 */
fun planModernHistoryImport(
    modernHistory: List<ThreadHistoryEntry>,
    current: Collection<CompatHistoryEntry>,
    knownBoardKeys: Set<String>,
    tombstoneAt: (canonicalUrl: String) -> Long?,
    historyLimit: Int,
    retain: (List<CompatHistoryEntry>) -> List<CompatHistoryEntry>
): List<CompatHistoryEntry> {
    val byUrl = LinkedHashMap<String, CompatHistoryEntry>()
    current.forEach { entry -> byUrl[entry.canonicalUrl] = entry }
    val changed = LinkedHashMap<String, CompatHistoryEntry>()
    val accepted = HashSet<String>()
    var lastAcceptedVisit = Long.MAX_VALUE
    for (modern in modernHistory.sortedByDescending(ThreadHistoryEntry::lastVisitedEpochMillis)) {
        if (accepted.size >= historyLimit && modern.lastVisitedEpochMillis < lastAcceptedVisit) break
        val entry = modern.toCompatHistoryEntry()?.takeIf { it.boardKey in knownBoardKeys } ?: continue
        val tombstone = tombstoneAt(entry.canonicalUrl)
        if (tombstone != null && entry.lastVisitedEpochMillis <= tombstone) continue
        accepted += entry.canonicalUrl
        lastAcceptedVisit = entry.lastVisitedEpochMillis
        val old = changed[entry.canonicalUrl] ?: byUrl[entry.canonicalUrl]
        val merged = mergeImportedModernHistoryEntry(entry, old)
        if (merged != old) changed[entry.canonicalUrl] = merged
    }
    if (changed.isEmpty()) return emptyList()
    changed.forEach { (url, entry) -> byUrl[url] = entry }
    val kept = retain(byUrl.values.toList()).mapTo(HashSet(), CompatHistoryEntry::canonicalUrl)
    return changed.values.filter { entry -> entry.canonicalUrl in kept }
}

/** Merge without dropping non-Futaba boards. */
fun mergeCompatibilityBoards(
    modernBoards: List<BoardSummary>,
    compatBoards: List<CompatBoard>
): List<BoardSummary> {
    val result = modernBoards.toMutableList()
    val known = modernBoards.mapTo(mutableSetOf()) { boardIdentity(it.url) }
    compatBoards.forEach { board ->
        val summary = board.toBoardSummary()
        if (known.add(boardIdentity(summary.url))) result += summary
    }
    return result
}

/** Convert the modern board list into the authoritative compatibility order. */
fun modernBoardsToCompatibility(modernBoards: List<BoardSummary>): List<CompatBoard> {
    val seen = mutableSetOf<String>()
    return modernBoards.mapIndexedNotNull { index, board ->
        val canonical = canonicalizeBoardUrl(board.url) ?: return@mapIndexedNotNull null
        if (!seen.add(canonical)) return@mapIndexedNotNull null
        CompatBoard(
            key = compatBoardKey(canonical),
            name = board.name.ifBlank { canonical.substringAfter("//").substringBefore('/') },
            canonicalUrl = canonical,
            originalUrl = board.url,
            sortOrder = index
        )
    }
}

/** Keys retained during synchronization, including the bundled iOS/desktop tutorial. */
fun compatibilityBoardSynchronizationKeys(modernBoards: List<BoardSummary>): Set<String> {
    val keys = modernBoardsToCompatibility(modernBoards).mapTo(mutableSetOf(), CompatBoard::key)
    if (modernBoards.any { isBundledTutorialBoardUrl(it.url) }) {
        keys += compatBoardKey("https://img.2chan.net/t/")
    }
    return keys
}

/**
 * Compatibility boards are authoritative while the legacy profile is active.
 * Replace Futaba boards (and the checked-in tutorial fixture) instead of only
 * unioning them, otherwise a board deleted in としあき(仮) is imported again
 * from the modern store on the next process start.
 *
 * Only the board set, names, URLs and the relative order of Futaba boards come
 * from the compatibility store. A board that already exists in the modern list
 * keeps its ID and the modern-only fields (pin, category, description), and
 * non-Futaba boards keep their positions: Futaba boards are written back into
 * the positions Futaba boards occupied, in compatibility order, and extra
 * compatibility boards are appended.
 *
 * The tutorial fixture follows the store only when the store can hold it ([compatibilityHoldsTutorial], i.e.
 * [CompatibilityStore.holdsBundledTutorialBoard]; iOS and desktop): there its absence means it was deleted in
 * としあき(仮). The Android store never holds it, so there it stays where it is, like any board the store cannot
 * hold; a tutorial the user deleted from the AppState is never added back. The tutorial is told by its address
 * ([isBundledTutorialBoardUrl]), not by its id: a copy imported from the store has a `compat_board_…` id.
 *
 * The result never holds a board twice (the same id or address, [removeDuplicateBoardSummaries]), so copies saved by
 * older versions are removed by the next synchronization.
 */
fun synchronizeModernBoardsFromCompatibility(
    modernBoards: List<BoardSummary>,
    compatBoards: List<CompatBoard>,
    compatibilityHoldsTutorial: Boolean = true
): List<BoardSummary> {
    val existingByIdentity = preferredBoardsByIdentity(modernBoards)
    val synchronized = compatBoards.sortedBy(CompatBoard::sortOrder).map { board ->
        val summary = board.toBoardSummary()
        val existing = existingByIdentity[boardIdentity(summary.url)] ?: return@map summary
        existing.copy(
            name = summary.name.ifBlank { existing.name },
            url = summary.url.ifBlank { existing.url }
        )
    }
    // Boards the store holds although their address is not an official one (the tutorial and other example.com
    // boards on iOS/desktop). They come from the store, at the position the AppState had them, and only once: keeping
    // the AppState's own copy as well added one more copy at every synchronization (2026-10-13 全体検証 7.6).
    val synchronizedNonOfficial = buildMap {
        synchronized.forEach { board ->
            if (canonicalizeBoardUrl(board.url) == null) {
                val key = boardIdentity(board.url)
                if (key !in this) put(key, board)
            }
        }
    }
    val placed = HashSet<String>()
    val remaining = synchronized.iterator()
    fun nextSynchronized(): BoardSummary? {
        while (remaining.hasNext()) {
            val next = remaining.next()
            if (placed.add(boardIdentity(next.url))) return next
        }
        return null
    }
    val result = ArrayList<BoardSummary>(modernBoards.size + synchronized.size)
    modernBoards.forEach { board ->
        val identity = boardIdentity(board.url)
        when {
            isBundledTutorialBoardUrl(board.url) && compatibilityHoldsTutorial -> Unit
            canonicalizeBoardUrl(board.url) != null -> nextSynchronized()?.let(result::add)
            else -> {
                val fromStore = synchronizedNonOfficial[identity]
                if (placed.add(identity)) result += fromStore ?: existingByIdentity[identity] ?: board
            }
        }
    }
    while (true) result += nextSynchronized() ?: break
    return removeDuplicateBoardSummaries(result)
}

/**
 * Whether [url] is the bundled tutorial board (`https://www.example.com/t/`, with or without `futaba.php`), whatever
 * its id: the fixture uses `t`, a copy imported from the iOS/desktop compatibility store uses `compat_board_…`.
 */
fun isBundledTutorialBoardUrl(url: String): Boolean = tutorialBoardUrlRegex.matches(url.trim())

private val tutorialBoardUrlRegex =
    Regex("^https?://(?:www\\.)?example\\.com/+t(?:/+(?:futaba\\.(?:php|htm))?)?$", RegexOption.IGNORE_CASE)

/**
 * Removes boards stored more than once: the same id, or the same board address (the official address normalized,
 * the tutorial by [isBundledTutorialBoardUrl]). Each board stays at its first position; of the copies, the bundled
 * fixture (`t`) is kept for the tutorial, otherwise the first one with its pin, category and description, as the
 * synchronization does. Returns [boards] itself when nothing is repeated.
 */
fun removeDuplicateBoardSummaries(boards: List<BoardSummary>): List<BoardSummary> {
    val preferred = preferredBoardsByIdentity(boards)
    val identities = HashSet<String>()
    val ids = HashSet<String>()
    val result = ArrayList<BoardSummary>(boards.size)
    boards.forEach { board ->
        val identity = boardIdentity(board.url)
        if (!identities.add(identity)) return@forEach
        val kept = preferred[identity] ?: board
        if (ids.add(kept.id)) result += kept
    }
    return if (result.size == boards.size) boards else result
}

/** The board kept for each address: the first one, or for the tutorial the bundled fixture when it is there. */
private fun preferredBoardsByIdentity(boards: List<BoardSummary>): Map<String, BoardSummary> = buildMap {
    boards.forEach { board ->
        val key = boardIdentity(board.url)
        val current = this[key]
        if (current == null || (board.id == BUNDLED_TUTORIAL_BOARD_ID && current.id != BUNDLED_TUTORIAL_BOARD_ID &&
                isBundledTutorialBoardUrl(board.url))
        ) {
            put(key, board)
        }
    }
}

private const val BUNDLED_TUTORIAL_BOARD_ID = "t"

/** Merge by canonical thread URL and retain the newest read position. */
fun mergeCompatibilityHistory(
    modernHistory: List<ThreadHistoryEntry>,
    compatHistory: List<CompatHistoryEntry>,
    modernBoards: List<BoardSummary> = emptyList()
): List<ThreadHistoryEntry> {
    val merged = LinkedHashMap<String, ThreadHistoryEntry>()
    val modernBoardIds = buildMap {
        modernBoards.forEach { board ->
            val key = boardIdentity(board.url)
            if (key !in this) put(key, board.id)
        }
    }
    modernHistory.forEach { entry -> merged[historyIdentity(entry)] = entry }
    compatHistory.mapNotNull { it.toModernThreadHistoryEntry() }.forEach { rawCandidate ->
        // Preserve the modern board ID when the board already exists there.
        // Modern history consumers use boardId for board lookup, while the
        // compatibility store intentionally uses a URL-derived key.
        val candidate = modernBoardIds[boardIdentity(rawCandidate.boardUrl)]
            ?.let { boardId -> rawCandidate.copy(boardId = boardId) }
            ?: rawCandidate
        val key = historyIdentity(candidate)
        val current = merged[key]
        if (current == null) {
            merged[key] = candidate
        } else {
            // Share explicit visit times, but keep mode-local scroll positions:
            // the two UIs have different list layouts.
            merged[key] = current.copy(
                boardId = current.boardId.ifBlank { candidate.boardId },
                title = candidate.title.ifBlank { current.title },
                titleImageUrl = candidate.titleImageUrl.ifBlank { current.titleImageUrl },
                boardName = candidate.boardName.ifBlank { current.boardName },
                boardUrl = candidate.boardUrl.ifBlank { current.boardUrl },
                replyCount = maxOf(current.replyCount, candidate.replyCount),
                lastVisitedEpochMillis = maxOf(current.lastVisitedEpochMillis, candidate.lastVisitedEpochMillis),
                hasAutoSave = current.hasAutoSave || candidate.hasAutoSave,
                isAutoRefreshDisabled = current.isAutoRefreshDisabled || candidate.isAutoRefreshDisabled,
                hasSelfPost = current.hasSelfPost || candidate.hasSelfPost,
                lastSelfPostEpochMillis = current.lastSelfPostEpochMillis
                    ?: candidate.lastSelfPostEpochMillis,
                lastConfirmedAliveEpochMillis = current.lastConfirmedAliveEpochMillis
                    ?: candidate.lastConfirmedAliveEpochMillis
            )
        }
    }
    return merged.values.sortedWith(
        compareByDescending<ThreadHistoryEntry> { it.lastVisitedEpochMillis }
            .thenBy { historyIdentity(it) }
    )
}

private fun historyIdentity(entry: ThreadHistoryEntry): String {
    val parsedThreadUrl = canonicalizeThreadUrl(entry.boardUrl)
    val board = parsedThreadUrl?.canonicalBoardUrl ?: canonicalizeBoardUrl(entry.boardUrl)
    val threadId = parsedThreadUrl?.threadNo ?: entry.threadId
    return if (board != null && threadId.matches(SHARED_HISTORY_THREAD_NUMBER)) {
        "${board}res/$threadId"
    } else {
        "${entry.boardId}::${entry.threadId}"
    }
}

private fun boardIdentity(url: String): String =
    canonicalizeBoardUrl(url)
        ?: if (isBundledTutorialBoardUrl(url)) BUNDLED_TUTORIAL_IDENTITY else url.trim().trimEnd('/').lowercase()

private const val BUNDLED_TUTORIAL_IDENTITY = "bundled-tutorial:www.example.com/t/"

private val SHARED_HISTORY_THREAD_NUMBER = Regex("[0-9]+")
