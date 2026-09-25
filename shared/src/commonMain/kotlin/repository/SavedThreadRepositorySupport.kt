package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.service.buildLegacyThreadStorageId
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlin.time.Clock

internal fun List<SavedThread>.safeSavedThreadTotalSize(
    onOverflow: () -> Unit = {}
): Long {
    var total = 0L
    for (thread in this) {
        val newTotal = total + thread.totalSize
        if (newTotal < total) {
            onOverflow()
            return Long.MAX_VALUE
        }
        total = newTotal
    }
    return total
}

/**
 * The saves to remove so the rest fit in [maxTotalBytes]: least recently saved first,
 * skipping [isProtected] ones. Uses the sizes recorded in the index; no folder walk.
 */
internal fun selectSavedThreadsOverSizeLimit(
    threads: List<SavedThread>,
    maxTotalBytes: Long,
    isProtected: (SavedThread) -> Boolean
): Set<SavedThread> {
    var total = threads.safeSavedThreadTotalSize()
    if (total <= maxTotalBytes) return emptySet()
    val evicted = linkedSetOf<SavedThread>()
    for (candidate in threads.sortedBy { it.savedAt }) {
        if (total <= maxTotalBytes) break
        if (isProtected(candidate)) continue
        evicted += candidate
        total -= candidate.totalSize.coerceAtLeast(0L)
    }
    return evicted
}

internal fun buildSavedThreadIndex(
    threads: List<SavedThread>,
    nowMillis: Long,
    onOverflow: () -> Unit = {}
): SavedThreadIndex {
    return SavedThreadIndex(
        threads = threads,
        totalSize = threads.safeSavedThreadTotalSize(onOverflow),
        lastUpdated = nowMillis
    )
}

internal fun isSameSavedThreadIdentity(
    thread: SavedThread,
    threadId: String,
    boardId: String?
): Boolean {
    if (thread.threadId != threadId) return false
    val normalizedBoardId = boardId?.trim().orEmpty()
    val candidateBoardId = thread.boardId.trim()
    if (normalizedBoardId.isBlank()) return candidateBoardId.isBlank()
    return candidateBoardId.equals(normalizedBoardId, ignoreCase = true)
}

internal fun resolveSavedThreadStorageId(thread: SavedThread): String {
    return thread.storageId
        ?.takeIf { it.isNotBlank() }
        ?: resolveSavedThreadStorageId(thread.threadId, thread.boardId)
}

internal fun resolveSavedThreadStorageId(threadId: String, boardId: String?): String {
    return buildThreadStorageId(boardId, threadId)
}

internal fun resolveLegacySavedThreadStorageId(threadId: String, boardId: String?): String {
    return buildLegacyThreadStorageId(boardId, threadId)
}

internal data class SavedThreadIndexSanitizeResult(
    val index: SavedThreadIndex,
    val droppedDuplicateCount: Int,
    val droppedInvalidCount: Int = 0
)

private const val MAX_SAVED_THREAD_INDEX_TEXT_CHARS = 8_192
private const val MAX_SAVED_THREAD_STORAGE_ID_CHARS = 255

private fun isValidSavedThreadIndexEntry(thread: SavedThread): Boolean {
    if (thread.totalSize < 0L || thread.postCount < 0 || thread.imageCount < 0 || thread.videoCount < 0) {
        return false
    }
    if (
        thread.threadId.length > MAX_SAVED_THREAD_INDEX_TEXT_CHARS ||
        thread.boardId.length > MAX_SAVED_THREAD_INDEX_TEXT_CHARS ||
        thread.boardName.length > MAX_SAVED_THREAD_INDEX_TEXT_CHARS ||
        thread.title.length > MAX_SAVED_THREAD_INDEX_TEXT_CHARS ||
        thread.thumbnailPath.orEmpty().length > MAX_SAVED_THREAD_INDEX_TEXT_CHARS
    ) return false
    val explicitStorageId = thread.storageId ?: return true
    return explicitStorageId.isNotBlank() &&
        explicitStorageId == explicitStorageId.trim() &&
        explicitStorageId.length <= MAX_SAVED_THREAD_STORAGE_ID_CHARS &&
        explicitStorageId != "." && explicitStorageId != ".." &&
        '/' !in explicitStorageId && '\\' !in explicitStorageId && '\u0000' !in explicitStorageId
}

internal fun sanitizeSavedThreadIndex(
    index: SavedThreadIndex,
    nowMillis: Long,
    onOverflow: () -> Unit = {}
): SavedThreadIndexSanitizeResult {
    if (index.threads.isEmpty()) {
        val normalized = if (index.totalSize != 0L) {
            index.copy(totalSize = 0L, lastUpdated = nowMillis)
        } else {
            index
        }
        return SavedThreadIndexSanitizeResult(
            index = normalized,
            droppedDuplicateCount = 0
        )
    }

    val validThreads = index.threads.filter(::isValidSavedThreadIndexEntry)
    val dedupedByStorageId = linkedMapOf<String, SavedThread>()
    validThreads.forEach { thread ->
        val storageId = resolveSavedThreadStorageId(thread)
        val current = dedupedByStorageId[storageId]
        if (current == null || thread.savedAt > current.savedAt) {
            dedupedByStorageId[storageId] = thread
        }
    }
    val normalizedThreads = dedupedByStorageId.values.toList()
    val recalculatedSize = normalizedThreads.safeSavedThreadTotalSize(onOverflow)
    val needsRepair =
        validThreads.size != index.threads.size ||
        normalizedThreads.size != index.threads.size ||
            recalculatedSize != index.totalSize
    val normalizedIndex = if (needsRepair) {
        SavedThreadIndex(
            threads = normalizedThreads,
            totalSize = recalculatedSize,
            lastUpdated = nowMillis
        )
    } else {
        index
    }
    return SavedThreadIndexSanitizeResult(
        index = normalizedIndex,
        droppedDuplicateCount = validThreads.size - normalizedThreads.size,
        droppedInvalidCount = index.threads.size - validThreads.size
    )
}

internal fun resolveSavedThreadMetadataCandidates(
    threads: List<SavedThread>,
    threadId: String,
    boardId: String?
): List<String> {
    val latestByStorageId = linkedMapOf<String, SavedThread>()
    threads.forEach { thread ->
        if (!isSameSavedThreadIdentity(thread, threadId, boardId)) return@forEach
        val storageId = resolveSavedThreadStorageId(thread)
        val existing = latestByStorageId[storageId]
        if (existing == null || thread.savedAt > existing.savedAt) {
            latestByStorageId[storageId] = thread
        }
    }
    val fromIndex = latestByStorageId.values
        .asSequence()
        .sortedByDescending { it.savedAt }
        .map { thread -> "${resolveSavedThreadStorageId(thread)}/metadata.json" }
        .toList()

    val fallbackCurrent = "${resolveSavedThreadStorageId(threadId, boardId)}/metadata.json"
    val fallbackLegacyStorageId = "${resolveLegacySavedThreadStorageId(threadId, boardId)}/metadata.json"
    val fallbackLegacy = "$threadId/metadata.json"
    return buildList {
        addAll(fromIndex)
        add(fallbackCurrent)
        if (fallbackLegacyStorageId != fallbackCurrent) {
            add(fallbackLegacyStorageId)
        }
        add(fallbackLegacy)
    }.distinct()
}

internal suspend fun SavedThreadRepository.buildUpdatedIndexUnlocked(
    transform: (List<SavedThread>) -> List<SavedThread>
): SavedThreadIndex {
    val currentIndex = readSavedThreadIndexUnlocked()
    return buildSavedThreadIndex(
        threads = transform(currentIndex.threads),
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        onOverflow = ::logTotalSizeOverflow
    )
}

internal suspend fun SavedThreadRepository.mutateIndexThreadsUnlocked(
    transform: (List<SavedThread>) -> List<SavedThread>
) {
    mutateIndexThreadsReturningEvictedUnlocked(transform)
}

/**
 * Applies [transform] and writes the index within its read limits. The auto-save
 * index drops its oldest entries to fit and returns them (the caller removes their
 * storage); any other index refuses the write with [SavedThreadIndexLimitException].
 */
internal suspend fun SavedThreadRepository.mutateIndexThreadsReturningEvictedUnlocked(
    transform: (List<SavedThread>) -> List<SavedThread>
): List<SavedThread> {
    val currentIndex = readSavedThreadIndexUnlocked()
    val updatedThreads = transform(currentIndex.threads)
    val updatedTotalSize = updatedThreads.safeSavedThreadTotalSize(::logTotalSizeOverflow)
    if (updatedThreads == currentIndex.threads && updatedTotalSize == currentIndex.totalSize) {
        // Known to match disk (written or verified earlier in this process): skip
        // re-reading and decoding both copies just to find nothing to repair.
        val cached = cachedIndexEntry?.takeIf { it.index === currentIndex }
        if (cached?.persistedVerified == true) return emptyList()
        if (!isPersistedSavedThreadIndexAlreadyNormalizedUnlocked(currentIndex)) {
            saveSavedThreadIndexUnlocked(
                currentIndex.copy(lastUpdated = Clock.System.now().toEpochMilliseconds())
            )
        } else {
            cached?.persistedVerified = true
        }
        return emptyList()
    }
    return saveSavedThreadIndexWithinLimitsUnlocked(updatedThreads)
}

private suspend fun SavedThreadRepository.saveSavedThreadIndexWithinLimitsUnlocked(
    threads: List<SavedThread>
): List<SavedThread> {
    var kept = threads
    val evicted = mutableListOf<SavedThread>()
    fun evictOldest(count: Int) {
        val oldestIndexes = kept.withIndex()
            .sortedBy { it.value.savedAt }
            .take(count)
            .mapTo(HashSet()) { it.index }
        kept.filterIndexedTo(evicted) { index, _ -> index in oldestIndexes }
        kept = kept.filterIndexed { index, _ -> index !in oldestIndexes }
    }
    if (kept.size > indexEntryLimit && isAutoSaveRepository) {
        evictOldest(kept.size - indexEntryLimit)
    }
    while (true) {
        try {
            saveSavedThreadIndexUnlocked(
                SavedThreadIndex(
                    threads = kept,
                    totalSize = kept.safeSavedThreadTotalSize(::logTotalSizeOverflow),
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
            )
            return evicted
        } catch (error: SavedThreadIndexLimitException) {
            if (!isAutoSaveRepository || kept.size <= 1) throw error
            evictOldest(maxOf(1, kept.size / 10))
        }
    }
}

private suspend fun SavedThreadRepository.isPersistedSavedThreadIndexAlreadyNormalizedUnlocked(
    currentIndex: SavedThreadIndex
): Boolean {
    if (!existsAt(indexRelativePath)) {
        return !existsAt("$indexRelativePath.backup") &&
            currentIndex.threads.isEmpty() &&
            currentIndex.totalSize == 0L
    }
    val primaryIndex = readNormalizedPersistedSavedThreadIndexOrNull(indexRelativePath) ?: return false
    val backupIndex = readNormalizedPersistedSavedThreadIndexOrNull("$indexRelativePath.backup") ?: return false
    return primaryIndex == currentIndex && backupIndex == currentIndex
}

private suspend fun SavedThreadRepository.readNormalizedPersistedSavedThreadIndexOrNull(
    relativePath: String
): SavedThreadIndex? {
    val payload = readStringAt(relativePath).getOrNull() ?: return null
    val persistedIndex = runSuspendCatchingPreservingCancellation {
        withContext(AppDispatchers.parsing) {
            json.decodeFromString<SavedThreadIndex>(payload)
        }
    }.getOrNull() ?: return null
    val sanitized = sanitizeSavedThreadIndex(
        index = persistedIndex,
        nowMillis = persistedIndex.lastUpdated,
        onOverflow = ::logTotalSizeOverflow
    )
    return sanitized.index.takeIf { it == persistedIndex }
}
