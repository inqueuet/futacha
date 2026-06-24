package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.random.Random

/**
 * 元HTML保存や外部リソース削除のオプション
 */
data class RawHtmlSaveOptions(
    val enable: Boolean = true,
    val stripExternalResources: Boolean = true
)

data class ThreadSaveLimits(
    val maxMediaItems: Int = ThreadSaveService.DEFAULT_MAX_MEDIA_ITEMS,
    val maxSaveDurationMs: Long = ThreadSaveService.DEFAULT_MAX_SAVE_DURATION_MS,
    val maxParallelDownloads: Int = ThreadSaveService.DEFAULT_MAX_PARALLEL_DOWNLOADS,
    val mediaDownloadStartDelayMs: Long = 0L
)

data class ThreadSaveStorageOptions(
    val storageIdOverride: String? = null,
    val clearExistingOutput: Boolean = true,
    val reuseExistingMedia: Boolean = false,
    val pruneUnreferencedExistingMedia: Boolean = false
)

/**
 * スレッド保存サービス
 */
@OptIn(ExperimentalTime::class)
class ThreadSaveService(
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem
) {
    private val _saveProgress = MutableStateFlow<SaveProgress?>(null)
    val saveProgress: StateFlow<SaveProgress?> = _saveProgress
    private val mediaWriteLocksGuard = Mutex()
    private val mediaWriteLocks = mutableMapOf<String, ThreadSaveMediaPathLock>()
    private val json = Json

    companion object {
        // ファイルサイズ制限: 8000KB = 8,192,000 bytes
        private const val MAX_FILE_SIZE_BYTES = 8_192_000L
        private const val MAX_THREAD_HTML_BYTES = 5 * 1024 * 1024L
        private const val MAX_TOTAL_SIZE_BYTES = 512L * 1024 * 1024 // Keep foreground saves bounded.
        const val DEFAULT_MAX_SAVE_DURATION_MS = 3 * 60 * 1000L // 3分上限

        // サポートされる拡張子
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf("webm", "mp4")

        // FIX: 最大メディア数を制限
        const val DEFAULT_MAX_MEDIA_ITEMS = 300

        // リトライ設定
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        // Timeout for media body retrieval.
        private const val READ_IDLE_TIMEOUT_MILLIS = 15_000L
        private const val MEDIA_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val THREAD_HTML_FETCH_TIMEOUT_MILLIS = 30_000L
        private const val WRITE_TIMEOUT_MILLIS = 60_000L
        private const val STREAM_READ_BUFFER_BYTES = 512 * 1024
        private const val MAX_ZERO_READ_RETRIES = 100
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 2
    }

    /**
     * スレッドを保存
     * @param baseSaveLocation 保存先 (Path/TreeUri/Bookmark)。nullの場合は従来の文字列パスとしてMANUAL_SAVE_DIRECTORYを使用
     * @param baseDirectory 後方互換用の文字列パス (baseSaveLocationがnullの場合のみ使用)
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun saveThread(
        threadId: String,
        boardId: String,
        boardName: String,
        boardUrl: String,
        title: String,
        expiresAtLabel: String?,
        posts: List<Post>,
        isTruncated: Boolean = false,
        truncationReason: String? = null,
        baseSaveLocation: SaveLocation? = null,
        baseDirectory: String = MANUAL_SAVE_DIRECTORY,
        writeMetadata: Boolean = baseDirectory == AUTO_SAVE_DIRECTORY,
        rawHtmlOptions: RawHtmlSaveOptions = RawHtmlSaveOptions(),
        limits: ThreadSaveLimits = ThreadSaveLimits(),
        storageOptions: ThreadSaveStorageOptions = ThreadSaveStorageOptions(),
        writeInitialMetadataBeforeMedia: Boolean = false,
        onInitialSavedThread: suspend (SavedThread) -> Unit = {}
    ): Result<SavedThread> = withContext(AppDispatchers.io) {
        val effectiveLimits = limits.copy(
            maxMediaItems = limits.maxMediaItems.coerceAtLeast(0),
            maxSaveDurationMs = limits.maxSaveDurationMs.coerceAtLeast(1L),
            maxParallelDownloads = limits.maxParallelDownloads.coerceAtLeast(1),
            mediaDownloadStartDelayMs = limits.mediaDownloadStartDelayMs.coerceAtLeast(0L)
        )
        val savedAtTimestamp = Clock.System.now().toEpochMilliseconds()
        val storageId = storageOptions.storageIdOverride
            ?.takeIf { it.isNotBlank() }
            ?: buildThreadSaveGenerationStorageId(
                boardId = boardId,
                threadId = threadId,
                savedAtEpochMillis = savedAtTimestamp,
                nonce = Random.nextInt(0, Int.MAX_VALUE).toString(36)
            )
        val storageTarget = buildThreadSaveStorageTarget(
            saveLocation = baseSaveLocation,
            baseDirectory = baseDirectory,
            storageId = storageId
        )
        var hasWrittenInitialMetadata = false
        try {
            Result.success(run {
                val startedAtMillis = savedAtTimestamp
                // 準備フェーズ
                updateProgress(SavePhase.PREPARING, 0, 1, "ディレクトリ作成中...")

                val boardPath = extractBoardPath(boardUrl, boardId)
                val opPostId = posts.firstOrNull()?.id

                prepareThreadSaveOutput(
                    fileSystem = fileSystem,
                    target = storageTarget,
                    request = ThreadSaveOutputPreparationRequest(
                        boardPath = boardPath,
                        clearExistingOutput = storageOptions.clearExistingOutput
                    )
                )

                // ダウンロードフェーズ
                var rawHtmlRelativePath: String? = null
                val mediaPlan = buildThreadSaveMediaDownloadPlan(
                    posts = posts,
                    maxMediaItems = effectiveLimits.maxMediaItems
                )

                if (writeMetadata && writeInitialMetadataBeforeMedia) {
                    updateProgress(SavePhase.CONVERTING, 0, posts.size, "投稿変換中...")
                    val initialSavedPosts = buildThreadSaveSavedPosts(
                        posts = posts,
                        mediaKeyToFileInfoMap = emptyMap(),
                        urlToPathMap = emptyMap(),
                        updateProgress = { current, total ->
                            updateProgress(
                                SavePhase.CONVERTING,
                                current,
                                total,
                                "投稿変換中... ($current/$total)"
                            )
                        }
                    )
                    val initialMetadataSize = writeThreadSaveMetadataIfEnabled(
                        writeMetadata = true,
                        fileSystem = fileSystem,
                        target = storageTarget,
                        request = ThreadSaveMetadataWriteRequest(
                            threadId = threadId,
                            boardId = boardId,
                            boardName = boardName,
                            boardUrl = boardUrl,
                            title = title,
                            storageId = storageId,
                            savedAtTimestamp = savedAtTimestamp,
                            expiresAtLabel = expiresAtLabel,
                            savedPosts = initialSavedPosts,
                            rawHtmlRelativePath = null,
                            strippedExternalResources = rawHtmlOptions.stripExternalResources,
                            isTruncated = isTruncated,
                            truncationReason = truncationReason,
                            baseTotalSize = 0L
                        ),
                        encodeMetadata = json::encodeToString
                    )
                    hasWrittenInitialMetadata = true
                    onInitialSavedThread(
                        buildThreadSaveSavedThread(
                            threadId = threadId,
                            boardId = boardId,
                            boardName = boardName,
                            title = title,
                            storageId = storageId,
                            thumbnailPath = null,
                            savedAtTimestamp = savedAtTimestamp,
                            postCount = posts.size,
                            imageCount = 0,
                            videoCount = 0,
                            totalSize = initialMetadataSize,
                            downloadFailureCount = 0,
                            skippedMediaCount = 0,
                            totalMediaCount = mediaPlan.totalMediaCount,
                            isContentTruncated = isTruncated,
                            statusOverride = SaveStatus.DOWNLOADING
                        )
                    )
                }

                val existingMetadata = if (storageOptions.reuseExistingMedia) {
                    loadThreadSaveExistingMetadata(storageTarget)
                } else {
                    null
                }
                val previousMetadata = existingMetadata?.metadata
                val reusableMediaSeed = if (previousMetadata != null) {
                    buildReusableThreadSaveMediaSeed(
                        target = storageTarget,
                        metadata = previousMetadata,
                        boardPath = boardPath,
                        scheduledItems = mediaPlan.scheduledItems,
                        opPostId = opPostId
                    )
                } else {
                    ThreadSaveMediaDownloadSeed()
                }
                val remainingMediaPlan = if (reusableMediaSeed.mediaKeyToFileInfoMap.isEmpty()) {
                    mediaPlan
                } else {
                    mediaPlan.copy(
                        scheduledItems = mediaPlan.scheduledItems.filterNot { mediaItem ->
                            buildThreadSaveMediaDownloadKey(
                                mediaItem.url,
                                mediaItem.requestType
                            ) in reusableMediaSeed.mediaKeyToFileInfoMap
                        }
                    )
                }

                if (
                    effectiveLimits.mediaDownloadStartDelayMs > 0L &&
                    remainingMediaPlan.scheduledItems.isNotEmpty()
                ) {
                    delay(effectiveLimits.mediaDownloadStartDelayMs)
                }

                // FIX: メディア数が異常に多い場合は警告
                if (mediaPlan.totalMediaCount > effectiveLimits.maxMediaItems) {
                    Logger.w(
                        "ThreadSaveService",
                        "Thread has ${mediaPlan.totalMediaCount} media items (max: ${effectiveLimits.maxMediaItems}), some may be skipped"
                    )
                }

                val mediaDownloadResult = executeThreadSaveMediaDownloadPlan(
                    plan = remainingMediaPlan,
                    opPostId = opPostId,
                    chunkSize = 50,
                    maxParallelDownloads = effectiveLimits.maxParallelDownloads,
                    maxRetries = MAX_RETRIES,
                    retryDelayMillis = RETRY_DELAY_MS,
                    logTag = "ThreadSaveService",
                    createUrlToPathMap = { createThreadSaveLruCache(effectiveLimits.maxMediaItems) },
                    createMediaKeyToFileInfoMap = { createThreadSaveLruCache(effectiveLimits.maxMediaItems) },
                    initialSeed = reusableMediaSeed,
                    updateProgress = { current, total ->
                        updateProgress(
                            SavePhase.DOWNLOADING,
                            current,
                            total,
                            "メディアダウンロード中... ($current/$total)"
                        )
                    },
                    downloadMedia = { mediaItem ->
                        downloadAndSaveMedia(
                            url = mediaItem.url,
                            storageTarget = storageTarget,
                            boardPath = boardPath,
                            requestType = mediaItem.requestType,
                            postId = mediaItem.postId,
                            startedAtMillis = startedAtMillis,
                            maxSaveDurationMs = effectiveLimits.maxSaveDurationMs
                        )
                    },
                    enforceBudget = { totalSizeBytes ->
                        enforceBudget(totalSizeBytes, startedAtMillis, effectiveLimits.maxSaveDurationMs)
                    }
                )
                val urlToPathMap = mediaDownloadResult.urlToPathMap
                val mediaKeyToFileInfoMap = mediaDownloadResult.mediaKeyToFileInfoMap
                val mediaCounts = mediaDownloadResult.mediaCounts
                var totalSize = mediaDownloadResult.totalSizeBytes
                val downloadFailureCount = mediaDownloadResult.downloadFailureCount
                val skippedMediaCount = mediaDownloadResult.skippedMediaCount
                val totalMediaCount = mediaPlan.totalMediaCount

                val rawHtmlWriteResult = saveThreadRawHtmlIfEnabled(
                    enabled = rawHtmlOptions.enable,
                    fileSystem = fileSystem,
                    target = storageTarget,
                    threadId = threadId,
                    fetchOriginalHtml = { fetchThreadHtml(boardUrl, threadId) },
                    rewriteHtml = { originalHtml ->
                        withContext(AppDispatchers.parsing) {
                            rewriteOriginalHtml(
                                html = originalHtml,
                                boardPath = boardPath,
                                urlToPathMap = urlToPathMap,
                                stripExternalResources = rawHtmlOptions.stripExternalResources
                            )
                        }
                    },
                    measureAbsolutePathSize = fileSystem::getFileSize,
                    logWarning = { message ->
                        Logger.w("ThreadSaveService", message)
                    }
                )
                rawHtmlRelativePath = rawHtmlWriteResult.relativePath
                totalSize += rawHtmlWriteResult.sizeBytes

                // 変換フェーズ
                updateProgress(SavePhase.CONVERTING, 0, posts.size, "HTML変換中...")
                val savedPosts = buildThreadSaveSavedPosts(
                    posts = posts,
                    mediaKeyToFileInfoMap = mediaKeyToFileInfoMap,
                    urlToPathMap = urlToPathMap,
                    updateProgress = { current, total ->
                        updateProgress(
                            SavePhase.CONVERTING,
                            current,
                            total,
                            "投稿変換中... ($current/$total)"
                        )
                    }
                )

                // 完了フェーズ
                updateProgress(SavePhase.FINALIZING, 0, 1, "完了処理中...")

                updateProgress(SavePhase.FINALIZING, 1, 1, "完了")

                // メタデータを書き出す（オフライン復元とインデックス用） - 自動保存のみ
                if (existingMetadata != null && !storageOptions.clearExistingOutput) {
                    writeThreadSaveMetadataBackup(
                        target = storageTarget,
                        payload = existingMetadata.payload
                    )
                }
                val metadataSize = writeThreadSaveMetadataIfEnabled(
                    writeMetadata = writeMetadata,
                    fileSystem = fileSystem,
                    target = storageTarget,
                    request = ThreadSaveMetadataWriteRequest(
                        threadId = threadId,
                        boardId = boardId,
                        boardName = boardName,
                        boardUrl = boardUrl,
                        title = title,
                        storageId = storageId,
                        savedAtTimestamp = savedAtTimestamp,
                        expiresAtLabel = expiresAtLabel,
                        savedPosts = savedPosts,
                        rawHtmlRelativePath = rawHtmlRelativePath,
                        strippedExternalResources = rawHtmlOptions.stripExternalResources,
                        isTruncated = isTruncated,
                        truncationReason = truncationReason,
                        baseTotalSize = totalSize
                    ),
                    encodeMetadata = json::encodeToString
                )
                val finalTotalSize = totalSize + metadataSize
                enforceBudget(finalTotalSize, startedAtMillis, effectiveLimits.maxSaveDurationMs)
                if (storageOptions.pruneUnreferencedExistingMedia && previousMetadata != null) {
                    pruneUnreferencedThreadSaveMedia(
                        target = storageTarget,
                        previousMetadata = previousMetadata,
                        currentSavedPosts = savedPosts
                    )
                }

                buildThreadSaveSavedThread(
                    threadId = threadId,
                    boardId = boardId,
                    boardName = boardName,
                    title = title,
                    storageId = storageId,
                    thumbnailPath = mediaCounts.thumbnailPath,
                    savedAtTimestamp = savedAtTimestamp,
                    postCount = posts.size,
                    imageCount = mediaCounts.imageCount,
                    videoCount = mediaCounts.videoCount,
                    totalSize = finalTotalSize,
                    downloadFailureCount = downloadFailureCount,
                    skippedMediaCount = skippedMediaCount,
                    totalMediaCount = totalMediaCount,
                    isContentTruncated = isTruncated
                )
            })
        } catch (e: CancellationException) {
            if (!hasWrittenInitialMetadata && storageOptions.clearExistingOutput) {
                withContext(NonCancellable) {
                    runCatching {
                        cleanupThreadSaveFailedOutput(
                            fileSystem = fileSystem,
                            target = storageTarget
                        )
                    }.onFailure { cleanupError ->
                        Logger.w(
                            "ThreadSaveService",
                            "Failed to cleanup canceled save for $storageId: ${cleanupError.message}"
                        )
                    }
                }
            }
            throw e
        } catch (t: Throwable) {
            if (!hasWrittenInitialMetadata && storageOptions.clearExistingOutput) {
                runCatching {
                    cleanupThreadSaveFailedOutput(
                        fileSystem = fileSystem,
                        target = storageTarget
                    )
                }.onFailure { cleanupError ->
                    Logger.w("ThreadSaveService", "Failed to cleanup failed save for $storageId: ${cleanupError.message}")
                }
            }
            Result.failure(t)
        }
    }

    private data class ExistingThreadSaveMedia(
        val relativePath: String,
        val fileType: FileType
    )

    private data class ExistingThreadSaveMetadata(
        val metadata: SavedThreadMetadata,
        val payload: String
    )

    private suspend fun loadThreadSaveExistingMetadata(
        target: ThreadSaveStorageTarget
    ): ExistingThreadSaveMetadata? {
        return readThreadSaveMetadataPayload(target, "metadata.json")
            ?: readThreadSaveMetadataPayload(target, "metadata.json.backup")
    }

    private suspend fun readThreadSaveMetadataPayload(
        target: ThreadSaveStorageTarget,
        relativePath: String
    ): ExistingThreadSaveMetadata? {
        return runCatching {
            val payload = readThreadSaveText(target, relativePath)
            withContext(AppDispatchers.parsing) {
                ExistingThreadSaveMetadata(
                    metadata = json.decodeFromString<SavedThreadMetadata>(payload),
                    payload = payload
                )
            }
        }.getOrNull()
    }

    private suspend fun readThreadSaveText(
        target: ThreadSaveStorageTarget,
        relativePath: String
    ): String {
        return if (target.saveLocation != null) {
            fileSystem.readString(target.saveLocation, target.relativeStoragePath(relativePath)).getOrThrow()
        } else {
            fileSystem.readString(target.absoluteStoragePath(relativePath)).getOrThrow()
        }
    }

    private suspend fun writeThreadSaveMetadataBackup(
        target: ThreadSaveStorageTarget,
        payload: String
    ) {
        runCatching {
            if (target.saveLocation != null) {
                fileSystem.writeString(
                    target.saveLocation,
                    target.relativeStoragePath("metadata.json.backup"),
                    payload
                ).getOrThrow()
            } else {
                fileSystem.writeString(
                    target.absoluteStoragePath("metadata.json.backup"),
                    payload
                ).getOrThrow()
            }
        }.onFailure { error ->
            Logger.w(
                "ThreadSaveService",
                "Failed to write auto-save metadata backup: ${error.message}"
            )
        }
    }

    private suspend fun buildReusableThreadSaveMediaSeed(
        target: ThreadSaveStorageTarget,
        metadata: SavedThreadMetadata,
        boardPath: String,
        scheduledItems: List<ThreadSaveScheduledMediaItem>,
        opPostId: String?
    ): ThreadSaveMediaDownloadSeed {
        val existingMediaByKey = buildExistingThreadSaveMediaMap(metadata.posts)
        if (existingMediaByKey.isEmpty()) return ThreadSaveMediaDownloadSeed()
        val existingMediaByPath = existingMediaByKey.values.associateBy { it.relativePath }

        val urlToPathMap = linkedMapOf<String, String>()
        val mediaKeyToFileInfoMap = linkedMapOf<String, ThreadSaveLocalFileInfo>()
        val countedRelativePaths = linkedSetOf<String>()
        var mediaCounts = ThreadSaveMediaCounts()
        var totalSizeBytes = 0L

        scheduledItems.forEach { mediaItem ->
            val mediaKey = buildThreadSaveMediaDownloadKey(mediaItem.url, mediaItem.requestType)
            val existingMedia = existingMediaByKey[mediaKey]
                ?: resolveExpectedExistingThreadSaveMedia(
                    boardPath = boardPath,
                    mediaItem = mediaItem,
                    existingMediaByPath = existingMediaByPath
                )
                ?: return@forEach
            val sizeBytes = measureExistingThreadSaveMedia(target, existingMedia.relativePath) ?: return@forEach
            val fileInfo = ThreadSaveLocalFileInfo(
                relativePath = existingMedia.relativePath,
                fileType = existingMedia.fileType,
                byteSize = sizeBytes
            )
            mediaKeyToFileInfoMap[mediaKey] = fileInfo
            when (mediaItem.requestType) {
                ThreadSaveMediaRequestType.THUMBNAIL -> {
                    if (urlToPathMap[mediaItem.url] == null) {
                        urlToPathMap[mediaItem.url] = existingMedia.relativePath
                    }
                }
                ThreadSaveMediaRequestType.FULL_IMAGE -> {
                    urlToPathMap[mediaItem.url] = existingMedia.relativePath
                }
            }
            mediaCounts = updateThreadSaveMediaCounts(
                current = mediaCounts,
                fileType = existingMedia.fileType,
                relativePath = existingMedia.relativePath,
                postId = mediaItem.postId,
                opPostId = opPostId
            )
            if (countedRelativePaths.add(existingMedia.relativePath)) {
                totalSizeBytes += sizeBytes
            }
        }

        return ThreadSaveMediaDownloadSeed(
            urlToPathMap = urlToPathMap,
            mediaKeyToFileInfoMap = mediaKeyToFileInfoMap,
            mediaCounts = mediaCounts,
            totalSizeBytes = totalSizeBytes
        )
    }

    private fun buildExistingThreadSaveMediaMap(
        posts: List<SavedPost>
    ): Map<String, ExistingThreadSaveMedia> {
        val result = linkedMapOf<String, ExistingThreadSaveMedia>()
        posts.forEach { post ->
            addExistingThreadSaveMedia(
                result = result,
                url = post.originalThumbnailUrl,
                requestType = ThreadSaveMediaRequestType.THUMBNAIL,
                relativePath = post.localThumbnailPath,
                fileType = FileType.THUMBNAIL
            )
            addExistingThreadSaveMedia(
                result = result,
                url = post.originalImageUrl,
                requestType = ThreadSaveMediaRequestType.FULL_IMAGE,
                relativePath = post.localImagePath,
                fileType = FileType.FULL_IMAGE
            )
            addExistingThreadSaveMedia(
                result = result,
                url = post.originalVideoUrl,
                requestType = ThreadSaveMediaRequestType.FULL_IMAGE,
                relativePath = post.localVideoPath,
                fileType = FileType.VIDEO
            )
        }
        return result
    }

    private fun resolveExpectedExistingThreadSaveMedia(
        boardPath: String,
        mediaItem: ThreadSaveScheduledMediaItem,
        existingMediaByPath: Map<String, ExistingThreadSaveMedia>
    ): ExistingThreadSaveMedia? {
        val extension = getThreadSaveExtensionFromUrl(mediaItem.url)
            ?.lowercase()
            ?.takeIf(::isThreadSaveSupportedExtension)
            ?: return null
        val fileType = resolveThreadSaveFileType(mediaItem.requestType, extension)
        val fileName = resolveThreadSaveFileName(
            url = mediaItem.url,
            extension = extension,
            postId = mediaItem.postId,
            timestampMillis = 0L
        )
        val relativePath = buildThreadSaveRelativePath(boardPath, fileType, fileName)
        return existingMediaByPath[relativePath]?.takeIf { it.fileType == fileType }
    }

    private fun addExistingThreadSaveMedia(
        result: MutableMap<String, ExistingThreadSaveMedia>,
        url: String?,
        requestType: ThreadSaveMediaRequestType,
        relativePath: String?,
        fileType: FileType
    ) {
        val normalizedUrl = url?.takeIf { it.isNotBlank() } ?: return
        val normalizedPath = relativePath
            ?.trim()
            ?.takeIf(::isReusableThreadSaveRelativePath)
            ?: return
        val mediaKey = buildThreadSaveMediaDownloadKey(normalizedUrl, requestType)
        if (mediaKey !in result) {
            result[mediaKey] = ExistingThreadSaveMedia(
                relativePath = normalizedPath,
                fileType = fileType
            )
        }
    }

    private fun isReusableThreadSaveRelativePath(path: String): Boolean {
        return path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.startsWith("content://") &&
            !path.startsWith("file://") &&
            !path.contains("../") &&
            !path.contains("/..")
    }

    private suspend fun measureExistingThreadSaveMedia(
        target: ThreadSaveStorageTarget,
        relativePath: String
    ): Long? {
        return runCatching {
            val exists = if (target.saveLocation != null) {
                fileSystem.exists(target.saveLocation, target.relativeStoragePath(relativePath))
            } else {
                fileSystem.exists(target.absoluteStoragePath(relativePath))
            }
            if (!exists) return null
            val size = if (target.saveLocation != null) {
                fileSystem.getFileSize(target.saveLocation, target.relativeStoragePath(relativePath))
            } else {
                fileSystem.getFileSize(target.absoluteStoragePath(relativePath))
            }
            size.takeIf { it > 0L }
        }.getOrNull()
    }

    private suspend fun pruneUnreferencedThreadSaveMedia(
        target: ThreadSaveStorageTarget,
        previousMetadata: SavedThreadMetadata,
        currentSavedPosts: List<SavedPost>
    ) {
        val previousPaths = collectThreadSaveMediaPaths(previousMetadata.posts)
        if (previousPaths.isEmpty()) return
        val currentPaths = collectThreadSaveMediaPaths(currentSavedPosts)
        previousPaths
            .filterNot { it in currentPaths }
            .forEach { relativePath ->
                runCatching {
                    if (target.saveLocation != null) {
                        fileSystem.delete(target.saveLocation, target.relativeStoragePath(relativePath)).getOrThrow()
                    } else {
                        fileSystem.delete(target.absoluteStoragePath(relativePath)).getOrThrow()
                    }
                }.onFailure { error ->
                    Logger.w(
                        "ThreadSaveService",
                        "Failed to prune unreferenced auto-save media $relativePath: ${error.message}"
                    )
                }
            }
    }

    private fun collectThreadSaveMediaPaths(posts: List<SavedPost>): Set<String> {
        val result = linkedSetOf<String>()
        posts.forEach { post ->
            listOf(post.localThumbnailPath, post.localImagePath, post.localVideoPath)
                .forEach { path ->
                    path?.trim()
                        ?.takeIf(::isReusableThreadSaveRelativePath)
                        ?.let(result::add)
                }
        }
        return result
    }

    /**
     * メディアをダウンロードして即座にファイルに保存（メモリ効率的）
     * @param saveLocation 保存先 (Path/TreeUri/Bookmark)。nullの場合は従来の文字列パス使用
     */
    private suspend fun downloadAndSaveMedia(
        url: String,
        storageTarget: ThreadSaveStorageTarget,
        boardPath: String,
        requestType: ThreadSaveMediaRequestType,
        postId: String,
        startedAtMillis: Long,
        maxSaveDurationMs: Long
    ): Result<ThreadSaveLocalFileInfo> {
        return downloadAndStoreThreadSaveMedia(
            httpClient = httpClient,
            fileSystem = fileSystem,
            logTag = "ThreadSaveService",
            request = ThreadSaveMediaDownloadRequest(
                url = url,
                target = storageTarget,
                boardPath = boardPath,
                requestType = requestType,
                postId = postId,
                startedAtMillis = startedAtMillis,
                mediaRequestTimeoutMillis = MEDIA_REQUEST_TIMEOUT_MILLIS,
                maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
                maxSaveDurationMs = maxSaveDurationMs,
                streamReadBufferBytes = STREAM_READ_BUFFER_BYTES,
                maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
                zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
                readIdleTimeoutMillis = READ_IDLE_TIMEOUT_MILLIS,
                writeTimeoutMillis = WRITE_TIMEOUT_MILLIS,
                nowMillis = { Clock.System.now().toEpochMilliseconds() },
                withMediaWriteLock = { relativePath, block ->
                    withMediaWriteLock(relativePath, block)
                }
            )
        )
    }

    /**
     * 元HTMLをローカルファイル向けに調整
     */
    private fun rewriteOriginalHtml(
        html: String,
        boardPath: String,
        urlToPathMap: Map<String, String>,
        stripExternalResources: Boolean
    ): String {
        return rewriteSavedOriginalHtml(html, boardPath, urlToPathMap, stripExternalResources)
    }

    /**
     * 板URLから板パス部分を抽出（例: https://may.2chan.net/b -> b）
     */
    private fun extractBoardPath(boardUrl: String, boardIdFallback: String): String {
        return extractThreadSaveBoardPath(boardUrl, boardIdFallback)
    }

    /**
     * スレッドHTMLを取得（Shift_JISを維持したまま文字列化）
     */
    private suspend fun fetchThreadHtml(boardUrl: String, threadId: String): Result<String> {
        return fetchThreadSaveHtml(
            httpClient = httpClient,
            request = ThreadSaveHtmlFetchRequest(
                boardUrl = boardUrl,
                threadId = threadId,
                threadHtmlFetchTimeoutMillis = THREAD_HTML_FETCH_TIMEOUT_MILLIS,
                maxThreadHtmlBytes = MAX_THREAD_HTML_BYTES,
                streamReadBufferBytes = STREAM_READ_BUFFER_BYTES,
                maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
                zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
                readIdleTimeoutMillis = READ_IDLE_TIMEOUT_MILLIS
            )
        )
    }

    /**
     * URLから拡張子を取得
     */
    /**
     * 進捗を更新
     */
    private fun updateProgress(phase: SavePhase, current: Int, total: Int, currentItem: String) {
        _saveProgress.value = SaveProgress(phase, current, total, currentItem)
    }

    private suspend fun <T> withMediaWriteLock(relativePath: String, block: suspend () -> T): T {
        return withThreadSaveMediaWriteLock(
            relativePath = relativePath,
            mediaWriteLocksGuard = mediaWriteLocksGuard,
            mediaWriteLocks = mediaWriteLocks,
            block = block
        )
    }

    /**
     * 保存処理の総容量/時間を監視し、上限を超えたら例外で中断する
     */
    private fun enforceBudget(totalSizeBytes: Long, startedAtMillis: Long, maxSaveDurationMs: Long) {
        enforceThreadSaveBudget(
            totalSizeBytes = totalSizeBytes,
            startedAtMillis = startedAtMillis,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            maxTotalSizeBytes = MAX_TOTAL_SIZE_BYTES,
            maxSaveDurationMs = maxSaveDurationMs
        )
    }
}
