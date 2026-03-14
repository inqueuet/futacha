package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.service.buildLegacyThreadStorageId
import com.valoser.futacha.shared.service.buildThreadStorageId
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
    val droppedDuplicateCount: Int
)

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

    val dedupedByStorageId = linkedMapOf<String, SavedThread>()
    index.threads.forEach { thread ->
        val storageId = resolveSavedThreadStorageId(thread)
        val current = dedupedByStorageId[storageId]
        if (current == null || thread.savedAt > current.savedAt) {
            dedupedByStorageId[storageId] = thread
        }
    }
    val normalizedThreads = dedupedByStorageId.values.toList()
    val recalculatedSize = normalizedThreads.safeSavedThreadTotalSize(onOverflow)
    val needsRepair =
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
        droppedDuplicateCount = index.threads.size - normalizedThreads.size
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
    }
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
    saveSavedThreadIndexUnlocked(buildUpdatedIndexUnlocked(transform))
}
