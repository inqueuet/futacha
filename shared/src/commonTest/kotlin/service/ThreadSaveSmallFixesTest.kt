package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadSaveSmallFixesTest {
    private fun hasLoneSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char.isHighSurrogate()) {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return true
                index += 2
                continue
            }
            if (char.isLowSurrogate()) return true
            index += 1
        }
        return false
    }

    @Test
    fun manualImageFolderNameCutsTheTitleByCodePointsNotUtf16Units() {
        val name = buildCompatManualImageFolderName("may", "𠮷𠮷𠮷𠮷𠮷", "123")
        assertFalse(hasLoneSurrogate(name))
        // Four code points of the title are kept: 8 UTF-16 units.
        assertTrue(name.contains("𠮷𠮷𠮷𠮷"))
        assertFalse(name.contains("𠮷𠮷𠮷𠮷𠮷"))
        assertEquals("ab", "abcdef".takeCodePoints(2))
        assertEquals("abc", "abc".takeCodePoints(9))
    }

    @Test
    fun takeWithoutSplittingSurrogatesDropsAHalfPairAtTheCut() {
        val text = "a" + "😀"
        assertEquals("a", text.takeWithoutSplittingSurrogates(2))
        assertEquals(text, text.takeWithoutSplittingSurrogates(3))
    }

    @Test
    fun savedMediaPathRewriteKeepsSeparateUrlsInOneTextRunApart() {
        val html = "see https://may.2chan.net/b/src/1.jpg and https://may.2chan.net/b/src/2.jpg end"
        val rewritten = replaceSavedMediaPaths(html, "b", emptyMap())
        assertEquals("see b/src/1.jpg and b/src/2.jpg end", rewritten)
        val quoted = """<a href="https://may.2chan.net/b/src/1.jpg"><img src="//may.2chan.net/b/thumb/1s.jpg"></a>"""
        assertEquals("""<a href="b/src/1.jpg"><img src="b/thumb/1s.jpg"></a>""", replaceSavedMediaPaths(quoted, "b", emptyMap()))
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun metadata(postCount: Int, htmlChars: Int) = SavedThreadMetadata(
        threadId = "1",
        boardId = "b",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        title = "t",
        storageId = "s",
        savedAt = 1L,
        expiresAtLabel = null,
        posts = (1..postCount).map {
            SavedPost(
                id = it.toString(), order = it, author = null, subject = null, timestamp = "t",
                messageHtml = "<b>x</b>" + "あ".repeat(htmlChars) + "<a href=\"x\">link</a>",
                originalImageUrl = null, localImagePath = null, originalVideoUrl = null,
                localVideoPath = null, originalThumbnailUrl = null, localThumbnailPath = null
            )
        },
        totalSize = 0L
    )

    @Test
    fun metadataLargerThanTheReaderAllowsIsShortenedAndMarkedTruncated() {
        val source = metadata(postCount = 20, htmlChars = 50_000)
        val (payload, size) = fitThreadSaveMetadataPayload(
            metadata = source,
            baseTotalSize = 100L,
            encodeMetadata = { json.encodeToString(it) },
            maxBytes = 300_000L
        )
        assertTrue(size <= 300_000L)
        val decoded = json.decodeFromString<SavedThreadMetadata>(payload)
        assertTrue(decoded.isTruncated)
        assertTrue(decoded.truncationReason.orEmpty().contains("metadata size limit"))
        assertEquals(20, decoded.posts.size)
        assertTrue(decoded.posts.all { it.messageHtml.length <= 4_000 })
        assertFalse(hasLoneSurrogate(decoded.posts.first().messageHtml))
    }

    @Test
    fun metadataThatFitsIsWrittenUnchangedAndOneThatNeverFitsFailsExplicitly() {
        val small = metadata(postCount = 2, htmlChars = 10)
        val (payload, _) = fitThreadSaveMetadataPayload(small, 0L, { json.encodeToString(it) }, maxBytes = 100_000L)
        assertFalse(json.decodeFromString<SavedThreadMetadata>(payload).isTruncated)

        assertFailsWith<IllegalStateException> {
            fitThreadSaveMetadataPayload(metadata(postCount = 20, htmlChars = 50_000), 0L, { json.encodeToString(it) }, maxBytes = 1_000L)
        }
    }
}
