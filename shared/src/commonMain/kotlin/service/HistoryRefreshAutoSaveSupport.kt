package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    val truncationReason: String?,
    /**
     * Set when this save only continues an indexed partial generation (no new
     * replies): that generation's incomplete media count.
     */
    val continuedIncompleteMediaCount: Int? = null,
    /** The newest indexed generation this save replaces, if any. */
    val previousGeneration: SavedThread? = null
)

/** Time kept after the media phase to write HTML, metadata and the index. */
internal const val HISTORY_AUTO_SAVE_FINALIZE_RESERVE_MILLIS = 20_000L

/**
 * Media budget for a save starting at [nowMillis]: what is left of the
 * per-thread timeout (started at [timeoutStartedAtMillis], before the storage
 * locks were awaited) and of the run's [autoSaveDeadline], after which the job
 * is cancelled, minus the finalize reserve. Lock and permit waits therefore
 * shorten the media phase instead of cutting off the publish of the partial
 * generation.
 */
internal fun historyAutoSaveMediaBudgetMillis(
    timeoutStartedAtMillis: Long,
    nowMillis: Long,
    threadTimeoutMillis: Long,
    autoSaveDeadline: Long?
): Long {
    val threadEnd = timeoutStartedAtMillis + threadTimeoutMillis
    val end = autoSaveDeadline?.let { minOf(it, threadEnd) } ?: threadEnd
    return (end - nowMillis - HISTORY_AUTO_SAVE_FINALIZE_RESERVE_MILLIS).coerceAtLeast(1_000L)
}

/** A stalled continuation is tried again after this, e.g. once the network allows originals. */
internal const val AUTO_SAVE_CONTINUATION_RETRY_MILLIS = 24L * 60L * 60L * 1_000L

/**
 * G5: a generation published when the media budget ran out is continued by
 * the next refresh even without new replies. A generation marked stalled
 * ([SavedThread.autoSaveContinuationStalledAtMillis]: media that keeps failing,
 * a thumbnails-only network, the media-count cap) is not continued again until
 * [AUTO_SAVE_CONTINUATION_RETRY_MILLIS] passed, so such a thread is not re-saved
 * on every refresh. The mark is in the index, so it holds in every Worker/BGTask
 * process (G4-4).
 */
internal fun shouldContinueAutoSaveGeneration(generation: SavedThread, nowMillis: Long): Boolean {
    if (generation.incompleteMediaCount <= 0) return false
    val stalledAt = generation.autoSaveContinuationStalledAtMillis
    return stalledAt <= 0L || hasEpochIntervalElapsed(nowMillis, stalledAt, AUTO_SAVE_CONTINUATION_RETRY_MILLIS)
}

/**
 * Stall mark of a generation about to be indexed: set when a continuation left at
 * least as much media missing as it started with. A save for new replies keeps the
 * mark of a stalled [previous] generation unless it left less media missing, so a
 * stalled thread is saved once per new reply instead of twice (G4-4).
 */
internal fun resolveAutoSaveContinuationStalledAtMillis(
    previous: SavedThread?,
    continuedIncompleteMediaCount: Int?,
    resultIncompleteMediaCount: Int,
    nowMillis: Long
): Long {
    if (resultIncompleteMediaCount <= 0) return 0L
    if (continuedIncompleteMediaCount != null) {
        return if (resultIncompleteMediaCount >= continuedIncompleteMediaCount) nowMillis else 0L
    }
    if (previous == null) return 0L
    val previousStalledAt = previous.autoSaveContinuationStalledAtMillis
    if (previousStalledAt <= 0L || resultIncompleteMediaCount < previous.incompleteMediaCount) return 0L
    return if (hasEpochIntervalElapsed(nowMillis, previousStalledAt, AUTO_SAVE_CONTINUATION_RETRY_MILLIS)) {
        nowMillis
    } else {
        previousStalledAt
    }
}

/**
 * Continuation saves of one run that hold an auto-save slot and have not started
 * to commit. A save for new replies that finds no free slot takes the slot of the
 * newest of them and cancels it, so continuations never keep threads with new
 * replies from being saved (H4-3); the cancelled continuation is retried later.
 */
internal class HistoryRefreshContinuationSlots {
    private val mutex = Mutex()
    private val preemptible = ArrayList<Job>()
    private val preempted = HashSet<Job>()

    suspend fun register(job: Job) = mutex.withLock { preemptible += job }

    /** Removes [job] before its commit; false when its slot was already taken. */
    suspend fun claimCommit(job: Job): Boolean = mutex.withLock { preemptible.remove(job) || job !in preempted }

    suspend fun wasPreempted(job: Job): Boolean = mutex.withLock { job in preempted }

    suspend fun release(job: Job) = mutex.withLock {
        preemptible.remove(job)
        preempted.remove(job)
    }

    /** Takes the slot of the newest uncommitted continuation, cancelling it. */
    suspend fun preempt(): Boolean {
        val victim = mutex.withLock {
            preemptible.removeLastOrNull()?.also { preempted += it }
        } ?: return false
        victim.cancel()
        return true
    }
}

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
    private val tag: String,
    /** Originals and videos only on an unmetered network; thumbnails always. */
    private val allowsFullMediaDownloads: () -> Boolean = AutoSaveNetworkPolicy::allowsFullMediaDownloads
) {
    private val continuationSlots = HistoryRefreshContinuationSlots()

    fun launch(plan: HistoryRefreshAutoSavePlan) {
        val autoSaveService = autoSaveService ?: return
        val autoSavedThreadRepository = autoSavedThreadRepository ?: return
        autoSaveScope.launch {
            val entry = plan.resolvedEntry.entry
            val board = plan.resolvedEntry.board
            val baseUrl = plan.resolvedEntry.baseUrl
            val isContinuation = plan.continuedIncompleteMediaCount != null
            val job = coroutineContext.job
            val nowForBudgetCheck = Clock.System.now().toEpochMilliseconds()
            var autoSaveSlotReserved = false
            val allowAutoSave = (
                stats.tryReserveAutoSaveSlot(
                    nowMillis = nowForBudgetCheck,
                    autoSaveDeadline = autoSaveDeadline,
                    maxAutoSavesPerRefresh = maxAutoSavesPerRefresh
                ) || (
                    !isContinuation &&
                        (autoSaveDeadline == null || nowForBudgetCheck <= autoSaveDeadline) &&
                        continuationSlots.preempt().also { taken ->
                            if (taken) Logger.d(tag, "Took a continuation's auto-save slot for ${entry.threadId}")
                        }
                    )
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
            if (isContinuation) continuationSlots.register(job)
            val resolvedBoardId = resolveHistoryEntryBoardId(entry, board, baseUrl)
            val stagingStorageId = buildThreadSaveGenerationStorageId(
                boardId = resolvedBoardId,
                threadId = entry.threadId,
                savedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                nonce = Random.nextInt(0, Int.MAX_VALUE).toString(36)
            )
            try {
                val stableStorageId = buildThreadStorageId(resolvedBoardId, entry.threadId)
                val saved = autoSaveSemaphore.withPermit {
                    if (autoSaveDeadline != null && Clock.System.now().toEpochMilliseconds() > autoSaveDeadline) {
                        Logger.w(tag, "Auto-save budget exceeded while waiting permit, skipping ${entry.threadId}")
                        // A taken slot now belongs to the save that took it.
                        if (autoSaveSlotReserved && !(isContinuation && continuationSlots.wasPreempted(job))) {
                            stats.releaseAutoSaveSlotIfReserved()
                            autoSaveSlotReserved = false
                        }
                        return@withPermit null
                    }
                    // Keeps the size cap from evicting this thread's seed while it is saved.
                    AutoSaveRetentionRegistry.retain(entry.threadId, resolvedBoardId) {
                        val timeoutStartedAt = Clock.System.now().toEpochMilliseconds()
                        withTimeoutOrNull(autoSaveThreadTimeoutMillis) {
                            ThreadStorageLockRegistry.withStorageLock(
                                buildThreadStorageLockKey(
                                    storageId = stableStorageId,
                                    baseDirectory = AUTO_SAVE_DIRECTORY
                                )
                            ) {
                                // The previous generation (or the screen's stable save) seeds this
                                // one, so unchanged media is linked instead of downloaded again.
                                withIndexedSeedStorageLock(
                                    resolveSeedStorageId = {
                                        autoSavedThreadRepository.resolveIndexedStorageId(entry.threadId, resolvedBoardId)
                                    },
                                    stableStorageId = stableStorageId
                                ) { seedStorageId ->
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
                                        limits = ThreadSaveLimits(
                                            downloadFullMedia = allowsFullMediaDownloads(),
                                            mediaDownloadBudgetMs = historyAutoSaveMediaBudgetMillis(
                                                timeoutStartedAtMillis = timeoutStartedAt,
                                                nowMillis = Clock.System.now().toEpochMilliseconds(),
                                                threadTimeoutMillis = autoSaveThreadTimeoutMillis,
                                                autoSaveDeadline = autoSaveDeadline
                                            )
                                        ),
                                        storageOptions = ThreadSaveStorageOptions(
                                            storageIdOverride = stagingStorageId,
                                            clearExistingOutput = true,
                                            reuseExistingMedia = false,
                                            pruneUnreferencedExistingMedia = false,
                                            seedFromStorageId = seedStorageId
                                        )
                                    ).getOrThrow()
                                }
                            }
                        }
                    }
                }
                if (saved != null && isContinuation && !continuationSlots.claimCommit(job)) {
                    // Its slot went to a save for new replies; nothing was indexed.
                    withContext(NonCancellable) {
                        cleanupRejectedHistoryAutoSave(fileSystem, saved.storageId ?: stagingStorageId, tag)
                    }
                    return@launch
                }
                if (saved != null) {
                    val savedStorageId = saved.storageId ?: stagingStorageId
                    val indexed = saved.copy(
                        autoSaveContinuationStalledAtMillis = resolveAutoSaveContinuationStalledAtMillis(
                            previous = plan.previousGeneration,
                            continuedIncompleteMediaCount = plan.continuedIncompleteMediaCount,
                            resultIncompleteMediaCount = saved.incompleteMediaCount,
                            nowMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    val committed = try {
                        commitGate {
                            autoSavedThreadRepository.addThreadToIndex(indexed).getOrThrow()
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
                if (isContinuation) {
                    withContext(NonCancellable) {
                        // Cancelled before its commit for a save with new replies: drop what it wrote.
                        if (continuationSlots.wasPreempted(job)) {
                            cleanupRejectedHistoryAutoSave(fileSystem, stagingStorageId, tag)
                        }
                    }
                }
                throw e
            } catch (error: Throwable) {
                Logger.e(tag, "Auto-save during background refresh failed for ${entry.threadId}", error)
            } finally {
                if (isContinuation) withContext(NonCancellable) { continuationSlots.release(job) }
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

private const val SEED_STORAGE_RESOLVE_ATTEMPTS = 3

private class SeedLockedResult<T>(val value: T)

/**
 * Resolves the seed generation only once the caller holds the stable lock, takes
 * the seed's lock and checks the seed is still the indexed one: a generation
 * replaced and deleted between resolving and locking would leave the save without
 * its seed, so all of its media would be downloaded again. A seed replaced
 * meanwhile is resolved again; after [SEED_STORAGE_RESOLVE_ATTEMPTS] the save
 * runs unseeded. Lock order is as in [withSeedStorageLock].
 */
internal suspend fun <T> withIndexedSeedStorageLock(
    resolveSeedStorageId: suspend () -> String?,
    stableStorageId: String,
    block: suspend (seedStorageId: String?) -> T
): T {
    suspend fun resolve(): String? =
        runSuspendCatchingPreservingCancellation { resolveSeedStorageId() }.getOrNull()
    repeat(SEED_STORAGE_RESOLVE_ATTEMPTS) {
        val seedStorageId = resolve()
        if (seedStorageId == null || seedStorageId == stableStorageId) return block(seedStorageId)
        val locked = ThreadStorageLockRegistry.withStorageLock(
            buildThreadStorageLockKey(storageId = seedStorageId, baseDirectory = AUTO_SAVE_DIRECTORY)
        ) {
            if (resolve() == seedStorageId) SeedLockedResult(block(seedStorageId)) else null
        }
        if (locked != null) return locked.value
    }
    return block(null)
}

/**
 * Holds the seed generation's lock while its files are linked, so its cleanup
 * cannot delete them midway. Locks are always taken stable first, then seed;
 * cleanup takes one lock at a time, so this cannot deadlock. The stable lock
 * is already held and the registry is not reentrant, so it is not taken again.
 */
internal suspend fun <T> withSeedStorageLock(seedStorageId: String?, stableStorageId: String, block: suspend () -> T): T {
    if (seedStorageId == null || seedStorageId == stableStorageId) return block()
    return ThreadStorageLockRegistry.withStorageLock(
        buildThreadStorageLockKey(storageId = seedStorageId, baseDirectory = AUTO_SAVE_DIRECTORY),
        block
    )
}
