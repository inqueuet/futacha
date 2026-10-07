package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the 2026-10-07 audit fixes in the network layer (MockEngine only). */
class AuditFixesPostingTest {
    private fun form(threadId: String = "123", chrenc: String = "文字"): String = """
        <form id="fm"><input name="resto" value="$threadId"><input name="mode" value="regist"><input name="chrenc" value="$chrenc">
        <input name='hash' value='server-hash'>
        <input name="ptua" value="123"><input name="MAX_FILE_SIZE" value="3072000">
        <input name="baseform"><input name="js"><input name="pthc"><input name="pthb"><input name="pthd"><input name="scsz">
        <input name="email"><textarea name="com"></textarea><input name="pwd">
        <input name='upfile'><input name='textonly'>
        </form>
    """.trimIndent()

    private fun field(wire: String, name: String): String? {
        val match = Regex("""Content-Disposition:[^\r\n]*\bname="?$name"?(?=[;\r\n])""").find(wire) ?: return null
        val bodyStart = wire.indexOf("\r\n\r\n", match.range.last) + 4
        return wire.substring(bodyStart, wire.indexOf("\r\n", bodyStart))
    }

    // ---- B1: Desktop has no PostingBrowser; it must still send the pre-12.4 environment values ----

    @Test
    fun postWithoutPostingBrowserSendsLegacyEnvironmentInsteadOfJsOff() = runBlocking {
        var wire = ""
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(form(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            } else {
                wire = (request.body as OutgoingContent).toByteArray().decodeToString()
                respond("""{"status":"ok","thisno":124}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        try {
            HttpBoardApi(client).replyToThread("https://img.2chan.net/b/", "123", "", "", "", "draft", "key", null, null, true)
            assertEquals("on", field(wire, "js"))
            assertEquals("1080x1920x24", field(wire, "scsz"))
            assertEquals("123", field(wire, "ptua"))
            assertEquals("", field(wire, "pthb"))
            assertEquals("", field(wire, "pthd"))
            assertNotNull(field(wire, "pthc")?.toLongOrNull())
            assertEquals("server-hash", field(wire, "hash"))
        } finally { client.close() }
    }

    @Test
    fun legacyEnvironmentUsesTheFormsPtuaAndTheGivenClock() {
        val config = HttpBoardApiPostingConfig(HttpBoardApiPostEncoding.SHIFT_JIS, "x", ptuaValue = "999")
        val prepared = applyLegacyPostingEnvironment(config, currentEpochMillis = 1_700_000_000_123L)
        assertEquals("on", prepared.environment["js"])
        assertEquals("1700000000123", prepared.environment["pthc"])
        assertEquals("999", prepared.environment["ptua"])
        assertEquals("1080x1920x24", prepared.environment["scsz"])
    }

    // ---- 8: a script error ("null") must read as a clear Japanese failure ----

    @Test
    fun nullResultFromThePostingBrowserBecomesAClearMessage() = runBlocking {
        val client = HttpClient(MockEngine {
            respond("// script", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/javascript"))
        })
        val browser = object : PostingBrowser {
            override suspend fun userAgent() = "ua"
            override suspend fun evaluate(pageUrl: String, html: String, script: String) = "null"
        }
        try {
            val error = assertFailsWith<NetworkException> {
                prepareOfficialPostingEnvironment(
                    client, browser, "https://img.2chan.net/b/futaba.htm",
                    HttpBoardApiPostingConfig(HttpBoardApiPostEncoding.SHIFT_JIS, "Shift_JIS"), "ua"
                )
            }
            assertTrue(error.message.orEmpty().contains("投稿の準備に失敗"))
        } finally { client.close() }
    }

    // ---- 5: the deletion key is sent in the board's charset, like the posting form ----

    private suspend fun deleteBody(password: String, getStatus: HttpStatusCode = HttpStatusCode.OK): String {
        var body = ""
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(form(), getStatus, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            } else {
                body = (request.body as OutgoingContent).toByteArray().decodeToString()
                respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            }
        })
        try {
            HttpBoardApi(client).deleteByUser("https://img.2chan.net/b/", "123", "321", password, imageOnly = false)
        } finally { client.close() }
        return body
    }

    @Test
    fun nonAsciiDeletionKeyIsPercentEncodedAsShiftJis() = runBlocking {
        // "あ" is 0x82 0xA0 in Shift_JIS (UTF-8 would be %E3%81%82).
        val body = deleteBody("あ1")
        assertTrue(body.contains("pwd=%82%A01"), body)
        assertTrue(body.contains("mode=usrdel"), body)
        assertTrue(body.contains("delete=321"), body)
    }

    @Test
    fun nonAsciiDeletionKeyFallsBackToShiftJisWhenTheFormCannotBeFetched() = runBlocking {
        val body = deleteBody("あ1", getStatus = HttpStatusCode.NotFound)
        assertTrue(body.contains("pwd=%82%A01"), body)
    }

    @Test
    fun asciiDeletionKeyKeepsTheOriginalFormBody() = runBlocking {
        val expected = Parameters.build {
            append("guid", "on")
            append("delete", "321")
            append("321", "delete")
            append("responsemode", "ajax")
            append("pwd", "pass 1234&x")
            append("onlyimgdel", "")
            append("mode", "usrdel")
        }.formUrlEncode()
        assertEquals(expected, deleteBody("pass 1234&x"))
    }

    @Test
    fun formUrlEncodedBodyEncodesTextWithTheGivenCharset() {
        val fields = listOf("a b" to "x y~-._&=", "pwd" to "あ1")
        assertEquals(
            "a+b=x+y~-._%26%3D&pwd=%82%A01",
            buildHttpBoardApiFormUrlEncodedBody(fields, HttpBoardApiPostEncoding.SHIFT_JIS)
        )
        assertEquals(
            "pwd=%E3%81%82",
            buildHttpBoardApiFormUrlEncodedBody(listOf("pwd" to "あ"), HttpBoardApiPostEncoding.UTF8)
        )
    }

    // ---- 7b: a form without MAX_FILE_SIZE allows the default size, like the sender ----

    @Test
    fun attachmentWithoutAdvertisedLimitUsesTheDefaultLimit() {
        val config = HttpBoardApiPostingConfig(
            encoding = HttpBoardApiPostEncoding.SHIFT_JIS,
            chrencValue = "x",
            hashValue = "h",
            ptuaValue = "1",
            formFields = setOf("upfile", "com", "email", "pwd", "chrenc", "mode", "hash")
        )
        buildHttpBoardApiPostFormData("t", "1", "", "", "", "c", "k", ByteArray(5_000_000), "a.jpg", false, config)
        assertFailsWith<NetworkException> {
            buildHttpBoardApiPostFormData("t", "1", "", "", "", "c", "k", ByteArray(8_192_001), "a.jpg", false, config)
        }
    }

    // ---- 7c: JSON error text is decoded, not shown with escapes ----

    @Test
    fun jsonErrorMessageIsDecoded() {
        val body = """{"status":"error","message":"書き込み規制中です \"A\" a\/b"}"""
        assertEquals("書き込み規制中です \"A\" a/b", extractHttpBoardApiServerError(body))
        assertTrue(summarizeHttpBoardApiResponse(body).contains("書き込み規制中です"))
        assertEquals("plain", decodeHttpBoardApiJsonStringValue("plain"))
        assertNull(extractHttpBoardApiServerError("""{"status":"ok","message":"あ"}"""))
    }
}
