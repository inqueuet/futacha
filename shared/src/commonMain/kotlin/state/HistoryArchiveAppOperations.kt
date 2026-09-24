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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    // Taken before reading the archive: a deletion made while it is read must
    // not be undone by the merge, while older deletions may be restored.
    val ticket = stateStore.beginHistoryImport()
    try {
        return importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = destinationRepository,
            request = HistoryArchiveImportRequest(
                archiveDirectory = archiveDirectory,
                selectedSnapshotIds = selectedSnapshotIds
            )
        ).mapCatching { archiveImport ->
            // Merged against the latest history in one mutation, so reading
            // positions and entries changed during the import are kept.
            val merge = stateStore.mergeImportedHistory(archiveImport.importedHistoryEntries, ticket)
            AppHistoryArchiveImportResult(
                archiveImport = archiveImport,
                merge = merge
            )
        }
    } finally {
        withContext(NonCancellable) { stateStore.endHistoryImport(ticket) }
    }
}
