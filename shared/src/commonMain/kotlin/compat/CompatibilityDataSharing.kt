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
    if (modernBoards.any { it.url == "https://www.example.com/t/futaba.php" }) {
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
 */
fun synchronizeModernBoardsFromCompatibility(
    modernBoards: List<BoardSummary>,
    compatBoards: List<CompatBoard>
): List<BoardSummary> {
    val existingByIdentity = buildMap {
        modernBoards.forEach { board ->
            val key = boardIdentity(board.url)
            if (key !in this) put(key, board)
        }
    }
    val synchronized = compatBoards.sortedBy(CompatBoard::sortOrder).map { board ->
        val summary = board.toBoardSummary()
        val existing = existingByIdentity[boardIdentity(summary.url)] ?: return@map summary
        existing.copy(
            name = summary.name.ifBlank { existing.name },
            url = summary.url.ifBlank { existing.url }
        )
    }
    val remaining = synchronized.iterator()
    val result = ArrayList<BoardSummary>(modernBoards.size + synchronized.size)
    modernBoards.forEach { board ->
        when {
            board.isCompatibilityTutorialFixture() -> Unit
            canonicalizeBoardUrl(board.url) != null -> if (remaining.hasNext()) result += remaining.next()
            else -> result += board
        }
    }
    remaining.forEach { result += it }
    return result
}

private fun BoardSummary.isCompatibilityTutorialFixture(): Boolean =
    id == "t" && url.contains("example.com", ignoreCase = true)

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
    canonicalizeBoardUrl(url) ?: url.trim().trimEnd('/').lowercase()

private val SHARED_HISTORY_THREAD_NUMBER = Regex("[0-9]+")
