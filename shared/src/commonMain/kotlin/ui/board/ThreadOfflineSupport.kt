package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.network.BoardUrlResolver

internal fun buildOfflineBoardIdCandidates(
    boardId: String,
    initialHistoryBoardId: String?,
    effectiveBoardUrl: String,
    initialHistoryBoardUrl: String?
): List<String?> {
    return buildList {
        add(boardId.ifBlank { null })
        add(initialHistoryBoardId?.ifBlank { null })
        add(
            runCatching { BoardUrlResolver.resolveBoardSlug(effectiveBoardUrl) }
                .getOrNull()
                ?.ifBlank { null }
        )
        add(
            runCatching { BoardUrlResolver.resolveBoardSlug(initialHistoryBoardUrl.orEmpty()) }
                .getOrNull()
                ?.ifBlank { null }
        )
        add(null)
    }.distinct()
}

internal fun buildExpectedOfflineBoardKeys(
    effectiveBoardUrl: String,
    boardUrl: String,
    initialHistoryBoardUrl: String?
): Set<String> {
    return buildSet {
        normalizeBoardUrlForOfflineLookup(effectiveBoardUrl)?.let(::add)
        normalizeBoardUrlForOfflineLookup(boardUrl)?.let(::add)
        normalizeBoardUrlForOfflineLookup(initialHistoryBoardUrl)?.let(::add)
    }
}

internal fun shouldUseOfflineMetadataCandidate(
    candidateBoardId: String?,
    metadata: SavedThreadMetadata,
    expectedBoardKeys: Set<String>
): Boolean {
    return candidateBoardId != null || metadata.matchesBoardForOfflineFallback(expectedBoardKeys)
}
