package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.futaber.FutaberThreadRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FutaberMhtLibraryTest {
    private val board = BoardSummary("may27", "ねこ", "", "https://may.2chan.net/27/", "")
    private val ref = FutaberThreadRef("may27", "324989", "うちの子です チョビたん：1歳", "", 2)
    private val thumb = ByteArray(300) { (it % 251).toByte() } .also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
    private val full = ByteArray(900) { (it % 241).toByte() } .also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
    private val page = ThreadPage(
        threadId = "324989", boardTitle = null, expiresAtLabel = null, deletedNotice = null,
        posts = listOf(
            Post("324989", 0, null, "題", "26/08/31(月)07:49:43", "ID:aaa", "本文<br>2行目", "https://may.2chan.net/27/src/1.jpg", "https://may.2chan.net/27/thumb/1s.jpg"),
            Post("325000", 1, null, null, "26/09/01(火)08:00:00", null, "返信", null, null)
        )
    )
    private val requested = mutableListOf<String>()

    private fun client(failFull: Boolean = false) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val url = request.url.toString()
                requested += url
                when {
                    url.endsWith("/thumb/1s.jpg") -> respond(thumb, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                    url.endsWith("/src/1.jpg") && !failFull -> respond(full, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        }
    }

    @Test
    fun theFileNameKeepsTwoBoardsOfOneHostApart() {
        assertEquals("may_2chan_net-27-324989.mht", FutaberMhtLibrary.fileNameFor(board, "324989"))
        val other = board.copy(url = "https://may.2chan.net/b/")
        assertEquals("may_2chan_net-b-324989.mht", FutaberMhtLibrary.fileNameFor(other, "324989"))
        // Two hosts that share a first label no longer share a name.
        val elsewhere = board.copy(url = "https://may.example.org/27/")
        assertEquals("may_example_org-27-324989.mht", FutaberMhtLibrary.fileNameFor(elsewhere, "324989"))
        // The name before the host was used whole is still known, to find the files saved so.
        assertEquals("may-27-324989.mht", FutaberMhtLibrary.legacyFileNameFor(board.url, "324989"))
        assertEquals("may-27-324989.mht", FutaberMhtLibrary.legacyFileNameFor(elsewhere.url, "324989"))
    }

    @Test
    fun picturesAreThumbnailsFirstWithFullSizeOnlyWhenAskedAndNoAddressTwice() {
        val posts = page.posts + page.posts[0].copy(id = "9")
        assertEquals(listOf("https://may.2chan.net/27/thumb/1s.jpg"), FutaberMhtLibrary.pictureAddresses(posts, fullImages = false))
        assertEquals(
            listOf("https://may.2chan.net/27/thumb/1s.jpg", "https://may.2chan.net/27/src/1.jpg"),
            FutaberMhtLibrary.pictureAddresses(posts, fullImages = true)
        )
    }

    @Test
    fun aSavedThreadIsListedOpenedSharedAndDeleted() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val saved = library.save(board, ref, page, fullImages = true, nowMillis = 1_790_000_000_000L).getOrThrow()
        assertEquals("may_2chan_net-27-324989.mht", saved.entry.fileName)
        assertEquals(2, saved.pictureCount)
        assertEquals(0, saved.missingPictures)

        val list = library.list()
        assertEquals(1, list.size)
        val entry = list.single()
        assertEquals("うちの子です チョビたん：1歳", entry.title)
        assertEquals("ねこ", entry.boardName)
        assertEquals("324989", entry.threadId)
        assertEquals(2, entry.postCount)
        assertTrue(entry.withFullImages)
        assertEquals(1_790_000_000_000L, entry.savedAtMillis)
        assertContentEquals(thumb, entry.thumbnail)
        assertTrue(entry.sizeBytes > 1000)

        val opened = library.open(entry).getOrThrow()
        assertEquals(2, opened.page.posts.size)
        assertEquals("本文<br>2行目", opened.page.posts[0].messageHtml)
        // The pictures now point at copies on this device, and the bytes are the ones that were saved.
        val thumbPath = assertNotNull(opened.page.posts[0].thumbnailUrl)
        val fullPath = assertNotNull(opened.page.posts[0].imageUrl)
        assertFalse(thumbPath.startsWith("http"))
        // (The in-memory file system keeps relative paths; the absolute path is its "/virtual" form of them.)
        fun relative(path: String) = path.substringAfter("/virtual/")
        assertContentEquals(thumb, fs.readBytes(relative(thumbPath)).getOrThrow())
        assertContentEquals(full, fs.readBytes(relative(fullPath)).getOrThrow())

        assertTrue(library.absolutePath(entry).endsWith("futaber_mht/may_2chan_net-27-324989.mht"))

        library.delete(entry).getOrThrow()
        assertEquals(emptyList(), library.list())
        assertFalse(fs.exists(relative(thumbPath)))
    }

    @Test
    fun aPictureThatCannotBeFetchedIsLeftOutAndCountedButTheFileIsStillWritten() = runBlocking {
        val library = FutaberMhtLibrary(MhtTestFileSystem(), client(failFull = true))
        val saved = library.save(board, ref, page, fullImages = true, nowMillis = 5L).getOrThrow()
        assertEquals(1, saved.pictureCount)
        assertEquals(1, saved.missingPictures)
        val opened = library.open(saved.entry).getOrThrow()
        // The full-size address stays as it was, since the file has no copy of it.
        assertEquals("https://may.2chan.net/27/src/1.jpg", opened.page.posts[0].imageUrl)
    }

    @Test
    fun thumbnailsOnlyDoesNotFetchTheFullSizePictures() = runBlocking {
        requested.clear()
        val library = FutaberMhtLibrary(MhtTestFileSystem(), client())
        val saved = library.save(board, ref, page, fullImages = false, nowMillis = 5L).getOrThrow()
        assertEquals(1, saved.pictureCount)
        assertFalse(requested.any { it.endsWith("/src/1.jpg") })
        assertFalse(library.list().single().withFullImages)
    }

    @Test
    fun aFileFromOutsideIsTakenInOnlyWhenItIsAThreadPage() = runBlocking {
        val fs = MhtTestFileSystem()
        val writer = FutaberMhtLibrary(fs, client())
        val saved = writer.save(board, ref, page, fullImages = false, nowMillis = 7L).getOrThrow()
        val bytes = fs.readBytes(saved.entry.relativePath).getOrThrow()

        val other = FutaberMhtLibrary(MhtTestFileSystem())
        val imported = other.import(bytes, "shared copy.mht", nowMillis = 9L).getOrThrow()
        assertEquals("shared_copy.mht", imported.fileName)
        assertEquals("うちの子です チョビたん：1歳", imported.title)
        assertEquals(1, other.list().size)
        // A second file of the same name does not overwrite the first.
        assertEquals("shared_copy-2.mht", other.import(bytes, "shared copy.mht", nowMillis = 9L).getOrThrow().fileName)

        val refused = other.import("not an mht".encodeToByteArray(), "x.mht", nowMillis = 9L)
        assertTrue(refused.isFailure)
        assertEquals(2, other.list().size)
    }

    @Test
    fun theDateHeaderIsInTheFormMhtReadersExpect() {
        assertEquals("Thu, 01 Jan 1970 00:00:00 +0000", FutaberMhtLibrary.rfc1123(0L))
        assertEquals("Tue, 06 Oct 2026 11:04:00 +0000", FutaberMhtLibrary.rfc1123(1_791_284_640_000L))
    }
}
