package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveFile
import com.valoser.futacha.shared.model.HistoryArchiveFileKind
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

const val IMPORTED_HISTORY_DIRECTORY = "imported_threads"

data class HistoryArchiveImportRequest(
    val archiveDirectory: String,
    val selectedSnapshotIds: Set<String>? = null
)

data class HistoryArchiveImportResult(
    val manifest: HistoryArchiveManifest,
    val importedHistoryEntries: List<ThreadHistoryEntry>,
    val restoredPayloadCount: Int,
    val historyOnlyCount: Int,
    val partialPayloadCount: Int,
    val skippedEntryCount: Int
)

suspend fun importHistoryArchive(
    fileSystem: FileSystem,
    destinationRepository: SavedThreadRepository,
    request: HistoryArchiveImportRequest,
    json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
): Result<HistoryArchiveImportResult> {
    return try {
        coroutineContext.ensureActive()
        val archiveDirectory = request.archiveDirectory.trim().trimEnd('/')
        val manifestPayload = fileSystem.readString("$archiveDirectory/manifest.json").getOrThrow()
        val manifest = json.decodeFromString(
            HistoryArchiveManifest.serializer(),
            manifestPayload
        )
        val selectedEntries = selectHistoryArchiveEntries(
            manifest = manifest,
            selectedSnapshotIds = request.selectedSnapshotIds
        )
        val importedHistory = mutableListOf<ThreadHistoryEntry>()
        var restoredPayloadCount = 0
        var historyOnlyCount = 0
        var partialPayloadCount = 0
        var skippedEntryCount = manifest.entries.size - selectedEntries.size

        selectedEntries.forEach { entry ->
            coroutineContext.ensureActive()
            val payloadResult = restoreHistoryArchiveEntryPayload(
                fileSystem = fileSystem,
                destinationRepository = destinationRepository,
                archiveDirectory = archiveDirectory,
                manifest = manifest,
                entry = entry,
                json = json
            )
            importedHistory += entry.historyEntry.copy(hasAutoSave = payloadResult.hasRestoredPayload)
            when {
                !payloadResult.hasRestoredPayload -> historyOnlyCount += 1
                payloadResult.isPartial -> partialPayloadCount += 1
                else -> restoredPayloadCount += 1
            }
        }

        Result.success(
            HistoryArchiveImportResult(
                manifest = manifest,
                importedHistoryEntries = importedHistory,
                restoredPayloadCount = restoredPayloadCount,
                historyOnlyCount = historyOnlyCount,
                partialPayloadCount = partialPayloadCount,
                skippedEntryCount = skippedEntryCount
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

private data class RestoredHistoryArchivePayloadResult(
    val hasRestoredPayload: Boolean,
    val isPartial: Boolean
)

private fun selectHistoryArchiveEntries(
    manifest: HistoryArchiveManifest,
    selectedSnapshotIds: Set<String>?
): List<HistoryArchiveEntry> {
    return if (selectedSnapshotIds == null) {
        manifest.entries
    } else {
        manifest.entries.filter { it.snapshotId in selectedSnapshotIds }
    }
}

private suspend fun restoreHistoryArchiveEntryPayload(
    fileSystem: FileSystem,
    destinationRepository: SavedThreadRepository,
    archiveDirectory: String,
    manifest: HistoryArchiveManifest,
    entry: HistoryArchiveEntry,
    json: Json
): RestoredHistoryArchivePayloadResult {
    val metadataPath = entry.metadataPath?.takeIf { it.isNotBlank() }
        ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    val metadataPayload = fileSystem.readString("$archiveDirectory/$metadataPath").getOrNull()
        ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    val sourceMetadata = runCatching {
        json.decodeFromString(SavedThreadMetadata.serializer(), metadataPayload)
    }.getOrNull() ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)

    val destinationStorageId = buildImportedHistoryStorageId(manifest.archiveId, entry.snapshotId)
    val payloadRoot = metadataPath.substringBeforeLast('/', "")
    val nonMetadataFiles = entry.payloadFiles.filter { it.kind != HistoryArchiveFileKind.METADATA }
    var failedCopyCount = 0
    var copiedSize = 0L
    nonMetadataFiles.forEach { payloadFile ->
        coroutineContext.ensureActive()
        val relativeInsideSnapshot = payloadFile.relativePath.removeArchivePayloadRoot(payloadRoot)
            ?: run {
                failedCopyCount += 1
                return@forEach
            }
        val bytes = fileSystem.readBytes("$archiveDirectory/${payloadFile.relativePath}")
            .getOrElse {
                failedCopyCount += 1
                return@forEach
            }
        destinationRepository.writeBytesAt("$destinationStorageId/$relativeInsideSnapshot", bytes)
            .getOrElse {
                failedCopyCount += 1
                return@forEach
            }
        copiedSize += bytes.size.toLong()
    }

    val updatedMetadata = sourceMetadata.copy(
        storageId = destinationStorageId,
        totalSize = copiedSize,
        version = sourceMetadata.version
    )
    val updatedMetadataPayload = json.encodeToString(SavedThreadMetadata.serializer(), updatedMetadata)
    destinationRepository.writeStringAt("$destinationStorageId/metadata.json", updatedMetadataPayload)
        .getOrElse {
            return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
        }
    val totalSize = copiedSize + updatedMetadataPayload.encodeToByteArray().size
    destinationRepository.addThreadSnapshotToIndex(
        buildImportedSavedThread(
            entry = entry,
            metadata = updatedMetadata.copy(totalSize = totalSize),
            storageId = destinationStorageId,
            totalSize = totalSize,
            isPartial = failedCopyCount > 0 || entry.payloadStatus == HistoryArchivePayloadStatus.PARTIAL
        )
    ).getOrThrow()
    return RestoredHistoryArchivePayloadResult(
        hasRestoredPayload = true,
        isPartial = failedCopyCount > 0 || entry.payloadStatus == HistoryArchivePayloadStatus.PARTIAL
    )
}

private fun buildImportedSavedThread(
    entry: HistoryArchiveEntry,
    metadata: SavedThreadMetadata,
    storageId: String,
    totalSize: Long,
    isPartial: Boolean
): SavedThread {
    val source = entry.savedThread
    return SavedThread(
        threadId = metadata.threadId,
        boardId = metadata.boardId,
        boardName = metadata.boardName,
        title = metadata.title,
        storageId = storageId,
        thumbnailPath = source?.thumbnailPath ?: firstLocalThumbnailPath(metadata.posts),
        savedAt = metadata.savedAt,
        postCount = metadata.posts.size,
        imageCount = source?.imageCount ?: metadata.posts.count { it.localImagePath != null },
        videoCount = source?.videoCount ?: metadata.posts.count { it.localVideoPath != null },
        totalSize = totalSize,
        status = if (isPartial) SaveStatus.PARTIAL else SaveStatus.COMPLETED
    )
}

private fun firstLocalThumbnailPath(posts: List<SavedPost>): String? {
    return posts.firstNotNullOfOrNull { post ->
        post.localThumbnailPath?.takeIf { it.isNotBlank() }
    }
}

private fun String.removeArchivePayloadRoot(payloadRoot: String): String? {
    if (payloadRoot.isBlank()) return this.takeIf { it.isNotBlank() }
    if (this == payloadRoot) return null
    return removePrefix("$payloadRoot/")
        .takeIf { it != this && it.isNotBlank() }
}

private fun buildImportedHistoryStorageId(archiveId: String, snapshotId: String): String {
    val archiveSegment = sanitizeImportedHistoryPathSegment(archiveId)
    val snapshotSegment = sanitizeImportedHistoryPathSegment(snapshotId)
    return "archive_${archiveSegment}__${snapshotSegment}".take(180)
}

private fun sanitizeImportedHistoryPathSegment(value: String): String {
    return value
        .trim()
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .trim('_')
        .ifBlank { "snapshot" }
        .take(80)
}
