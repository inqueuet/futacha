package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompatArchiveResponseTest {
    private val op = """<span class="cno">No.123</span>
        <a href="/b/src/123.jpg"><img src="/b/thumb/123s.jpg"></a>
        <blockquote>OP</blockquote>"""
    private val reply = """<table border=0><tr><td><span class="cno">No.124</span>
        <blockquote>reply</blockquote></td></tr></table>"""

    @Test fun forestTemplateKeepsOpImageAndRepliesIncludingZeroReplies() = runBlocking {
        for (replies in listOf("", reply)) {
            val html = """<html><head><script>
                ${'$'}data = `$op$replies`;
                </script></head><body><div id="res_body"></div></body></html>"""
            val client = HttpClient(MockEngine { request ->
                assertEquals("https", request.url.protocol.name)
                respond(html, headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
            })
            try {
                val page = fetchCompatArchiveThreadPage(client, buildCompatForestUrl("https://may.2chan.net/b/res/123.htm")!!)
                assertEquals(if (replies.isEmpty()) listOf("123") else listOf("123", "124"), page.posts.map { it.id })
                assertEquals("OP", page.posts.first().messageHtml)
                assertEquals("https://futabaforest.net/b/src/123.jpg", page.posts.first().imageUrl)
                assertEquals("https://futabaforest.net/b/thumb/123s.jpg", page.posts.first().thumbnailUrl)
                assertFalse(page.isTruncated)
            } finally { client.close() }
        }
    }

    @Test fun normalLinksAndScriptUrlAssignmentsDoNotRedirectFutapo() = runBlocking {
        val source = "https://kako.futakuro.com/futa/may_b/123/"
        val html = """<html><head>
            <link rel="alternate" href="/m/#thread?mode=thread&url=base64value">
            <script>var url = 'https://example.invalid/advert';</script>
            <link rel="canonical" href="https://may.2chan.net/b/res/123.htm">
            </head><body><div class="thre">$op$reply</div></body></html>"""
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals(source, request.url.toString())
            respond(html, headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
        })
        try {
            val page = fetchCompatArchiveThreadPage(client, source)
            assertEquals(listOf("123", "124"), page.posts.map { it.id })
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun metaRefreshRecognizesReorderedAttributesAndDecodesQueryEntities() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val html = when (request.url.encodedPath) {
                "/start" -> """<meta content="0; URL='next?a=1&amp;b=2'" HTTP-EQUIV='Refresh'>"""
                "/next" -> {
                    assertEquals("2", request.url.parameters["b"])
                    """<div class="thre">$op</div>"""
                }
                else -> error("unexpected request: ${request.url}")
            }
            respond(html, headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
        })
        try { assertEquals("123", fetchCompatArchiveThreadPage(client, "https://archive.example/start").posts.first().id) }
        finally { client.close() }
    }

    @Test fun selfRefreshThroughARelativePathAndLongDelayReloadsAreContent() = runBlocking {
        // Both used to be followed as redirects (the self-refresh up to the loop limit).
        for (refresh in listOf(
            """<meta http-equiv="refresh" content="0;URL=./123.htm">""",
            """<meta http-equiv="refresh" content="300;URL=other.htm">"""
        )) {
            var requests = 0
            val client = HttpClient(MockEngine { request ->
                requests++
                assertEquals("/b/res/123.htm", request.url.encodedPath)
                respond("$refresh<div class=\"thre\">$op</div>", headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
            })
            try {
                val page = fetchCompatArchiveThreadPage(client, "https://archive.example/b/res/123.htm")
                assertEquals("123", page.posts.first().id)
                assertEquals(1, requests)
            } finally { client.close() }
        }
    }

    @Test fun futapoMissingThumbnailUsesNamedAttachmentForMirrorRecovery() = runBlocking {
        val source = "https://kako.futakuro.com/futa/may_b/123/"
        val html = """<link rel="canonical" href="https://may.2chan.net/b/res/123.htm">
            <div class="thre"><span class="cno">No.123</span>
            <a href="123.jpg">123.jpg</a>
            <a href="http://kako.futakuro.com/futa/404.png"><img src="http://kako.futakuro.com/futa/404s.png"></a>
            <blockquote>OP</blockquote></div>"""
        val client = HttpClient(MockEngine { respond(html) })
        try {
            val post = fetchCompatArchiveThreadPage(client, source).posts.single()
            assertEquals("${source}123.jpg", post.imageUrl)
            assertEquals(post.imageUrl, post.thumbnailUrl)
        } finally { client.close() }
    }

    @Test fun futapoMissingPlaceholderIsNotAnAttachment() = runBlocking {
        val html = """<div class="thre"><span class="cno">No.123</span>
            <a href="http://kako.futakuro.com/futa/404.png"><img src="http://kako.futakuro.com/futa/404s.png"></a>
            <blockquote>OP</blockquote></div>"""
        val client = HttpClient(MockEngine { respond(html) })
        try {
            val post = fetchCompatArchiveThreadPage(client, "https://kako.futakuro.com/futa/may_b/123/").posts.single()
            assertNull(post.imageUrl)
            assertNull(post.thumbnailUrl)
        } finally { client.close() }
    }
    @Test fun forestTemplateEscapesAreDecodedWithoutExecutingJavaScript() {
        val html = """<script>${'$'}data = `<blockquote>\`quoted\` \\ \n \u65e5 \x41 \${'$'}{literal}</blockquote>`;</script>"""
        assertEquals("<div class=\"thre\"><blockquote>`quoted` \\ \n 日 A ${'$'}{literal}</blockquote></div>", normalizeForestThreadHtml(html))
        assertFailsWith<IllegalStateException> { normalizeForestThreadHtml("""${'$'}data = `unterminated""") }
    }
    @Test fun forestTemplateDecodesCodePointEscapes() {
        val html = """<script>${'$'}data = `<blockquote>\u{65e5}\u{1F600}\u{41} ok</blockquote>`;</script>"""
        assertEquals("<div class=\"thre\"><blockquote>日\uD83D\uDE00A ok</blockquote></div>", normalizeForestThreadHtml(html))
        assertFailsWith<IllegalArgumentException> { normalizeForestThreadHtml("""${'$'}data = `\u{110000}`""") }
        assertFailsWith<IllegalArgumentException> { normalizeForestThreadHtml("""${'$'}data = `\u{}`""") }
    }

}
