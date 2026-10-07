package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMhtThreadTest {
    private val posts = listOf(
        Post(
            id = "324989", order = 0, author = "としあき", subject = "無題 & \"題\"", timestamp = "26/08/31(月)07:49:43",
            posterId = "ID:abcd1234", messageHtml = "本文<br>&gt;引用 <b>太字</b> a &amp; b",
            imageUrl = "https://may.2chan.net/27/src/1788130183312.jpg", thumbnailUrl = "https://may.2chan.net/27/thumb/1788130183312s.jpg",
            saidaneLabel = "そうだね×3", mail = "sage", thumbnailWidth = 250, thumbnailHeight = 188, imageFileSizeBytes = 419_000L
        ),
        Post(id = "325033", order = 1, author = null, subject = null, timestamp = "26/09/06(日)20:16:36", messageHtml = "書き込みをした人によって削除されました", imageUrl = null, thumbnailUrl = null, isDeleted = true)
    )

    @Test
    fun theHtmlWrittenHereGivesTheSamePostsBack() {
        val html = FutaberMhtThreadHtml.build("うちの子です <チョビ>", "https://may.2chan.net/27/res/324989.htm", "ねこ", posts)
        assertTrue(FutaberMhtThreadHtml.isOurs(html))
        assertEquals("うちの子です <チョビ>", FutaberMhtThreadHtml.title(html))
        assertEquals(posts, FutaberMhtThreadHtml.parse(html))
    }

    @Test
    fun aPageFromElsewhereIsNotTakenForOurs() {
        assertFalse(FutaberMhtThreadHtml.isOurs("<html><body>plain</body></html>"))
        assertEquals(emptyList(), FutaberMhtThreadHtml.parse("<html><body>plain</body></html>"))
    }

    @Test
    fun aFileWrittenHereIsReadAsAThreadWithItsPictures() = runBlocking {
        val boundary = FutaberMhtWriter.newBoundary(7)
        val html = FutaberMhtThreadHtml.build("題", "https://may.2chan.net/27/res/324989.htm", "ねこ", posts)
        val thumb = byteArrayOf(1, 2, 3)
        val bytes = FutaberMhtWriter.header(
            boundary, "題", "Tue, 06 Oct 2026 11:04:00 +0000", "https://may.2chan.net/27/res/324989.htm",
            listOf("Board" to "may", "Board-Name" to "ねこ", "Board-Url" to "https://may.2chan.net/27/", "Thread" to "324989")
        ) + FutaberMhtWriter.part(boundary, "text/html", "https://may.2chan.net/27/res/324989.htm", html.encodeToByteArray(), "utf-8") +
            FutaberMhtWriter.part(boundary, "image/jpeg", "https://may.2chan.net/27/thumb/1788130183312s.jpg", thumb) +
            FutaberMhtWriter.end(boundary)
        val thread = FutaberMhtThreadReader.read(FutaberMhtReader.parse(bytes))
        assertEquals("題", thread.title)
        assertEquals("ねこ", thread.boardName)
        assertEquals("https://may.2chan.net/27/", thread.boardUrl)
        assertEquals("324989", thread.threadId)
        assertEquals(posts, thread.page.posts)
        assertEquals(setOf("https://may.2chan.net/27/thumb/1788130183312s.jpg"), thread.pictures.keys)
    }

    @Test
    fun relativePictureAddressesAreMatchedToTheirParts() {
        val part = FutaberMhtPart("image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg", null, ByteArray(1))
        val pictures = mapOf("https://may.2chan.net/27/thumb/1s.jpg" to part)
        val base = "https://may.2chan.net/27/res/9.htm"
        assertEquals("https://may.2chan.net/27/thumb/1s.jpg", FutaberMhtThreadReader.resolvePicture("../thumb/1s.jpg", pictures, base))
        assertEquals("https://may.2chan.net/27/thumb/1s.jpg", FutaberMhtThreadReader.resolvePicture("/27/thumb/1s.jpg", pictures, base))
        assertEquals("https://may.2chan.net/27/thumb/1s.jpg", FutaberMhtThreadReader.resolvePicture("https://may.2chan.net/27/thumb/1s.jpg", pictures, base))
        // An address with no part stays as it was (the picture is simply not in the file).
        assertEquals("../thumb/2s.jpg", FutaberMhtThreadReader.resolvePicture("../thumb/2s.jpg", pictures, base))
        assertNull(FutaberMhtThreadReader.resolvePicture(null, pictures, base))
    }

    @Test
    fun theBoardAndThreadAreReadFromAThreadAddress() {
        assertEquals("324989", FutaberMhtThreadReader.threadIdFromUrl("https://may.2chan.net/27/res/324989.htm"))
        assertEquals("https://may.2chan.net/27/", FutaberMhtThreadReader.boardUrlFromThreadUrl("https://may.2chan.net/27/res/324989.htm"))
        assertNull(FutaberMhtThreadReader.threadIdFromUrl("https://example.com/"))
        assertNull(FutaberMhtThreadReader.boardUrlFromThreadUrl("https://example.com/"))
    }
}
