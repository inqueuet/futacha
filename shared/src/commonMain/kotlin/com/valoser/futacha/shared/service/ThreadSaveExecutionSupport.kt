package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val accumulator = ThreadSaveMediaDownloadAccumulator(
        urlToPathMap = createUrlToPathMap(),
        mediaKeyToFileInfoMap = createMediaKeyToFileInfoMap()
    )
    val progressTotal = minOf(plan.totalMediaCount, plan.scheduledItems.size)
    var processedMediaCount = 0
    val execution = ThreadSaveMediaDownloadExecutionContext(
        maxRetries = maxRetries,
        retryDelayMillis = retryDelayMillis,
        progressTotal = progressTotal,
        logTag = logTag,
        updateProgress = updateProgress,
        downloadMedia = downloadMedia
    )

    plan.scheduledItems.chunked(chunkSize).forEach { itemChunk ->
        coroutineContext.ensureActive()
        yield()
        enforceBudget(accumulator.totalSizeBytes)

        itemChunk.chunked(maxParallelDownloads).forEach { itemBatch ->
            coroutineContext.ensureActive()
            val nextProgressIndex = processedMediaCount + 1
            val results = executeThreadSaveMediaBatch(
                itemBatch = itemBatch,
                firstProgressIndex = nextProgressIndex,
                execution = execution
            )
            processedMediaCount += itemBatch.size
            applyThreadSaveMediaBatchResults(
                results = results,
                accumulator = accumulator,
                opPostId = opPostId,
                enforceBudget = enforceBudget,
                logTag = logTag
            )
        }
    }

    return ThreadSaveMediaDownloadBatchResult(
        urlToPathMap = accumulator.urlToPathMap,
        mediaKeyToFileInfoMap = accumulator.mediaKeyToFileInfoMap,
        mediaCounts = accumulator.mediaCounts,
        totalSizeBytes = accumulator.totalSizeBytes,
        downloadFailureCount = accumulator.downloadFailureCount
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
        savedPosts += buildThreadSaveSavedPost(
            post = post,
            resolvedMedia = resolveThreadSavePostMedia(
                post = post,
                mediaKeyToFileInfoMap = mediaKeyToFileInfoMap
            ),
            urlToPathMap = urlToPathMap
        )
    }
    return savedPosts
}

internal suspend fun prepareThreadSaveOutput(
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget,
    request: ThreadSaveOutputPreparationRequest
) {
    prepareThreadSaveStorageTarget(fileSystem, target, request.boardPath)
}

internal suspend fun writeThreadSaveMetadataIfEnabled(
    writeMetadata: Boolean,
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget,
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
    writeThreadSaveTextFile(
        fileSystem = fileSystem,
        target = target,
        relativePath = "metadata.json",
        content = metadataPayload,
        measureAbsolutePathSize = fileSystem::getFileSize
    )
    return payloadSize
}

internal suspend fun saveThreadRawHtmlIfEnabled(
    enabled: Boolean,
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget,
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
            val sizeBytes = writeThreadSaveTextFile(
                fileSystem = fileSystem,
                target = target,
                relativePath = fileName,
                content = rewritten,
                measureAbsolutePathSize = measureAbsolutePathSize
            )
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
    target: ThreadSaveStorageTarget
) {
    cleanupThreadSaveStorageTarget(fileSystem, target)
}
