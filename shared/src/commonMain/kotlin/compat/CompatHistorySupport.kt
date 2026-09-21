package com.valoser.futacha.shared.compat

/** Metadata refreshes must not change the user's browsing order or read position. */
fun mergeCompatHistoryEntry(
    incoming: CompatHistoryEntry,
    current: CompatHistoryEntry?,
    recordVisit: Boolean = false
): CompatHistoryEntry {
    current ?: return incoming
    val metadata = if (recordVisit && current.contentUpdatedAtEpochMillis > incoming.contentUpdatedAtEpochMillis) current else incoming
    return metadata.copy(
        scrollAnchor = current.scrollAnchor,
        lastVisitedEpochMillis = if (recordVisit) {
            maxOf(current.lastVisitedEpochMillis, incoming.lastVisitedEpochMillis)
        } else current.lastVisitedEpochMillis
    )
}

fun CompatTab.toVisitedHistoryEntry(visitedAtEpochMillis: Long) = CompatHistoryEntry(
    canonicalUrl = canonicalUrl,
    originalUrl = originalUrl,
    boardKey = boardKey,
    boardName = boardName,
    threadNo = threadNo,
    title = title,
    thumbnailUrl = thumbnailUrl,
    replyCount = replyCount,
    contentUpdatedAtEpochMillis = contentUpdatedAtEpochMillis,
    scrollAnchor = scrollAnchor,
    lastVisitedEpochMillis = visitedAtEpochMillis
)
