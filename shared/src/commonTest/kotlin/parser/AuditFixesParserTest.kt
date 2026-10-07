package com.valoser.futacha.shared.parser

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A 200 page that is not a catalog/thread must fail instead of becoming an empty success. */
class AuditFixesParserTest {
    private val maintenance = "<html><body><h1>ただいまメンテナンス中です</h1><p>しばらくお待ちください</p></body></html>"
    private val portal = "<html><head><title>Wi-Fi login</title></head><body><form action=\"/login\">Accept</form></body></html>"

    @Test
    fun nonCatalogPagesAreRejected() {
        for (html in listOf(maintenance, portal, "")) {
            assertFailsWith<ParserException> {
                runBlocking { CatalogHtmlParserCore.parseCatalogPage(html, "https://may.2chan.net/b") }
            }
        }
    }

    @Test
    fun genuineEmptyCatalogsStayASuccess() {
        val emptyTable = """<html><body><a href="futaba.php?mode=cat">cat</a><table border="1" id="cattable"><tr></tr></table></body></html>"""
        val noTableButFutabaPage = """<html><body><form action="futaba.php" method="POST"></form><a href="futaba.php?mode=cat">cat</a></body></html>"""
        for (html in listOf(emptyTable, noTableButFutabaPage)) {
            val page = runBlocking { CatalogHtmlParserCore.parseCatalogPage(html, "https://may.2chan.net/b") }
            assertTrue(page.items.isEmpty())
        }
    }

    @Test
    fun nonThreadPagesAreRejected() {
        for (html in listOf(maintenance, portal, "")) {
            assertFailsWith<ParserException> {
                runBlocking { ThreadHtmlParserCore.parseThread(html, "https://may.2chan.net/b/res/123.htm") }
            }
        }
    }

    @Test
    fun aRealThreadStillParses() {
        val html = """<html><body><div class="thre" data-res="123"><span class="cno">No.123</span><blockquote>本文</blockquote></div></body></html>"""
        val page = runBlocking { ThreadHtmlParserCore.parseThread(html, "https://may.2chan.net/b/res/123.htm") }
        assertEquals("123", page.posts.single().id)
    }
}
