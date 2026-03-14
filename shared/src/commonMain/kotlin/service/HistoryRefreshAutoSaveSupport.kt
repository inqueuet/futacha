package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

internal data class HistoryRefreshAutoSavePlan(
    val resolvedEntry: HistoryRefreshResolvedEntry,
    val updatedEntry: ThreadHistoryEntry,
    val resolvedTitle: String,
    val boardName: String,
    val expiresAtLabel: String?,
    val posts: List<Post>
)

internal class HistoryRefreshAutoSaveLauncher(
    private val stateStore: AppStateStore,
    private val autoSaveScope: CoroutineScope,
    private val autoSaveSemaphore: Semaphore,
    private val autoSaveService: ThreadSaveService?,
    private val autoSavedThreadRepository: SavedThreadRepository?,
    private val autoSaveThreadTimeoutMillis: Long,
    private val autoSaveDeadline: Long?,
    private val maxAutoSavesPerRefresh: Int,
    private val stats: HistoryRefreshRunStats,
    private val tag: String
) {
    fun launch(plan: HistoryRefreshAutoSavePlan) {
        val autoSaveService = autoSaveService ?: return
        val autoSavedThreadRepository = autoSavedThreadRepository ?: return
        autoSaveScope.launch {
            val entry = plan.resolvedEntry.entry
            val board = plan.resolvedEntry.board
            val baseUrl = plan.resolvedEntry.baseUrl
            val nowForBudgetCheck = Clock.System.now().toEpochMilliseconds()
            var autoSaveSlotReserved = false
            val allowAutoSave = stats.tryReserveAutoSaveSlot(
                nowMillis = nowForBudgetCheck,
                autoSaveDeadline = autoSaveDeadline,
                maxAutoSavesPerRefresh = maxAutoSavesPerRefresh
            ).also { reserved ->
                autoSaveSlotReserved = reserved
            }
            if (!allowAutoSave) {
                if (autoSaveDeadline != null && nowForBudgetCheck > autoSaveDeadline) {
                    Logger.w(tag, "Auto-save budget exceeded, skipping auto-save for ${entry.threadId}")
                } else {
                    Logger.d(
                        tag,
                        "Auto-save limit reached ($maxAutoSavesPerRefresh), skipping ${entry.threadId}"
                    )
                }
                return@launch
            }
            try {
                val resolvedBoardId = resolveHistoryEntryBoardId(entry, board, baseUrl)
                val saved = autoSaveSemaphore.withPermit {
                    if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                        Logger.w(tag, "Auto-save budget exceeded while waiting permit, skipping ${entry.threadId}")
                        if (autoSaveSlotReserved) {
                            stats.releaseAutoSaveSlotIfReserved()
                            autoSaveSlotReserved = false
                        }
                        return@withPermit null
                    }
                    withTimeoutOrNull(autoSaveThreadTimeoutMillis) {
                        autoSaveService.saveThread(
                            threadId = entry.threadId,
                            boardId = resolvedBoardId,
                            boardName = plan.boardName,
                            boardUrl = baseUrl,
                            title = plan.resolvedTitle,
                            expiresAtLabel = plan.expiresAtLabel,
                            posts = plan.posts,
                            baseDirectory = AUTO_SAVE_DIRECTORY,
                            writeMetadata = true
                        ).getOrThrow()
                    }
                }
                if (saved != null) {
                    autoSavedThreadRepository.addThreadToIndex(saved)
                        .onFailure { Logger.e(tag, "Failed to index auto-saved thread ${entry.threadId}", it) }
                    runCatching {
                        stateStore.upsertHistoryEntry(plan.updatedEntry.copy(hasAutoSave = true))
                    }.onFailure {
                        Logger.w(
                            tag,
                            "Failed to update history hasAutoSave flag for ${entry.threadId}: ${it.message}"
                        )
                    }
                } else {
                    Logger.w(tag, "Auto-save timed out for ${entry.threadId}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                Logger.e(tag, "Auto-save during background refresh failed for ${entry.threadId}", error)
            }
        }
    }
}

internal suspend fun hasHistoryAutoSavedCopy(
    entry: ThreadHistoryEntry,
    board: BoardSummary?,
    baseUrl: String,
    repository: SavedThreadRepository?
): Boolean {
    repository ?: return false
    return try {
        val resolvedBoardId = resolveHistoryEntryBoardId(entry, board, baseUrl)
        repository.loadThreadMetadata(
            threadId = entry.threadId,
            boardId = resolvedBoardId.ifBlank { null }
        ).isSuccess
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }
}
