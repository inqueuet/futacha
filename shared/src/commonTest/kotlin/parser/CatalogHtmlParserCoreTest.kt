package com.valoser.futacha.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogHtmlParserCoreTest {
    @Test
    fun parseCatalog_extractsCells() {
        val html = """
            <html>
            <body>
            <table id='cattable'>
                <tr>
                    <td><a href='res/354621.htm'><img src='/t/cat/1762145224666s.jpg'></a><br><font size=2>17</font></td>
                    <td><a href='res/1364612020.htm'><img src='/t/cat/1762436883775s.jpg'></a><br><font size=2>チュートリアル</font></td>
                </tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val items = CatalogHtmlParserCore.parseCatalog(html)

        assertEquals(2, items.size)
        assertEquals("354621", items[0].id)
        assertEquals("https://dat.2chan.net/res/354621.htm", items[0].threadUrl)
        assertEquals("https://dat.2chan.net/t/cat/1762145224666s.jpg", items[0].thumbnailUrl)
        assertEquals(17, items[0].replyCount)
        assertEquals("17", items[0].title)

        assertEquals("1364612020", items[1].id)
        assertEquals("https://dat.2chan.net/res/1364612020.htm", items[1].threadUrl)
        assertEquals("チュートリアル", items[1].title)
        assertEquals(0, items[1].replyCount)
    }
}
