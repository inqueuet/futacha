package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

internal data class HistoryRefreshMissingEntryDependencies(
    val httpClient: HttpClient?,
    val repository: BoardRepository,
    val archiveSearchJson: Json,
    val fetchTimeoutMillis: Long,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val skipThreadIds: MutableStateFlow<Map<HistoryRefreshKey, Long>>,
    val stats: HistoryRefreshRunStats,
    val errors: HistoryRefreshErrorTracker,
    val updates: HistoryRefreshUpdateBuffer,
    val archiveLookupAbortThreshold: HistoryRefreshAbortThreshold,
    val archiveLookupStage: String,
    val refreshAbortStage: String,
    val tag: String
)

internal suspend fun handleHistoryRefreshNotFound(
    resolvedEntry: HistoryRefreshResolvedEntry,
    dependencies: HistoryRefreshMissingEntryDependencies
) {
    val entry = resolvedEntry.entry
    val board = resolvedEntry.board
    val key = resolvedEntry.key
    val baseUrl = resolvedEntry.baseUrl
    when (
        val archiveResult = tryRefreshHistoryEntryFromArchive(
            entry = entry,
            board = board,
            httpClient = dependencies.httpClient,
            repository = dependencies.repository,
            fetchTimeoutMillis = dependencies.fetchTimeoutMillis,
            archiveSearchJson = dependencies.archiveSearchJson,
            tag = dependencies.tag
        )
    ) {
        is ArchiveRefreshResult.Success -> {
            dependencies.stats.markSuccess()
            dependencies.updates.put(key, archiveResult.entry)
            return
        }

        ArchiveRefreshResult.NotFound,
        ArchiveRefreshResult.NoMatch -> {
            markHistoryThreadSkipped(dependencies.skipThreadIds, key)
        }

        is ArchiveRefreshResult.Error -> {
            dependencies.errors.record(
                threadId = entry.threadId,
                message = archiveResult.message,
                stage = dependencies.archiveLookupStage
            )
            dependencies.stats.recordArchiveLookupFailure()
            val abortReason = dependencies.stats.archiveLookupAbortReason(
                dependencies.archiveLookupAbortThreshold
            )
            if (abortReason != null) {
                dependencies.errors.record(
                    threadId = "refresh-abort",
                    message = abortReason,
                    stage = dependencies.refreshAbortStage
                )
                Logger.e(dependencies.tag, abortReason)
                throw NetworkException(abortReason)
            }
            return
        }
    }
    val hasAutoSave = hasHistoryAutoSavedCopy(
        entry = entry,
        board = board,
        baseUrl = baseUrl,
        repository = dependencies.autoSavedThreadRepository
    )
    if (hasAutoSave && !entry.hasAutoSave) {
        dependencies.updates.put(key, entry.copy(hasAutoSave = true))
        Logger.i(dependencies.tag, "Thread ${entry.threadId} not found but has auto-save")
    } else {
        Logger.i(dependencies.tag, "Skip thread ${entry.threadId} (not found)")
    }
}
