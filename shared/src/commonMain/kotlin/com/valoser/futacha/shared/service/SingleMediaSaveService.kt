package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.MediaSaveSource
import com.valoser.futacha.shared.util.withMediaSaveSource
import com.valoser.futacha.shared.util.isSupportedMediaSaveSource
import com.valoser.futacha.shared.util.hasEpochDurationExceeded
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Single media (image/video) save service used from media preview dialogs.
 */
@OptIn(ExperimentalTime::class)
class SingleMediaSaveService(
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem
) {

    suspend fun saveMedia(
        mediaUrl: String,
        boardId: String,
        threadId: String,
        baseSaveLocation: SaveLocation? = null,
        baseDirectory: String = MANUAL_SAVE_DIRECTORY,
        /** Null keeps structured thread storage; empty saves at the selected root. */
        storageDirectoryOverride: String? = null,
        useTypeSubdirectory: Boolean = true,
        outputFileNameOverride: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<SavedMediaFile> = withContext(AppDispatchers.io) {
        val normalizedUrl = mediaUrl.trim()
        if (!isSupportedMediaSaveSource(normalizedUrl)) {
            return@withContext Result.failure(IllegalArgumentException("このメディアURLは保存に対応していません"))
        }

        val storageId = storageDirectoryOverride?.sanitizeDirectoryCandidate()
            ?: buildThreadStorageId(boardId = boardId, threadId = threadId)
        val lockKey = buildThreadStorageLockKey(
            storageId = "media__${storageId.ifBlank { "manual_root" }}",
            baseDirectory = baseDirectory,
            baseSaveLocation = baseSaveLocation
        )

        val saveResult = ThreadStorageLockRegistry.withStorageLockOrNull(
            storageId = lockKey,
            waitTimeoutMillis = STORAGE_LOCK_WAIT_TIMEOUT_MILLIS
        ) {
                try {
                    Result.success(run {
                        val startedAtMillis = Clock.System.now().toEpochMilliseconds()
                        withMediaSaveSource(httpClient, fileSystem, normalizedUrl) { source ->
                            val headerContentLength = source.declaredSize
                            if (headerContentLength > MAX_FILE_SIZE_BYTES) {
                                throw IllegalStateException(
                                    "保存に失敗しました: ファイルサイズが上限を超えています (${headerContentLength / 1024}KB)"
                                )
                            }

                            val contentType = source.contentType

                            val extension = (
                                getExtensionFromUrl(normalizedUrl)
                                    ?: getExtensionFromContentType(contentType)
                                    ?: DEFAULT_EXTENSION
                                ).lowercase()

                            val mediaType = resolveSavedMediaType(extension, contentType)
                            val targetSubDirectory = when (mediaType) {
                                SavedMediaType.VIDEO -> VIDEO_SUB_DIRECTORY
                                else -> IMAGE_SUB_DIRECTORY
                            }

                            val savedAt = Clock.System.now().toEpochMilliseconds()
                            val fileName = outputFileNameOverride
                                ?.substringAfterLast('/')
                                ?.substringAfterLast('\\')
                                ?.sanitizeFileNameCandidate()
                                ?.takeIf { it.isNotBlank() }
                                ?: buildOutputFileName(
                                    mediaUrl = normalizedUrl,
                                    extension = extension,
                                    savedAt = savedAt
                                )

                            val relativeMediaPath = if (useTypeSubdirectory) {
                                buildSingleMediaRelativePath(
                                    storageId = "",
                                    targetSubDirectory = targetSubDirectory,
                                    fileName = fileName
                                ).trimStart('/')
                            } else fileName
                            val storageTarget = buildThreadSaveStorageTarget(
                                saveLocation = baseSaveLocation,
                                baseDirectory = baseDirectory,
                                storageId = storageId
                            )
                            val relativePath = storageTarget.relativeStoragePath(relativeMediaPath)
                            val mediaBaseRelativePath = relativeMediaPath
                                .substringBeforeLast('/', missingDelimiterValue = "")

                            if (baseSaveLocation != null && mediaBaseRelativePath.isNotBlank()) {
                                fileSystem.createDirectory(
                                    baseSaveLocation,
                                    storageTarget.relativeStoragePath(mediaBaseRelativePath)
                                ).getOrThrow()
                            } else if (baseSaveLocation == null) {
                                fileSystem.createDirectory(
                                    storageTarget.absoluteStoragePath(mediaBaseRelativePath)
                                ).getOrThrow()
                            }
                            val binaryTarget = resolveThreadSaveBinaryWriteTarget(storageTarget, relativeMediaPath)
                            val sourcePath = com.valoser.futacha.shared.util.localMediaSavePath(normalizedUrl)
                            if (!normalizedUrl.startsWith("http", true)) {
                                val destination = fileSystem.resolveSavedFile(
                                    baseSaveLocation ?: SaveLocation.Path(baseDirectory), relativePath
                                ).getOrNull()
                                require(sourcePath != destination) { "元のファイルと保存先が同じです。別のフォルダを選んでください。" }
                            }
                            val byteSize = streamPayloadToStorage(
                                source = source,
                                binaryTarget = binaryTarget,
                                startedAtMillis = startedAtMillis,
                                declaredSize = headerContentLength,
                                onProgress = onProgress
                            )

                            SavedMediaFile(
                                fileName = fileName,
                                relativePath = relativePath,
                                mediaType = mediaType,
                                byteSize = byteSize,
                                savedAtEpochMillis = savedAt
                            )
                        }
                    })
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Result.failure(t)
                }
        }
        saveResult ?: Result.failure(
            IllegalStateException("保存処理が混雑しています。しばらく待ってから再試行してください。")
        )
    }

    private suspend fun streamPayloadToStorage(
        source: MediaSaveSource,
        binaryTarget: ThreadSaveBinaryWriteTarget,
        startedAtMillis: Long,
        declaredSize: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Long {
        cleanupThreadSaveBinaryWriteTarget(fileSystem, binaryTarget)
        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var totalBytesRead = 0L
        var zeroReadCount = 0
        var loopCount = 0L

        try {
            writeThreadSaveBinaryStream(
                fileSystem = fileSystem,
                target = binaryTarget,
                writeTimeoutMillis = WRITE_TIMEOUT_MILLIS
            ) { sink ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) {
                        source.read(buffer)
                    } ?: throw IllegalStateException("保存に失敗しました: ストリーム読み込みがタイムアウトしました")

                    if (read == -1) break
                    if (read == 0) {
                        zeroReadCount += 1
                        if (zeroReadCount >= MAX_ZERO_READ_RETRIES) {
                            throw IllegalStateException("保存に失敗しました: メディアストリームが停止しました")
                        }
                        delay(ZERO_READ_BACKOFF_MILLIS)
                        continue
                    }

                    zeroReadCount = 0
                    val requiredSize = totalBytesRead + read
                    if (requiredSize > MAX_FILE_SIZE_BYTES) {
                        throw IllegalStateException("保存に失敗しました: 実際のファイルサイズが上限を超えました")
                    }

                    if (hasEpochDurationExceeded(
                            Clock.System.now().toEpochMilliseconds(),
                            startedAtMillis,
                            MAX_SAVE_DURATION_MILLIS
                        )
                    ) {
                        throw IllegalStateException("保存に失敗しました: 処理時間が上限を超えました")
                    }

                    sink.write(buffer, 0, read)
                    totalBytesRead = requiredSize
                    onProgress(totalBytesRead, declaredSize)

                    loopCount += 1
                    if (loopCount % 32L == 0L) {
                        yield()
                    }
                }
            }
            if (totalBytesRead <= 0L) {
                throw IllegalStateException("保存に失敗しました: メディアファイルが空です")
            }
            check(declaredSize <= 0L || totalBytesRead == declaredSize) { "保存に失敗しました: ファイルを最後まで読み込めませんでした" }
            return totalBytesRead
        } catch (t: Throwable) {
            cleanupThreadSaveBinaryWriteTarget(fileSystem, binaryTarget)
            throw t
        }
    }

    private fun buildOutputFileName(mediaUrl: String, extension: String, savedAt: Long): String {
        val fromUrl = mediaUrl
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.sanitizeFileNameCandidate()

        val baseName = fromUrl
            ?.substringBeforeLast('.', missingDelimiterValue = fromUrl)
            ?.takeIf { it.isNotBlank() }
            ?: "media"

        val safeExtension = extension
            .trim()
            .lowercase()
            .ifBlank { DEFAULT_EXTENSION }

        return "${baseName}_${savedAt}.$safeExtension"
    }

    private fun String.sanitizeFileNameCandidate(): String {
        val sanitized = replace(INVALID_FILENAME_SEGMENT_REGEX, "_")
            .trim('_')
            .take(MAX_FILENAME_LENGTH)
        return sanitized.ifBlank { "media" }
    }

    private fun String.sanitizeDirectoryCandidate(): String =
        replace(INVALID_DIRECTORY_SEGMENT_REGEX, "_")
            .trim()
            .trim('.', '_')
            .take(MAX_FILENAME_LENGTH)

    private fun getExtensionFromUrl(url: String): String? {
        val sanitized = url
            .substringBefore('#')
            .substringBefore('?')
        val raw = sanitized.substringAfterLast('.', "")
        return raw.takeIf { candidate ->
            candidate.length in 2..6 && candidate.all { it.isLetterOrDigit() }
        }
    }

    private fun getExtensionFromContentType(contentType: ContentType?): String? {
        return when (contentType?.contentSubtype?.lowercase()) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            "gif" -> "gif"
            "webp" -> "webp"
            "mp4" -> "mp4"
            "webm" -> "webm"
            "quicktime", "mov" -> "mov"
            else -> null
        }
    }

    private fun resolveSavedMediaType(
        extension: String,
        contentType: ContentType?
    ): SavedMediaType {
        if (extension in VIDEO_EXTENSIONS) return SavedMediaType.VIDEO
        if (extension in IMAGE_EXTENSIONS) return SavedMediaType.IMAGE

        val type = contentType?.contentType?.lowercase().orEmpty()
        return when {
            type == "video" -> SavedMediaType.VIDEO
            type == "image" -> SavedMediaType.IMAGE
            else -> SavedMediaType.IMAGE
        }
    }

    companion object {
        private const val IMAGE_SUB_DIRECTORY = "images"
        private const val VIDEO_SUB_DIRECTORY = "videos"
        private const val DEFAULT_EXTENSION = "bin"

        // Futaba uploads are usually smaller, but archived/external videos can
        // be much larger. Stream to disk under a finite per-file bound.
        private const val MAX_FILE_SIZE_BYTES = 512L * 1024L * 1024L
        private const val MAX_SAVE_DURATION_MILLIS = 15 * 60 * 1000L
        private const val STORAGE_LOCK_WAIT_TIMEOUT_MILLIS = 15_000L
        private const val READ_IDLE_TIMEOUT_MILLIS = 15_000L
        private const val WRITE_TIMEOUT_MILLIS = 15_000L
        private const val STREAM_READ_BUFFER_BYTES = 512 * 1024
        private const val MAX_ZERO_READ_RETRIES = 100
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        private const val MAX_FILENAME_LENGTH = 96

        private val INVALID_FILENAME_SEGMENT_REGEX = Regex("""[^A-Za-z0-9._()\-]""")
        private val INVALID_DIRECTORY_SEGMENT_REGEX = Regex("""[\\/:*?\"<>|\u0000-\u001F]""")
        private val IMAGE_EXTENSIONS = FUTABA_COMPAT_IMAGE_EXTENSIONS
        private val VIDEO_EXTENSIONS = FUTABA_COMPAT_VIDEO_EXTENSIONS
    }
}

data class SavedMediaFile(
    val fileName: String,
    val relativePath: String,
    val mediaType: SavedMediaType,
    val byteSize: Long,
    val savedAtEpochMillis: Long
)

enum class SavedMediaType {
    IMAGE,
    VIDEO
}
