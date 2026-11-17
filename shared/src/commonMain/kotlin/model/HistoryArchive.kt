package com.valoser.futacha.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val HISTORY_ARCHIVE_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HistoryArchiveManifest(
    // Always written (not omitted as a default), so a later format can tell its files from this one.
    @EncodeDefault val archiveVersion: Int = HISTORY_ARCHIVE_VERSION,
    val archiveId: String,
    val exportedAtEpochMillis: Long,
    val appVersion: String? = null,
    val entryCount: Int,
    val totalPayloadBytes: Long = 0L,
    val entries: List<HistoryArchiveEntry>
)

@Serializable
data class HistoryArchiveEntry(
    val snapshotId: String,
    val historyEntry: ThreadHistoryEntry,
    val savedThread: SavedThread? = null,
    val metadataPath: String? = null,
    val htmlPath: String? = null,
    val payloadFiles: List<HistoryArchiveFile> = emptyList(),
    val payloadStatus: HistoryArchivePayloadStatus = HistoryArchivePayloadStatus.HISTORY_ONLY
)

@Serializable
data class HistoryArchiveFile(
    val relativePath: String,
    val sizeBytes: Long,
    val kind: HistoryArchiveFileKind
)

@Serializable
enum class HistoryArchiveFileKind {
    METADATA,
    HTML,
    THUMBNAIL,
    IMAGE,
    VIDEO,
    OTHER
}

@Serializable
enum class HistoryArchivePayloadStatus {
    FULL,
    PARTIAL,
    HISTORY_ONLY
}
