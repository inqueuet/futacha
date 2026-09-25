package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.HistoryArchiveExportRequest
import com.valoser.futacha.shared.repository.HistoryArchiveExportResult
import com.valoser.futacha.shared.repository.HistoryArchiveImportRequest
import com.valoser.futacha.shared.repository.HistoryArchiveImportResult
import com.valoser.futacha.shared.repository.MAX_HISTORY_ARCHIVE_ENTRIES
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repository.exportHistoryArchive
import com.valoser.futacha.shared.repository.importHistoryArchive
import com.valoser.futacha.shared.util.AppDispatchers
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
): Result<HistoryArchiveExportResult> = withContext(AppDispatchers.io) {
    val requestedEntries = selectedEntries ?: stateStore.history.first()
    // History holds up to 20,000 entries but an archive at most 2,000 (import
    // reads the whole manifest at once), so the most recently visited ones are
    // exported and the rest reported instead of failing the whole export.
    val exportEntries = limitHistoryArchiveExportEntries(requestedEntries)
    exportHistoryArchive(
        fileSystem = fileSystem,
        sourceRepositories = sourceRepositories,
        request = HistoryArchiveExportRequest(
            archiveId = archiveId,
            historyEntries = exportEntries,
            exportedAtEpochMillis = exportedAtEpochMillis,
            appVersion = appVersion
        )
    ).map { result ->
        result.copy(omittedEntryCount = requestedEntries.size - exportEntries.size)
    }
}

/** The [limit] most recently visited entries, in their original order. */
internal fun limitHistoryArchiveExportEntries(
    entries: List<ThreadHistoryEntry>,
    limit: Int = MAX_HISTORY_ARCHIVE_ENTRIES
): List<ThreadHistoryEntry> {
    if (entries.size <= limit) return entries
    val keptIndices = entries.indices
        .sortedByDescending { entries[it].lastVisitedEpochMillis }
        .take(limit.coerceAtLeast(0))
        .toHashSet()
    return entries.filterIndexed { index, _ -> index in keptIndices }
}

suspend fun importAppHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem,
    destinationRepository: SavedThreadRepository,
    archiveDirectory: String,
    selectedSnapshotIds: Set<String>? = null
): Result<AppHistoryArchiveImportResult> = withContext(AppDispatchers.io) {
    // Taken before reading the archive: a deletion made while it is read must
    // not be undone by the merge, while older deletions may be restored.
    val ticket = stateStore.beginHistoryImport()
    try {
        importHistoryArchive(
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
