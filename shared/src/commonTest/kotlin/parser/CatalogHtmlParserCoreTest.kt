package com.valoser.futacha.shared.parser

import kotlinx.coroutines.runBlocking
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

        val items = runBlocking { CatalogHtmlParserCore.parseCatalog(html) }

        assertEquals(2, items.size)
        assertEquals("354621", items[0].id)
        assertEquals("https://www.example.com/res/354621.htm", items[0].threadUrl)
        // Updated expectation: /cat/ -> /thumb/
        assertEquals("https://www.example.com/t/thumb/1762145224666s.jpg", items[0].thumbnailUrl)
        // Updated expectation: Derived full image URL
        assertEquals("https://www.example.com/t/src/1762145224666.jpg", items[0].fullImageUrl)
        assertEquals(17, items[0].replyCount)
        assertEquals("17", items[0].title)

        assertEquals("1364612020", items[1].id)
        assertEquals("https://www.example.com/res/1364612020.htm", items[1].threadUrl)
        assertEquals("チュートリアル", items[1].title)
        assertEquals(0, items[1].replyCount)
    }

    @Test
    fun parseCatalog_decodesSupplementaryEntities() {
        val expected = String(Character.toChars(0x2E81C))
        val html = """
            <html>
            <body>
            <table id='cattable'>
                <tr>
                    <td><a href='res/4000000000.htm'></a><br><small>&#188604;</small><br><font size=2>1</font></td>
                </tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val items = runBlocking { CatalogHtmlParserCore.parseCatalog(html) }

        assertEquals(1, items.size)
        assertEquals(expected, items[0].title)
    }

    @Test
    fun parseCatalog_preservesNonJpgMediaUrls() {
        val html = """
            <html>
            <body>
            <table id='cattable'>
                <tr>
                    <td>
                        <a href='res/5000000000.htm'>thread</a>
                        <a href='/t/src/5000000000.webm'>media</a>
                        <img src='/t/cat/5000000000s.gif'>
                        <br><font size=2>2</font>
                    </td>
                </tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val items = runBlocking { CatalogHtmlParserCore.parseCatalog(html) }

        assertEquals(1, items.size)
        assertEquals("https://www.example.com/t/thumb/5000000000s.gif", items[0].thumbnailUrl)
        assertEquals("https://www.example.com/t/src/5000000000.webm", items[0].fullImageUrl)
    }
}
