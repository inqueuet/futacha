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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMhtLibraryHardeningTest {
    private val board = BoardSummary("may27", "ねこ", "", "https://may.2chan.net/27/", "")
    private val ref = FutaberThreadRef("may27", "324989", "うちの子です", "", 2)
    private val thumb = ByteArray(300) { (it % 251).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
    private val full = ByteArray(900) { (it % 241).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
    private val page = ThreadPage(
        threadId = "324989", boardTitle = null, expiresAtLabel = null, deletedNotice = null,
        posts = listOf(
            Post("324989", 0, null, "題", "26/08/31(月)07:49:43", "ID:aaa", "本文", "https://may.2chan.net/27/src/1.jpg", "https://may.2chan.net/27/thumb/1s.jpg"),
            Post("325000", 1, null, null, "26/09/01(火)08:00:00", null, "返信", null, null)
        )
    )

    private fun client() = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val url = request.url.toString()
                when {
                    url.endsWith("/thumb/1s.jpg") -> respond(thumb, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                    url.endsWith("/src/1.jpg") -> respond(full, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        }
    }

    @Test
    fun aSaveThatFailsOrIsCancelledLeavesTheEarlierFileAsItWasAndNoTemporaryFile() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val first = library.save(board, ref, page, fullImages = true, nowMillis = 1L).getOrThrow()
        val before = fs.readBytes(first.entry.relativePath).getOrThrow()

        val failed = library.save(board, ref, page, fullImages = true, nowMillis = 2L) { done, _ -> if (done == 1) error("boom") }
        assertTrue(failed.isFailure)
        assertContentEquals(before, fs.readBytes(first.entry.relativePath).getOrThrow())

        assertFailsWith<CancellationException> {
            library.save(board, ref, page, fullImages = true, nowMillis = 3L) { done, _ -> if (done == 1) throw CancellationException("stop") }
        }
        assertContentEquals(before, fs.readBytes(first.entry.relativePath).getOrThrow())
        assertEquals(listOf("may_2chan_net-27-324989.mht"), fs.listFiles("futaber_mht"))
        assertEquals(1, library.list().size)
    }

    @Test
    fun aFileSavedAgainReplacesTheEarlierOneWithTheNewContent() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        library.save(board, ref, page, fullImages = false, nowMillis = 1L).getOrThrow()
        val again = library.save(board, ref, page, fullImages = true, nowMillis = 2L).getOrThrow()
        val list = library.list()
        assertEquals(1, list.size)
        assertEquals(2L, list.single().savedAtMillis)
        assertEquals(again.entry.sizeBytes, list.single().sizeBytes)
        assertTrue(list.single().withFullImages)
    }

    @Test
    fun theFileNeverGrowsPastItsLimitSoWhatWasSavedCanBeOpened() = runBlocking {
        val whole = FutaberMhtLibrary(MhtTestFileSystem(), client()).save(board, ref, page, fullImages = true, nowMillis = 5L).getOrThrow()
        assertEquals(2, whole.pictureCount)
        val limit = whole.entry.sizeBytes - 1

        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client(), maxFileBytes = limit)
        val saved = library.save(board, ref, page, fullImages = true, nowMillis = 5L).getOrThrow()
        assertEquals(1, saved.pictureCount)
        assertEquals(1, saved.missingPictures)
        assertTrue(saved.entry.sizeBytes <= limit)
        assertEquals(saved.entry.sizeBytes, fs.getFileSize(saved.entry.relativePath))

        val opened = library.open(saved.entry).getOrThrow()
        // The picture that did not fit stays the board's address; the one that did is a copy on this device.
        assertEquals("https://may.2chan.net/27/src/1.jpg", opened.page.posts[0].imageUrl)
        assertFalse(opened.page.posts[0].thumbnailUrl!!.startsWith("http"))
    }

    @Test
    fun theRoomLeftInAFileBecomesTheMostARawPictureMayHave() {
        assertEquals(0L, FutaberMhtLibrary.maxRawBytesFor(0L))
        assertEquals(0L, FutaberMhtLibrary.maxRawBytesFor(-5L))
        val room = 1_000_000L
        val raw = FutaberMhtLibrary.maxRawBytesFor(room)
        // What fits is never more than the room, even with the line breaks.
        assertTrue(FutaberMhtWriter.bodySize(raw.toInt()) <= room)
    }

    @Test
    fun anOpenedFileKeepsNoPictureBytesAndNoPictureOfAnotherHostIsShown() = runBlocking {
        val boundary = FutaberMhtWriter.newBoundary(4)
        val posts = listOf(
            Post("324989", 0, null, null, "t", null, "本文", "http://evil.example/x.jpg", "https://may.2chan.net/27/thumb/1s.jpg"),
            Post("325000", 1, null, null, "t", null, "返信", "https://may.2chan.net/27/src/9.jpg", "file:///etc/passwd"),
        )
        val url = "https://may.2chan.net/27/res/324989.htm"
        val html = FutaberMhtThreadHtml.build("題", url, "ねこ", posts)
        val bytes = FutaberMhtWriter.header(boundary, "題", "Tue, 06 Oct 2026 11:04:00 +0000", url, listOf("Thread" to "324989")) +
            FutaberMhtWriter.part(boundary, "text/html", url, html.encodeToByteArray(), "utf-8") +
            FutaberMhtWriter.part(boundary, "image/jpeg", "https://may.2chan.net/27/thumb/1s.jpg", thumb) +
            FutaberMhtWriter.part(boundary, "image/jpeg", "http://evil.example/x.jpg", full) +
            FutaberMhtWriter.end(boundary)
        val library = FutaberMhtLibrary(MhtTestFileSystem())
        val entry = library.import(bytes, "x.mht", 1L).getOrThrow()
        val opened = library.open(entry).getOrThrow()

        assertTrue(opened.thread.pictures.isEmpty())
        val first = opened.page.posts[0]
        assertFalse(first.thumbnailUrl!!.startsWith("http"))
        // A picture the file carries is a copy on this device, whatever address it had; the address of another host that the file does
        // not carry is dropped, and a Futaba host's address that it does not carry stays (it is the board's own).
        assertFalse(first.imageUrl!!.startsWith("http"))
        assertEquals("https://may.2chan.net/27/src/9.jpg", opened.page.posts[1].imageUrl)
        assertNull(opened.page.posts[1].thumbnailUrl)
    }

    @Test
    fun aPictureThatCannotBeDecodedLeavesTheFileOpenableWithItsAddressKept() = runBlocking {
        val boundary = FutaberMhtWriter.newBoundary(4)
        val url = "https://may.2chan.net/27/res/324989.htm"
        val html = FutaberMhtThreadHtml.build("題", url, "ねこ", page.posts)
        val bytes = FutaberMhtWriter.header(boundary, "題", "Tue, 06 Oct 2026 11:04:00 +0000", url, listOf("Thread" to "324989")) +
            FutaberMhtWriter.part(boundary, "text/html", url, html.encodeToByteArray(), "utf-8") +
            ("--$boundary\r\nContent-Type: image/jpeg\r\nContent-Transfer-Encoding: base64\r\n" +
                "Content-Location: https://may.2chan.net/27/thumb/1s.jpg\r\n\r\n!!!not base64!!!\r\n").encodeToByteArray() +
            FutaberMhtWriter.part(boundary, "image/jpeg", "https://may.2chan.net/27/src/1.jpg", full) +
            FutaberMhtWriter.end(boundary)
        val library = FutaberMhtLibrary(MhtTestFileSystem())
        val entry = library.import(bytes, "broken.mht", 1L).getOrThrow()
        val opened = library.open(entry).getOrThrow()
        assertEquals("https://may.2chan.net/27/thumb/1s.jpg", opened.page.posts[0].thumbnailUrl)
        assertFalse(opened.page.posts[0].imageUrl!!.startsWith("http"))
    }

    @Test
    fun filesSavedUnderTheOldNameAreStillListedOpenedReplacedAndDeleted() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val saved = library.save(board, ref, page, fullImages = false, nowMillis = 1L).getOrThrow()
        val bytes = fs.readBytes(saved.entry.relativePath).getOrThrow()
        fs.delete(saved.entry.relativePath)
        fs.writeBytes("futaber_mht/may-27-324989.mht", bytes)

        val entry = library.list().single()
        assertEquals("may-27-324989.mht", entry.fileName)
        library.open(entry).getOrThrow()

        // Saving the same thread again replaces the old-named file instead of adding a second one.
        val again = library.save(board, ref, page, fullImages = false, nowMillis = 2L).getOrThrow()
        assertEquals("may-27-324989.mht", again.entry.fileName)
        assertEquals(1, library.list().size)

        // The same old name from another host is another board's file: it is not overwritten.
        val elsewhere = board.copy(id = "x", url = "https://may.example.org/27/")
        val other = library.save(elsewhere, ref, page, fullImages = false, nowMillis = 3L).getOrThrow()
        assertEquals("may_example_org-27-324989.mht", other.entry.fileName)
        assertEquals(2, library.list().size)

        library.delete(again.entry).getOrThrow()
        assertEquals(listOf("may_example_org-27-324989.mht"), library.list().map { it.fileName })
    }

    @Test
    fun onlyTheLastFewFilesKeepTheirPicturesAndADeletedFilesPicturesGoWithIt() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val entries = (1..8).map { n ->
            library.save(board, ref.copy(threadId = "30000$n"), page, fullImages = false, nowMillis = n.toLong()).getOrThrow().entry
        }
        entries.forEach { library.open(it).getOrThrow() }
        val folders = fs.listFiles("private/futaber_mht_cache").filter { !it.startsWith(".") }
        assertEquals(6, folders.size)
        assertTrue(entries.last().fileName.removeSuffix(".mht") in folders)
        assertFalse(entries.first().fileName.removeSuffix(".mht") in folders)
        // Nothing is written to the data folder's own cache any more.
        assertFalse(fs.exists("futaber_mht_cache"))

        library.delete(entries.last()).getOrThrow()
        assertFalse(entries.last().fileName.removeSuffix(".mht") in fs.listFiles("private/futaber_mht_cache"))

        // A file that no longer exists leaves nothing behind at the next open.
        fs.delete(entries[6].relativePath)
        library.open(entries[5]).getOrThrow()
        assertFalse(entries[6].fileName.removeSuffix(".mht") in fs.listFiles("private/futaber_mht_cache"))
    }

    @Test
    fun theFolderTheOldVersionUsedIsCleanedUpPerFileWhenItsFileIsDeleted() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val entry = library.save(board, ref, page, fullImages = false, nowMillis = 1L).getOrThrow().entry
        val legacy = "futaber_mht_cache/${entry.fileName.removeSuffix(".mht")}"
        fs.createDirectory(legacy)
        fs.writeBytes("$legacy/0.jpg", thumb)
        library.delete(entry).getOrThrow()
        assertFalse(fs.exists(legacy))
    }

    @Test
    fun picturesKeptForATabStayAfterTheFileIsDeletedOrAnotherIsOpened() = runBlocking {
        val fs = MhtTestFileSystem()
        val library = FutaberMhtLibrary(fs, client())
        val entry = library.save(board, ref, page, fullImages = false, nowMillis = 1L).getOrThrow().entry
        val opened = library.open(entry, keepForTab = true).getOrThrow()
        val path = opened.page.posts[0].thumbnailUrl!!.substringAfter("/virtual/")
        assertTrue(path.startsWith("private/futaber_mht_tab_media/"))
        assertContentEquals(thumb, fs.readBytes(path).getOrThrow())

        library.delete(entry).getOrThrow()
        assertContentEquals(thumb, fs.readBytes(path).getOrThrow())
        // The viewer's own cache is a different folder.
        assertFalse(fs.exists("private/futaber_mht_cache/${entry.fileName.removeSuffix(".mht")}"))
    }

    @Test
    fun aFileThatIsNoLongerThereIsRefusedWithAMessage(): Unit = runBlocking {
        val library = FutaberMhtLibrary(MhtTestFileSystem(), client())
        val missing = library.open(
            FutaberMhtEntry("none.mht", "futaber_mht/none.mht", 0, "", "", null, null, "1", 0, 0, false, null)
        )
        assertTrue(missing.exceptionOrNull() is FutaberMhtFormatException)
        assertNotNull(missing.exceptionOrNull()?.message)
    }

    @Test
    fun twoLibrariesOverTheSameFileSystemAndClientAreTheSameKey() {
        val fs = MhtTestFileSystem()
        val http = client()
        assertEquals(FutaberMhtLibrary(fs, http), FutaberMhtLibrary(fs, http))
        assertEquals(FutaberMhtLibrary(fs, http).hashCode(), FutaberMhtLibrary(fs, http).hashCode())
        assertFalse(FutaberMhtLibrary(fs, http) == FutaberMhtLibrary(MhtTestFileSystem(), http))
        assertFalse(FutaberMhtLibrary(fs, http) == FutaberMhtLibrary(fs, null))
    }

    @Test
    fun onlyAFutabaHostsAddressIsAMediaAddressThatMayBeShown() {
        assertTrue(FutaberMhtLibrary.isOfficialMediaUrl("https://may.2chan.net/27/src/1.jpg"))
        assertTrue(FutaberMhtLibrary.isOfficialMediaUrl("http://img.2chan.net/b/thumb/1s.jpg"))
        assertFalse(FutaberMhtLibrary.isOfficialMediaUrl("http://evil.example/x.jpg"))
        assertFalse(FutaberMhtLibrary.isOfficialMediaUrl("https://evil.com\\@may.2chan.net/x.jpg"))
        assertFalse(FutaberMhtLibrary.isOfficialMediaUrl("file:///etc/passwd"))
        assertFalse(FutaberMhtLibrary.isOfficialMediaUrl("/data/user/0/app/files/0.jpg"))
    }
}
