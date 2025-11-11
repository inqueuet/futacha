package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * スレッド保存サービス
 */
class ThreadSaveService(
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _saveProgress = MutableStateFlow<SaveProgress?>(null)
    val saveProgress: StateFlow<SaveProgress?> = _saveProgress

    companion object {
        // ファイルサイズ制限: 8000KB = 8,192,000 bytes
        private const val MAX_FILE_SIZE_BYTES = 8_192_000L

        // サポートされる拡張子
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf("webm", "mp4")
    }

    /**
     * スレッドを保存
     */
    suspend fun saveThread(
        threadId: String,
        boardId: String,
        boardName: String,
        boardUrl: String,
        title: String,
        expiresAtLabel: String?,
        posts: List<Post>
    ): Result<SavedThread> = withContext(Dispatchers.Default) {
        runCatching {
            // 準備フェーズ
            updateProgress(SavePhase.PREPARING, 0, 1, "ディレクトリ作成中...")

            val baseDir = "saved_threads/$threadId"
            fileSystem.createDirectory(baseDir).getOrThrow()
            fileSystem.createDirectory("$baseDir/images").getOrThrow()
            fileSystem.createDirectory("$baseDir/videos").getOrThrow()

            // URL→ローカルパスのマッピング
            val urlToPathMap = mutableMapOf<String, String>()

            // ダウンロードフェーズ
            val savedPosts = mutableListOf<SavedPost>()
            var thumbnailPath: String? = null
            var imageCount = 0
            var videoCount = 0
            var totalSize = 0L

            val mediaItems = posts.flatMap { post ->
                buildList {
                    post.thumbnailUrl?.let { add(MediaItem(it, MediaType.THUMBNAIL, post)) }
                    post.imageUrl?.let { add(MediaItem(it, MediaType.FULL_IMAGE, post)) }
                }
            }

            var downloadFailureCount = 0
            mediaItems.forEachIndexed { index, mediaItem ->
                updateProgress(
                    SavePhase.DOWNLOADING,
                    index + 1,
                    mediaItems.size,
                    "メディアダウンロード中... (${index + 1}/${mediaItems.size})"
                )

                val downloadResult = downloadMedia(mediaItem.url, threadId, mediaItem.type, mediaItem.post.id)
                downloadResult
                    .onSuccess { fileInfo ->
                        totalSize += fileSystem.getFileSize("$baseDir/${fileInfo.relativePath}")

                        // URL→ローカルパスマッピングに追加
                        urlToPathMap[mediaItem.url] = fileInfo.relativePath

                        when (fileInfo.fileType) {
                            FileType.THUMBNAIL -> {
                                if (thumbnailPath == null && mediaItem.post.id == "1") {
                                    thumbnailPath = fileInfo.relativePath
                                }
                            }
                            FileType.FULL_IMAGE -> imageCount++
                            FileType.VIDEO -> {} // 現在は動画なし
                        }
                    }
                    .onFailure { error ->
                        // ダウンロード失敗はログに記録してスキップ
                        downloadFailureCount++
                        println("Failed to download ${mediaItem.url}: ${error.message}")
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
                val localImagePath = post.imageUrl?.let { urlToPathMap[it] }
                val localThumbnailPath = post.thumbnailUrl?.let { urlToPathMap[it] }

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
                        originalImageUrl = post.imageUrl,
                        localImagePath = localImagePath,
                        originalThumbnailUrl = post.thumbnailUrl,
                        localThumbnailPath = localThumbnailPath,
                        downloadSuccess = localImagePath != null || post.imageUrl == null
                    )
                )
            }

            // 完了フェーズ
            updateProgress(SavePhase.FINALIZING, 0, 1, "メタデータ保存中...")

            // メタデータ保存
            val metadata = SavedThreadMetadata(
                threadId = threadId,
                boardId = boardId,
                boardName = boardName,
                boardUrl = boardUrl,
                title = title,
                savedAt = System.currentTimeMillis(),
                expiresAtLabel = expiresAtLabel,
                posts = savedPosts,
                totalSize = totalSize,
                version = 1
            )

            val metadataJson = json.encodeToString(metadata)
            fileSystem.writeString("$baseDir/metadata.json", metadataJson).getOrThrow()

            // HTML生成
            val html = generateHtml(metadata)
            fileSystem.writeString("$baseDir/thread.html", html).getOrThrow()

            updateProgress(SavePhase.FINALIZING, 1, 1, "完了")

            // ステータスを失敗数に応じて設定
            val status = when {
                downloadFailureCount == 0 -> SaveStatus.COMPLETED
                downloadFailureCount < mediaItems.size -> SaveStatus.PARTIAL
                else -> SaveStatus.FAILED
            }

            SavedThread(
                threadId = threadId,
                boardId = boardId,
                boardName = boardName,
                title = title,
                thumbnailPath = thumbnailPath,
                savedAt = metadata.savedAt,
                postCount = posts.size,
                imageCount = imageCount,
                videoCount = videoCount,
                totalSize = totalSize,
                status = status
            )
        }
    }

    /**
     * メディアをダウンロード
     */
    private suspend fun downloadMedia(
        url: String,
        threadId: String,
        type: MediaType,
        postId: String
    ): Result<LocalFileInfo> = withContext(Dispatchers.Default) {
        runCatching {
            // まずHEADリクエストでファイルサイズと拡張子を確認
            val headResponse: HttpResponse = httpClient.head(url)

            // Content-Lengthを取得
            val contentLength = headResponse.headers["Content-Length"]?.toLongOrNull() ?: 0L

            // ファイルサイズチェック（8000KB制限）
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                throw Exception("File too large: ${contentLength / 1024}KB (max: 8000KB)")
            }

            // 拡張子チェック
            val extension = getExtensionFromUrl(url) ?: getExtensionFromContentType(headResponse.contentType())
            val isSupported = extension in SUPPORTED_IMAGE_EXTENSIONS || extension in SUPPORTED_VIDEO_EXTENSIONS
            if (!isSupported) {
                throw Exception("Unsupported file type: $extension")
            }

            // 実際にダウンロード
            val response: HttpResponse = httpClient.get(url)
            if (!response.status.isSuccess()) {
                throw Exception("Download failed: ${response.status}")
            }

            val bytes = response.readBytes()

            val (subDir, prefix) = when (type) {
                MediaType.THUMBNAIL -> "images" to "thumb_"
                MediaType.FULL_IMAGE -> "images" to "img_"
            }

            val fileName = "${prefix}${postId}_${System.currentTimeMillis()}.$extension"
            val relativePath = "$subDir/$fileName"
            val fullPath = "saved_threads/$threadId/$relativePath"

            fileSystem.writeBytes(fullPath, bytes).getOrThrow()

            LocalFileInfo(
                relativePath = relativePath,
                fileType = when (type) {
                    MediaType.THUMBNAIL -> FileType.THUMBNAIL
                    MediaType.FULL_IMAGE -> FileType.FULL_IMAGE
                }
            )
        }
    }

    /**
     * HTML内のパスを相対パスに変換
     */
    private fun convertHtmlPaths(html: String, urlToPathMap: Map<String, String>): String {
        var converted = html

        // 画像URLを相対パスに変換
        val imageRegex = """<img[^>]+src="([^"]+)"[^>]*>""".toRegex()
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
        val linkRegex = """<a[^>]+href="([^"]+)"[^>]*>""".toRegex()
        converted = linkRegex.replace(converted) { matchResult ->
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
     * HTMLを生成
     */
    private fun generateHtml(metadata: SavedThreadMetadata): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"ja\">")
            appendLine("<head>")
            appendLine("    <meta charset=\"UTF-8\">")
            appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("    <title>${metadata.title}</title>")
            appendLine("    <style>")
            appendLine("        body { font-family: sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }")
            appendLine("        .post { border: 1px solid #ddd; margin: 10px 0; padding: 10px; }")
            appendLine("        .post-header { font-weight: bold; color: #007bff; }")
            appendLine("        .post-body { margin-top: 10px; }")
            appendLine("        .post-image { max-width: 100%; height: auto; }")
            appendLine("        .metadata { background: #f5f5f5; padding: 10px; margin-bottom: 20px; }")
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class=\"metadata\">")
            appendLine("        <h1>${metadata.title}</h1>")
            appendLine("        <p>板: ${metadata.boardName}</p>")
            appendLine("        <p>保存日時: ${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(java.util.Date(metadata.savedAt))}</p>")
            appendLine("        <p>投稿数: ${metadata.posts.size}</p>")
            if (metadata.expiresAtLabel != null) {
                appendLine("        <p>有効期限: ${metadata.expiresAtLabel}</p>")
            }
            appendLine("    </div>")

            metadata.posts.forEach { post ->
                appendLine("    <div class=\"post\" id=\"post-${post.id}\">")
                appendLine("        <div class=\"post-header\">")
                append("            ${post.order ?: ""}. ")
                if (post.author != null) append("${post.author} ")
                if (post.subject != null) append("<strong>${post.subject}</strong> ")
                appendLine(post.timestamp)
                appendLine("        </div>")

                if (post.localThumbnailPath != null || post.localImagePath != null) {
                    val imagePath = post.localImagePath ?: post.localThumbnailPath
                    appendLine("        <div class=\"post-image-container\">")
                    appendLine("            <img src=\"$imagePath\" class=\"post-image\" />")
                    appendLine("        </div>")
                }

                appendLine("        <div class=\"post-body\">")
                appendLine("            ${post.messageHtml}")
                appendLine("        </div>")
                appendLine("    </div>")
            }

            appendLine("</body>")
            appendLine("</html>")
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

    /**
     * メディアアイテム
     */
    private data class MediaItem(
        val url: String,
        val type: MediaType,
        val post: Post
    )
}
