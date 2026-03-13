package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.ui.isDefaultManualSaveRoot
import com.valoser.futacha.shared.util.FileSystem

internal data class ThreadScreenEnvironmentBundle(
    val activeRepository: BoardRepository,
    val effectiveBoardUrl: String,
    val initialHistoryEntry: ThreadHistoryEntry?,
    val autoSaveRepository: SavedThreadRepository?,
    val manualSaveRepository: SavedThreadRepository?,
    val legacyManualSaveRepository: SavedThreadRepository?,
    val offlineLookupContext: OfflineThreadLookupContext,
    val offlineSources: List<OfflineThreadSource>
)

internal fun buildThreadScreenEnvironmentBundle(
    repository: BoardRepository?,
    autoSavedThreadRepository: SavedThreadRepository?,
    fileSystem: FileSystem?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    board: BoardSummary,
    resolvedThreadUrlOverride: String?
): ThreadScreenEnvironmentBundle {
    val activeRepository = repository ?: FakeBoardRepository()
    val effectiveBoardUrl = resolveEffectiveBoardUrl(resolvedThreadUrlOverride, board.url)
    val normalizedBoardUrlForHistory = board.url
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
    val initialHistoryEntry = history.firstOrNull { entry ->
        if (entry.threadId != threadId) {
            false
        } else if (entry.boardId.isNotBlank() && board.id.isNotBlank()) {
            entry.boardId == board.id
        } else {
            entry.boardUrl.trim().substringBefore('?').trimEnd('/').lowercase() == normalizedBoardUrlForHistory
        }
    }
    val manualSaveRepository = fileSystem?.let { fs ->
        SavedThreadRepository(
            fs,
            baseDirectory = manualSaveDirectory,
            baseSaveLocation = manualSaveLocation
        )
    }
    val legacyManualSaveRepository = fileSystem?.let { fs ->
        val isCurrentDefaultPath = manualSaveLocation is SaveLocation.Path &&
            isDefaultManualSaveRoot(manualSaveDirectory)
        if (isCurrentDefaultPath) {
            null
        } else {
            SavedThreadRepository(
                fs,
                baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
            )
        }
    }
    val offlineLookupContext = buildOfflineThreadLookupContext(
        boardId = board.id,
        initialHistoryBoardId = initialHistoryEntry?.boardId,
        effectiveBoardUrl = effectiveBoardUrl,
        boardUrl = board.url,
        initialHistoryBoardUrl = initialHistoryEntry?.boardUrl
    )
    return ThreadScreenEnvironmentBundle(
        activeRepository = activeRepository,
        effectiveBoardUrl = effectiveBoardUrl,
        initialHistoryEntry = initialHistoryEntry,
        autoSaveRepository = autoSavedThreadRepository,
        manualSaveRepository = manualSaveRepository,
        legacyManualSaveRepository = legacyManualSaveRepository,
        offlineLookupContext = offlineLookupContext,
        offlineSources = buildThreadOfflineSources(
            autoSaveRepository = autoSavedThreadRepository,
            manualSaveRepository = manualSaveRepository,
            legacyManualSaveRepository = legacyManualSaveRepository,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation
        )
    )
}
