package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveLocation
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

internal data class ThreadSaveLocalFileInfo(
    val relativePath: String,
    val fileType: FileType,
    val byteSize: Long
)

internal data class ThreadSaveScheduledMediaItem(
    val url: String,
    val requestType: ThreadSaveMediaRequestType,
    val postId: String
)

internal data class ThreadSaveMediaDownloadPlan(
    val scheduledItems: List<ThreadSaveScheduledMediaItem>,
    val totalMediaCount: Int
)

internal data class ThreadSaveMediaDownloadBatchResult(
    val urlToPathMap: Map<String, String>,
    val mediaKeyToFileInfoMap: Map<String, ThreadSaveLocalFileInfo>,
    val mediaCounts: ThreadSaveMediaCounts,
    val totalSizeBytes: Long,
    val downloadFailureCount: Int
)

internal data class ThreadSaveMetadataWriteRequest(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val storageId: String,
    val savedAtTimestamp: Long,
    val expiresAtLabel: String?,
    val savedPosts: List<SavedPost>,
    val rawHtmlRelativePath: String?,
    val strippedExternalResources: Boolean,
    val baseTotalSize: Long
)

internal data class ThreadSaveRawHtmlWriteResult(
    val relativePath: String?,
    val sizeBytes: Long
)

internal data class ThreadSaveOutputPreparationRequest(
    val baseDirectory: String,
    val storageId: String,
    val boardPath: String
)

internal data class ThreadSaveMediaPathLock(
    val mutex: Mutex,
    var holders: Int = 0
)

internal fun buildThreadSaveMediaDownloadPlan(
    posts: List<Post>,
    maxMediaItems: Int
): ThreadSaveMediaDownloadPlan {
    val scheduledKeys = linkedSetOf<String>()
    val scheduledItems = buildList {
        posts.forEach { post ->
            post.thumbnailUrl?.let { thumbnailUrl ->
                val key = buildThreadSaveMediaDownloadKey(thumbnailUrl, ThreadSaveMediaRequestType.THUMBNAIL)
                if (scheduledKeys.add(key) && size < maxMediaItems) {
                    add(
                        ThreadSaveScheduledMediaItem(
                            url = thumbnailUrl,
                            requestType = ThreadSaveMediaRequestType.THUMBNAIL,
                            postId = post.id
                        )
                    )
                }
            }
            post.imageUrl?.let { imageUrl ->
                val key = buildThreadSaveMediaDownloadKey(imageUrl, ThreadSaveMediaRequestType.FULL_IMAGE)
                if (scheduledKeys.add(key) && size < maxMediaItems) {
                    add(
                        ThreadSaveScheduledMediaItem(
                            url = imageUrl,
                            requestType = ThreadSaveMediaRequestType.FULL_IMAGE,
                            postId = post.id
                        )
                    )
                }
            }
        }
    }
    return ThreadSaveMediaDownloadPlan(
        scheduledItems = scheduledItems,
        totalMediaCount = scheduledKeys.size
    )
}

internal suspend fun executeThreadSaveMediaDownloadPlan(
    plan: ThreadSaveMediaDownloadPlan,
    opPostId: String?,
    chunkSize: Int,
    maxParallelDownloads: Int,
    maxRetries: Int,
    retryDelayMillis: Long,
    logTag: String,
    createUrlToPathMap: () -> MutableMap<String, String>,
    createMediaKeyToFileInfoMap: () -> MutableMap<String, ThreadSaveLocalFileInfo>,
    updateProgress: (current: Int, total: Int) -> Unit,
    downloadMedia: suspend (ThreadSaveScheduledMediaItem) -> Result<ThreadSaveLocalFileInfo>,
    enforceBudget: (Long) -> Unit
): ThreadSaveMediaDownloadBatchResult {
    val urlToPathMap = createUrlToPathMap()
    val mediaKeyToFileInfoMap = createMediaKeyToFileInfoMap()
    var mediaCounts = ThreadSaveMediaCounts()
    var totalSizeBytes = 0L
    var downloadFailureCount = 0
    val progressTotal = minOf(plan.totalMediaCount, plan.scheduledItems.size)
    var processedMediaCount = 0

    plan.scheduledItems.chunked(chunkSize).forEach { itemChunk ->
        coroutineContext.ensureActive()
        yield()
        enforceBudget(totalSizeBytes)

        itemChunk.chunked(maxParallelDownloads).forEach { itemBatch ->
            coroutineContext.ensureActive()
            coroutineScope {
                val deferredResults = itemBatch.map { mediaItem ->
                    processedMediaCount += 1
                    val currentCount = processedMediaCount
                    async(AppDispatchers.io) {
                        coroutineContext.ensureActive()
                        updateProgress(currentCount, progressTotal)

                        var downloadResult: Result<ThreadSaveLocalFileInfo>? = null
                        var lastError: Throwable? = null
                        for (attempt in 1..maxRetries) {
                            coroutineContext.ensureActive()
                            downloadResult = downloadMedia(mediaItem)
                            if (downloadResult.isSuccess) break

                            lastError = downloadResult.exceptionOrNull()
                            if (attempt < maxRetries) {
                                Logger.w(logTag, "Download attempt $attempt failed for ${mediaItem.url}, retrying...")
                                delay(retryDelayMillis * attempt)
                            }
                        }

                        if (downloadResult?.isFailure == true) {
                            Logger.e(
                                logTag,
                                "Failed to download ${mediaItem.url} after $maxRetries attempts: ${lastError?.message}"
                            )
                        }

                        mediaItem to downloadResult
                    }
                }

                deferredResults.forEach { deferred ->
                    coroutineContext.ensureActive()
                    val (mediaItem, downloadResult) = deferred.await()
                    val result = downloadResult
                    if (result == null) {
                        downloadFailureCount += 1
                        return@forEach
                    }

                    result
                        .onSuccess { fileInfo ->
                            totalSizeBytes += fileInfo.byteSize
                            enforceBudget(totalSizeBytes)
                            val mediaKey = buildThreadSaveMediaDownloadKey(mediaItem.url, mediaItem.requestType)
                            mediaKeyToFileInfoMap[mediaKey] = fileInfo
                            when (mediaItem.requestType) {
                                ThreadSaveMediaRequestType.THUMBNAIL -> {
                                    if (urlToPathMap[mediaItem.url] == null) {
                                        urlToPathMap[mediaItem.url] = fileInfo.relativePath
                                    }
                                }
                                ThreadSaveMediaRequestType.FULL_IMAGE -> {
                                    urlToPathMap[mediaItem.url] = fileInfo.relativePath
                                }
                            }

                            mediaCounts = updateThreadSaveMediaCounts(
                                current = mediaCounts,
                                fileType = fileInfo.fileType,
                                relativePath = fileInfo.relativePath,
                                postId = mediaItem.postId,
                                opPostId = opPostId
                            )
                        }
                        .onFailure { error ->
                            downloadFailureCount += 1
                            Logger.e(logTag, "Failed to download ${mediaItem.url}: ${error.message}")
                        }
                }
            }
        }
    }

    return ThreadSaveMediaDownloadBatchResult(
        urlToPathMap = urlToPathMap,
        mediaKeyToFileInfoMap = mediaKeyToFileInfoMap,
        mediaCounts = mediaCounts,
        totalSizeBytes = totalSizeBytes,
        downloadFailureCount = downloadFailureCount
    )
}

internal suspend fun buildThreadSaveSavedPosts(
    posts: List<Post>,
    mediaKeyToFileInfoMap: Map<String, ThreadSaveLocalFileInfo>,
    urlToPathMap: Map<String, String>,
    updateProgress: (current: Int, total: Int) -> Unit
): List<SavedPost> {
    val savedPosts = ArrayList<SavedPost>(posts.size)
    posts.forEachIndexed { index, post ->
        coroutineContext.ensureActive()
        if (index % 32 == 0) {
            yield()
        }
        updateProgress(index + 1, posts.size)

        val imageFileInfo = post.imageUrl?.let {
            mediaKeyToFileInfoMap[buildThreadSaveMediaDownloadKey(it, ThreadSaveMediaRequestType.FULL_IMAGE)]
        }
        val thumbnailFileInfo = post.thumbnailUrl?.let {
            mediaKeyToFileInfoMap[buildThreadSaveMediaDownloadKey(it, ThreadSaveMediaRequestType.THUMBNAIL)]
        }
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

        savedPosts += SavedPost(
            id = post.id,
            order = post.order,
            author = post.author,
            subject = post.subject,
            timestamp = post.timestamp,
            messageHtml = convertSavedThreadHtmlPaths(post.messageHtml, urlToPathMap),
            originalImageUrl = originalImageUrl,
            localImagePath = localImagePath,
            originalVideoUrl = originalVideoUrl,
            localVideoPath = localVideoPath,
            originalThumbnailUrl = post.thumbnailUrl,
            localThumbnailPath = localThumbnailPath,
            downloadSuccess = resolveThreadSavedPostDownloadSuccess(
                originalImageUrl = post.imageUrl,
                localImagePath = localImagePath,
                localVideoPath = localVideoPath
            )
        )
    }
    return savedPosts
}

internal suspend fun prepareThreadSaveOutput(
    fileSystem: FileSystem,
    saveLocation: SaveLocation?,
    request: ThreadSaveOutputPreparationRequest
) {
    val baseDir = "${request.baseDirectory}/${request.storageId}"
    if (saveLocation != null) {
        fileSystem.delete(saveLocation, request.storageId).getOrNull()
        fileSystem.createDirectory(saveLocation, request.storageId).getOrThrow()
        val boardMediaPath = if (request.boardPath.isNotBlank()) {
            "${request.storageId}/${request.boardPath}"
        } else {
            request.storageId
        }
        fileSystem.createDirectory(saveLocation, "$boardMediaPath/src").getOrThrow()
        fileSystem.createDirectory(saveLocation, "$boardMediaPath/thumb").getOrThrow()
    } else {
        fileSystem.deleteRecursively(baseDir).getOrNull()
        fileSystem.createDirectory(request.baseDirectory).getOrThrow()
        fileSystem.createDirectory(baseDir).getOrThrow()
        val boardMediaRoot = listOf(baseDir, request.boardPath)
            .filter { it.isNotBlank() }
            .joinToString("/")
        fileSystem.createDirectory(boardMediaRoot).getOrThrow()
        fileSystem.createDirectory("$boardMediaRoot/src").getOrThrow()
        fileSystem.createDirectory("$boardMediaRoot/thumb").getOrThrow()
    }
}

internal suspend fun writeThreadSaveMetadataIfEnabled(
    writeMetadata: Boolean,
    fileSystem: FileSystem,
    saveLocation: SaveLocation?,
    baseDir: String,
    request: ThreadSaveMetadataWriteRequest,
    encodeMetadata: (SavedThreadMetadata) -> String
): Long {
    if (!writeMetadata) {
        return 0L
    }
    val metadata = SavedThreadMetadata(
        threadId = request.threadId,
        boardId = request.boardId,
        boardName = request.boardName,
        boardUrl = request.boardUrl,
        title = request.title,
        storageId = request.storageId,
        savedAt = request.savedAtTimestamp,
        expiresAtLabel = request.expiresAtLabel,
        posts = request.savedPosts,
        totalSize = 0L,
        rawHtmlPath = request.rawHtmlRelativePath,
        strippedExternalResources = request.strippedExternalResources,
        version = 1
    )
    val (metadataPayload, payloadSize) = buildThreadSaveMetadataPayloadWithStableSize(
        metadata = metadata,
        baseTotalSize = request.baseTotalSize,
        encodeMetadata = encodeMetadata
    )

    if (saveLocation != null) {
        fileSystem.writeString(saveLocation, "${request.storageId}/metadata.json", metadataPayload).getOrThrow()
    } else {
        fileSystem.writeString("$baseDir/metadata.json", metadataPayload).getOrThrow()
    }
    return payloadSize
}

internal suspend fun saveThreadRawHtmlIfEnabled(
    enabled: Boolean,
    fileSystem: FileSystem,
    saveLocation: SaveLocation?,
    storageId: String,
    baseDir: String,
    threadId: String,
    fetchOriginalHtml: suspend () -> Result<String>,
    rewriteHtml: (String) -> String,
    measureAbsolutePathSize: suspend (String) -> Long,
    logWarning: (String) -> Unit
): ThreadSaveRawHtmlWriteResult {
    if (!enabled) {
        return ThreadSaveRawHtmlWriteResult(relativePath = null, sizeBytes = 0L)
    }

    return fetchOriginalHtml()
        .mapCatching { originalHtml ->
            val rewritten = rewriteHtml(originalHtml)
            val fileName = "$threadId.htm"
            val sizeBytes = if (saveLocation != null) {
                fileSystem.writeString(saveLocation, "$storageId/$fileName", rewritten).getOrThrow()
                measureThreadSaveUtf8ByteLength(rewritten)
            } else {
                val fullPath = "$baseDir/$fileName"
                fileSystem.writeString(fullPath, rewritten).getOrThrow()
                measureAbsolutePathSize(fullPath)
            }
            ThreadSaveRawHtmlWriteResult(relativePath = fileName, sizeBytes = sizeBytes)
        }
        .getOrElse { error ->
            logWarning("Failed to save raw HTML: ${error.message}")
            ThreadSaveRawHtmlWriteResult(relativePath = null, sizeBytes = 0L)
        }
}

internal fun buildThreadSaveSavedThread(
    threadId: String,
    boardId: String,
    boardName: String,
    title: String,
    storageId: String,
    thumbnailPath: String?,
    savedAtTimestamp: Long,
    postCount: Int,
    imageCount: Int,
    videoCount: Int,
    totalSize: Long,
    downloadFailureCount: Int,
    totalMediaCount: Int
): SavedThread {
    return SavedThread(
        threadId = threadId,
        boardId = boardId,
        boardName = boardName,
        title = title,
        storageId = storageId,
        thumbnailPath = thumbnailPath,
        savedAt = savedAtTimestamp,
        postCount = postCount,
        imageCount = imageCount,
        videoCount = videoCount,
        totalSize = totalSize,
        status = resolveThreadSaveStatus(downloadFailureCount, totalMediaCount)
    )
}

internal suspend fun <T> withThreadSaveMediaWriteLock(
    relativePath: String,
    mediaWriteLocksGuard: Mutex,
    mediaWriteLocks: MutableMap<String, ThreadSaveMediaPathLock>,
    block: suspend () -> T
): T {
    val lockEntry = mediaWriteLocksGuard.withLock {
        val entry = mediaWriteLocks.getOrPut(relativePath) { ThreadSaveMediaPathLock(Mutex()) }
        entry.holders += 1
        entry
    }
    return try {
        lockEntry.mutex.withLock {
            block()
        }
    } finally {
        mediaWriteLocksGuard.withLock {
            val current = mediaWriteLocks[relativePath]
            if (current === lockEntry) {
                current.holders -= 1
                if (current.holders <= 0 && !current.mutex.isLocked) {
                    mediaWriteLocks.remove(relativePath)
                }
            }
        }
    }
}

internal suspend fun cleanupThreadSaveFailedOutput(
    fileSystem: FileSystem,
    baseSaveLocation: com.valoser.futacha.shared.model.SaveLocation?,
    baseDirectory: String,
    storageId: String
) {
    if (baseSaveLocation != null) {
        fileSystem.delete(baseSaveLocation, storageId).getOrNull()
    } else {
        fileSystem.deleteRecursively("$baseDirectory/$storageId").getOrNull()
    }
}

internal suspend fun fetchThreadSaveHtml(
    httpClient: HttpClient,
    boardUrl: String,
    threadId: String,
    threadHtmlFetchTimeoutMillis: Long,
    maxThreadHtmlBytes: Long,
    streamReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    readIdleTimeoutMillis: Long
): Result<String> = withContext(AppDispatchers.io) {
    try {
        val html = withTimeoutOrNull(threadHtmlFetchTimeoutMillis) {
            val threadUrl = BoardUrlResolver.resolveThreadUrl(boardUrl, threadId)
            val response: HttpResponse = httpClient.get(threadUrl) {
                headers[HttpHeaders.Referrer] = BoardUrlResolver.resolveBoardBaseUrl(boardUrl)
            }
            try {
                if (!response.status.isSuccess()) {
                    throw Exception("Fetch thread HTML failed: ${response.status}")
                }
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > maxThreadHtmlBytes) {
                    throw IllegalStateException("Thread HTML is too large: ${contentLength / 1024}KB")
                }
                val bodyBytes = readThreadSaveResponseBytesWithLimit(
                    response = response,
                    maxBytes = maxThreadHtmlBytes.toInt(),
                    streamReadBufferBytes = streamReadBufferBytes,
                    maxZeroReadRetries = maxZeroReadRetries,
                    zeroReadBackoffMillis = zeroReadBackoffMillis,
                    readIdleTimeoutMillis = readIdleTimeoutMillis
                )
                TextEncoding.decodeToString(bodyBytes, response.headers[HttpHeaders.ContentType])
            } finally {
                runCatching { response.bodyAsChannel().cancel() }
            }
        } ?: throw IllegalStateException("Fetch thread HTML timed out after ${threadHtmlFetchTimeoutMillis}ms")
        Result.success(html)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal suspend fun readThreadSaveResponseBytesWithLimit(
    response: HttpResponse,
    maxBytes: Int,
    streamReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    readIdleTimeoutMillis: Long
): ByteArray {
    val channel = response.bodyAsChannel()
    val buffer = ByteArray(streamReadBufferBytes)
    var output = ByteArray(minOf(streamReadBufferBytes, maxBytes.coerceAtLeast(1)))
    var totalBytes = 0
    var zeroReadCount = 0
    var readLoopCount = 0L

    try {
        while (true) {
            coroutineContext.ensureActive()
            val read = withTimeoutOrNull(readIdleTimeoutMillis) {
                channel.readAvailable(buffer, 0, buffer.size)
            } ?: throw IllegalStateException("Thread HTML read stalled")

            if (read == -1) break
            if (read == 0) {
                zeroReadCount += 1
                if (zeroReadCount >= maxZeroReadRetries) {
                    throw IllegalStateException("Thread HTML read stalled")
                }
                delay(zeroReadBackoffMillis)
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

internal suspend fun streamThreadSaveResponseToStorage(
    response: HttpResponse,
    fileSystem: FileSystem,
    saveLocation: SaveLocation?,
    saveLocationPath: String?,
    absolutePath: String?,
    startedAtMillis: Long,
    streamReadBufferBytes: Int,
    maxFileSizeBytes: Long,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    readIdleTimeoutMillis: Long,
    writeTimeoutMillis: Long,
    maxSaveDurationMs: Long,
    nowMillis: () -> Long
): Long {
    val channel = response.bodyAsChannel()
    val buffer = ByteArray(streamReadBufferBytes)
    var output = ByteArray(minOf(streamReadBufferBytes, maxFileSizeBytes.toInt()))
    var totalBytesRead = 0
    var zeroReadCount = 0
    var readLoopCount = 0L

    while (true) {
        coroutineContext.ensureActive()
        val read = withTimeoutOrNull(readIdleTimeoutMillis) {
            channel.readAvailable(buffer, 0, buffer.size)
        } ?: throw IllegalStateException("Save aborted: timed out waiting for media stream data")

        if (read == -1) break
        if (read == 0) {
            zeroReadCount += 1
            if (zeroReadCount >= maxZeroReadRetries) {
                throw IllegalStateException("Save aborted: media stream stalled")
            }
            delay(zeroReadBackoffMillis)
            continue
        }

        zeroReadCount = 0
        val requiredSize = totalBytesRead + read
        if (requiredSize.toLong() > maxFileSizeBytes) {
            throw Exception("Actual file size exceeds limit: ${requiredSize / 1024}KB")
        }
        if (nowMillis() - startedAtMillis > maxSaveDurationMs) {
            throw IllegalStateException("Save aborted: exceeded time limit during download")
        }

        if (requiredSize > output.size) {
            var newSize = output.size
            while (newSize < requiredSize) {
                newSize = (newSize * 2).coerceAtMost(maxFileSizeBytes.toInt())
                if (newSize == output.size) break
            }
            if (newSize < requiredSize) {
                throw IllegalStateException("Failed to expand media buffer safely")
            }
            output = output.copyOf(newSize)
        }
        buffer.copyInto(output, destinationOffset = totalBytesRead, startIndex = 0, endIndex = read)
        totalBytesRead = requiredSize

        readLoopCount += 1
        if (readLoopCount % 32L == 0L) {
            yield()
        }
    }

    val payload = if (totalBytesRead == output.size) {
        output
    } else {
        output.copyOf(totalBytesRead)
    }
    if (saveLocation != null && saveLocationPath != null) {
        val completed = withTimeoutOrNull(writeTimeoutMillis) {
            fileSystem.writeBytes(saveLocation, saveLocationPath, payload).getOrThrow()
            true
        } ?: false
        if (!completed) {
            throw IllegalStateException("Save aborted: timed out while writing media file")
        }
    } else if (absolutePath != null) {
        val completed = withTimeoutOrNull(writeTimeoutMillis) {
            fileSystem.writeBytes(absolutePath, payload).getOrThrow()
            true
        } ?: false
        if (!completed) {
            throw IllegalStateException("Save aborted: timed out while writing media file")
        }
    } else {
        throw IllegalStateException("No target path specified for media stream")
    }

    return totalBytesRead.toLong()
}

internal suspend fun downloadAndStoreThreadSaveMedia(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    logTag: String,
    url: String,
    saveLocation: SaveLocation?,
    baseDir: String,
    boardPath: String,
    storageId: String,
    requestType: ThreadSaveMediaRequestType,
    postId: String,
    startedAtMillis: Long,
    mediaRequestTimeoutMillis: Long,
    maxFileSizeBytes: Long,
    maxSaveDurationMs: Long,
    streamReadBufferBytes: Int,
    maxZeroReadRetries: Int,
    zeroReadBackoffMillis: Long,
    readIdleTimeoutMillis: Long,
    writeTimeoutMillis: Long,
    nowMillis: () -> Long,
    withMediaWriteLock: suspend (String, suspend () -> Long) -> Long
): Result<ThreadSaveLocalFileInfo> = withContext(AppDispatchers.io) {
    try {
        Result.success(
            run {
                val response: HttpResponse = withTimeoutOrNull(mediaRequestTimeoutMillis) {
                    httpClient.get(url) {
                        headers[HttpHeaders.Accept] = "image/*,video/*;q=0.8,*/*;q=0.2"
                    }
                } ?: throw IllegalStateException("Download request timed out after ${mediaRequestTimeoutMillis}ms: $url")
                try {
                    if (!response.status.isSuccess()) {
                        throw Exception("Download failed: ${response.status}")
                    }

                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                    if (contentLength > maxFileSizeBytes) {
                        throw Exception("File too large: ${contentLength / 1024}KB (max: 8000KB)")
                    }

                    val extension = (
                        getThreadSaveExtensionFromUrl(url)
                            ?: getThreadSaveExtensionFromContentType(
                                response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                            )
                        ).lowercase()
                    if (!isThreadSaveSupportedExtension(extension)) {
                        throw Exception("Unsupported file type: $extension")
                    }

                    val fileType = resolveThreadSaveFileType(requestType, extension)
                    val fileName = resolveThreadSaveFileName(
                        url = url,
                        extension = extension,
                        postId = postId,
                        timestampMillis = nowMillis()
                    )
                    val relativePath = buildThreadSaveRelativePath(boardPath, fileType, fileName)

                    if (nowMillis() - startedAtMillis > maxSaveDurationMs) {
                        throw IllegalStateException("Save aborted: exceeded time limit during download")
                    }

                    val totalBytesRead = withMediaWriteLock(relativePath) {
                        if (saveLocation != null) {
                            val fileRelativePath = "$storageId/$relativePath"
                            var completed = false
                            try {
                                val writtenBytes = streamThreadSaveResponseToStorage(
                                    response = response,
                                    fileSystem = fileSystem,
                                    saveLocation = saveLocation,
                                    saveLocationPath = fileRelativePath,
                                    absolutePath = null,
                                    startedAtMillis = startedAtMillis,
                                    streamReadBufferBytes = streamReadBufferBytes,
                                    maxFileSizeBytes = maxFileSizeBytes,
                                    maxZeroReadRetries = maxZeroReadRetries,
                                    zeroReadBackoffMillis = zeroReadBackoffMillis,
                                    readIdleTimeoutMillis = readIdleTimeoutMillis,
                                    writeTimeoutMillis = writeTimeoutMillis,
                                    maxSaveDurationMs = maxSaveDurationMs,
                                    nowMillis = nowMillis
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
                                val writtenBytes = streamThreadSaveResponseToStorage(
                                    response = response,
                                    fileSystem = fileSystem,
                                    saveLocation = null,
                                    saveLocationPath = null,
                                    absolutePath = fullPath,
                                    startedAtMillis = startedAtMillis,
                                    streamReadBufferBytes = streamReadBufferBytes,
                                    maxFileSizeBytes = maxFileSizeBytes,
                                    maxZeroReadRetries = maxZeroReadRetries,
                                    zeroReadBackoffMillis = zeroReadBackoffMillis,
                                    readIdleTimeoutMillis = readIdleTimeoutMillis,
                                    writeTimeoutMillis = writeTimeoutMillis,
                                    maxSaveDurationMs = maxSaveDurationMs,
                                    nowMillis = nowMillis
                                )
                                completed = true
                                writtenBytes
                            } finally {
                                if (!completed) {
                                    fileSystem.delete(fullPath).getOrNull()
                                }
                            }
                        }
                    }

                    ThreadSaveLocalFileInfo(
                        relativePath = relativePath,
                        fileType = fileType,
                        byteSize = totalBytesRead
                    )
                } finally {
                    runCatching { response.bodyAsChannel().cancel() }
                }
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Logger.e(logTag, "Failed to download $url: ${t.message}")
        Result.failure(t)
    }
}
