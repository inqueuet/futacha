package com.valoser.futacha.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogHtmlParserCoreTest {
    @Test
    fun parseCatalog_extractsCells() {
        val html = """
            <html>
            <body>
            <table id='cattable'>
                <tr>
                    <td><a href='res/354621.htm'><img src='/t/cat/1762145224666s.jpg'></a><br><font size=2>17</font></td>
                    <td><a href='res/354711.htm'><img src='/t/cat/1762436883775s.jpg'></a><br><font size=2>1</font></td>
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
        assertTrue(items[0].title.contains("料理"))

        assertEquals("354711", items[1].id)
        assertEquals(1, items[1].replyCount)
    }
}
