package com.valoser.futacha.shared.compat.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.InMemoryFileSystem

/**
 * AM-B0: records every request a MockEngine client sends to the Futaba hosts, as text, for the request goldens.
 *
 * The text is deterministic: multipart boundaries are replaced by `BOUNDARY`, the posting environment's
 * `pthc` (the current epoch millis, PostingBrowser.applyLegacyPostingEnvironment) by `<now>`, and bytes outside
 * printable ASCII are written as `%XX` (Shift_JIS bodies stay byte-exact without guessing an encoding).
 */
internal class FutabaRequestRecorder(
    private val respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { futabaFixtureResponse(it) }
) {
    val lines = mutableListOf<String>()
    val cookieStorage = PersistentCookieStorage(InMemoryFileSystem(), "private/cookies/am-b0.json")

    val client: HttpClient = HttpClient(MockEngine { request ->
        lines += render(request)
        respond(request)
    }) {
        install(HttpCookies) { storage = cookieStorage }
    }

    private suspend fun render(request: HttpRequestData): String {
        val bytes = request.body.toByteArray()
        val contentType = request.body.contentType?.toString()
        val boundary = contentType?.substringAfter("boundary=", "")?.trim('"')?.takeIf { it.isNotEmpty() }
        var body = escape(bytes)
        var type = contentType
        if (boundary != null) {
            body = body.replace(boundary, "BOUNDARY")
            type = type!!.replace(boundary, "BOUNDARY")
        }
        body = PTHC.replace(body) { "${it.groupValues[1]}<now>" }
        val headers = request.headers.entries()
            .filter { it.key != HttpHeaders.ContentLength }
            .joinToString(", ") { (k, v) ->
                // The cookie store's order differs by platform (iOS hashes); compare the cookies as a sorted set.
                val value = if (k == HttpHeaders.Cookie) v.flatMap { it.split("; ") }.sorted().joinToString("; ") else v.joinToString("|")
                "$k=$value"
            }
        return "${request.method.value} ${request.url} [$headers] type=$type body=$body"
    }

    fun close() = client.close()

    companion object {
        private val PTHC = Regex("""(name="pthc"%0D%0A(?:[^%]*%0D%0A)*?%0D%0A)\d{13}""")
        fun escape(bytes: ByteArray): String = buildString {
            for (b in bytes) {
                val v = b.toInt() and 0xff
                if (v in 0x20..0x7e && v != '%'.code) append(v.toChar()) else {
                    append('%'); append("0123456789ABCDEF"[v shr 4]); append("0123456789ABCDEF"[v and 15])
                }
            }
        }
    }
}

internal const val FIXTURE_BOARD = "https://may.2chan.net/b/"
internal const val FIXTURE_THREAD_ID = "1439323740"

internal val FIXTURE_THREAD_HTML = """<html><head><title>テスト＠ふたば</title></head><body>
<div class="thre" data-res="1439323740"><span class="csb">無念</span>Name <span class="cnm">としあき</span> <span class="cnw">25/11/10(月)12:00:00 ID:abc</span> <span class="cno">No.1439323740</span>
<a href="/b/src/1762743600000.jpg" target="_blank"><img src="/b/thumb/1762743600000s.jpg" border="0" width="250" height="188"></a>
<blockquote>スレ本文</blockquote>
<table border="0"><tr><td class="rts">…</td><td class="rtd"><span id="delcheck1439323741" class="rsc">1</span>Name <span class="cnm">としあき</span> <span class="cnw">25/11/10(月)12:00:10</span> <span class="cno">No.1439323741</span><blockquote>レス</blockquote></td></tr></table>
</div>${fixtureFormHtml(FIXTURE_THREAD_ID)}</body></html>"""

internal val FIXTURE_CATALOG_HTML = """<html><body><table border="1" align="center" id="cattable"><tr>
<td><a href="res/1439323740.htm" target="_blank"><img src="/b/cat/1762743600000s.jpg" border="0" width="50" height="38"></a><br><small>スレ本文</small><br><font size="2">1</font></td>
</tr></table></body></html>"""

internal fun fixtureFormHtml(resto: String?) = """<form action="futaba.php?guid=on" method="POST" enctype="multipart/form-data" id="fm">
<input type="hidden" name="mode" value="regist"><input type="hidden" name="MAX_FILE_SIZE" value="8192000">
<input type="hidden" name="baseform" value=""><input type="hidden" name="pthb" value=""><input type="hidden" name="pthc" value="">
<input type="hidden" name="pthd" value=""><input type="hidden" name="ptua" value="1234567890"><input type="hidden" name="scsz" value="">
<input type="hidden" name="hash" value="1762743600000000-0123456789abcdef0123456789abcdef"><input type="hidden" name="js" value="off">
<input type="hidden" name="chrenc" value="文字">${if (resto != null) "<input type=\"hidden\" name=\"resto\" value=\"$resto\">" else ""}
<input name="name"><input name="email"><input name="sub"><textarea name="com"></textarea>
<input type="file" name="upfile"><input type="checkbox" name="textonly" value="on"><input type="password" name="pwd">
</form>"""

/** One fixed answer per Futaba endpoint; everything else (the archives) is a 404. */
internal fun MockRequestHandleScope.futabaFixtureResponse(request: HttpRequestData): HttpResponseData {
    val url = request.url
    val html = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
    if (!url.host.endsWith(".2chan.net")) return respond("missing", HttpStatusCode.NotFound)
    val path = url.encodedPath
    return when {
        url.parameters["mode"] == "catset" -> respond("<html>ok</html>", HttpStatusCode.OK, headersOf(
            HttpHeaders.ContentType to listOf("text/html; charset=UTF-8"),
            HttpHeaders.SetCookie to listOf("posttime=1762743600000; path=/; domain=.2chan.net")))
        url.parameters["mode"] == "cat" -> respond(FIXTURE_CATALOG_HTML, HttpStatusCode.OK, html)
        path.endsWith("/futaba.htm") -> respond("<html><body>${fixtureFormHtml(null)}</body></html>", HttpStatusCode.OK, html)
        path.contains("/res/") -> respond(FIXTURE_THREAD_HTML, HttpStatusCode.OK, html)
        path.endsWith("/sd.php") -> respond("1", HttpStatusCode.OK, html)
        path.endsWith("/del.php") -> respond("<html>削除依頼を受け付けました</html>", HttpStatusCode.OK, html)
        path.endsWith("/futaba.php") && url.parameters["mode"] == null ->
            respond("""{"status":"ok","jumpto":1439323742,"thisno":1439323742}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        path.endsWith("/futaba.php") -> respond("<html>ok</html>", HttpStatusCode.OK, html)
        else -> respond("missing", HttpStatusCode.NotFound)
    }
}
