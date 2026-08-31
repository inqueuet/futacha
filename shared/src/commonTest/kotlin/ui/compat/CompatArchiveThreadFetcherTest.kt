package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompatArchiveThreadFetcherTest {
    private val sourceUrl = "https://may.2chan.net/b/res/123.htm"

    private fun post(id: String, order: Int, message: String): Post = Post(
        id = id,
        order = order,
        author = "としあき",
        subject = null,
        timestamp = "26/08/08(土)00:00:00",
        posterId = null,
        messageHtml = message,
        imageUrl = null,
        thumbnailUrl = null
    )

    @Test
    fun archiveFollowUpUrl_staysOnTheOriginalOrigin() {
        val base = "https://dev2.ftbucket.info/scdev2/download.php"

        assertEquals(
            "https://dev2.ftbucket.info/scdev2/cont/may/index.htm",
            resolveCompatArchiveRelativeUrl(base, "cont/may/index.htm")
        )
        assertEquals(
            "https://dev2.ftbucket.info/scdev2/cont/may/index.htm",
            resolveCompatArchiveRelativeUrl(
                base,
                "https://dev2.ftbucket.info/scdev2/cont/may/index.htm"
            )
        )
        assertFailsWith<IllegalArgumentException> {
            resolveCompatArchiveRelativeUrl(base, "http://127.0.0.1/private")
        }
        assertFailsWith<IllegalArgumentException> {
            resolveCompatArchiveRelativeUrl(base, "https://example.com/archive")
        }
    }

    @Test
    fun archivePagesFillMissingPostsAndLivePostWinsOnDuplicate() {
        val primary = ThreadPage(
            threadId = "123",
            boardTitle = "板",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(post("123", 0, "live OP"), post("125", 2, "live reply")),
            isTruncated = true,
            truncationReason = "limit"
        )
        val archive = ThreadPage(
            threadId = "123",
            boardTitle = "板",
            expiresAtLabel = "消滅：01:00頃",
            deletedNotice = null,
            posts = listOf(
                post("123", 0, "archived OP"),
                post("124", 1, "missing reply"),
                post("125", 2, "archived reply")
            )
        )

        val merged = mergeCompatThreadPages(primary, listOf(archive))
        assertEquals(listOf("123", "124", "125"), merged.posts.map(Post::id))
        assertEquals("live OP", merged.posts.first().messageHtml)
        assertEquals("live reply", merged.posts.last().messageHtml)
        assertEquals("消滅：01:00頃", merged.expiresAtLabel)
        assertTrue(!merged.isTruncated)
    }

    @Test
    fun archiveApuViewSuffixIsRemovedFromBodyAndQuoteOnly() {
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "板",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                post(
                    "123",
                    0,
                    "<a target=_blank href=\"https://archive.example/cache/fu7190971.png\">fu7190971.png</a>" +
                        "<span id=\"preview-1\" style=\"cursor:pointer;\" " +
                        "onClick=\"previewImg('preview-1','fu7190971.png')\">[見る]</span><br>本文[見る]"
                ),
                post(
                    "124",
                    1,
                    "&gt;<a target=_blank href=\"https://archive.example/cache/fu7190971.png\">fu7190971.png</a>" +
                        "<span id=\"preview-2\" style=\"cursor:pointer;\">[見る]</span>"
                ),
                post(
                    "125",
                    2,
                    "<a href=\"https://archive.example/page\">通常リンク[見る]</a>"
                )
            )
        )

        val normalized = normalizeCompatArchiveApuViewLabels(page)

        assertEquals(
            "<a target=_blank href=\"https://archive.example/cache/fu7190971.png\">fu7190971.png</a><br>本文[見る]",
            normalized.posts[0].messageHtml
        )
        assertEquals(
            "&gt;<a target=_blank href=\"https://archive.example/cache/fu7190971.png\">fu7190971.png</a>",
            normalized.posts[1].messageHtml
        )
        assertEquals(page.posts[2].messageHtml, normalized.posts[2].messageHtml)
    }

    @Test
    fun archiveApuViewSuffixLegacyAndEncodedVariantsAreRemovedAtLineBoundary() {
        val variants = listOf(
            "<a href=\"/cache/fu1.png\">fu1.png[見る]</a><br>本文" to
                "<a href=\"/cache/fu1.png\">fu1.png</a><br>本文",
            "<a href=\"/cache/fu2.png\">fu2.png</a>[見る]<br>本文" to
                "<a href=\"/cache/fu2.png\">fu2.png</a><br>本文",
            "f3.webp&#91;見る&#93;<br>本文" to "f3.webp<br>本文",
            "fu4.webm［見る］" to "fu4.webm"
        )

        variants.forEach { (raw, expected) ->
            assertEquals(expected, normalizeCompatArchiveApuViewLabelHtml(raw))
        }
        assertEquals("本文fu4.webm[見る]続き", normalizeCompatArchiveApuViewLabelHtml("本文fu4.webm[見る]続き"))
        assertEquals("通常リンク[見る]", normalizeCompatArchiveApuViewLabelHtml("通常リンク[見る]"))
    }

    @Test
    fun incompleteLivePageIsSupplementedByArchiveCandidate() = runBlocking {
        val primary = ThreadPage(
            threadId = "123",
            boardTitle = "板",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(post("123", 0, "live"))
        )
        val archive = primary.copy(
            posts = listOf(post("123", 0, "archive"), post("124", 1, "archive reply"))
        )
        val result = loadCompatThreadWithFallback(
            sourceUrl = sourceUrl,
            cacheEnabled = false,
            cacheBaseUrl = null,
            loader = { primary },
            expectedReplyCount = 1,
            archiveLoader = { url ->
                if (url.contains("ftbucket", ignoreCase = true)) archive
                else error("not this archive")
            }
        ).getOrThrow()

        assertEquals(CompatThreadFetchSource.MERGED, result.source)
        assertEquals(listOf("123", "124"), result.page.posts.map(Post::id))
    }

    @Test
    fun ftbucketDownloadPageIsResolvedToShiftJisThreadPage() = runBlocking {
        val threadHtml = """
            <html><head><meta charset="Shift_JIS"><link rel="canonical" href="$sourceUrl"></head>
            <body>
              <span id="tit">board</span>
              <span class="thre" data-res="123">
                <span class="cnw">26/08/08 00:00:00 ID:ABC</span>
                <span class="cno">No.123</span>
                <a href="img/photo.jpg"><img src="thumb/photo.jpg"></a>
                <blockquote><a target=_blank href="other/fu7199371.png">fu7199371.png</a><span
                  id="preview" style="cursor:pointer;" onclick="previewImg('preview','other/fu7199371.png')"
                >VIEW0000</span><br>OP000000</blockquote>
              </span>
              <table border=0><tr><td>
                <span class="cnw">26/08/08 00:01:00 ID:DEF</span>
                <span class="cno">No.124</span>
                <blockquote>RP000000</blockquote>
              </td></tr></table>
            </body></html>
        """.trimIndent()
        // MockEngine responses are normally UTF-8 when created from String.
        // Replace ASCII placeholders with their actual Shift_JIS byte values
        // so this test exercises the same decoding path as FTBucket.
        val threadBytes = threadHtml.encodeToByteArray().toMutableList()
        fun replaceAsciiToken(token: String, replacement: ByteArray) {
            val start = threadHtml.indexOf(token)
            require(start >= 0)
            threadBytes.subList(start, start + token.length).clear()
            threadBytes.addAll(start, replacement.toList())
        }
        replaceAsciiToken("OP000000", intArrayOf(149, 219, 145, 182, 150, 123, 149, 182).map { it.toByte() }.toByteArray())
        replaceAsciiToken("RP000000", intArrayOf(149, 226, 138, 174, 131, 140, 131, 88).map { it.toByte() }.toByteArray())
        replaceAsciiToken("VIEW0000", intArrayOf(91, 140, 169, 130, 233, 93).map { it.toByte() }.toByteArray())
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/scdev2/scrapshot.php" -> respond(
                            "<meta http-equiv=\"refresh\" content=\"0;URL=download.php?rooturl=x\">",
                            HttpStatusCode.OK,
                            headersOf("Content-Type", "text/html; charset=UTF-8")
                        )
                        "/scdev2/download.php" -> respond(
                            "<a href='cont/may_test/index.htm'>thread</a>",
                            HttpStatusCode.OK,
                            headersOf("Content-Type", "text/html; charset=UTF-8")
                        )
                        "/scdev2/cont/may_test/index.htm" -> respond(
                            threadBytes.toByteArray(),
                            HttpStatusCode.OK,
                            headersOf("Content-Type", "text/html; charset=Shift_JIS")
                        )
                        else -> error("unexpected archive URL: ${request.url}")
                    }
                }
            }
        }
        try {
            val page = fetchCompatArchiveThreadPage(
                client,
                "https://dev2.ftbucket.info/scdev2/scrapshot.php?rooturl=x"
            )
            assertEquals(listOf("123", "124"), page.posts.map { it.id })
            assertEquals(
                listOf(
                    "<a target=_blank href=\"other/fu7199371.png\">fu7199371.png</a><br>保存本文",
                    "補完レス"
                ),
                page.posts.map { it.messageHtml }
            )
            assertEquals(
                "https://dev2.ftbucket.info/scdev2/cont/may_test/img/photo.jpg",
                page.posts.first().imageUrl
            )
            assertEquals(
                "https://dev2.ftbucket.info/scdev2/cont/may_test/thumb/photo.jpg",
                page.posts.first().thumbnailUrl
            )
        } finally {
            client.close()
        }
    }
}
