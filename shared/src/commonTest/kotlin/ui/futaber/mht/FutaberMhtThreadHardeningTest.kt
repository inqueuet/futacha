package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberMhtThreadHardeningTest {
    private val posts = listOf(
        Post(
            id = "324989", order = 0, author = null, subject = "題", timestamp = "26/08/31(月)07:49:43",
            messageHtml = "本文", imageUrl = null, thumbnailUrl = null, referencedCount = 2
        ),
        Post(
            id = "325000", order = 1, author = null, subject = null, timestamp = "26/09/01(火)08:00:00",
            messageHtml = "&gt;&gt;324989", imageUrl = null, thumbnailUrl = null,
            quoteReferences = listOf(
                QuoteReference(">>324989", listOf("324989")),
                QuoteReference("\">&<'quote", listOf("324989", "325000"))
            )
        )
    )

    private fun file(
        threadId: String,
        snapshot: String,
        saved: String? = null,
        date: String = "Tue, 06 Oct 2026 11:04:00 +0000",
        info: FutaberMhtPageInfo = FutaberMhtPageInfo()
    ): ByteArray {
        val boundary = FutaberMhtWriter.newBoundary(9)
        val html = FutaberMhtThreadHtml.build("題", snapshot, "ねこ", posts, info)
        val custom = buildList {
            add("Thread" to threadId)
            if (saved != null) add("Saved" to saved)
        }
        return FutaberMhtWriter.header(boundary, "題", date, snapshot, custom) +
            FutaberMhtWriter.part(boundary, "text/html", snapshot, html.encodeToByteArray(), "utf-8") +
            FutaberMhtWriter.end(boundary)
    }

    @Test
    fun quotesReplyCountsAndThePagesOwnFactsComeBackFromAFileWrittenHere() = runBlocking {
        val info = FutaberMhtPageInfo("12:00頃消えます", "削除されました", isTruncated = true, truncationReason = "上限")
        val html = FutaberMhtThreadHtml.build("題", "https://may.2chan.net/27/res/324989.htm", "ねこ", posts, info)
        assertEquals(posts, FutaberMhtThreadHtml.parse(html))
        assertEquals(info, FutaberMhtThreadHtml.pageInfo(html))

        val thread = FutaberMhtThreadReader.read(
            FutaberMhtReader.parse(file("324989", "https://may.2chan.net/27/res/324989.htm", info = info))
        )
        assertEquals(posts, thread.page.posts)
        assertEquals(2, thread.page.posts[0].referencedCount)
        assertEquals(2, thread.page.posts[1].quoteReferences.size)
        assertEquals("12:00頃消えます", thread.page.expiresAtLabel)
        assertEquals("削除されました", thread.page.deletedNotice)
        assertTrue(thread.page.isTruncated)
        assertEquals("上限", thread.page.truncationReason)
    }

    @Test
    fun aFileWrittenBeforeThoseFactsWereKeptIsStillRead() = runBlocking {
        val html = FutaberMhtThreadHtml.build("題", "https://may.2chan.net/27/res/324989.htm", "ねこ", posts)
            .replace(Regex(" data-refs=\"[^\"]*\""), "")
            .replace(Regex(" data-quotes=\"[^\"]*\""), "")
        val parsed = FutaberMhtThreadHtml.parse(html)
        assertEquals(2, parsed.size)
        assertEquals(0, parsed[0].referencedCount)
        assertEquals(emptyList(), parsed[1].quoteReferences)
        assertEquals(FutaberMhtPageInfo(), FutaberMhtThreadHtml.pageInfo(html))
        assertEquals(FutaberMhtPageInfo(), FutaberMhtThreadHtml.pageInfo("<html><body>plain</body></html>"))
    }

    @Test
    fun manyPostsAreWalkedOnceAndMoreThanAThreadCanHaveAreRefused() {
        val many = (1..3_000).map { Post(id = "$it", order = it, author = null, subject = null, timestamp = "t", messageHtml = "m$it", imageUrl = null, thumbnailUrl = null) }
        val html = FutaberMhtThreadHtml.build("題", "https://may.2chan.net/27/res/1.htm", "ねこ", many)
        assertEquals(3_000, FutaberMhtThreadHtml.parse(html).size)

        // Opening tags with no closing tag are not searched from again and again.
        val unclosed = "<body data-futaber-mht=\"1\">" + "<article class=\"res\" data-no=\"1\">".repeat(20_000)
        assertEquals(emptyList(), FutaberMhtThreadHtml.parse(unclosed))

        val tooMany = "<body data-futaber-mht=\"1\">" + "<article class=\"res\" data-no=\"1\"></article>".repeat(10_001)
        assertFailsWith<FutaberMhtFormatException> { FutaberMhtThreadHtml.parse(tooMany) }
    }

    @Test
    fun theThreadNumberIsDigitsAndIsTheOneTheAddressSays() = runBlocking {
        val ok = FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", "https://may.2chan.net/27/res/324989.htm")))
        assertEquals("324989", ok.threadId)
        assertFailsWith<FutaberMhtFormatException> {
            FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("../324989", "https://may.2chan.net/27/res/324989.htm")))
        }
        assertFailsWith<FutaberMhtFormatException> {
            FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", "https://may.2chan.net/27/res/111.htm")))
        }
        assertTrue(FutaberMhtThreadReader.isThreadNumber("1"))
        assertFalse(FutaberMhtThreadReader.isThreadNumber(""))
        assertFalse(FutaberMhtThreadReader.isThreadNumber("12a"))
        assertFalse(FutaberMhtThreadReader.isThreadNumber("1".repeat(21)))
    }

    @Test
    fun whenTheFileWasSavedIsReadFromItsOwnHeaderThenItsDateHeader() = runBlocking {
        val url = "https://may.2chan.net/27/res/324989.htm"
        assertEquals(12_345L, FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", url, saved = "12345"))).savedAtMillis)
        assertEquals(1_791_284_640_000L, FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", url))).savedAtMillis)
        assertNull(FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", url, date = "unknown"))).savedAtMillis)
    }

    @Test
    fun aThreadWithoutItsPicturesHoldsNoPartOfTheFile() = runBlocking {
        val bytes = file("324989", "https://may.2chan.net/27/res/324989.htm")
        val thread = FutaberMhtThreadReader.read(FutaberMhtReader.parse(bytes))
        assertTrue(thread.withoutPictures().pictures.isEmpty())
        assertEquals(thread.page, thread.withoutPictures().page)
    }

    @Test
    fun aBoardNamedByAFileIsOnlyUsedForAFutabaHostAndAStandInCannotBePostedTo() = runBlocking {
        val registered = BoardSummary("may27", "ねこ", "", "https://may.2chan.net/27/", "")
        val url = "https://may.2chan.net/27/res/324989.htm"
        val thread = FutaberMhtThreadReader.read(FutaberMhtReader.parse(file("324989", url)))
        assertEquals("may27", futaberBoardForMht(thread, listOf(registered)).id)
        assertTrue(futaberMhtCanPost(registered))

        val standIn = futaberBoardForMht(thread, emptyList())
        assertTrue(futaberMhtBoardIsStandIn(standIn))
        assertFalse(futaberMhtCanPost(standIn))
        // A file that names another host as its board does not get that host as the stand-in's address.
        val foreign = FutaberMhtThread(
            title = "t", boardName = "x", boardKey = null, boardUrl = "https://evil.example/b/", threadId = "1",
            threadUrl = "https://evil.example/b/res/1.htm", page = thread.page, pictures = emptyMap()
        )
        assertEquals("https://invalid.local/", futaberBoardForMht(foreign, emptyList()).url)
    }
}
