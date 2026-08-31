package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.HISTORY_ARCHIVE_DIRECTORY
import com.valoser.futacha.shared.repository.IMPORTED_HISTORY_DIRECTORY
import com.valoser.futacha.shared.repository.MAX_HISTORY_ARCHIVE_ENTRIES
import com.valoser.futacha.shared.repository.MAX_HISTORY_ARCHIVE_FILES_PER_ENTRY
import com.valoser.futacha.shared.repository.MAX_HISTORY_ARCHIVE_MANIFEST_BYTES
import com.valoser.futacha.shared.repository.MAX_HISTORY_ARCHIVE_TOTAL_FILES
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repository.safeHistoryArchiveExportSizeAdd
import com.valoser.futacha.shared.state.AppHistoryArchiveImportResult
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.exportAppHistoryArchive
import com.valoser.futacha.shared.state.importAppHistoryArchive
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val MAX_HISTORY_ARCHIVE_PREVIEW_CANDIDATES = 500
private const val MAX_HISTORY_ARCHIVE_SCANNED_NAMES = 10_000

data class FutachaHistoryArchivePreview(
    val archiveDirectory: String,
    val archiveId: String,
    val exportedAtEpochMillis: Long,
    val entries: List<FutachaHistoryArchivePreviewEntry>
)

data class FutachaHistoryArchivePreviewEntry(
    val snapshotId: String,
    val historyEntry: ThreadHistoryEntry,
    val payloadStatus: HistoryArchivePayloadStatus,
    val payloadBytes: Long,
    val alreadyExistsInHistory: Boolean
)

internal fun buildFutachaHistoryArchiveId(epochMillis: Long): String {
    return "history_$epochMillis"
}

internal fun buildFutachaHistoryArchiveUnavailableMessage(): String {
    return "履歴アーカイブを利用できません"
}

internal fun buildFutachaHistoryArchiveMissingMessage(): String {
    return "インポートできる履歴アーカイブがありません"
}

internal fun buildFutachaHistoryArchiveExportMessage(entryCount: Int, archiveDirectory: String): String {
    return "履歴アーカイブを作成しました: ${entryCount}件 ($archiveDirectory)"
}

internal fun buildFutachaHistoryArchiveExportThenClearMessage(entryCount: Int, archiveDirectory: String): String {
    return "履歴アーカイブを作成し、履歴を一括削除しました: ${entryCount}件 ($archiveDirectory)"
}

internal fun buildFutachaHistoryArchiveImportMessage(result: AppHistoryArchiveImportResult): String {
    val importedCount = result.archiveImport.importedHistoryEntries.size
    val merge = result.merge
    return "履歴アーカイブを読み込みました: ${importedCount}件、追加${merge.addedCount}件、更新${merge.updatedCount}件"
}

internal suspend fun exportAllFutachaHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    sourceRepositories: List<SavedThreadRepository>,
    appVersion: String?
): String {
    val fs = fileSystem ?: return buildFutachaHistoryArchiveUnavailableMessage()
    val now = Clock.System.now().toEpochMilliseconds()
    val result = exportAppHistoryArchive(
        stateStore = stateStore,
        fileSystem = fs,
        sourceRepositories = sourceRepositories,
        archiveId = buildFutachaHistoryArchiveId(now),
        exportedAtEpochMillis = now,
        appVersion = appVersion
    ).getOrThrow()
    return buildFutachaHistoryArchiveExportMessage(
        entryCount = result.manifest.entryCount,
        archiveDirectory = result.archiveDirectory
    )
}

internal suspend fun exportAllFutachaHistoryArchiveThenClear(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    sourceRepositories: List<SavedThreadRepository>,
    appVersion: String?,
    clearHistory: suspend () -> Unit
): String {
    val fs = fileSystem ?: return buildFutachaHistoryArchiveUnavailableMessage()
    val now = Clock.System.now().toEpochMilliseconds()
    val result = exportAppHistoryArchive(
        stateStore = stateStore,
        fileSystem = fs,
        sourceRepositories = sourceRepositories,
        archiveId = buildFutachaHistoryArchiveId(now),
        exportedAtEpochMillis = now,
        appVersion = appVersion
    ).getOrThrow()
    clearHistory()
    return buildFutachaHistoryArchiveExportThenClearMessage(
        entryCount = result.manifest.entryCount,
        archiveDirectory = result.archiveDirectory
    )
}

internal suspend fun exportSelectedFutachaHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    sourceRepositories: List<SavedThreadRepository>,
    appVersion: String?,
    selectedEntries: List<ThreadHistoryEntry>
): String {
    val fs = fileSystem ?: return buildFutachaHistoryArchiveUnavailableMessage()
    if (selectedEntries.isEmpty()) {
        return "エクスポートする履歴を選択してください"
    }
    val now = Clock.System.now().toEpochMilliseconds()
    val result = exportAppHistoryArchive(
        stateStore = stateStore,
        fileSystem = fs,
        sourceRepositories = sourceRepositories,
        archiveId = buildFutachaHistoryArchiveId(now),
        exportedAtEpochMillis = now,
        appVersion = appVersion,
        selectedEntries = selectedEntries
    ).getOrThrow()
    return buildFutachaHistoryArchiveExportMessage(
        entryCount = result.manifest.entryCount,
        archiveDirectory = result.archiveDirectory
    )
}

internal suspend fun importLatestFutachaHistoryArchive(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    destinationRepository: SavedThreadRepository?,
    selectedSnapshotIds: Set<String>? = null
): String {
    val fs = fileSystem ?: return buildFutachaHistoryArchiveUnavailableMessage()
    val destination = destinationRepository ?: return buildFutachaHistoryArchiveUnavailableMessage()
    val archiveDirectory = findNextFutachaHistoryArchiveDirectory(stateStore, fs)
        ?: return buildFutachaHistoryArchiveMissingMessage()
    val result = importAppHistoryArchive(
        stateStore = stateStore,
        fileSystem = fs,
        destinationRepository = destination,
        archiveDirectory = archiveDirectory,
        selectedSnapshotIds = selectedSnapshotIds
    ).getOrThrow()
    return buildFutachaHistoryArchiveImportMessage(result)
}

internal suspend fun findLatestFutachaHistoryArchiveDirectory(
    fileSystem: FileSystem,
    json: Json = Json { ignoreUnknownKeys = true }
): String? {
    return boundedHistoryArchiveCandidateNames(fileSystem)
        .mapNotNull { fileName ->
            val archiveName = fileName.trim().trim('/')
            if (archiveName.isBlank()) {
                return@mapNotNull null
            }
            val archiveDirectory = "$HISTORY_ARCHIVE_DIRECTORY/$archiveName"
            val manifest = loadBoundedHistoryArchiveManifest(fileSystem, archiveDirectory, json)
                ?: return@mapNotNull null
            archiveDirectory to manifest.exportedAtEpochMillis
        }
        .maxByOrNull { it.second }
        ?.first
}

internal suspend fun loadLatestFutachaHistoryArchivePreview(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    json: Json = Json { ignoreUnknownKeys = true }
): FutachaHistoryArchivePreview? {
    val fs = fileSystem ?: return null
    val archiveDirectory = findNextFutachaHistoryArchiveDirectory(stateStore, fs, json) ?: return null
    val manifest = loadBoundedHistoryArchiveManifest(fs, archiveDirectory, json) ?: return null
    val currentKeys = stateStore.historyValueKeys()
    return FutachaHistoryArchivePreview(
        archiveDirectory = archiveDirectory,
        archiveId = manifest.archiveId,
        exportedAtEpochMillis = manifest.exportedAtEpochMillis,
        entries = manifest.entries.map { entry ->
            FutachaHistoryArchivePreviewEntry(
                snapshotId = entry.snapshotId,
                historyEntry = entry.historyEntry,
                payloadStatus = entry.payloadStatus,
                payloadBytes = entry.payloadFiles.fold(0L) { total, file ->
                    safeHistoryArchiveExportSizeAdd(total, file.sizeBytes)
                },
                alreadyExistsInHistory = buildHistoryIdentityKey(entry.historyEntry) in currentKeys
            )
        }
    )
}

internal suspend fun findNextFutachaHistoryArchiveDirectory(
    stateStore: AppStateStore,
    fileSystem: FileSystem,
    json: Json = Json { ignoreUnknownKeys = true }
): String? {
    val currentKeys = stateStore.historyValueKeys()
    return loadFutachaHistoryArchiveManifestCandidates(fileSystem, json)
        .maxWithOrNull(
            compareBy<Pair<String, HistoryArchiveManifest>> {
                it.second.entries.count { entry ->
                    buildHistoryIdentityKey(entry.historyEntry) !in currentKeys
                }
            }.thenBy { it.second.exportedAtEpochMillis }
        )
        ?.first
}

internal fun buildImportedHistoryRepository(fileSystem: FileSystem?): SavedThreadRepository? {
    return fileSystem?.let { fs ->
        SavedThreadRepository(fs, baseDirectory = IMPORTED_HISTORY_DIRECTORY)
    }
}

private suspend fun AppStateStore.historyValueKeys(): Set<String> {
    return historyValue().mapTo(linkedSetOf(), ::buildHistoryIdentityKey)
}

private suspend fun loadFutachaHistoryArchiveManifestCandidates(
    fileSystem: FileSystem,
    json: Json
): List<Pair<String, HistoryArchiveManifest>> {
    return boundedHistoryArchiveCandidateNames(fileSystem)
        .mapNotNull { fileName ->
            val archiveName = fileName.trim().trim('/')
            if (archiveName.isBlank()) {
                return@mapNotNull null
            }
            val archiveDirectory = "$HISTORY_ARCHIVE_DIRECTORY/$archiveName"
            val manifest = loadBoundedHistoryArchiveManifest(fileSystem, archiveDirectory, json)
                ?: return@mapNotNull null
            archiveDirectory to manifest
        }
}

private suspend fun boundedHistoryArchiveCandidateNames(fileSystem: FileSystem): List<String> {
    return fileSystem.listFiles(HISTORY_ARCHIVE_DIRECTORY)
        .asSequence()
        .take(MAX_HISTORY_ARCHIVE_SCANNED_NAMES)
        .map { it.trim().trim('/') }
        .filter { it.isNotBlank() }
        .sortedDescending()
        .take(MAX_HISTORY_ARCHIVE_PREVIEW_CANDIDATES)
        .toList()
}

private suspend fun loadBoundedHistoryArchiveManifest(
    fileSystem: FileSystem,
    archiveDirectory: String,
    json: Json
): HistoryArchiveManifest? {
    val path = "$archiveDirectory/manifest.json"
    val size = runSuspendCatchingPreservingCancellation { fileSystem.getFileSize(path) }.getOrNull()
        ?: return null
    if (size !in 0L..MAX_HISTORY_ARCHIVE_MANIFEST_BYTES) return null
    val payload = fileSystem.readString(path).getOrNull() ?: return null
    if (payload.encodeToByteArray().size.toLong() > MAX_HISTORY_ARCHIVE_MANIFEST_BYTES) return null
    val manifest = withContext(AppDispatchers.parsing) {
        runCatching {
            json.decodeFromString(HistoryArchiveManifest.serializer(), payload)
        }.getOrNull()
    } ?: return null
    if (manifest.entries.size > MAX_HISTORY_ARCHIVE_ENTRIES) return null
    if (manifest.entryCount != manifest.entries.size) return null
    if (manifest.entries.mapTo(hashSetOf()) { it.snapshotId }.size != manifest.entries.size) return null
    if (manifest.entries.sumOf { entry -> entry.payloadFiles.size.toLong() } > MAX_HISTORY_ARCHIVE_TOTAL_FILES) {
        return null
    }
    if (manifest.entries.any { entry ->
            entry.payloadFiles.size > MAX_HISTORY_ARCHIVE_FILES_PER_ENTRY ||
                entry.payloadFiles.mapTo(hashSetOf()) { it.relativePath }.size != entry.payloadFiles.size ||
                runCatching {
                    entry.payloadFiles.fold(0L) { total, file ->
                        safeHistoryArchiveExportSizeAdd(total, file.sizeBytes)
                    }
                }.isFailure
        }
    ) return null
    return manifest
}

private suspend fun AppStateStore.historyValue(): List<ThreadHistoryEntry> {
    return history.first()
}

private fun buildHistoryIdentityKey(entry: ThreadHistoryEntry): String {
    val boardKey = entry.boardId.ifBlank { entry.boardUrl }
    return "${boardKey.trim()}|${entry.threadId.trim()}"
}
