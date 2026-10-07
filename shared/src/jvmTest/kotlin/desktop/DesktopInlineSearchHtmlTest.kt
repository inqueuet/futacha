package com.valoser.futacha.shared.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopInlineSearchHtmlTest {
    @Test
    fun baseAndScriptBlockingPolicyAreInsertedRightAfterHead() {
        val html = buildDesktopInlineSearchHtml(
            "<html><HEAD><title>x</title></HEAD><body><img src=\"/thumb/1.jpg\"></body></html>",
            "https://iqdb.org/"
        )
        val headEnd = html.indexOf("<HEAD>") + "<HEAD>".length
        assertTrue(html.startsWith("<html><HEAD><meta http-equiv=\"Content-Security-Policy\""))
        assertTrue(html.indexOf("<base href=\"https://iqdb.org/\">") > headEnd)
        assertTrue(html.indexOf("<base") < html.indexOf("<title>"))
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("<img src=\"/thumb/1.jpg\">"))
    }

    @Test
    fun aFragmentWithoutHeadIsWrappedAndTheBaseIsEscaped() {
        val html = buildDesktopInlineSearchHtml("<p>result</p>", "https://saucenao.com/?a=1&b=\"2\"")
        assertTrue(html.startsWith("<!doctype html><html><head>"))
        assertTrue(html.contains("<base href=\"https://saucenao.com/?a=1&amp;b=&quot;2&quot;\">"))
        assertTrue(html.contains("<body><p>result</p></body>"))
    }

    @Test
    fun aNonWebBaseIsRefusedBeforeAnythingIsWritten() {
        val failure = runCatching { openDesktopInlineSearchHtml("<p>x</p>", "file:///tmp/") }.exceptionOrNull()
        assertEquals("このリンクは開けません", failure?.message)
    }
}
