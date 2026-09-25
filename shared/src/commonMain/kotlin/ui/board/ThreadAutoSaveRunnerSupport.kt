package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.AutoSaveNetworkPolicy
import com.valoser.futacha.shared.service.AutoSaveRetentionRegistry
import com.valoser.futacha.shared.service.RawHtmlSaveOptions
import com.valoser.futacha.shared.service.ThreadSaveStorageOptions
import com.valoser.futacha.shared.service.ThreadSaveLimits
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.ThreadStorageLockRegistry
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.service.buildThreadStorageLockKey
import com.valoser.futacha.shared.service.withSeedStorageLock
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.CancellationException

private const val THREAD_AUTO_SAVE_MAX_MEDIA_ITEMS = 1_200
private const val THREAD_AUTO_SAVE_MAX_DURATION_MS = 180_000L
private const val THREAD_AUTO_SAVE_MAX_PARALLEL_DOWNLOADS = 2
private const val THREAD_AUTO_SAVE_MEDIA_START_DELAY_MS = 2_000L

internal data class ThreadAutoSaveRunnerConfig(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val expiresAtLabel: String?,
    val posts: List<Post>,
    val isTruncated: Boolean,
    val truncationReason: String?,
    val previousTimestampMillis: Long,
    val attemptStartedAtMillis: Long,
    val completionTimestampMillis: Long
)

internal fun buildThreadAutoSaveRunnerConfig(
    threadId: String,
    boardId: String,
    boardName: String,
    boardUrl: String,
    title: String,
    expiresAtLabel: String?,
    posts: List<Post>,
    isTruncated: Boolean = false,
    truncationReason: String? = null,
    previousTimestampMillis: Long,
    attemptStartedAtMillis: Long,
    completionTimestampMillis: Long
): ThreadAutoSaveRunnerConfig {
    return ThreadAutoSaveRunnerConfig(
        threadId = threadId,
        boardId = boardId,
        boardName = boardName,
        boardUrl = boardUrl,
        title = title,
        expiresAtLabel = expiresAtLabel,
        posts = posts,
        isTruncated = isTruncated,
        truncationReason = truncationReason,
        previousTimestampMillis = previousTimestampMillis,
        attemptStartedAtMillis = attemptStartedAtMillis,
        completionTimestampMillis = completionTimestampMillis
    )
}

internal data class ThreadAutoSaveRunResult(
    val completionState: ThreadAutoSaveCompletionState
)

internal data class ThreadAutoSaveRunnerCallbacks(
    val saveThread: suspend (ThreadAutoSaveRunnerConfig, suspend (SavedThread) -> Unit) -> Result<SavedThread>
)

internal fun buildThreadAutoSaveRunnerCallbacks(
    saveService: ThreadSaveService,
    /**
     * The indexed auto-save of the thread. When a background run replaced the
     * stable folder with a newer generation, that generation seeds this save
     * instead of every file being downloaded again.
     */
    resolveIndexedStorageId: suspend (threadId: String, boardId: String) -> String? = { _, _ -> null },
    /** Originals and videos only on an unmetered network; thumbnails always. */
    allowsFullMediaDownloads: () -> Boolean = AutoSaveNetworkPolicy::allowsFullMediaDownloads
): ThreadAutoSaveRunnerCallbacks {
    return ThreadAutoSaveRunnerCallbacks(
        saveThread = { config, onInitialSavedThread ->
            val stableStorageId = buildThreadStorageId(config.boardId, config.threadId)
            val seedStorageId = runSuspendCatchingPreservingCancellation {
                resolveIndexedStorageId(config.threadId, config.boardId)
            }.getOrNull()
            // The auto-save size cap must not evict the thread being saved (and shown).
            AutoSaveRetentionRegistry.retain(config.threadId, config.boardId) {
                ThreadStorageLockRegistry.withStorageLock(
                    buildThreadStorageLockKey(
                        storageId = stableStorageId,
                        baseDirectory = AUTO_SAVE_DIRECTORY
                    )
                ) {
                    withSeedStorageLock(seedStorageId, stableStorageId) {
                        saveService.saveThread(
                            threadId = config.threadId,
                            boardId = config.boardId,
                            boardName = config.boardName,
                            boardUrl = config.boardUrl,
                            title = config.title,
                            expiresAtLabel = config.expiresAtLabel,
                            posts = config.posts,
                            isTruncated = config.isTruncated,
                            truncationReason = config.truncationReason,
                            baseDirectory = AUTO_SAVE_DIRECTORY,
                            writeMetadata = true,
                            rawHtmlOptions = RawHtmlSaveOptions(enable = false),
                            limits = ThreadSaveLimits(
                                maxMediaItems = THREAD_AUTO_SAVE_MAX_MEDIA_ITEMS,
                                maxSaveDurationMs = THREAD_AUTO_SAVE_MAX_DURATION_MS,
                                maxParallelDownloads = THREAD_AUTO_SAVE_MAX_PARALLEL_DOWNLOADS,
                                mediaDownloadStartDelayMs = THREAD_AUTO_SAVE_MEDIA_START_DELAY_MS,
                                downloadFullMedia = allowsFullMediaDownloads()
                            ),
                            storageOptions = ThreadSaveStorageOptions(
                                storageIdOverride = stableStorageId,
                                clearExistingOutput = false,
                                reuseExistingMedia = true,
                                pruneUnreferencedExistingMedia = true,
                                seedFromStorageId = seedStorageId
                            ),
                            writeInitialMetadataBeforeMedia = false,
                            onInitialSavedThread = onInitialSavedThread
                        )
                    }
                }
            }
        }
    )
}

internal suspend fun performThreadAutoSave(
    config: ThreadAutoSaveRunnerConfig,
    callbacks: ThreadAutoSaveRunnerCallbacks,
    onInitialSavedThread: suspend (SavedThread) -> Unit = {}
): ThreadAutoSaveRunResult {
    val saveResult = try {
        callbacks.saveThread(config, onInitialSavedThread)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
    return ThreadAutoSaveRunResult(
        completionState = resolveThreadAutoSaveCompletionState(
            threadId = config.threadId,
            saveResult = saveResult,
            previousTimestampMillis = config.previousTimestampMillis,
            attemptStartedAtMillis = config.attemptStartedAtMillis,
            completionTimestampMillis = config.completionTimestampMillis
        )
    )
}
