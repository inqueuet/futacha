package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveStatus
import io.ktor.http.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadSaveSupportTest {
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
}
