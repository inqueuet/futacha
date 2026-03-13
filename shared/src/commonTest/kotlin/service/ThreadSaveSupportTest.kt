package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadSaveSupportTest {
    @Test
    fun threadSaveExecutionSupport_buildsDeduplicatedMediaPlanWithCap() {
        val posts = listOf(
            Post(
                id = "1",
                author = "a",
                subject = null,
                timestamp = "now",
                messageHtml = "",
                imageUrl = "https://may.2chan.net/b/src/1.jpg",
                thumbnailUrl = "https://may.2chan.net/b/thumb/1.jpg"
            ),
            Post(
                id = "2",
                author = "b",
                subject = null,
                timestamp = "now",
                messageHtml = "",
                imageUrl = "https://may.2chan.net/b/src/1.jpg",
                thumbnailUrl = "https://may.2chan.net/b/thumb/2.jpg"
            )
        )

        val plan = buildThreadSaveMediaDownloadPlan(posts, maxMediaItems = 2)

        assertEquals(3, plan.totalMediaCount)
        assertEquals(
            listOf(
                ThreadSaveScheduledMediaItem(
                    url = "https://may.2chan.net/b/thumb/1.jpg",
                    requestType = ThreadSaveMediaRequestType.THUMBNAIL,
                    postId = "1"
                ),
                ThreadSaveScheduledMediaItem(
                    url = "https://may.2chan.net/b/src/1.jpg",
                    requestType = ThreadSaveMediaRequestType.FULL_IMAGE,
                    postId = "1"
                )
            ),
            plan.scheduledItems
        )
    }

    @Test
    fun threadSaveExecutionSupport_executesPlanAndAggregatesMappings() = runBlocking {
        val plan = ThreadSaveMediaDownloadPlan(
            scheduledItems = listOf(
                ThreadSaveScheduledMediaItem(
                    url = "https://may.2chan.net/b/thumb/1.jpg",
                    requestType = ThreadSaveMediaRequestType.THUMBNAIL,
                    postId = "100"
                ),
                ThreadSaveScheduledMediaItem(
                    url = "https://may.2chan.net/b/src/2.webm",
                    requestType = ThreadSaveMediaRequestType.FULL_IMAGE,
                    postId = "101"
                )
            ),
            totalMediaCount = 2
        )
        val progressUpdates = mutableListOf<Pair<Int, Int>>()

        val result = executeThreadSaveMediaDownloadPlan(
            plan = plan,
            opPostId = "100",
            chunkSize = 50,
            maxParallelDownloads = 1,
            maxRetries = 1,
            retryDelayMillis = 0L,
            logTag = "ThreadSaveSupportTest",
            createUrlToPathMap = { linkedMapOf() },
            createMediaKeyToFileInfoMap = { linkedMapOf() },
            updateProgress = { current, total -> progressUpdates += current to total },
            downloadMedia = { mediaItem ->
                Result.success(
                    when (mediaItem.url) {
                        "https://may.2chan.net/b/thumb/1.jpg" -> ThreadSaveLocalFileInfo(
                            relativePath = "b/thumb/1.jpg",
                            fileType = FileType.THUMBNAIL,
                            byteSize = 10L
                        )
                        else -> ThreadSaveLocalFileInfo(
                            relativePath = "b/src/2.webm",
                            fileType = FileType.VIDEO,
                            byteSize = 20L
                        )
                    }
                )
            },
            enforceBudget = { }
        )

        assertEquals(listOf(1 to 2, 2 to 2), progressUpdates)
        assertEquals(30L, result.totalSizeBytes)
        assertEquals(0, result.downloadFailureCount)
        assertEquals("b/thumb/1.jpg", result.urlToPathMap["https://may.2chan.net/b/thumb/1.jpg"])
        assertEquals("b/src/2.webm", result.urlToPathMap["https://may.2chan.net/b/src/2.webm"])
        assertEquals("b/thumb/1.jpg", result.mediaCounts.thumbnailPath)
        assertEquals(0, result.mediaCounts.imageCount)
        assertEquals(1, result.mediaCounts.videoCount)
    }

    @Test
    fun threadSaveExecutionSupport_buildsSavedPostsFromDownloadedMedia() = runBlocking {
        val posts = listOf(
            Post(
                id = "100",
                author = "a",
                subject = "sub",
                timestamp = "now",
                messageHtml = """<a href="https://may.2chan.net/b/src/2.webm">video</a>""",
                imageUrl = "https://may.2chan.net/b/src/2.webm",
                thumbnailUrl = "https://may.2chan.net/b/thumb/2.jpg"
            )
        )
        val mediaMap = mapOf(
            buildThreadSaveMediaDownloadKey(
                "https://may.2chan.net/b/src/2.webm",
                ThreadSaveMediaRequestType.FULL_IMAGE
            ) to ThreadSaveLocalFileInfo(
                relativePath = "b/src/2.webm",
                fileType = FileType.VIDEO,
                byteSize = 20L
            ),
            buildThreadSaveMediaDownloadKey(
                "https://may.2chan.net/b/thumb/2.jpg",
                ThreadSaveMediaRequestType.THUMBNAIL
            ) to ThreadSaveLocalFileInfo(
                relativePath = "b/thumb/2.jpg",
                fileType = FileType.THUMBNAIL,
                byteSize = 10L
            )
        )

        val savedPosts = buildThreadSaveSavedPosts(
            posts = posts,
            mediaKeyToFileInfoMap = mediaMap,
            urlToPathMap = mapOf("https://may.2chan.net/b/src/2.webm" to "b/src/2.webm"),
            updateProgress = { _, _ -> }
        )

        assertEquals(1, savedPosts.size)
        assertEquals("b/src/2.webm", savedPosts.first().localVideoPath)
        assertEquals("b/thumb/2.jpg", savedPosts.first().localThumbnailPath)
        assertTrue(savedPosts.first().downloadSuccess)
        assertTrue(savedPosts.first().messageHtml.contains("""href="b/src/2.webm""""))
    }

    @Test
    fun threadSaveExecutionSupport_preparesOutputDirectories() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.createDirectory("manual").getOrThrow()
        fileSystem.createDirectory("manual/thread").getOrThrow()
        fileSystem.writeString("manual/thread/old.txt", "stale").getOrThrow()

        prepareThreadSaveOutput(
            fileSystem = fileSystem,
            saveLocation = null,
            request = ThreadSaveOutputPreparationRequest(
                baseDirectory = "manual",
                storageId = "thread",
                boardPath = "b"
            )
        )

        assertFalse(fileSystem.exists("manual/thread/old.txt"))
        assertTrue(fileSystem.exists("manual/thread/b/src"))
        assertTrue(fileSystem.exists("manual/thread/b/thumb"))
    }

    @Test
    fun threadSaveExecutionSupport_writesMetadataWhenEnabled() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.createDirectory("manual").getOrThrow()
        fileSystem.createDirectory("manual/thread").getOrThrow()

        val payloadSize = writeThreadSaveMetadataIfEnabled(
            writeMetadata = true,
            fileSystem = fileSystem,
            saveLocation = null,
            baseDir = "manual/thread",
            request = ThreadSaveMetadataWriteRequest(
                threadId = "123",
                boardId = "b",
                boardName = "may/b",
                boardUrl = "https://may.2chan.net/b/futaba.php",
                title = "title",
                storageId = "thread",
                savedAtTimestamp = 10L,
                expiresAtLabel = null,
                savedPosts = emptyList(),
                rawHtmlRelativePath = "123.htm",
                strippedExternalResources = true,
                baseTotalSize = 100L
            ),
            encodeMetadata = {
                kotlinx.serialization.json.Json.encodeToString(SavedThreadMetadata.serializer(), it)
            }
        )

        assertTrue(payloadSize > 0L)
        val written = fileSystem.readString("manual/thread/metadata.json").getOrThrow()
        assertTrue(written.contains(""""threadId":"123""""))
    }

    @Test
    fun threadSaveExecutionSupport_savesRawHtmlAndBuildsSavedThread() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.createDirectory("manual").getOrThrow()
        fileSystem.createDirectory("manual/thread").getOrThrow()

        val rawHtmlResult = saveThreadRawHtmlIfEnabled(
            enabled = true,
            fileSystem = fileSystem,
            saveLocation = null,
            storageId = "thread",
            baseDir = "manual/thread",
            threadId = "123",
            fetchOriginalHtml = { Result.success("<html>ok</html>") },
            rewriteHtml = { "$it<!-- saved -->" },
            measureAbsolutePathSize = fileSystem::getFileSize,
            logWarning = { error("unexpected warning: $it") }
        )

        assertEquals("123.htm", rawHtmlResult.relativePath)
        assertTrue(rawHtmlResult.sizeBytes > 0L)
        assertTrue(fileSystem.readString("manual/thread/123.htm").getOrThrow().contains("saved"))

        val savedThread = buildThreadSaveSavedThread(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            title = "title",
            storageId = "thread",
            thumbnailPath = "b/thumb/1.jpg",
            savedAtTimestamp = 10L,
            postCount = 5,
            imageCount = 2,
            videoCount = 1,
            totalSize = 100L,
            downloadFailureCount = 1,
            totalMediaCount = 3
        )

        assertEquals(SaveStatus.PARTIAL, savedThread.status)
        assertEquals(100L, savedThread.totalSize)
        assertEquals("b/thumb/1.jpg", savedThread.thumbnailPath)
    }

    @Test
    fun convertSavedThreadHtmlPaths_rewritesMappedMediaUrls() {
        val html = """
            <div>
              <img src="https://may.2chan.net/b/thumb/123.jpg">
              <a href="https://may.2chan.net/b/src/123.webm">video</a>
              <video src="https://may.2chan.net/b/src/123.webm"></video>
              <source src="https://may.2chan.net/b/src/123.webm">
            </div>
        """.trimIndent()

        val converted = convertSavedThreadHtmlPaths(
            html = html,
            urlToPathMap = mapOf(
                "https://may.2chan.net/b/thumb/123.jpg" to "b/thumb/123.jpg",
                "https://may.2chan.net/b/src/123.webm" to "b/src/123.webm"
            )
        )

        assertTrue(converted.contains("""<img src="b/thumb/123.jpg">"""))
        assertTrue(converted.contains("""<a href="b/src/123.webm">video</a>"""))
        assertTrue(converted.contains("""<video src="b/src/123.webm"></video>"""))
        assertTrue(converted.contains("""<source src="b/src/123.webm">"""))
    }

    @Test
    fun rewriteSavedOriginalHtml_stripsExternalResources_and_forcesUtf8() {
        val html = """
            <html>
              <head>
                <meta charset="Shift_JIS">
                <meta http-equiv="Content-Type" content="text/html; charset=Shift_JIS">
                <script src="https://dec.2chan.net/bin/ad.js"></script>
              </head>
              <body>
                <iframe src="https://dec.2chan.net/bin/frame.html"></iframe>
                <img src="/b/thumb/123.jpg">
                <a href="https://may.2chan.net/b/src/456.jpg">img</a>
              </body>
            </html>
        """.trimIndent()

        val rewritten = rewriteSavedOriginalHtml(
            html = html,
            boardPath = "b",
            urlToPathMap = emptyMap(),
            stripExternalResources = true
        )

        assertFalse(rewritten.contains("<script"))
        assertFalse(rewritten.contains("<iframe"))
        assertTrue(rewritten.contains("""charset="UTF-8""""))
        assertTrue(rewritten.contains("charset=UTF-8"))
        assertTrue(rewritten.contains("""src="b/thumb/123.jpg""""))
        assertTrue(rewritten.contains("""href="b/src/456.jpg""""))
    }

    @Test
    fun rewriteSavedOriginalHtml_keepsExternalResources_whenDisabled() {
        val html = """
            <script src="https://dec.2chan.net/bin/ad.js"></script>
            <iframe src="https://dec.2chan.net/bin/frame.html"></iframe>
        """.trimIndent()

        val rewritten = rewriteSavedOriginalHtml(
            html = html,
            boardPath = "b",
            urlToPathMap = emptyMap(),
            stripExternalResources = false
        )

        assertTrue(rewritten.contains("<script"))
        assertTrue(rewritten.contains("<iframe"))
    }

    @Test
    fun extractThreadSaveBoardPath_prefersBoardPath_and_fallsBackToBoardId() {
        assertEquals(
            "img/b",
            extractThreadSaveBoardPath("https://dec.2chan.net/img/b/futaba.php", "fallback")
        )
        assertEquals(
            "fallback",
            extractThreadSaveBoardPath("not-a-url", "fallback")
        )
    }

    @Test
    fun threadSaveExtensionHelpers_coverUrlAndContentTypeCases() {
        assertEquals("webm", getThreadSaveExtensionFromUrl("https://may.2chan.net/b/src/1.webm?foo=1"))
        assertEquals("jpg", getThreadSaveExtensionFromContentType(ContentType.Image.JPEG))
        assertEquals("mp4", getThreadSaveExtensionFromContentType(ContentType.Video.MP4))
        assertEquals("jpg", getThreadSaveExtensionFromContentType(null))
    }

    @Test
    fun threadSaveMediaHelpers_classifySupportAndPaths() {
        assertTrue(isThreadSaveSupportedExtension("jpg"))
        assertTrue(isThreadSaveSupportedExtension("WEBM"))
        assertFalse(isThreadSaveSupportedExtension("svg"))

        assertEquals(
            FileType.THUMBNAIL,
            resolveThreadSaveFileType(ThreadSaveMediaRequestType.THUMBNAIL, "jpg")
        )
        assertEquals(
            FileType.VIDEO,
            resolveThreadSaveFileType(ThreadSaveMediaRequestType.FULL_IMAGE, "mp4")
        )
        assertEquals(
            FileType.FULL_IMAGE,
            resolveThreadSaveFileType(ThreadSaveMediaRequestType.FULL_IMAGE, "png")
        )

        assertEquals(
            "b/thumb/123.jpg",
            buildThreadSaveRelativePath("b", FileType.THUMBNAIL, "123.jpg")
        )
        assertEquals(
            "b/src/123.webm",
            buildThreadSaveRelativePath("b", FileType.VIDEO, "123.webm")
        )
    }

    @Test
    fun threadSaveSummaryHelpers_trackThumbnailAndMediaCounts() {
        val afterThumbnail = updateThreadSaveMediaCounts(
            current = ThreadSaveMediaCounts(),
            fileType = FileType.THUMBNAIL,
            relativePath = "b/thumb/op.jpg",
            postId = "100",
            opPostId = "100"
        )
        val afterImage = updateThreadSaveMediaCounts(
            current = afterThumbnail,
            fileType = FileType.FULL_IMAGE,
            relativePath = "b/src/1.jpg",
            postId = "101",
            opPostId = "100"
        )
        val afterVideo = updateThreadSaveMediaCounts(
            current = afterImage,
            fileType = FileType.VIDEO,
            relativePath = "b/src/2.webm",
            postId = "102",
            opPostId = "100"
        )

        assertEquals("b/thumb/op.jpg", afterVideo.thumbnailPath)
        assertEquals(1, afterVideo.imageCount)
        assertEquals(1, afterVideo.videoCount)
    }

    @Test
    fun threadSaveSummaryHelpers_resolveStatusAndSavedPostFlags() {
        assertEquals(SaveStatus.COMPLETED, resolveThreadSaveStatus(downloadFailureCount = 0, totalMediaCount = 3))
        assertEquals(SaveStatus.PARTIAL, resolveThreadSaveStatus(downloadFailureCount = 1, totalMediaCount = 3))
        assertEquals(SaveStatus.FAILED, resolveThreadSaveStatus(downloadFailureCount = 3, totalMediaCount = 3))

        assertTrue(
            resolveThreadSavedPostDownloadSuccess(
                originalImageUrl = null,
                localImagePath = null,
                localVideoPath = null
            )
        )
        assertTrue(
            resolveThreadSavedPostDownloadSuccess(
                originalImageUrl = "https://may.2chan.net/b/src/1.jpg",
                localImagePath = "b/src/1.jpg",
                localVideoPath = null
            )
        )
        assertTrue(
            resolveThreadSavedPostDownloadSuccess(
                originalImageUrl = "https://may.2chan.net/b/src/1.webm",
                localImagePath = null,
                localVideoPath = "b/src/1.webm"
            )
        )
        assertFalse(
            resolveThreadSavedPostDownloadSuccess(
                originalImageUrl = "https://may.2chan.net/b/src/missing.jpg",
                localImagePath = null,
                localVideoPath = null
            )
        )
    }

    @Test
    fun threadSaveRuntimeSupport_lruCache_movesReadsAndEvictsOldest() {
        val cache = createThreadSaveLruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2

        assertEquals(1, cache["a"])

        cache["c"] = 3

        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }

    @Test
    fun threadSaveRuntimeSupport_buildsStableMetadataPayloadSize() {
        val metadata = SavedThreadMetadata(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            storageId = "b_123",
            savedAt = 1L,
            expiresAtLabel = null,
            posts = emptyList(),
            totalSize = 0L,
            rawHtmlPath = null,
            strippedExternalResources = true,
            version = 1
        )

        val (payload, payloadSize) = buildThreadSaveMetadataPayloadWithStableSize(
            metadata = metadata,
            baseTotalSize = 100L,
            encodeMetadata = { kotlinx.serialization.json.Json.encodeToString(SavedThreadMetadata.serializer(), it) }
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(SavedThreadMetadata.serializer(), payload)

        assertEquals(payloadSize, measureThreadSaveUtf8ByteLength(payload))
        assertEquals(100L + payloadSize, decoded.totalSize)
    }

    @Test
    fun threadSaveRuntimeSupport_utf8LengthMediaKeyFileNameAndBudgetBehaveAsExpected() {
        assertEquals(4L, measureThreadSaveUtf8ByteLength("😀"))
        assertEquals(
            "FULL_IMAGE|https://may.2chan.net/b/src/1.jpg",
            buildThreadSaveMediaDownloadKey(
                " https://may.2chan.net/b/src/1.jpg ",
                ThreadSaveMediaRequestType.FULL_IMAGE
            )
        )
        assertEquals(
            "1.jpg",
            resolveThreadSaveFileName(
                url = "https://may.2chan.net/b/src/1.jpg?foo=1",
                extension = "jpg",
                postId = "1",
                timestampMillis = 100L
            )
        )
        assertEquals(
            "2_100.jpg",
            resolveThreadSaveFileName(
                url = "https://may.2chan.net/b/src/",
                extension = "jpg",
                postId = "2",
                timestampMillis = 100L
            )
        )

        enforceThreadSaveBudget(
            totalSizeBytes = 10L,
            startedAtMillis = 100L,
            nowMillis = 150L,
            maxTotalSizeBytes = 100L,
            maxSaveDurationMs = 1_000L
        )

        assertFailsWith<IllegalStateException> {
            enforceThreadSaveBudget(
                totalSizeBytes = 200L,
                startedAtMillis = 100L,
                nowMillis = 150L,
                maxTotalSizeBytes = 100L,
                maxSaveDurationMs = 1_000L
            )
        }
        assertFailsWith<IllegalStateException> {
            enforceThreadSaveBudget(
                totalSizeBytes = 10L,
                startedAtMillis = 100L,
                nowMillis = 2_000L,
                maxTotalSizeBytes = 100L,
                maxSaveDurationMs = 1_000L
            )
        }
    }

    @Test
    fun threadSaveExecutionSupport_cleansUpOutputsAndReleasesMediaLocks() = kotlinx.coroutines.runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.createDirectory("manual").getOrThrow()
        fileSystem.createDirectory("manual/thread").getOrThrow()
        fileSystem.writeString("manual/thread/test.txt", "ok").getOrThrow()

        cleanupThreadSaveFailedOutput(
            fileSystem = fileSystem,
            baseSaveLocation = null,
            baseDirectory = "manual",
            storageId = "thread"
        )
        assertFalse(fileSystem.exists("manual/thread"))

        val guard = Mutex()
        val locks = mutableMapOf<String, ThreadSaveMediaPathLock>()
        val result = withThreadSaveMediaWriteLock(
            relativePath = "b/src/1.jpg",
            mediaWriteLocksGuard = guard,
            mediaWriteLocks = locks
        ) {
            "locked"
        }

        assertEquals("locked", result)
        assertTrue(locks.isEmpty())
    }
}
