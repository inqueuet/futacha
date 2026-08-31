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
import kotlin.time.Clock

const val IMPORTED_HISTORY_DIRECTORY = "imported_threads"
internal const val MAX_HISTORY_ARCHIVE_MANIFEST_BYTES = 4L * 1024L * 1024L
internal const val MAX_HISTORY_ARCHIVE_ENTRIES = 2_000
internal const val MAX_HISTORY_ARCHIVE_FILES_PER_ENTRY = 10_000
internal const val MAX_HISTORY_ARCHIVE_TOTAL_FILES = 50_000L
private const val MAX_HISTORY_ARCHIVE_METADATA_BYTES = 8L * 1024L * 1024L
internal const val MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES = 32L * 1024L * 1024L
internal const val MAX_HISTORY_ARCHIVE_TOTAL_PAYLOAD_BYTES = 4L * 1024L * 1024L * 1024L
private const val MAX_HISTORY_ARCHIVE_RELATIVE_PATH_LENGTH = 4_096

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
        val importStartedAtMillis = Clock.System.now().toEpochMilliseconds()
        val archiveDirectory = request.archiveDirectory.trim().trimEnd('/')
        val manifestPath = "$archiveDirectory/manifest.json"
        val manifestSize = fileSystem.getFileSize(manifestPath)
        require(manifestSize in 0L..MAX_HISTORY_ARCHIVE_MANIFEST_BYTES) {
            "History archive manifest is too large"
        }
        val manifestPayload = fileSystem.readString(manifestPath).getOrThrow()
        require(manifestPayload.encodeToByteArray().size.toLong() <= MAX_HISTORY_ARCHIVE_MANIFEST_BYTES) {
            "History archive manifest grew beyond its permitted size while reading"
        }
        val manifest = json.decodeFromString(
            HistoryArchiveManifest.serializer(),
            manifestPayload
        )
        require(manifest.entries.size <= MAX_HISTORY_ARCHIVE_ENTRIES) {
            "History archive contains too many entries"
        }
        require(manifest.entryCount == manifest.entries.size) {
            "History archive entry count is inconsistent"
        }
        require(manifest.entries.mapTo(hashSetOf()) { it.snapshotId }.size == manifest.entries.size) {
            "History archive contains duplicate snapshots"
        }
        require(manifest.entries.all { it.payloadFiles.size <= MAX_HISTORY_ARCHIVE_FILES_PER_ENTRY }) {
            "History archive entry contains too many files"
        }
        require(manifest.entries.all { entry ->
            entry.payloadFiles.mapTo(hashSetOf()) { it.relativePath }.size == entry.payloadFiles.size
        }) {
            "History archive entry contains duplicate files"
        }
        require(manifest.entries.all(::hasSafeHistoryArchivePaths)) {
            "History archive contains an invalid payload path"
        }
        require(manifest.entries.sumOf { it.payloadFiles.size.toLong() } <= MAX_HISTORY_ARCHIVE_TOTAL_FILES) {
            "History archive contains too many files"
        }
        val declaredPayloadBytes = manifest.entries
            .asSequence()
            .flatMap { it.payloadFiles.asSequence() }
            .fold(0L) { total, file ->
                require(file.sizeBytes in 0L..MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES) {
                    "History archive contains an invalid file size"
                }
                safeHistoryArchiveExportSizeAdd(total, file.sizeBytes)
            }
        require(declaredPayloadBytes <= MAX_HISTORY_ARCHIVE_TOTAL_PAYLOAD_BYTES) {
            "History archive payload is too large"
        }
        require(manifest.totalPayloadBytes == 0L || manifest.totalPayloadBytes == declaredPayloadBytes) {
            "History archive payload size is inconsistent"
        }
        val selectedEntries = selectHistoryArchiveEntries(
            manifest = manifest,
            selectedSnapshotIds = request.selectedSnapshotIds
        )
        require(
            selectedEntries
                .filter { !it.metadataPath.isNullOrBlank() }
                .map { buildImportedHistoryStorageId(manifest.archiveId, it.snapshotId) }
                .distinct()
                .size == selectedEntries.count { !it.metadataPath.isNullOrBlank() }
        ) {
            "History archive snapshots resolve to duplicate storage locations"
        }
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
                importStartedAtMillis = importStartedAtMillis,
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

private fun hasSafeHistoryArchivePaths(entry: HistoryArchiveEntry): Boolean {
    val metadataPath = entry.metadataPath
    if (metadataPath != null) {
        if (!isSafeHistoryArchiveRelativePath(metadataPath)) return false
        if (entry.payloadFiles.none {
                it.kind == HistoryArchiveFileKind.METADATA && it.relativePath == metadataPath
            }
        ) return false
    }
    return entry.payloadFiles.all { isSafeHistoryArchiveRelativePath(it.relativePath) }
}

internal fun isSafeHistoryArchiveRelativePath(path: String): Boolean {
    if (path.isBlank() || path != path.trim() || path.length > MAX_HISTORY_ARCHIVE_RELATIVE_PATH_LENGTH) return false
    if (path.startsWith('/') || path.startsWith('\\') || '\u0000' in path || '\\' in path) return false
    return path.split('/').all { segment ->
        segment.isNotEmpty() && segment != "." && segment != ".."
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
    importStartedAtMillis: Long,
    json: Json
): RestoredHistoryArchivePayloadResult {
    val metadataPath = entry.metadataPath?.takeIf { it.isNotBlank() }
        ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    val fullMetadataPath = "$archiveDirectory/$metadataPath"
    val declaredMetadataSize = entry.payloadFiles
        .firstOrNull { it.kind == HistoryArchiveFileKind.METADATA && it.relativePath == metadataPath }
        ?.sizeBytes
        ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    val metadataSize = try {
        fileSystem.getFileSize(fullMetadataPath)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
    if (
        metadataSize == null ||
        metadataSize !in 0L..MAX_HISTORY_ARCHIVE_METADATA_BYTES ||
        metadataSize != declaredMetadataSize
    ) {
        return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    }
    val metadataPayload = fileSystem.readString(fullMetadataPath).getOrNull()
        ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    val metadataPayloadSize = metadataPayload.encodeToByteArray().size.toLong()
    if (
        metadataPayloadSize > MAX_HISTORY_ARCHIVE_METADATA_BYTES ||
        metadataPayloadSize != declaredMetadataSize
    ) {
        return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
    }
    val sourceMetadata = runCatching {
        requireSavedThreadMetadataWithinLimits(
            json.decodeFromString(SavedThreadMetadata.serializer(), metadataPayload)
        )
    }.getOrNull() ?: return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)

    val destinationStorageId = buildImportedHistoryStorageId(manifest.archiveId, entry.snapshotId)
    val payloadRoot = metadataPath.substringBeforeLast('/', "")
    val nonMetadataFiles = entry.payloadFiles.filter { it.kind != HistoryArchiveFileKind.METADATA }
    var failedCopyCount = 0
    var copiedSize = 0L
    val copiedRelativePaths = mutableSetOf<String>()
    nonMetadataFiles.forEach { payloadFile ->
        coroutineContext.ensureActive()
        val relativeInsideSnapshot = payloadFile.relativePath.removeArchivePayloadRoot(payloadRoot)
            ?: run {
                failedCopyCount += 1
                return@forEach
            }
        val sourcePath = "$archiveDirectory/${payloadFile.relativePath}"
        val actualSize = try {
            fileSystem.getFileSize(sourcePath)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (
            actualSize == null || actualSize < 0L ||
            actualSize > MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES ||
            actualSize != payloadFile.sizeBytes
        ) {
            failedCopyCount += 1
            return@forEach
        }
        val bytes = fileSystem.readBytes(sourcePath)
            .getOrElse {
                failedCopyCount += 1
                return@forEach
            }
        if (
            bytes.size.toLong() > MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES ||
            bytes.size.toLong() != payloadFile.sizeBytes
        ) {
            failedCopyCount += 1
            return@forEach
        }
        destinationRepository.writeBytesAt("$destinationStorageId/$relativeInsideSnapshot", bytes)
            .getOrElse {
                failedCopyCount += 1
                return@forEach
            }
        copiedRelativePaths += relativeInsideSnapshot
        copiedSize = safeArchiveImportedSizeAdd(copiedSize, bytes.size.toLong())
    }

    val sanitizedMetadata = sanitizeImportedMetadataLocalPaths(sourceMetadata, copiedRelativePaths)
    val updatedMetadata = sanitizedMetadata.copy(
        storageId = destinationStorageId,
        totalSize = copiedSize,
        version = sourceMetadata.version
    )
    val updatedMetadataPayload = json.encodeToString(SavedThreadMetadata.serializer(), updatedMetadata)
    destinationRepository.writeStringAt("$destinationStorageId/metadata.json", updatedMetadataPayload)
        .getOrElse {
            return RestoredHistoryArchivePayloadResult(hasRestoredPayload = false, isPartial = false)
        }
    val totalSize = safeArchiveImportedSizeAdd(
        copiedSize,
        updatedMetadataPayload.encodeToByteArray().size.toLong()
    )
    destinationRepository.addThreadSnapshotToIndex(
        buildImportedSavedThread(
            metadata = updatedMetadata.copy(totalSize = totalSize),
            storageId = destinationStorageId,
            totalSize = totalSize,
            isPartial = failedCopyCount > 0 || entry.payloadStatus == HistoryArchivePayloadStatus.PARTIAL
        ),
        mutationStartedAtMillis = importStartedAtMillis
    ).getOrThrow()
    return RestoredHistoryArchivePayloadResult(
        hasRestoredPayload = true,
        isPartial = failedCopyCount > 0 || entry.payloadStatus == HistoryArchivePayloadStatus.PARTIAL
    )
}

internal fun sanitizeImportedMetadataLocalPaths(
    metadata: SavedThreadMetadata,
    copiedRelativePaths: Set<String>
): SavedThreadMetadata {
    fun retained(path: String?): String? {
        return path?.takeIf { candidate ->
            isSafeHistoryArchiveRelativePath(candidate) && candidate in copiedRelativePaths
        }
    }
    return metadata.copy(
        rawHtmlPath = retained(metadata.rawHtmlPath),
        posts = metadata.posts.map { post ->
            post.copy(
                localImagePath = retained(post.localImagePath),
                localVideoPath = retained(post.localVideoPath),
                localThumbnailPath = retained(post.localThumbnailPath)
            )
        }
    )
}

private fun safeArchiveImportedSizeAdd(current: Long, addition: Long): Long {
    require(addition >= 0L && current <= Long.MAX_VALUE - addition) {
        "History archive payload size overflow"
    }
    return current + addition
}

private fun buildImportedSavedThread(
    metadata: SavedThreadMetadata,
    storageId: String,
    totalSize: Long,
    isPartial: Boolean
): SavedThread {
    return SavedThread(
        threadId = metadata.threadId,
        boardId = metadata.boardId,
        boardName = metadata.boardName,
        title = metadata.title,
        storageId = storageId,
        thumbnailPath = firstLocalThumbnailPath(metadata.posts),
        savedAt = metadata.savedAt,
        postCount = metadata.posts.size,
        imageCount = metadata.posts.count { it.localImagePath != null },
        videoCount = metadata.posts.count { it.localVideoPath != null },
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
