package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveFile
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

const val HISTORY_ARCHIVE_DIRECTORY = "history_archives"

data class HistoryArchiveExportRequest(
    val archiveId: String,
    val historyEntries: List<ThreadHistoryEntry>,
    val exportedAtEpochMillis: Long,
    val appVersion: String? = null
)

data class HistoryArchiveExportResult(
    val manifest: HistoryArchiveManifest,
    val archiveDirectory: String,
    val copiedFileCount: Int,
    val historyOnlyCount: Int,
    val partialPayloadCount: Int
)

suspend fun exportHistoryArchive(
    fileSystem: FileSystem,
    sourceRepositories: List<SavedThreadRepository>,
    request: HistoryArchiveExportRequest,
    archiveBaseDirectory: String = HISTORY_ARCHIVE_DIRECTORY,
    json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
): Result<HistoryArchiveExportResult> {
    var incompleteArchiveDirectory: String? = null
    return try {
        coroutineContext.ensureActive()
        require(request.historyEntries.size <= MAX_HISTORY_ARCHIVE_ENTRIES) {
            "History archive contains too many entries"
        }
        val archiveId = normalizeHistoryArchivePathSegment(request.archiveId.ifBlank {
            "archive_${request.exportedAtEpochMillis}"
        })
        val archiveDirectory = "$archiveBaseDirectory/$archiveId"
        require(!fileSystem.exists(archiveDirectory)) {
            "History archive already exists: $archiveId"
        }
        fileSystem.createDirectory(archiveDirectory).getOrThrow()
        incompleteArchiveDirectory = archiveDirectory

        val exportedEntries = mutableListOf<HistoryArchiveEntry>()
        var copiedFileCount = 0
        var plannedFileCount = 0L
        request.historyEntries.forEach { historyEntry ->
            coroutineContext.ensureActive()
            val planned = chooseBestHistoryArchivePayloadPlan(
                sourceRepositories = sourceRepositories,
                historyEntry = historyEntry,
                archiveId = archiveId
            )
            val copiedFiles = mutableListOf<HistoryArchiveFile>()
            var failedCopyCount = 0
            val sourceFiles = planned?.plan?.sourceFiles.orEmpty()
            require(sourceFiles.size <= MAX_HISTORY_ARCHIVE_FILES_PER_ENTRY) {
                "History archive entry contains too many files"
            }
            plannedFileCount += sourceFiles.size.toLong()
            require(plannedFileCount <= MAX_HISTORY_ARCHIVE_TOTAL_FILES) {
                "History archive contains too many files"
            }
            sourceFiles.forEach { sourceFile ->
                coroutineContext.ensureActive()
                val sourceRepository = planned?.repository ?: return@forEach
                if (sourceFile.sizeBytes !in 0L..MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES) {
                    failedCopyCount += 1
                    return@forEach
                }
                val bytes = sourceRepository.readBytesAt(sourceFile.sourceRelativePath)
                    .getOrElse {
                        failedCopyCount += 1
                        return@forEach
                    }
                if (bytes.size.toLong() > MAX_HISTORY_ARCHIVE_SINGLE_PAYLOAD_BYTES) {
                    failedCopyCount += 1
                    return@forEach
                }
                fileSystem.writeBytes("$archiveDirectory/${sourceFile.archiveRelativePath}", bytes)
                    .getOrElse {
                        failedCopyCount += 1
                        return@forEach
                    }
                copiedFileCount += 1
                copiedFiles += HistoryArchiveFile(
                    relativePath = sourceFile.archiveRelativePath,
                    sizeBytes = bytes.size.toLong(),
                    kind = sourceFile.kind
                )
            }
            exportedEntries += buildCopiedHistoryArchiveEntry(
                plannedEntry = planned?.plan?.archiveEntry
                    ?: buildHistoryOnlyArchiveEntry(historyEntry, archiveId),
                copiedFiles = copiedFiles,
                failedCopyCount = failedCopyCount
            )
        }

        val totalPayloadBytes = exportedEntries
            .flatMap { it.payloadFiles }
            .fold(0L) { total, file -> safeHistoryArchiveExportSizeAdd(total, file.sizeBytes) }
        require(totalPayloadBytes <= MAX_HISTORY_ARCHIVE_TOTAL_PAYLOAD_BYTES) {
            "History archive payload is too large"
        }
        val manifest = HistoryArchiveManifest(
            archiveId = archiveId,
            exportedAtEpochMillis = request.exportedAtEpochMillis,
            appVersion = request.appVersion,
            entryCount = exportedEntries.size,
            totalPayloadBytes = totalPayloadBytes,
            entries = exportedEntries
        )
        val manifestPayload = json.encodeToString(HistoryArchiveManifest.serializer(), manifest)
        require(manifestPayload.encodeToByteArray().size.toLong() <= MAX_HISTORY_ARCHIVE_MANIFEST_BYTES) {
            "History archive manifest is too large"
        }
        fileSystem.writeString(
            "$archiveDirectory/manifest.json",
            manifestPayload
        ).getOrThrow()
        incompleteArchiveDirectory = null
        Result.success(
            HistoryArchiveExportResult(
                manifest = manifest,
                archiveDirectory = archiveDirectory,
                copiedFileCount = copiedFileCount,
                historyOnlyCount = exportedEntries.count {
                    it.payloadStatus == HistoryArchivePayloadStatus.HISTORY_ONLY
                },
                partialPayloadCount = exportedEntries.count {
                    it.payloadStatus == HistoryArchivePayloadStatus.PARTIAL
                }
            )
        )
    } catch (e: CancellationException) {
        cleanupIncompleteHistoryArchive(fileSystem, incompleteArchiveDirectory)
        throw e
    } catch (t: Throwable) {
        cleanupIncompleteHistoryArchive(fileSystem, incompleteArchiveDirectory)
        Result.failure(t)
    }
}

private suspend fun cleanupIncompleteHistoryArchive(fileSystem: FileSystem, directory: String?) {
    val target = directory ?: return
    withContext(NonCancellable) {
        withTimeoutOrNull(5_000L) {
            fileSystem.deleteRecursively(target)
        }
    }
}

internal fun safeHistoryArchiveExportSizeAdd(current: Long, addition: Long): Long {
    require(current >= 0L && addition >= 0L && current <= Long.MAX_VALUE - addition) {
        "History archive payload size overflow"
    }
    return current + addition
}

private data class PlannedHistoryArchivePayload(
    val repository: SavedThreadRepository,
    val plan: SavedThreadArchivePayloadPlan
)

private suspend fun chooseBestHistoryArchivePayloadPlan(
    sourceRepositories: List<SavedThreadRepository>,
    historyEntry: ThreadHistoryEntry,
    archiveId: String
): PlannedHistoryArchivePayload? {
    val snapshotId = buildHistoryArchiveSnapshotId(historyEntry)
    return sourceRepositories
        .mapNotNull { repository ->
            repository.planHistoryArchivePayload(
                historyEntry = historyEntry,
                archiveId = archiveId,
                snapshotId = snapshotId
            ).getOrNull()?.let { plan ->
                PlannedHistoryArchivePayload(repository, plan)
            }
        }
        .maxByOrNull { payloadRank(it.plan.archiveEntry.payloadStatus) }
}

private fun buildCopiedHistoryArchiveEntry(
    plannedEntry: HistoryArchiveEntry,
    copiedFiles: List<HistoryArchiveFile>,
    failedCopyCount: Int
): HistoryArchiveEntry {
    val copiedPaths = copiedFiles.mapTo(linkedSetOf()) { it.relativePath }
    val copiedStatus = when {
        copiedFiles.isEmpty() -> HistoryArchivePayloadStatus.HISTORY_ONLY
        failedCopyCount > 0 || plannedEntry.payloadStatus == HistoryArchivePayloadStatus.PARTIAL ->
            HistoryArchivePayloadStatus.PARTIAL
        else -> HistoryArchivePayloadStatus.FULL
    }
    return plannedEntry.copy(
        metadataPath = plannedEntry.metadataPath?.takeIf { it in copiedPaths },
        htmlPath = plannedEntry.htmlPath?.takeIf { it in copiedPaths },
        payloadFiles = copiedFiles,
        payloadStatus = copiedStatus
    )
}

private fun buildHistoryOnlyArchiveEntry(
    historyEntry: ThreadHistoryEntry,
    archiveId: String
): HistoryArchiveEntry {
    return HistoryArchiveEntry(
        snapshotId = "${normalizeHistoryArchivePathSegment(archiveId)}_${buildHistoryArchiveSnapshotId(historyEntry)}",
        historyEntry = historyEntry,
        payloadStatus = HistoryArchivePayloadStatus.HISTORY_ONLY
    )
}

private fun payloadRank(status: HistoryArchivePayloadStatus): Int {
    return when (status) {
        HistoryArchivePayloadStatus.FULL -> 3
        HistoryArchivePayloadStatus.PARTIAL -> 2
        HistoryArchivePayloadStatus.HISTORY_ONLY -> 1
    }
}

private fun buildHistoryArchiveSnapshotId(entry: ThreadHistoryEntry): String {
    val base = buildThreadStorageId(entry.boardId.ifBlank { null }, entry.threadId)
    return normalizeHistoryArchivePathSegment("${base}_v${entry.lastVisitedEpochMillis}")
}

private fun normalizeHistoryArchivePathSegment(value: String): String {
    return value
        .trim()
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .trim('_')
        .ifBlank { "archive" }
        .take(120)
}
