package com.valoser.futacha.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpBoardApiSupportTest {
    @Test
    fun postResponseHelpers_extractThreadIds_fromHtmlAndJson() {
        assertEquals(
            "123456",
            tryExtractHttpBoardApiThreadId("""<a href="res/123456.htm">thread</a>""")
        )
        assertEquals(
            "654321",
            tryExtractHttpBoardApiThreadId("""{"redirect":"futaba.php?res=654321"}""")
        )
        assertEquals(
            "777",
            tryParseHttpBoardApiThreadIdFromJson("""{"status":"ok","jumpto":777}""")
        )
        assertEquals(
            "888",
            tryParseHttpBoardApiThreadIdFromJson("""{"status":"success","thisno":888}""")
        )
        assertNull(tryParseHttpBoardApiThreadIdFromJson("""{"status":"error","message":"nope"}"""))
    }

    @Test
    fun postResponseHelpers_detectSuccessAndErrors() {
        assertTrue(isSuccessfulHttpBoardApiPostResponse("書き込みました。"))
        assertTrue(isSuccessfulHttpBoardApiPostResponse("""{"status":"ok","thisno":10}"""))
        assertTrue(isSuccessfulHttpBoardApiPostResponse("""<meta http-equiv="refresh" content="0;URL=res/10.htm">"""))
        assertFalse(isSuccessfulHttpBoardApiPostResponse("""{"status":"error","message":"規制中"}"""))

        assertEquals(
            "規制中",
            extractHttpBoardApiServerError("""{"status":"error","message":"規制中"}""")
        )
        assertEquals(
            "status=error",
            extractHttpBoardApiServerError("""{"status":"error"}""")
        )
        assertEquals(
            "エラー: 時間を置いてください",
            extractHttpBoardApiServerError("\n\nエラー: 時間を置いてください\n")
        )
        assertNull(extractHttpBoardApiServerError("""{"status":"ok","thisno":10}"""))
    }

    @Test
    fun postResponseHelpers_summarizeJsonAndPlainText() {
        assertEquals(
            "status=error, message=規制中",
            summarizeHttpBoardApiResponse("""{"status":"error","message":"規制中"}""")
        )
        assertEquals(
            "最初の行",
            summarizeHttpBoardApiResponse("\n 最初の行 \n 次の行")
        )
    }

    @Test
    fun postResponseHelpers_extractThisNo_and_jsonShape() {
        assertEquals("123", tryExtractHttpBoardApiThisNo("""{"status":"ok","thisno":123}"""))
        assertNull(tryExtractHttpBoardApiThisNo("""{"status":"ok"}"""))
        assertTrue(looksLikeHttpBoardApiJson(" {\"status\":\"ok\"}"))
        assertFalse(looksLikeHttpBoardApiJson("<html></html>"))
        assertTrue(isHttpBoardApiJsonStatusOk("""{"status":"success"}"""))
        assertFalse(isHttpBoardApiJsonStatusOk("""{"status":"error"}"""))
    }

    @Test
    fun chrencHelpers_extractAndDecodeEntities() {
        val html = """
            <html>
              <form>
                <input type="hidden" name="chrenc" value="&#x6587;&#23383;">
              </form>
            </html>
        """.trimIndent()

        assertEquals("文字", parseHttpBoardApiChrencValue(html))
        assertEquals("A", decodeHttpBoardApiNumericEntities("&#65;"))
        assertEquals("\uD83D\uDE00", decodeHttpBoardApiNumericEntities("&#x1F600;"))
        assertEquals("plain", decodeHttpBoardApiNumericEntities("plain"))
    }
}
