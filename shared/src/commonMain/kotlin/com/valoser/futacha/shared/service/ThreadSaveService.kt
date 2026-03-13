package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.encodeToString

/**
 * 元HTML保存や外部リソース削除のオプション
 */
data class RawHtmlSaveOptions(
    val enable: Boolean = true,
    val stripExternalResources: Boolean = true
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
    private val json = Json { prettyPrint = true }

    companion object {
        // ファイルサイズ制限: 8000KB = 8,192,000 bytes
        private const val MAX_FILE_SIZE_BYTES = 8_192_000L
        private const val MAX_THREAD_HTML_BYTES = 5 * 1024 * 1024L
        private const val MAX_TOTAL_SIZE_BYTES = 8L * 1024 * 1024 * 1024 // 約8GBまで
        private const val MAX_SAVE_DURATION_MS = 5 * 60 * 1000L // 5分上限

        // サポートされる拡張子
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf("webm", "mp4")

        // FIX: 最大メディア数を制限
        private const val MAX_MEDIA_ITEMS = 10000

        // リトライ設定
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        // Timeout for media body retrieval.
        private const val READ_IDLE_TIMEOUT_MILLIS = 15_000L
        private const val MEDIA_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val THREAD_HTML_FETCH_TIMEOUT_MILLIS = 30_000L
        private const val STORAGE_LOCK_WAIT_TIMEOUT_MILLIS = 120_000L
        private const val WRITE_TIMEOUT_MILLIS = 60_000L
        private const val STREAM_READ_BUFFER_BYTES = 64 * 1024
        private const val MAX_ZERO_READ_RETRIES = 100
        private const val ZERO_READ_BACKOFF_MILLIS = 25L
        private const val MAX_PARALLEL_DOWNLOADS = 1
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
        baseSaveLocation: SaveLocation? = null,
        baseDirectory: String = MANUAL_SAVE_DIRECTORY,
        writeMetadata: Boolean = baseDirectory == AUTO_SAVE_DIRECTORY,
        rawHtmlOptions: RawHtmlSaveOptions = RawHtmlSaveOptions()
    ): Result<SavedThread> = withContext(AppDispatchers.io) {
        val storageId = buildThreadStorageId(boardId, threadId)
        val storageLockKey = buildThreadStorageLockKey(
            storageId = storageId,
            baseDirectory = baseDirectory,
            baseSaveLocation = baseSaveLocation
        )
        val saveResult = ThreadStorageLockRegistry.withStorageLockOrNull(
            storageId = storageLockKey,
            waitTimeoutMillis = STORAGE_LOCK_WAIT_TIMEOUT_MILLIS
        ) {
            try {
                Result.success(run {
                val savedAtTimestamp = Clock.System.now().toEpochMilliseconds()
                val startedAtMillis = savedAtTimestamp
                // 準備フェーズ
                updateProgress(SavePhase.PREPARING, 0, 1, "ディレクトリ作成中...")

                val baseDir = "$baseDirectory/$storageId"
                val boardPath = extractBoardPath(boardUrl, boardId)
                val opPostId = posts.firstOrNull()?.id

                prepareThreadSaveOutput(
                    fileSystem = fileSystem,
                    saveLocation = baseSaveLocation,
                    request = ThreadSaveOutputPreparationRequest(
                        baseDirectory = baseDirectory,
                        storageId = storageId,
                        boardPath = boardPath
                    )
                )

            // ダウンロードフェーズ
            var rawHtmlRelativePath: String? = null
            val mediaPlan = buildThreadSaveMediaDownloadPlan(
                posts = posts,
                maxMediaItems = MAX_MEDIA_ITEMS
            )

            // FIX: メディア数が異常に多い場合は警告
            if (mediaPlan.totalMediaCount > MAX_MEDIA_ITEMS) {
                Logger.w(
                    "ThreadSaveService",
                    "Thread has ${mediaPlan.totalMediaCount} media items (max: $MAX_MEDIA_ITEMS), some may be skipped"
                )
            }

            val mediaDownloadResult = executeThreadSaveMediaDownloadPlan(
                plan = mediaPlan,
                opPostId = opPostId,
                chunkSize = 50,
                maxParallelDownloads = MAX_PARALLEL_DOWNLOADS,
                maxRetries = MAX_RETRIES,
                retryDelayMillis = RETRY_DELAY_MS,
                logTag = "ThreadSaveService",
                createUrlToPathMap = { createThreadSaveLruCache(MAX_MEDIA_ITEMS) },
                createMediaKeyToFileInfoMap = { createThreadSaveLruCache(MAX_MEDIA_ITEMS) },
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
                        saveLocation = baseSaveLocation,
                        baseDir = baseDir,
                        boardPath = boardPath,
                        storageId = storageId,
                        requestType = mediaItem.requestType,
                        postId = mediaItem.postId,
                        startedAtMillis = startedAtMillis
                    )
                },
                enforceBudget = { totalSizeBytes ->
                    enforceBudget(totalSizeBytes, startedAtMillis)
                }
            )
            val urlToPathMap = mediaDownloadResult.urlToPathMap
            val mediaKeyToFileInfoMap = mediaDownloadResult.mediaKeyToFileInfoMap
            val mediaCounts = mediaDownloadResult.mediaCounts
            var totalSize = mediaDownloadResult.totalSizeBytes
            val downloadFailureCount = mediaDownloadResult.downloadFailureCount
            val totalMediaCount = mediaPlan.totalMediaCount

            val rawHtmlWriteResult = saveThreadRawHtmlIfEnabled(
                enabled = rawHtmlOptions.enable,
                fileSystem = fileSystem,
                saveLocation = baseSaveLocation,
                storageId = storageId,
                baseDir = baseDir,
                threadId = threadId,
                fetchOriginalHtml = { fetchThreadHtml(boardUrl, threadId) },
                rewriteHtml = { originalHtml ->
                    rewriteOriginalHtml(
                        html = originalHtml,
                        boardPath = boardPath,
                        urlToPathMap = urlToPathMap,
                        stripExternalResources = rawHtmlOptions.stripExternalResources
                    )
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
            val metadataSize = writeThreadSaveMetadataIfEnabled(
                writeMetadata = writeMetadata,
                fileSystem = fileSystem,
                saveLocation = baseSaveLocation,
                baseDir = baseDir,
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
                    baseTotalSize = totalSize
                ),
                encodeMetadata = json::encodeToString
            )
            val finalTotalSize = totalSize + metadataSize
            enforceBudget(finalTotalSize, startedAtMillis)

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
                totalMediaCount = totalMediaCount
                )
            })
            } catch (e: CancellationException) {
                runCatching {
                    cleanupThreadSaveFailedOutput(
                        fileSystem = fileSystem,
                        baseSaveLocation = baseSaveLocation,
                        baseDirectory = baseDirectory,
                        storageId = storageId
                    )
                }.onFailure { cleanupError ->
                    Logger.w("ThreadSaveService", "Failed to cleanup canceled save for $storageId: ${cleanupError.message}")
                }
                throw e
            } catch (t: Throwable) {
                runCatching {
                    cleanupThreadSaveFailedOutput(
                        fileSystem = fileSystem,
                        baseSaveLocation = baseSaveLocation,
                        baseDirectory = baseDirectory,
                        storageId = storageId
                    )
                }.onFailure { cleanupError ->
                    Logger.w("ThreadSaveService", "Failed to cleanup failed save for $storageId: ${cleanupError.message}")
                }
                Result.failure(t)
            }
        }
        saveResult ?: Result.failure(
            IllegalStateException("保存処理が混雑しています。しばらく待ってから再試行してください。")
        )
    }

    /**
     * メディアをダウンロードして即座にファイルに保存（メモリ効率的）
     * @param saveLocation 保存先 (Path/TreeUri/Bookmark)。nullの場合は従来の文字列パス使用
     */
    private suspend fun downloadAndSaveMedia(
        url: String,
        saveLocation: SaveLocation?,
        baseDir: String,
        boardPath: String,
        storageId: String,
        requestType: ThreadSaveMediaRequestType,
        postId: String,
        startedAtMillis: Long
    ): Result<ThreadSaveLocalFileInfo> {
        return downloadAndStoreThreadSaveMedia(
            httpClient = httpClient,
            fileSystem = fileSystem,
            logTag = "ThreadSaveService",
            url = url,
            saveLocation = saveLocation,
            baseDir = baseDir,
            boardPath = boardPath,
            storageId = storageId,
            requestType = requestType,
            postId = postId,
            startedAtMillis = startedAtMillis,
            mediaRequestTimeoutMillis = MEDIA_REQUEST_TIMEOUT_MILLIS,
            maxFileSizeBytes = MAX_FILE_SIZE_BYTES,
            maxSaveDurationMs = MAX_SAVE_DURATION_MS,
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
            boardUrl = boardUrl,
            threadId = threadId,
            threadHtmlFetchTimeoutMillis = THREAD_HTML_FETCH_TIMEOUT_MILLIS,
            maxThreadHtmlBytes = MAX_THREAD_HTML_BYTES,
            streamReadBufferBytes = STREAM_READ_BUFFER_BYTES,
            maxZeroReadRetries = MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ZERO_READ_BACKOFF_MILLIS,
            readIdleTimeoutMillis = READ_IDLE_TIMEOUT_MILLIS
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
    private fun enforceBudget(totalSizeBytes: Long, startedAtMillis: Long) {
        enforceThreadSaveBudget(
            totalSizeBytes = totalSizeBytes,
            startedAtMillis = startedAtMillis,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            maxTotalSizeBytes = MAX_TOTAL_SIZE_BYTES,
            maxSaveDurationMs = MAX_SAVE_DURATION_MS
        )
    }
}
