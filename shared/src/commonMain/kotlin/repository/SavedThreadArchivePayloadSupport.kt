package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveFile
import com.valoser.futacha.shared.model.HistoryArchiveFileKind
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry

data class SavedThreadArchivePayloadPlan(
    val archiveEntry: HistoryArchiveEntry,
    val sourceFiles: List<SavedThreadArchiveSourceFile>
)

data class SavedThreadArchiveSourceFile(
    val sourceRelativePath: String,
    val archiveRelativePath: String,
    val sizeBytes: Long,
    val kind: HistoryArchiveFileKind
)

suspend fun SavedThreadRepository.planHistoryArchivePayload(
    historyEntry: ThreadHistoryEntry,
    archiveId: String,
    snapshotId: String
): Result<SavedThreadArchivePayloadPlan> = runCatching {
    val metadata = loadThreadMetadata(historyEntry.threadId, historyEntry.boardId).getOrNull()
        ?: return@runCatching historyOnlyArchivePayloadPlan(
            historyEntry = historyEntry,
            snapshotId = snapshotId
        )
    val savedThread = findSavedThreadForArchive(metadata)
    val storageId = metadata.storageId
        ?.takeIf { it.isNotBlank() }
        ?: savedThread?.let(::resolveSavedThreadStorageId)
        ?: resolveSavedThreadStorageId(metadata.threadId, metadata.boardId)
    val archiveRoot = "threads/${archiveId.trim()}/${snapshotId.trim()}"
    val sourceFileResult = buildSavedThreadArchiveSourceFiles(
        metadata = metadata,
        storageId = storageId,
        archiveRoot = archiveRoot
    )
    val sourceFiles = sourceFileResult.files
    val payloadStatus = when {
        sourceFiles.isEmpty() -> HistoryArchivePayloadStatus.HISTORY_ONLY
        sourceFileResult.missingExpectedFileCount > 0 -> HistoryArchivePayloadStatus.PARTIAL
        else -> HistoryArchivePayloadStatus.FULL
    }
    SavedThreadArchivePayloadPlan(
        archiveEntry = HistoryArchiveEntry(
            snapshotId = snapshotId,
            historyEntry = historyEntry.copy(hasAutoSave = true),
            savedThread = savedThread,
            metadataPath = "$archiveRoot/metadata.json",
            htmlPath = metadata.rawHtmlPath?.takeIf { it.isNotBlank() }?.let { "$archiveRoot/$it" },
            payloadFiles = sourceFiles.map { source ->
                HistoryArchiveFile(
                    relativePath = source.archiveRelativePath,
                    sizeBytes = source.sizeBytes,
                    kind = source.kind
                )
            },
            payloadStatus = payloadStatus
        ),
        sourceFiles = sourceFiles
    )
}

private fun historyOnlyArchivePayloadPlan(
    historyEntry: ThreadHistoryEntry,
    snapshotId: String
): SavedThreadArchivePayloadPlan {
    return SavedThreadArchivePayloadPlan(
        archiveEntry = HistoryArchiveEntry(
            snapshotId = snapshotId,
            historyEntry = historyEntry,
            payloadStatus = HistoryArchivePayloadStatus.HISTORY_ONLY
        ),
        sourceFiles = emptyList()
    )
}

private suspend fun SavedThreadRepository.findSavedThreadForArchive(
    metadata: SavedThreadMetadata
): SavedThread? {
    return getAllThreads()
        .asSequence()
        .filter { thread -> isSameSavedThreadIdentity(thread, metadata.threadId, metadata.boardId) }
        .sortedByDescending { it.savedAt }
        .firstOrNull()
}

private suspend fun SavedThreadRepository.buildSavedThreadArchiveSourceFiles(
    metadata: SavedThreadMetadata,
    storageId: String,
    archiveRoot: String
): SavedThreadArchiveSourceFileResult {
    val files = linkedMapOf<String, HistoryArchiveFileKind>()
    files["metadata.json"] = HistoryArchiveFileKind.METADATA
    metadata.rawHtmlPath
        ?.takeIf { it.isNotBlank() }
        ?.let { files[it] = HistoryArchiveFileKind.HTML }
    metadata.posts.forEach { post ->
        post.localThumbnailPath
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it !in files) files[it] = HistoryArchiveFileKind.THUMBNAIL }
        post.localImagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it !in files) files[it] = HistoryArchiveFileKind.IMAGE }
        post.localVideoPath
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it !in files) files[it] = HistoryArchiveFileKind.VIDEO }
    }
    var missingExpectedFileCount = 0
    val sourceFiles = files.mapNotNull { (relativePath, kind) ->
        val sourceRelativePath = "$storageId/$relativePath"
        if (!existsAt(sourceRelativePath)) {
            missingExpectedFileCount += 1
            return@mapNotNull null
        }
        SavedThreadArchiveSourceFile(
            sourceRelativePath = sourceRelativePath,
            archiveRelativePath = "$archiveRoot/$relativePath",
            sizeBytes = getFileSizeAt(sourceRelativePath),
            kind = kind
        )
    }
    return SavedThreadArchiveSourceFileResult(
        files = sourceFiles,
        missingExpectedFileCount = missingExpectedFileCount
    )
}

private data class SavedThreadArchiveSourceFileResult(
    val files: List<SavedThreadArchiveSourceFile>,
    val missingExpectedFileCount: Int
)
