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
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    companion object {
        // ファイルサイズ制限: 8000KB = 8,192,000 bytes
        private const val MAX_FILE_SIZE_BYTES = 8_192_000L
        private const val MAX_TOTAL_SIZE_BYTES = 8L * 1024 * 1024 * 1024 // 約8GBまで
        private const val MAX_SAVE_DURATION_MS = 5 * 60 * 1000L // 5分上限

        // サポートされる拡張子
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf("webm", "mp4")

        // FIX: 最大メディア数を制限
        private const val MAX_MEDIA_ITEMS = 10000
    }

    private fun <K, V> trimMap(map: LinkedHashMap<K, V>) {
        while (map.size > MAX_MEDIA_ITEMS) {
            val firstKey = map.keys.firstOrNull() ?: break
            map.remove(firstKey)
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
    ): Result<SavedThread> = withContext(Dispatchers.Default) {
        runCatching {
            val savedAtTimestamp = Clock.System.now().toEpochMilliseconds()
            val startedAtMillis = savedAtTimestamp
            // 準備フェーズ
            updateProgress(SavePhase.PREPARING, 0, 1, "ディレクトリ作成中...")

            // SaveLocation対応: nullの場合は従来のパスベース、非nullの場合は新API使用
            val useSaveLocation = baseSaveLocation != null
            val baseDir = "$baseDirectory/$threadId"
            val boardPath = extractBoardPath(boardUrl, boardId)

            if (useSaveLocation) {
                val location = requireNotNull(baseSaveLocation) { "baseSaveLocation must not be null when useSaveLocation is true" }
                fileSystem.createDirectory(location, threadId).getOrThrow()
                val boardMediaPath = if (boardPath.isNotBlank()) "$threadId/$boardPath" else threadId
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
            // accessOrder 付きコンストラクタはKMPに無いため、標準のLinkedHashMapを使い都度トリム
            val urlToPathMap = LinkedHashMap<String, String>()
            val urlToFileInfoMap = LinkedHashMap<String, LocalFileInfo>()

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
            val totalMediaCount = posts.sumOf {
                (if (it.thumbnailUrl != null) 1 else 0) + (if (it.imageUrl != null) 1 else 0)
            }

            // FIX: メディア数が異常に多い場合は警告
            if (totalMediaCount > MAX_MEDIA_ITEMS) {
                Logger.w("ThreadSaveService", "Thread has $totalMediaCount media items (max: $MAX_MEDIA_ITEMS), some may be skipped")
            }

            var processedMediaCount = 0

            posts.chunked(chunkSize).forEach { postChunk ->
                enforceBudget(totalSize, startedAtMillis)
                val mediaItems = postChunk.flatMap { post ->
                    buildList {
                        post.thumbnailUrl?.let { add(MediaItem(it, MediaType.THUMBNAIL, post)) }
                        post.imageUrl?.let { add(MediaItem(it, MediaType.FULL_IMAGE, post)) }
                    }
                }

                // FIX: 並列ダウンロードで処理速度を改善（最大4並列）
                mediaItems.chunked(4).forEach { itemBatch ->
                    kotlinx.coroutines.coroutineScope {
                        val deferredResults = itemBatch.map { mediaItem ->
                            async(Dispatchers.Default) {
                                // FIX: 最大数を超えたらスキップ
                                if (processedMediaCount >= MAX_MEDIA_ITEMS) {
                                    Logger.w("ThreadSaveService", "Skipping media item (exceeds MAX_MEDIA_ITEMS)")
                                    return@async Pair<MediaItem?, Result<LocalFileInfo>?>(null, null)
                                }

                                val currentCount = counterMutex.withLock {
                                    ++processedMediaCount
                                    processedMediaCount
                                }

                                updateProgress(
                                    SavePhase.DOWNLOADING,
                                    currentCount,
                                    minOf(totalMediaCount, MAX_MEDIA_ITEMS),
                                    "メディアダウンロード中... ($currentCount/${minOf(totalMediaCount, MAX_MEDIA_ITEMS)})"
                                )

                                val downloadResult = downloadAndSaveMedia(
                                    url = mediaItem.url,
                                    saveLocation = baseSaveLocation,
                                    baseDir = baseDir,
                                    boardPath = boardPath,
                                    threadId = threadId,
                                    type = mediaItem.type,
                                    postId = mediaItem.post.id,
                                    startedAtMillis = startedAtMillis
                                )

                                Pair<MediaItem?, Result<LocalFileInfo>?>(mediaItem, downloadResult)
                            }
                        }

                        // すべての並列ダウンロードの完了を待つ
                        deferredResults.forEach { deferred ->
                            val (mediaItem, downloadResult) = deferred.await()
                            if (mediaItem == null || downloadResult == null) {
                                downloadFailureCount++
                                return@forEach
                            }

                            downloadResult
                                .onSuccess { fileInfo ->
                                    val fileSize = fileSystem.getFileSize("$baseDir/${fileInfo.relativePath}")
                                    totalSize += fileSize
                                    enforceBudget(totalSize, startedAtMillis)
                                    // URL→ローカルパスマッピングに追加
                                    urlToPathMap[mediaItem.url] = fileInfo.relativePath
                                    trimMap(urlToPathMap)
                                    urlToFileInfoMap[mediaItem.url] = fileInfo
                                    trimMap(urlToFileInfoMap)

                                    when (fileInfo.fileType) {
                                        FileType.THUMBNAIL -> {
                                            if (thumbnailPath == null && mediaItem.post.id == "1") {
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
                                    println("Failed to download ${mediaItem.url}: ${error.message}")
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
                            fileSystem.writeString(baseSaveLocation, "$threadId/$fileName", rewritten).getOrThrow()
                            // SaveLocation版ではファイルサイズ取得は省略（getFileSize未実装のため）
                            totalSize += rewritten.toByteArray(Charsets.UTF_8).size
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
                    savedAt = savedAtTimestamp,
                    expiresAtLabel = expiresAtLabel,
                    posts = savedPosts,
                    totalSize = 0L, // 一旦0、書き込み後に更新
                    rawHtmlPath = rawHtmlRelativePath,
                    strippedExternalResources = rawHtmlOptions.stripExternalResources,
                    version = 1
                )
                val metadataJson = json.encodeToString(metadata)

                if (baseSaveLocation != null) {
                    val metadataRelativePath = "$threadId/metadata.json"
                    fileSystem.writeString(baseSaveLocation, metadataRelativePath, metadataJson).getOrThrow()
                    val size = metadataJson.toByteArray(Charsets.UTF_8).size.toLong()
                    val metadataWithSize = metadata.copy(totalSize = totalSize + size)
                    fileSystem.writeString(baseSaveLocation, metadataRelativePath, json.encodeToString(metadataWithSize)).getOrThrow()
                    size
                } else {
                    val metadataPath = "$baseDir/metadata.json"
                    fileSystem.writeString(metadataPath, metadataJson).getOrThrow()
                    val size = fileSystem.getFileSize(metadataPath)
                    val metadataWithSize = metadata.copy(totalSize = totalSize + size)
                    fileSystem.writeString(metadataPath, json.encodeToString(metadataWithSize)).getOrThrow()
                    size
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
                thumbnailPath = thumbnailPath,
                savedAt = savedAtTimestamp,
                postCount = posts.size,
                imageCount = imageCount,
                videoCount = videoCount,
                totalSize = finalTotalSize,
                status = status
            )
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
        threadId: String,
        type: MediaType,
        postId: String,
        startedAtMillis: Long
    ): Result<LocalFileInfo> = withContext(Dispatchers.Default) {
        runCatching {
            // Download media directly and inspect headers from GET response
            val response: HttpResponse = httpClient.get(url) {
                headers[HttpHeaders.Accept] = "image/*,video/*;q=0.8,*/*;q=0.2"
            }

            if (!response.status.isSuccess()) {
                throw Exception("Download failed: ${response.status}")
            }

            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                throw Exception("File too large: ${contentLength / 1024}KB (max: 8000KB)")
            }

            val extension = (getExtensionFromUrl(url) ?: getExtensionFromContentType(response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }))
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

            // FIX: メモリ効率のためストリーミングで保存
            val channel = response.bodyAsChannel()
            var totalBytesRead = 0L
            val bufferSize = 8192 // 8KB chunks

            // SaveLocation対応: nullの場合は従来の fullPath 使用、非nullの場合は新API使用
            if (saveLocation != null) {
                val fileRelativePath = "$threadId/$relativePath"
                // 最初にファイルを作成
                fileSystem.writeBytes(saveLocation, fileRelativePath, ByteArray(0)).getOrThrow()

                // チャンクごとに読み込んで追記
                while (!channel.isClosedForRead) {
                    if (Clock.System.now().toEpochMilliseconds() - startedAtMillis > MAX_SAVE_DURATION_MS) {
                        throw IllegalStateException("Save aborted: exceeded time limit during download")
                    }
                    val buffer = ByteArray(bufferSize)
                    val bytesRead = channel.readAvailable(buffer, 0, bufferSize)
                    if (bytesRead <= 0) break

                    val chunk = buffer.copyOf(bytesRead)
                    totalBytesRead += chunk.size

                    if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
                        throw Exception("Actual file size exceeds limit: ${totalBytesRead / 1024}KB")
                    }

                    fileSystem.appendBytes(saveLocation, fileRelativePath, chunk).getOrThrow()
                }
            } else {
                val fullPath = "$baseDir/$relativePath"
                // 最初にファイルを作成
                fileSystem.writeBytes(fullPath, ByteArray(0)).getOrThrow()

                // チャンクごとに読み込んで追記
                while (!channel.isClosedForRead) {
                    if (Clock.System.now().toEpochMilliseconds() - startedAtMillis > MAX_SAVE_DURATION_MS) {
                        fileSystem.delete(fullPath).getOrNull()
                        throw IllegalStateException("Save aborted: exceeded time limit during download")
                    }
                    val buffer = ByteArray(bufferSize)
                    val bytesRead = channel.readAvailable(buffer, 0, bufferSize)
                    if (bytesRead <= 0) break

                    val chunk = buffer.copyOf(bytesRead)
                    totalBytesRead += chunk.size

                    if (totalBytesRead > MAX_FILE_SIZE_BYTES) {
                        // サイズ超過時はファイルを削除
                        fileSystem.delete(fullPath).getOrNull()
                        throw Exception("Actual file size exceeds limit: ${totalBytesRead / 1024}KB")
                    }

                    // FIX: appendBytesを使用してメモリ効率的に追記
                    fileSystem.appendBytes(fullPath, chunk).getOrThrow()
                }
            }

            LocalFileInfo(
                relativePath = relativePath,
                fileType = fileType
            )
        }
    }

    /**
     * HTML内のパスを相対パスに変換
     */
    private fun convertHtmlPaths(html: String, urlToPathMap: Map<String, String>): String {
        var converted = html

        // 画像URLを相対パスに変換
        val imageRegex = """<img[^>]+src="([^"]+)"[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        converted = imageRegex.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // リンクURLを相対パスに変換
        val linkRegex = """<a[^>]+href="([^"]+)"[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        converted = linkRegex.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // videoタグのsrcを相対パスに変換
        val videoRegex = """<video[^>]+src="([^"]+)"[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        converted = videoRegex.replace(converted) { matchResult ->
            val originalUrl = matchResult.groupValues[1]
            val relativePath = urlToPathMap[originalUrl]
            if (relativePath != null) {
                matchResult.value.replace(originalUrl, relativePath)
            } else {
                matchResult.value
            }
        }

        // sourceタグのsrcも相対パスに変換
        val sourceRegex = """<source[^>]+src="([^"]+)"[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
        converted = sourceRegex.replace(converted) { matchResult ->
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
        val scriptRegex = Regex("(?si)<script[^>]+src=\"([^\"]+)\"[^>]*>.*?</script>")
        val iframeRegex = Regex("(?si)<iframe[^>]+src=\"([^\"]+)\"[^>]*>.*?</iframe>")
        fun shouldStrip(url: String): Boolean {
            val normalized = url.lowercase()
            return normalized.contains("/bin/") || normalized.contains("dec.2chan.net")
        }
        var updated = scriptRegex.replace(html) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1).orEmpty()
            if (shouldStrip(src)) "" else matchResult.value
        }
        updated = iframeRegex.replace(updated) { matchResult ->
            val src = matchResult.groupValues.getOrNull(1).orEmpty()
            if (shouldStrip(src)) "" else matchResult.value
        }
        return updated
    }

    /**
     * 書き出しはUTF-8固定なので charset をUTF-8に置き換える
     */
    private fun forceUtf8Charset(html: String): String {
        val charsetRegex = Regex(
            pattern = "<meta[^>]+charset\\s*=\\s*\"?([^\"\\s>]+)\"?[^>]*>",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val contentTypeRegex = Regex(
            pattern = "<meta[^>]+http-equiv\\s*=\\s*\"?Content-Type\"?[^>]*content\\s*=\\s*\"[^\"]*charset=([^\";>\\s]+)[^\"]*\"[^>]*>",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        var updated = charsetRegex.replace(html) { matchResult ->
            matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
        }
        updated = contentTypeRegex.replace(updated) { matchResult ->
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

    /**
     * スレッドHTMLを取得（Shift_JISを維持したまま文字列化）
     */
    private suspend fun fetchThreadHtml(boardUrl: String, threadId: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val threadUrl = BoardUrlResolver.resolveThreadUrl(boardUrl, threadId)
            val response: HttpResponse = httpClient.get(threadUrl) {
                headers[HttpHeaders.Referrer] = BoardUrlResolver.resolveBoardBaseUrl(boardUrl)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Fetch thread HTML failed: ${response.status}")
            }
            response.bodyAsText()
        }
    }

    /**
     * URLから拡張子を取得
     */
    private fun getExtensionFromUrl(url: String): String? {
        return url.substringAfterLast('.', "").takeIf { it.length in 3..4 }
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
        val fileType: FileType
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
