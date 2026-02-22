package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.text.RegexOption
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
    private val counterMutex = Mutex()
    private val json = Json { prettyPrint = true }

    // グローバルダウンロード並行数制限
    private val downloadSemaphore = Semaphore(MAX_PARALLEL_DOWNLOADS)

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
        private const val STREAM_READ_BUFFER_BYTES = 64 * 1024
        private const val MAX_ZERO_READ_RETRIES = 100
        private const val ZERO_READ_BACKOFF_MILLIS = 8L
        private const val MAX_PARALLEL_DOWNLOADS = 2

        // FIX: Regexプリコンパイル（パフォーマンス最適化）
        private val IMAGE_SRC_REGEX = Regex("""<img[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
        private val LINK_HREF_REGEX = Regex("""<a[^>]+href\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
        private val VIDEO_SRC_REGEX = Regex("""<video[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
        private val SOURCE_SRC_REGEX = Regex("""<source[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
        private val SCRIPT_SRC_REGEX = Regex("""(?si)<script[^>]+src="([^"]+)"[^>]*>.*?</script>""")
        private val IFRAME_SRC_REGEX = Regex("""(?si)<iframe[^>]+src="([^"]+)"[^>]*>.*?</iframe>""")
        private val CHARSET_REGEX = Regex("""<meta[^>]+charset\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
        private val CONTENT_TYPE_REGEX = Regex("""<meta[^>]+http-equiv\s*=\s*["']?Content-Type["']?[^>]+content\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
    }

    /**
     * Simple LRU cache implementation for common code
     */
    private fun <K, V> createLruCache(maxSize: Int): MutableMap<K, V> {
        return LruCache(maxSize)
    }

    private class LruCache<K, V>(private val maxSize: Int) : MutableMap<K, V> {
        private val backing = LinkedHashMap<K, V>()

        override val size: Int
            get() = backing.size

        override fun isEmpty(): Boolean = backing.isEmpty()

        override fun containsKey(key: K): Boolean = backing.containsKey(key)

        override fun containsValue(value: V): Boolean = backing.containsValue(value)

        override fun get(key: K): V? {
            val value = backing.remove(key) ?: return null
            backing[key] = value
            return value
        }

        override fun put(key: K, value: V): V? {
            val previous = backing.remove(key)
            backing[key] = value
            trimToSize()
            return previous
        }

        override fun putAll(from: Map<out K, V>) {
            from.forEach { (key, value) -> put(key, value) }
        }

        override fun remove(key: K): V? = backing.remove(key)

        override fun clear() {
            backing.clear()
        }

        override val keys: MutableSet<K>
            get() = backing.keys

        override val values: MutableCollection<V>
            get() = backing.values

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = backing.entries

        private fun trimToSize() {
            while (backing.size > maxSize) {
                val iterator = backing.entries.iterator()
                if (!iterator.hasNext()) return
                iterator.next()
                iterator.remove()
            }
        }
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
        try {
            Result.success(run {
            val savedAtTimestamp = Clock.System.now().toEpochMilliseconds()
            val startedAtMillis = savedAtTimestamp
            // 準備フェーズ
            updateProgress(SavePhase.PREPARING, 0, 1, "ディレクトリ作成中...")

            // SaveLocation対応: nullの場合は従来のパスベース、非nullの場合は新API使用
            val useSaveLocation = baseSaveLocation != null
            val storageId = buildThreadStorageId(boardId, threadId)
            val baseDir = "$baseDirectory/$storageId"
            val boardPath = extractBoardPath(boardUrl, boardId)
            val opPostId = posts.firstOrNull()?.id

            if (useSaveLocation) {
                val location = requireNotNull(baseSaveLocation) { "baseSaveLocation must not be null when useSaveLocation is true" }
                fileSystem.createDirectory(location, storageId).getOrThrow()
                val boardMediaPath = if (boardPath.isNotBlank()) "$storageId/$boardPath" else storageId
                fileSystem.createDirectory(location, "$boardMediaPath/src").getOrThrow()
                fileSystem.createDirectory(location, "$boardMediaPath/thumb").getOrThrow()
            } else {
                fileSystem.createDirectory(baseDirectory).getOrThrow()
                fileSystem.createDirectory(baseDir).getOrThrow()
                val boardMediaRoot = listOf(baseDir, boardPath)
                    .filter { it.isNotBlank() }
                    .joinToString("/")
                val srcDir = "$boardMediaRoot/src"
                val thumbDir = "$boardMediaRoot/thumb"
                fileSystem.createDirectory(boardMediaRoot).getOrThrow()
                fileSystem.createDirectory(srcDir).getOrThrow()
                fileSystem.createDirectory(thumbDir).getOrThrow()
            }

            // FIX: マップのサイズ制限とメモリ効率化
            // LRUキャッシュを使用して自動的に古いエントリを削除
            val urlToPathMap = createLruCache<String, String>(MAX_MEDIA_ITEMS)
            val urlToFileInfoMap = createLruCache<String, LocalFileInfo>(MAX_MEDIA_ITEMS)

            // ダウンロードフェーズ
            val savedPosts = mutableListOf<SavedPost>()
            var thumbnailPath: String? = null
            var imageCount = 0
            var videoCount = 0
            var totalSize = 0L
            var rawHtmlRelativePath: String? = null

            // FIX: Process posts in chunks to prevent memory accumulation
            // Instead of flatMap all at once, process in batches
            val chunkSize = 50 // Process 50 posts at a time
            var downloadFailureCount = 0

            // FIX: Build media items in chunks to avoid massive list creation
            // Use asSequence() to avoid creating intermediate collections
            val totalMediaCount = posts.asSequence().sumOf {
                (if (it.thumbnailUrl != null) 1 else 0) + (if (it.imageUrl != null) 1 else 0)
            }

            // FIX: メディア数が異常に多い場合は警告
            if (totalMediaCount > MAX_MEDIA_ITEMS) {
                Logger.w("ThreadSaveService", "Thread has $totalMediaCount media items (max: $MAX_MEDIA_ITEMS), some may be skipped")
            }

            var processedMediaCount = 0

            posts.chunked(chunkSize).forEach { postChunk ->
                coroutineContext.ensureActive()
                yield()
                enforceBudget(totalSize, startedAtMillis)
                val mediaItems = postChunk.flatMap { post ->
                    buildList {
                        post.thumbnailUrl?.let { add(MediaItem(it, MediaType.THUMBNAIL, post)) }
                        post.imageUrl?.let { add(MediaItem(it, MediaType.FULL_IMAGE, post)) }
                    }
                }

                // FIX: 並列ダウンロードで処理速度を改善（セマフォで並行数を制限）
                mediaItems.chunked(MAX_PARALLEL_DOWNLOADS).forEach { itemBatch ->
                    coroutineContext.ensureActive()
                    kotlinx.coroutines.coroutineScope {
                        val deferredResults = itemBatch.map { mediaItem ->
                            async(AppDispatchers.io) {
                                coroutineContext.ensureActive()
                                // セマフォでグローバルな並行数を制限
                                downloadSemaphore.withPermit {
                                    coroutineContext.ensureActive()
                                    val currentCount = counterMutex.withLock {
                                        if (processedMediaCount >= MAX_MEDIA_ITEMS) {
                                            null
                                        } else {
                                            ++processedMediaCount
                                        }
                                    }
                                    if (currentCount == null) {
                                        Logger.w("ThreadSaveService", "Skipping media item (exceeds MAX_MEDIA_ITEMS)")
                                        return@withPermit Pair<MediaItem?, Result<LocalFileInfo>?>(null, null)
                                    }

                                    updateProgress(
                                        SavePhase.DOWNLOADING,
                                        currentCount,
                                        minOf(totalMediaCount, MAX_MEDIA_ITEMS),
                                        "メディアダウンロード中... ($currentCount/${minOf(totalMediaCount, MAX_MEDIA_ITEMS)})"
                                    )

                                    // リトライロジック付きダウンロード
                                    var downloadResult: Result<LocalFileInfo>? = null
                                    var lastError: Throwable? = null
                                    for (attempt in 1..MAX_RETRIES) {
                                        coroutineContext.ensureActive()
                                        downloadResult = downloadAndSaveMedia(
                                            url = mediaItem.url,
                                            saveLocation = baseSaveLocation,
                                            baseDir = baseDir,
                                            boardPath = boardPath,
                                            storageId = storageId,
                                            type = mediaItem.type,
                                            postId = mediaItem.post.id,
                                            startedAtMillis = startedAtMillis
                                        )

                                        if (downloadResult.isSuccess) break

                                        lastError = downloadResult.exceptionOrNull()
                                        if (attempt < MAX_RETRIES) {
                                            Logger.w("ThreadSaveService", "Download attempt $attempt failed for ${mediaItem.url}, retrying...")
                                            delay(RETRY_DELAY_MS * attempt) // 指数バックオフ
                                        }
                                    }

                                    if (downloadResult?.isFailure == true) {
                                        Logger.e("ThreadSaveService", "Failed to download ${mediaItem.url} after $MAX_RETRIES attempts: ${lastError?.message}")
                                    }

                                    Pair<MediaItem?, Result<LocalFileInfo>?>(mediaItem, downloadResult)
                                }
                            }
                        }

                        // すべての並列ダウンロードの完了を待つ
                        deferredResults.forEach { deferred ->
                            coroutineContext.ensureActive()
                            val (mediaItem, downloadResult) = deferred.await()
                            if (mediaItem == null || downloadResult == null) {
                                downloadFailureCount++
                                return@forEach
                            }

                            downloadResult
                                .onSuccess { fileInfo ->
                                    totalSize += fileInfo.byteSize
                                    enforceBudget(totalSize, startedAtMillis)
                                    // URL→ローカルパスマッピングに追加
                                    urlToPathMap[mediaItem.url] = fileInfo.relativePath
                                    urlToFileInfoMap[mediaItem.url] = fileInfo

                                    when (fileInfo.fileType) {
                                        FileType.THUMBNAIL -> {
                                            if (thumbnailPath == null && mediaItem.post.id == opPostId) {
                                                thumbnailPath = fileInfo.relativePath
                                            }
                                        }
                                        FileType.FULL_IMAGE -> imageCount++
                                        FileType.VIDEO -> videoCount++
                                    }
                                }
                                .onFailure { error ->
                                    // ダウンロード失敗はログに記録してスキップ
                                    downloadFailureCount++
                                    Logger.e("ThreadSaveService", "Failed to download ${mediaItem.url}: ${error.message}")
                                }
                        }
                    }
                }
            }

            // 元HTML保存（リンクを書き換え、外部JS/広告を除去）
            if (rawHtmlOptions.enable) {
                fetchThreadHtml(boardUrl, threadId)
                    .onSuccess { originalHtml ->
                        val rewritten = rewriteOriginalHtml(
                            html = originalHtml,
                            boardPath = boardPath,
                            urlToPathMap = urlToPathMap,
                            stripExternalResources = rawHtmlOptions.stripExternalResources
                        )
                        val fileName = "$threadId.htm"
                        if (baseSaveLocation != null) {
                            fileSystem.writeString(baseSaveLocation, "$storageId/$fileName", rewritten).getOrThrow()
                            // SaveLocation版ではファイルサイズ取得は省略（getFileSize未実装のため）
                            totalSize += utf8ByteLength(rewritten)
                        } else {
                            val fullPath = "$baseDir/$fileName"
                            fileSystem.writeString(fullPath, rewritten).getOrThrow()
                            totalSize += fileSystem.getFileSize(fullPath)
                        }
                        rawHtmlRelativePath = fileName
                    }
                    .onFailure { error ->
                        Logger.w("ThreadSaveService", "Failed to save raw HTML: ${error.message}")
                    }
            }

            // 変換フェーズ
            updateProgress(SavePhase.CONVERTING, 0, posts.size, "HTML変換中...")

            posts.forEachIndexed { index, post ->
                coroutineContext.ensureActive()
                if (index % 32 == 0) {
                    yield()
                }
                updateProgress(
                    SavePhase.CONVERTING,
                    index + 1,
                    posts.size,
                    "投稿変換中... (${index + 1}/${posts.size})"
                )

                // マッピングからローカルパスを取得（再ダウンロードしない）
                val imageFileInfo = post.imageUrl?.let { urlToFileInfoMap[it] }
                val thumbnailFileInfo = post.thumbnailUrl?.let { urlToFileInfoMap[it] }
                val localImagePath = imageFileInfo
                    ?.takeIf { it.fileType == FileType.FULL_IMAGE }
                    ?.relativePath
                val originalImageUrl = post.imageUrl
                    ?.takeIf { imageFileInfo?.fileType == FileType.FULL_IMAGE }
                val localVideoPath = imageFileInfo
                    ?.takeIf { it.fileType == FileType.VIDEO }
                    ?.relativePath
                val originalVideoUrl = post.imageUrl
                    ?.takeIf { imageFileInfo?.fileType == FileType.VIDEO }
                val localThumbnailPath = thumbnailFileInfo?.relativePath

                // HTML内のURLを相対パスに変換
                val convertedHtml = convertHtmlPaths(post.messageHtml, urlToPathMap)

                savedPosts.add(
                    SavedPost(
                        id = post.id,
                        order = post.order,
                        author = post.author,
                        subject = post.subject,
                        timestamp = post.timestamp,
                        messageHtml = convertedHtml,
                        originalImageUrl = originalImageUrl,
                        localImagePath = localImagePath,
                        originalVideoUrl = originalVideoUrl,
                        localVideoPath = localVideoPath,
                        originalThumbnailUrl = post.thumbnailUrl,
                        localThumbnailPath = localThumbnailPath,
                        downloadSuccess = post.imageUrl == null || localImagePath != null || localVideoPath != null
                    )
                )
            }

            // 完了フェーズ
            updateProgress(SavePhase.FINALIZING, 0, 1, "完了処理中...")

            updateProgress(SavePhase.FINALIZING, 1, 1, "完了")

            // ステータスを失敗数に応じて設定
            val status = when {
                downloadFailureCount == 0 -> SaveStatus.COMPLETED
                downloadFailureCount < totalMediaCount -> SaveStatus.PARTIAL
                else -> SaveStatus.FAILED
            }

            // メタデータを書き出す（オフライン復元とインデックス用） - 自動保存のみ
            val metadataSize = if (writeMetadata) {
                val metadata = SavedThreadMetadata(
                    threadId = threadId,
                    boardId = boardId,
                    boardName = boardName,
                    boardUrl = boardUrl,
                    title = title,
                    storageId = storageId,
                    savedAt = savedAtTimestamp,
                    expiresAtLabel = expiresAtLabel,
                    posts = savedPosts,
                    totalSize = 0L, // 一旦0、書き込み後に更新
                    rawHtmlPath = rawHtmlRelativePath,
                    strippedExternalResources = rawHtmlOptions.stripExternalResources,
                    version = 1
                )
                val (metadataPayload, payloadSize) = buildMetadataPayloadWithStableSize(
                    metadata = metadata,
                    baseTotalSize = totalSize
                )

                if (baseSaveLocation != null) {
                    val metadataRelativePath = "$storageId/metadata.json"
                    fileSystem.writeString(baseSaveLocation, metadataRelativePath, metadataPayload).getOrThrow()
                    payloadSize
                } else {
                    val metadataPath = "$baseDir/metadata.json"
                    fileSystem.writeString(metadataPath, metadataPayload).getOrThrow()
                    payloadSize
                }
            } else {
                0L
            }
            val finalTotalSize = totalSize + metadataSize
            enforceBudget(finalTotalSize, startedAtMillis)

                SavedThread(
                threadId = threadId,
                boardId = boardId,
                boardName = boardName,
                title = title,
                storageId = storageId,
                thumbnailPath = thumbnailPath,
                savedAt = savedAtTimestamp,
                postCount = posts.size,
                imageCount = imageCount,
                videoCount = videoCount,
                totalSize = finalTotalSize,
                status = status
                )
            })
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
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
        type: MediaType,
        postId: String,
        startedAtMillis: Long
    ): Result<LocalFileInfo> = withContext(AppDispatchers.io) {
        try {
            Result.success(run {
                // Download media directly and inspect headers from GET response
                val response: HttpResponse = withTimeoutOrNull(MEDIA_REQUEST_TIMEOUT_MILLIS) {
                    httpClient.get(url) {
                        headers[HttpHeaders.Accept] = "image/*,video/*;q=0.8,*/*;q=0.2"
                    }
                } ?: throw IllegalStateException("Download request timed out after ${MEDIA_REQUEST_TIMEOUT_MILLIS}ms: $url")
                try {
                    if (!response.status.isSuccess()) {
                        throw Exception("Download failed: ${response.status}")
                    }

                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                    if (contentLength > MAX_FILE_SIZE_BYTES) {
                        throw Exception("File too large: ${contentLength / 1024}KB (max: 8000KB)")
                    }

                    val extension = (getExtensionFromUrl(url)
                        ?: getExtensionFromContentType(response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }))
                        .lowercase()
                    val isSupported = extension in SUPPORTED_IMAGE_EXTENSIONS || extension in SUPPORTED_VIDEO_EXTENSIONS
                    if (!isSupported) {
                        throw Exception("Unsupported file type: $extension")
                    }

                    val fileType = when {
                        type == MediaType.THUMBNAIL -> FileType.THUMBNAIL
                        extension in SUPPORTED_VIDEO_EXTENSIONS -> FileType.VIDEO
                        else -> FileType.FULL_IMAGE
                    }
                    val fileName = extractFileName(url, extension, postId)
                    val boardPrefix = boardPath.trim('/').takeIf { it.isNotEmpty() }?.let { "$it/" } ?: ""
                    val subDir = when (fileType) {
                        FileType.THUMBNAIL -> "${boardPrefix}thumb"
                        FileType.FULL_IMAGE, FileType.VIDEO -> "${boardPrefix}src"
                    }
                    val relativePath = "$subDir/$fileName"

                    if (Clock.System.now().toEpochMilliseconds() - startedAtMillis > MAX_SAVE_DURATION_MS) {
                        throw IllegalStateException("Save aborted: exceeded time limit during download")
                    }

                    val totalBytesRead = if (saveLocation != null) {
                        val fileRelativePath = "$storageId/$relativePath"
                        var completed = false
                        try {
                            val writtenBytes = streamResponseToStorage(
                                response = response,
                                saveLocation = saveLocation,
                                saveLocationPath = fileRelativePath,
                                absolutePath = null,
                                startedAtMillis = startedAtMillis
                            )
                            completed = true
                            writtenBytes
                        } finally {
                            if (!completed) {
                                fileSystem.delete(saveLocation, fileRelativePath).getOrNull()
                            }
                        }
                    } else {
                        val fullPath = "$baseDir/$relativePath"
                        var completed = false
                        try {
                            val writtenBytes = streamResponseToStorage(
                                response = response,
                                saveLocation = null,
                                saveLocationPath = null,
                                absolutePath = fullPath,
                                startedAtMillis = startedAtMillis
                            )
                            completed = true
                            writtenBytes
                        } finally {
                            if (!completed) {
                                fileSystem.delete(fullPath).getOrNull()
                            }
                        }
                    }

                    LocalFileInfo(
                        relativePath = relativePath,
                        fileType = fileType,
                        byteSize = totalBytesRead
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

    private suspend fun streamResponseToStorage(
        response: HttpResponse,
        saveLocation: SaveLocation?,
        saveLocationPath: String?,
        absolutePath: String?,
        startedAtMillis: Long
    ): Long {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var totalBytesRead = 0L
        var zeroReadCount = 0
        var readLoopCount = 0L

        if (saveLocation != null && saveLocationPath != null) {
            fileSystem.delete(saveLocation, saveLocationPath).getOrNull()
        } else if (absolutePath != null) {
            fileSystem.delete(absolutePath).getOrNull()
        }

        while (true) {
            coroutineContext.ensureActive()
            val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) {
                channel.readAvailable(buffer, 0, buffer.size)
            } ?: throw IllegalStateException("Save aborted: timed out waiting for media stream data")

            if (read == -1) break
            if (read == 0) {
                zeroReadCount += 1
                if (zeroReadCount >= MAX_ZERO_READ_RETRIES) {
                    throw IllegalStateException("Save aborted: media stream stalled")
                }
                delay(ZERO_READ_BACKOFF_MILLIS)
                continue
            }

            zeroReadCount = 0
            totalBytesRead += read
            if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
                throw Exception("Actual file size exceeds limit: ${totalBytesRead / 1024}KB")
            }
            if (Clock.System.now().toEpochMilliseconds() - startedAtMillis > MAX_SAVE_DURATION_MS) {
                throw IllegalStateException("Save aborted: exceeded time limit during download")
            }

            val chunk = if (read == buffer.size) {
                buffer
            } else {
                buffer.copyOf(read)
            }
            if (saveLocation != null && saveLocationPath != null) {
                fileSystem.appendBytes(saveLocation, saveLocationPath, chunk).getOrThrow()
            } else if (absolutePath != null) {
                fileSystem.appendBytes(absolutePath, chunk).getOrThrow()
            } else {
                throw IllegalStateException("No target path specified for media stream")
            }
            readLoopCount += 1
            if (readLoopCount % 32L == 0L) {
                yield()
            }
        }

        return totalBytesRead
    }

    /**
     * HTML内のパスを相対パスに変換
     */
    private fun convertHtmlPaths(html: String, urlToPathMap: Map<String, String>): String {
        var converted = html

        // FIX: プリコンパイル済みのRegexを使用（パフォーマンス最適化）
        // 画像URLを相対パスに変換
        converted = IMAGE_SRC_REGEX.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // リンクURLを相対パスに変換
        converted = LINK_HREF_REGEX.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // videoタグのsrcを相対パスに変換
        converted = VIDEO_SRC_REGEX.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // sourceタグのsrcも相対パスに変換
        converted = SOURCE_SRC_REGEX.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        return converted
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
        var updated = html
        if (stripExternalResources) {
            updated = stripExternalScriptsAndIframes(updated)
        }
        updated = replaceMediaPaths(updated, boardPath, urlToPathMap)
        updated = forceUtf8Charset(updated)
        return updated
    }

    private fun stripExternalScriptsAndIframes(html: String): String {
        // FIX: プリコンパイル済みのRegexを使用（パフォーマンス最適化）
        fun shouldStrip(url: String): Boolean {
            val normalized = url.lowercase()
            return normalized.contains("/bin/") || normalized.contains("dec.2chan.net")
        }
        var updated = SCRIPT_SRC_REGEX.replace(html) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1).orEmpty()
            if (shouldStrip(src)) "" else matchResult.value
        }
        updated = IFRAME_SRC_REGEX.replace(updated) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1).orEmpty()
            if (shouldStrip(src)) "" else matchResult.value
        }
        return updated
    }

    /**
     * 書き出しはUTF-8固定なので charset をUTF-8に置き換える
     */
    private fun forceUtf8Charset(html: String): String {
        // FIX: プリコンパイル済みのRegexを使用（パフォーマンス最適化）
        var updated = CHARSET_REGEX.replace(html) { matchResult ->
            matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
        }
        updated = CONTENT_TYPE_REGEX.replace(updated) { matchResult ->
            matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
        }
        return updated
    }

    /**
     * Futabaの src/thumb をローカルパスに差し替える
     */
    private fun replaceMediaPaths(
        html: String,
        boardPath: String,
        urlToPathMap: Map<String, String>
    ): String {
        var updated = html
        // まずはダウンロード時に記録したURLを優先して置き換え
        urlToPathMap.forEach { (original, relative) ->
            updated = updated.replace(original, relative)
        }

        val normalizedBoard = boardPath.trim('/').takeIf { it.isNotEmpty() } ?: return updated
        val escapedBoard = Regex.escape(normalizedBoard)

        val srcPatterns = listOf(
            Regex("https?://[^\"'>]+/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
            Regex("//[^\"'>]+/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
            Regex("/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
        )
        val thumbPatterns = listOf(
            Regex("https?://[^\"'>]+/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
            Regex("//[^\"'>]+/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
            Regex("/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
        )

        srcPatterns.forEach { regex ->
            updated = regex.replace(updated) { matchResult ->
                "$normalizedBoard/src/${matchResult.groupValues[1]}"
            }
        }
        thumbPatterns.forEach { regex ->
            updated = regex.replace(updated) { matchResult ->
                "$normalizedBoard/thumb/${matchResult.groupValues[1]}"
            }
        }
        return updated
    }

    /**
     * 板URLから板パス部分を抽出（例: https://may.2chan.net/b -> b）
     */
    private fun extractBoardPath(boardUrl: String, boardIdFallback: String): String {
        val fallback = boardIdFallback.trim('/').ifEmpty { "b" }
        return runCatching {
            val base = BoardUrlResolver.resolveBoardBaseUrl(boardUrl)
            val afterHost = base.substringAfter("://", base)
            val path = afterHost.substringAfter('/', "").trim('/')
            path.ifEmpty { fallback }
        }.getOrElse { fallback }
    }

    private fun buildMetadataPayloadWithStableSize(
        metadata: SavedThreadMetadata,
        baseTotalSize: Long
    ): Pair<String, Long> {
        var estimatedSize = 0L
        var payload = json.encodeToString(metadata.copy(totalSize = baseTotalSize))
        repeat(8) {
            val size = utf8ByteLength(payload)
            if (size == estimatedSize) {
                return payload to size
            }
            estimatedSize = size
            payload = json.encodeToString(metadata.copy(totalSize = baseTotalSize + size))
        }
        val finalSize = utf8ByteLength(payload)
        return payload to finalSize
    }

    private fun utf8ByteLength(value: String): Long {
        var total = 0L
        var index = 0
        while (index < value.length) {
            val code = value[index].code
            val nextCode = value.getOrNull(index + 1)?.code
            val hasSurrogatePair =
                code in 0xD800..0xDBFF &&
                    nextCode != null &&
                    nextCode in 0xDC00..0xDFFF
            total += when {
                code <= 0x7F -> 1L
                code <= 0x7FF -> 2L
                hasSurrogatePair -> {
                    index += 1
                    4L
                }
                else -> 3L
            }
            index += 1
        }
        return total
    }

    /**
     * スレッドHTMLを取得（Shift_JISを維持したまま文字列化）
     */
    private suspend fun fetchThreadHtml(boardUrl: String, threadId: String): Result<String> = withContext(AppDispatchers.io) {
        try {
            val html = withTimeoutOrNull(THREAD_HTML_FETCH_TIMEOUT_MILLIS) {
                val threadUrl = BoardUrlResolver.resolveThreadUrl(boardUrl, threadId)
                val response: HttpResponse = httpClient.get(threadUrl) {
                    headers[HttpHeaders.Referrer] = BoardUrlResolver.resolveBoardBaseUrl(boardUrl)
                }
                try {
                    if (!response.status.isSuccess()) {
                        throw Exception("Fetch thread HTML failed: ${response.status}")
                    }
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_THREAD_HTML_BYTES) {
                        throw IllegalStateException("Thread HTML is too large: ${contentLength / 1024}KB")
                    }
                    val bodyBytes = readResponseBytesWithLimit(
                        response = response,
                        maxBytes = MAX_THREAD_HTML_BYTES.toInt()
                    )
                    TextEncoding.decodeToString(bodyBytes, response.headers[HttpHeaders.ContentType])
                } finally {
                    runCatching { response.bodyAsChannel().cancel() }
                }
            } ?: throw IllegalStateException("Fetch thread HTML timed out after ${THREAD_HTML_FETCH_TIMEOUT_MILLIS}ms")
            Result.success(html)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun readResponseBytesWithLimit(
        response: HttpResponse,
        maxBytes: Int
    ): ByteArray {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(STREAM_READ_BUFFER_BYTES)
        var output = ByteArray(minOf(STREAM_READ_BUFFER_BYTES, maxBytes.coerceAtLeast(1)))
        var totalBytes = 0
        var zeroReadCount = 0
        var readLoopCount = 0L

        try {
            while (true) {
                coroutineContext.ensureActive()
                val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) {
                    channel.readAvailable(buffer, 0, buffer.size)
                } ?: throw IllegalStateException("Thread HTML read stalled")

                if (read == -1) break
                if (read == 0) {
                    zeroReadCount += 1
                    if (zeroReadCount >= MAX_ZERO_READ_RETRIES) {
                        throw IllegalStateException("Thread HTML read stalled")
                    }
                    delay(ZERO_READ_BACKOFF_MILLIS)
                    continue
                }

                zeroReadCount = 0
                val requiredSize = totalBytes + read
                if (requiredSize > maxBytes) {
                    throw IllegalStateException("Thread HTML is too large")
                }
                if (requiredSize > output.size) {
                    var newSize = output.size
                    while (newSize < requiredSize) {
                        newSize = (newSize * 2).coerceAtMost(maxBytes)
                        if (newSize == output.size) break
                    }
                    if (newSize < requiredSize) {
                        throw IllegalStateException("Failed to expand thread HTML buffer safely")
                    }
                    output = output.copyOf(newSize)
                }
                buffer.copyInto(output, destinationOffset = totalBytes, startIndex = 0, endIndex = read)
                totalBytes = requiredSize
                readLoopCount += 1
                if (readLoopCount % 32L == 0L) {
                    yield()
                }
            }
        } finally {
            runCatching { channel.cancel() }
        }

        return if (totalBytes == output.size) {
            output
        } else {
            output.copyOf(totalBytes)
        }
    }

    /**
     * URLから拡張子を取得
     */
    private fun getExtensionFromUrl(url: String): String? {
        val sanitized = url
            .substringBefore('#')
            .substringBefore('?')
        return sanitized.substringAfterLast('.', "").takeIf { it.length in 3..4 }
    }

    /**
     * ContentTypeから拡張子を取得
     */
    private fun getExtensionFromContentType(contentType: ContentType?): String {
        return when (contentType?.contentSubtype) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            "gif" -> "gif"
            "webp" -> "webp"
            "mp4" -> "mp4"
            "webm" -> "webm"
            else -> "jpg"
        }
    }

    /**
     * 進捗を更新
     */
    private fun updateProgress(phase: SavePhase, current: Int, total: Int, currentItem: String) {
        _saveProgress.value = SaveProgress(phase, current, total, currentItem)
    }

    /**
     * メディアタイプ
     */
    private enum class MediaType {
        THUMBNAIL,
        FULL_IMAGE
    }

    private fun extractFileName(url: String, extension: String, postId: String): String {
        val candidate = url
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
        return candidate ?: "${postId}_${Clock.System.now().toEpochMilliseconds()}.$extension"
    }

    private data class LocalFileInfo(
        val relativePath: String,
        val fileType: FileType,
        val byteSize: Long
    )

    /**
     * 保存処理の総容量/時間を監視し、上限を超えたら例外で中断する
     */
    private fun enforceBudget(totalSizeBytes: Long, startedAtMillis: Long) {
        val elapsed = Clock.System.now().toEpochMilliseconds() - startedAtMillis
        if (totalSizeBytes > MAX_TOTAL_SIZE_BYTES) {
            throw IllegalStateException("Save aborted: total size exceeds limit (${totalSizeBytes / (1024 * 1024)}MB > ${MAX_TOTAL_SIZE_BYTES / (1024 * 1024)}MB)")
        }
        if (elapsed > MAX_SAVE_DURATION_MS) {
            throw IllegalStateException("Save aborted: exceeded time limit (${elapsed / 1000}s > ${MAX_SAVE_DURATION_MS / 1000}s)")
        }
    }

    /**
     * メディアアイテム
     */
    private data class MediaItem(
        val url: String,
        val type: MediaType,
        val post: Post
    )
}
