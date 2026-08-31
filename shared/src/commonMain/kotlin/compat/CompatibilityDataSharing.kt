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
    val contentUpdatedAtEpochMillis: Long
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
        contentUpdatedAtEpochMillis = entry.contentUpdatedAtEpochMillis
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
        lastVisitedEpochMillis = contentUpdatedAtEpochMillis,
        replyCount = replyCount,
        lastReadItemIndex = scrollAnchor.fallbackIndex.coerceAtLeast(0),
        lastReadItemOffset = scrollAnchor.offsetPx.coerceAtLeast(0)
    )
}

fun ThreadHistoryEntry.toCompatHistoryEntry(): CompatHistoryEntry? {
    if (!threadId.matches(Regex("[0-9]+"))) return null
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
        scrollAnchor = ScrollAnchor(
            fallbackIndex = lastReadItemIndex.coerceAtLeast(0),
            offsetPx = lastReadItemOffset.coerceAtLeast(0)
        )
    )
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

/**
 * Compatibility boards are authoritative while the legacy profile is active.
 * Replace Futaba boards (and the checked-in tutorial fixture) instead of only
 * unioning them, otherwise a board deleted in としあき(仮) is imported again
 * from the modern store on the next process start.
 */
fun synchronizeModernBoardsFromCompatibility(
    modernBoards: List<BoardSummary>,
    compatBoards: List<CompatBoard>
): List<BoardSummary> {
    val existingIds = modernBoards.associateBy({ boardIdentity(it.url) }, BoardSummary::id)
    val preserved = modernBoards.filterNot { board ->
        canonicalizeBoardUrl(board.url) != null || board.isCompatibilityTutorialFixture()
    }
    val synchronized = compatBoards.sortedBy(CompatBoard::sortOrder).map { board ->
        val summary = board.toBoardSummary()
        existingIds[boardIdentity(summary.url)]?.let { id -> summary.copy(id = id) } ?: summary
    }
    return preserved + synchronized
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
            // Compatibility contentUpdatedAt is a network/content timestamp,
            // not a reading timestamp. The two UIs also use different list
            // layouts, so their numeric positions are not interchangeable.
            // Merge shared metadata only and keep modern-local visit/read
            // state intact for an existing entry.
            merged[key] = current.copy(
                boardId = current.boardId.ifBlank { candidate.boardId },
                title = candidate.title.ifBlank { current.title },
                titleImageUrl = candidate.titleImageUrl.ifBlank { current.titleImageUrl },
                boardName = candidate.boardName.ifBlank { current.boardName },
                boardUrl = candidate.boardUrl.ifBlank { current.boardUrl },
                replyCount = maxOf(current.replyCount, candidate.replyCount),
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
    return if (board != null && threadId.matches(Regex("[0-9]+"))) {
        "${board}res/$threadId"
    } else {
        "${entry.boardId}::${entry.threadId}"
    }
}

private fun boardIdentity(url: String): String =
    canonicalizeBoardUrl(url) ?: url.trim().trimEnd('/').lowercase()
