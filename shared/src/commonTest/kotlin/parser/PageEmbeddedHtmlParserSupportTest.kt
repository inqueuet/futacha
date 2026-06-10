package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageEmbeddedHtmlParserSupportTest {
    @Test
    fun extractThreadEmbeddedHtml_extractsFooterIframe() {
        val html = """
            <html>
            <body>
            <div class="thre"></div>
            <!--スレッド終了-->
            <iframe src="/ad" width="360" height="120"></iframe>
            </body>
            </html>
        """.trimIndent()

        val embeds = PageEmbeddedHtmlParserSupport.extractThreadEmbeddedHtml(
            html = html,
            baseUrl = "https://may.2chan.net/b/"
        )

        assertEquals(1, embeds.size)
        assertEquals(EmbeddedHtmlPlacement.Footer, embeds.single().placement)
        assertTrue(embeds.single().html.contains("https://may.2chan.net/ad"))
    }

    @Test
    fun extractThreadEmbeddedHtml_stopsScanningAfterIframeLimit() {
        val bodyIframes = (0 until 64).joinToString(separator = "\n") { index ->
            """<iframe src="/body-$index" width="360" height="120"></iframe>"""
        }
        val html = """
            <html>
            <body>
            <div class="thre">
            $bodyIframes
            </div>
            <!--スレッド終了-->
            <iframe src="/footer-after-limit" width="360" height="120"></iframe>
            </body>
            </html>
        """.trimIndent()

        val embeds = PageEmbeddedHtmlParserSupport.extractThreadEmbeddedHtml(
            html = html,
            baseUrl = "https://may.2chan.net/b/"
        )

        assertTrue(embeds.isEmpty())
    }
}
