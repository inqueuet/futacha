package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.HistoryArchiveExportRequest
import com.valoser.futacha.shared.repository.HistoryArchiveExportResult
import com.valoser.futacha.shared.repository.HistoryArchiveImportRequest
import com.valoser.futacha.shared.repository.HistoryArchiveImportResult
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repository.exportHistoryArchive
import com.valoser.futacha.shared.repository.importHistoryArchive
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.flow.first

data class AppHistoryArchiveImportResult(
    val archiveImport: HistoryArchiveImportResult,
    val merge: HistoryArchiveImportMergeResult
)

suspend fun exportAppHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem,
    sourceRepositories: List<SavedThreadRepository>,
    archiveId: String,
    exportedAtEpochMillis: Long,
    appVersion: String? = null,
    selectedEntries: List<ThreadHistoryEntry>? = null
): Result<HistoryArchiveExportResult> {
    val currentHistory = stateStore.history.first()
    val exportEntries = selectedEntries ?: currentHistory
    return exportHistoryArchive(
        fileSystem = fileSystem,
        sourceRepositories = sourceRepositories,
        request = HistoryArchiveExportRequest(
            archiveId = archiveId,
            historyEntries = exportEntries,
            exportedAtEpochMillis = exportedAtEpochMillis,
            appVersion = appVersion
        )
    )
}

suspend fun importAppHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem,
    destinationRepository: SavedThreadRepository,
    archiveDirectory: String,
    selectedSnapshotIds: Set<String>? = null
): Result<AppHistoryArchiveImportResult> {
    return importHistoryArchive(
        fileSystem = fileSystem,
        destinationRepository = destinationRepository,
        request = HistoryArchiveImportRequest(
            archiveDirectory = archiveDirectory,
            selectedSnapshotIds = selectedSnapshotIds
        )
    ).mapCatching { archiveImport ->
        val merge = resolveHistoryArchiveImportMergeEntries(
            currentHistory = stateStore.history.first(),
            importedHistory = archiveImport.importedHistoryEntries
        )
        stateStore.setHistory(merge.updatedHistory)
        AppHistoryArchiveImportResult(
            archiveImport = archiveImport,
            merge = merge
        )
    }
}
