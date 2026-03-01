package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
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
        baseDirectory: String = MANUAL_SAVE_DIRECTORY
    ): Result<SavedMediaFile> = withContext(AppDispatchers.io) {
        val normalizedUrl = mediaUrl.trim()
        if (!normalizedUrl.isRemoteHttpUrl()) {
            return@withContext Result.failure(IllegalArgumentException("このメディアURLは保存に対応していません"))
        }

        val storageId = buildThreadStorageId(boardId = boardId, threadId = threadId)
        val lockKey = buildThreadStorageLockKey(
            storageId = "media__$storageId",
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
                        val response = withTimeoutOrNull(MEDIA_REQUEST_TIMEOUT_MILLIS) {
                            httpClient.get(normalizedUrl) {
                                headers[HttpHeaders.Accept] = "image/*,video/*;q=0.9,*/*;q=0.2"
                            }
                        } ?: throw IllegalStateException(
                            "保存に失敗しました: ダウンロードがタイムアウトしました (${MEDIA_REQUEST_TIMEOUT_MILLIS}ms)"
                        )

                        try {
                            if (!response.status.isSuccess()) {
                                throw IllegalStateException("保存に失敗しました: HTTP ${response.status.value}")
                            }

                            val headerContentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                            if (headerContentLength > MAX_FILE_SIZE_BYTES) {
                                throw IllegalStateException(
                                    "保存に失敗しました: ファイルサイズが上限を超えています (${headerContentLength / 1024}KB)"
                                )
                            }

                            val contentType = response.headers[HttpHeaders.ContentType]
                                ?.let { raw -> runCatching { ContentType.parse(raw) }.getOrNull() }

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

                            val payload = readPayloadBytes(response, startedAtMillis)
                            val savedAt = Clock.System.now().toEpochMilliseconds()
                            val fileName = buildOutputFileName(
                                mediaUrl = normalizedUrl,
                                extension = extension,
                                savedAt = savedAt
                            )

                            val mediaBaseRelativePath = "$SAVED_MEDIA_DIRECTORY/$storageId/$targetSubDirectory"
                            val relativePath = "$mediaBaseRelativePath/$fileName"

                            if (baseSaveLocation != null) {
                                fileSystem.createDirectory(baseSaveLocation, mediaBaseRelativePath).getOrThrow()
                                fileSystem.writeBytes(baseSaveLocation, relativePath, payload).getOrThrow()
                            } else {
                                val absoluteDirectory = "$baseDirectory/$mediaBaseRelativePath"
                                val absolutePath = "$absoluteDirectory/$fileName"
                                fileSystem.createDirectory(absoluteDirectory).getOrThrow()
                                fileSystem.writeBytes(absolutePath, payload).getOrThrow()
                            }

                            SavedMediaFile(
                                fileName = fileName,
                                relativePath = relativePath,
                                mediaType = mediaType,
                                byteSize = payload.size.toLong(),
                                savedAtEpochMillis = savedAt
                            )
                        } finally {
                            runCatching { response.bodyAsChannel().cancel() }
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

    private suspend fun readPayloadBytes(response: HttpResponse, startedAtMillis: Long): ByteArray {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var output = ByteArray(minOf(STREAM_READ_BUFFER_BYTES, MAX_FILE_SIZE_BYTES.toInt()))
        var totalBytesRead = 0
        var zeroReadCount = 0
        var loopCount = 0L

        while (true) {
            coroutineContext.ensureActive()
            val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) {
                channel.readAvailable(buffer, 0, buffer.size)
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
            if (requiredSize.toLong() > MAX_FILE_SIZE_BYTES) {
                throw IllegalStateException("保存に失敗しました: 実際のファイルサイズが上限を超えました")
            }

            val elapsed = Clock.System.now().toEpochMilliseconds() - startedAtMillis
            if (elapsed > MAX_SAVE_DURATION_MILLIS) {
                throw IllegalStateException("保存に失敗しました: 処理時間が上限を超えました")
            }

            if (requiredSize > output.size) {
                var newSize = output.size
                while (newSize < requiredSize) {
                    newSize = (newSize * 2).coerceAtMost(MAX_FILE_SIZE_BYTES.toInt())
                    if (newSize == output.size) break
                }
                if (newSize < requiredSize) {
                    throw IllegalStateException("保存に失敗しました: バッファ拡張に失敗しました")
                }
                output = output.copyOf(newSize)
            }

            buffer.copyInto(
                destination = output,
                destinationOffset = totalBytesRead,
                startIndex = 0,
                endIndex = read
            )
            totalBytesRead = requiredSize

            loopCount += 1
            if (loopCount % 32L == 0L) {
                yield()
            }
        }

        return if (totalBytesRead == output.size) {
            output
        } else {
            output.copyOf(totalBytesRead)
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

    private fun getExtensionFromUrl(url: String): String? {
        val sanitized = url
            .substringBefore('#')
            .substringBefore('?')
        val raw = sanitized.substringAfterLast('.', "")
        return raw.takeIf { candidate -> candidate.length in 2..6 }
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

    private fun String.isRemoteHttpUrl(): Boolean {
        return startsWith("https://", ignoreCase = true) ||
            startsWith("http://", ignoreCase = true)
    }

    companion object {
        private const val SAVED_MEDIA_DIRECTORY = "saved_media"
        private const val IMAGE_SUB_DIRECTORY = "images"
        private const val VIDEO_SUB_DIRECTORY = "videos"
        private const val DEFAULT_EXTENSION = "bin"

        // Keep same limit as thread save and board upload cap (8000KB).
        private const val MAX_FILE_SIZE_BYTES = 8_192_000L
        private const val MAX_SAVE_DURATION_MILLIS = 2 * 60 * 1000L
        private const val STORAGE_LOCK_WAIT_TIMEOUT_MILLIS = 120_000L
        private const val MEDIA_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val READ_IDLE_TIMEOUT_MILLIS = 15_000L
        private const val STREAM_READ_BUFFER_BYTES = 64 * 1024
        private const val MAX_ZERO_READ_RETRIES = 100
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        private const val MAX_FILENAME_LENGTH = 96

        private val INVALID_FILENAME_SEGMENT_REGEX = Regex("""[^A-Za-z0-9._-]""")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
        private val VIDEO_EXTENSIONS = setOf("webm", "mp4", "mov", "mkv", "avi", "flv", "ts")
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
