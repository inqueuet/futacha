package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.random.Random

internal data class HistoryRefreshAutoSavePlan(
    val resolvedEntry: HistoryRefreshResolvedEntry,
    val updatedEntry: ThreadHistoryEntry,
    val resolvedTitle: String,
    val boardName: String,
    val expiresAtLabel: String?,
    val posts: List<Post>,
    val isTruncated: Boolean,
    val truncationReason: String?
)

internal class HistoryRefreshAutoSaveLauncher(
    private val updates: HistoryRefreshUpdateBuffer,
    private val autoSaveScope: CoroutineScope,
    private val autoSaveSemaphore: Semaphore,
    private val autoSaveService: ThreadSaveService?,
    private val autoSavedThreadRepository: SavedThreadRepository?,
    private val fileSystem: FileSystem?,
    private val commitGate: suspend (commit: suspend () -> Unit) -> Boolean,
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
                val stableStorageId = buildThreadStorageId(resolvedBoardId, entry.threadId)
                val stagingStorageId = buildThreadSaveGenerationStorageId(
                    boardId = resolvedBoardId,
                    threadId = entry.threadId,
                    savedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    nonce = Random.nextInt(0, Int.MAX_VALUE).toString(36)
                )
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
                        ThreadStorageLockRegistry.withStorageLock(
                            buildThreadStorageLockKey(
                                storageId = stableStorageId,
                                baseDirectory = AUTO_SAVE_DIRECTORY
                            )
                        ) {
                            autoSaveService.saveThread(
                                threadId = entry.threadId,
                                boardId = resolvedBoardId,
                                boardName = plan.boardName,
                                boardUrl = baseUrl,
                                title = plan.resolvedTitle,
                                expiresAtLabel = plan.expiresAtLabel,
                                posts = plan.posts,
                                isTruncated = plan.isTruncated,
                                truncationReason = plan.truncationReason,
                                baseDirectory = AUTO_SAVE_DIRECTORY,
                                writeMetadata = true,
                                storageOptions = ThreadSaveStorageOptions(
                                    storageIdOverride = stagingStorageId,
                                    clearExistingOutput = true,
                                    reuseExistingMedia = false,
                                    pruneUnreferencedExistingMedia = false
                                )
                            ).getOrThrow()
                        }
                    }
                }
                if (saved != null) {
                    val savedStorageId = saved.storageId ?: stagingStorageId
                    val committed = try {
                        commitGate {
                            autoSavedThreadRepository.addThreadToIndex(saved).getOrThrow()
                        }
                    } catch (error: Throwable) {
                        cleanupRejectedHistoryAutoSave(fileSystem, savedStorageId, tag)
                        throw error
                    }
                    if (committed) {
                        runSuspendCatchingPreservingCancellation {
                            updates.put(
                                plan.resolvedEntry.key,
                                plan.updatedEntry.copy(hasAutoSave = true)
                            )
                        }.onFailure {
                            Logger.w(
                                tag,
                                "Failed to update history hasAutoSave flag for ${entry.threadId}: ${it.message}"
                            )
                        }
                    } else {
                        cleanupRejectedHistoryAutoSave(fileSystem, savedStorageId, tag)
                        Logger.d(tag, "Discarded stale auto-save for ${entry.threadId}")
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

private suspend fun cleanupRejectedHistoryAutoSave(
    fileSystem: FileSystem?,
    storageId: String,
    tag: String
) {
    fileSystem ?: return
    runSuspendCatchingPreservingCancellation {
        cleanupThreadSaveStorageTarget(
            fileSystem = fileSystem,
            target = buildThreadSaveStorageTarget(
                saveLocation = null,
                baseDirectory = AUTO_SAVE_DIRECTORY,
                storageId = storageId
            )
        )
    }.onFailure { error ->
        Logger.w(tag, "Failed to clean rejected auto-save $storageId: ${error.message}")
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
