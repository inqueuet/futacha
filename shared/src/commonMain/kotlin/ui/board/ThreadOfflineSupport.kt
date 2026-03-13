package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.FileSystem

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

internal data class OfflineThreadLookupContext(
    val boardIdCandidates: List<String?>,
    val expectedBoardKeys: Set<String>
)

internal data class OfflineThreadSource(
    val repository: SavedThreadRepository?,
    val baseDirectory: String,
    val baseSaveLocation: SaveLocation? = null
)

internal fun buildOfflineThreadLookupContext(
    boardId: String,
    initialHistoryBoardId: String?,
    effectiveBoardUrl: String,
    boardUrl: String,
    initialHistoryBoardUrl: String?
): OfflineThreadLookupContext {
    return OfflineThreadLookupContext(
        boardIdCandidates = buildOfflineBoardIdCandidates(
            boardId = boardId,
            initialHistoryBoardId = initialHistoryBoardId,
            effectiveBoardUrl = effectiveBoardUrl,
            initialHistoryBoardUrl = initialHistoryBoardUrl
        ),
        expectedBoardKeys = buildExpectedOfflineBoardKeys(
            effectiveBoardUrl = effectiveBoardUrl,
            boardUrl = boardUrl,
            initialHistoryBoardUrl = initialHistoryBoardUrl
        )
    )
}

internal fun buildThreadOfflineSources(
    autoSaveRepository: SavedThreadRepository?,
    manualSaveRepository: SavedThreadRepository?,
    legacyManualSaveRepository: SavedThreadRepository?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?
): List<OfflineThreadSource> {
    return listOf(
        OfflineThreadSource(
            repository = autoSaveRepository,
            baseDirectory = AUTO_SAVE_DIRECTORY
        ),
        OfflineThreadSource(
            repository = manualSaveRepository,
            baseDirectory = manualSaveDirectory,
            baseSaveLocation = manualSaveLocation
        ),
        OfflineThreadSource(
            repository = legacyManualSaveRepository,
            baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
            baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
        )
    )
}

internal fun hasOfflineThreadSources(sources: List<OfflineThreadSource>): Boolean {
    return sources.any { it.repository != null }
}

internal fun buildOfflineMetadataBoardMismatchLogMessage(
    threadId: String,
    boardUrl: String
): String {
    return "Skip offline metadata due to board mismatch: threadId=$threadId boardUrl=$boardUrl"
}

internal fun buildOfflineMetadataNotFoundLogMessage(
    threadId: String,
    boardIdCandidates: List<String?>
): String {
    return "Offline metadata not found for threadId=$threadId boardIdCandidates=$boardIdCandidates"
}

internal suspend fun loadFirstOfflineThreadPage(
    threadId: String,
    boardIdCandidates: List<String?>,
    expectedBoardKeys: Set<String>,
    fileSystem: FileSystem,
    sources: List<OfflineThreadSource>,
    onBoardMismatch: (SavedThreadMetadata) -> Unit = {}
): ThreadPage? {
    sources.forEach { source ->
        val metadata = loadFirstOfflineMetadata(
            repository = source.repository,
            threadId = threadId,
            boardIdCandidates = boardIdCandidates,
            expectedBoardKeys = expectedBoardKeys,
            onBoardMismatch = onBoardMismatch
        ) ?: return@forEach
        return metadata.toThreadPage(
            fileSystem = fileSystem,
            baseDirectory = source.baseDirectory,
            baseSaveLocation = source.baseSaveLocation
        )
    }
    return null
}

internal suspend fun loadOfflineThreadPage(
    threadId: String,
    lookupContext: OfflineThreadLookupContext,
    fileSystem: FileSystem?,
    sources: List<OfflineThreadSource>,
    onBoardMismatch: (SavedThreadMetadata) -> Unit = {}
): ThreadPage? {
    val localFileSystem = fileSystem ?: return null
    return loadFirstOfflineThreadPage(
        threadId = threadId,
        boardIdCandidates = lookupContext.boardIdCandidates,
        expectedBoardKeys = lookupContext.expectedBoardKeys,
        fileSystem = localFileSystem,
        sources = sources,
        onBoardMismatch = onBoardMismatch
    )
}

internal fun SavedThreadMetadata.matchesBoardForOfflineFallback(expectedBoardKeys: Set<String>): Boolean {
    if (expectedBoardKeys.isEmpty()) return true
    val metadataBoardKey = normalizeBoardUrlForOfflineLookup(boardUrl) ?: return false
    return metadataBoardKey in expectedBoardKeys
}

internal fun normalizeBoardUrlForOfflineLookup(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalizedBase = runCatching {
        BoardUrlResolver.resolveBoardBaseUrl(candidate)
    }.getOrElse {
        candidate
    }
    return normalizedBase
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
}

private suspend fun loadFirstOfflineMetadata(
    repository: SavedThreadRepository?,
    threadId: String,
    boardIdCandidates: List<String?>,
    expectedBoardKeys: Set<String>,
    onBoardMismatch: (SavedThreadMetadata) -> Unit
): SavedThreadMetadata? {
    repository ?: return null
    for (candidateBoardId in boardIdCandidates) {
        val metadata = repository.loadThreadMetadata(threadId, candidateBoardId).getOrNull() ?: continue
        if (!shouldUseOfflineMetadataCandidate(candidateBoardId, metadata, expectedBoardKeys)) {
            onBoardMismatch(metadata)
            continue
        }
        return metadata
    }
    return null
}
