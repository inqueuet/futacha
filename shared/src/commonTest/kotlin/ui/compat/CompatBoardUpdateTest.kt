package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatBoardUpdateTest {
    @Test
    fun boardUpdateAcceptsOnlyReferenceEntryAndUsesReferenceAliases() {
        assertEquals(COMPAT_REFERENCE_BOARD_UPDATE_URL, normalizeCompatBoardUpdateUrl("debug"))
        assertEquals(COMPAT_REFERENCE_BOARD_UPDATE_URL, normalizeCompatBoardUpdateUrl("E&E"))
        assertTrue(isCompatBoardUpdateUrlAccepted(COMPAT_REFERENCE_BOARD_UPDATE_URL))
        assertFalse(isCompatBoardUpdateUrlAccepted("https://example.com/menu.html"))
        assertEquals(
            "https://www.2chan.net/bbsmenu.html",
            COMPAT_LEGACY_DEFAULT_BOARD_MENU_URL
        )
    }

    @Test
    fun boardInputMatchesReferenceNormalizationAndErrorOrder() {
        val existing = CompatBoard(
            key = "may",
            name = "may",
            canonicalUrl = "https://may.2chan.net/b/",
            originalUrl = "https://may.2chan.net/b/",
            sortOrder = 0
        )

        val missing = validateCompatBoardInput(" 　", " ", listOf(existing), checkDuplicate = true)
        assertEquals("", missing.normalizedName)
        assertEquals("", missing.normalizedUrl)
        assertEquals(
            "表示名を入力して下さい\n" +
                "アドレスを入力して下さい\n" +
                "正しいURLを入力して下さい\nhttps://***.2chan.net/***/",
            missing.errorMessage
        )

        val duplicate = validateCompatBoardInput(
            rawName = " 二 次 元 裏　may ",
            rawUrl = " https://may.2chan.net/b/futaba.htm ",
            existingBoards = listOf(existing),
            checkDuplicate = true
        )
        assertEquals("二次元裏may", duplicate.normalizedName)
        assertEquals(existing.canonicalUrl, duplicate.normalizedUrl)
        assertEquals(existing.canonicalUrl, duplicate.canonicalUrl)
        assertEquals("既に登録されています", duplicate.errorMessage)

        val valid = validateCompatBoardInput(
            rawName = " 新規板 ",
            rawUrl = " https://img.2chan.net/b/ ",
            existingBoards = listOf(existing),
            checkDuplicate = true
        )
        assertEquals("新規板", valid.normalizedName)
        assertEquals("https://img.2chan.net/b/", valid.canonicalUrl)
        assertNull(valid.errorMessage)
    }

    @Test
    fun parsesLegacyBbsmenuAndAddsSpecialBoards() {
        val html = """
            <a href="https://may.2chan.net/b/futaba.htm" target="cont">二次元裏</a>
            <a href="//dec.2chan.net/b/futaba.htm" target="cont"><span>二次元裏</span></a>
            <a href="/ktinv.htm" target="cont">スマホ用</a>
            <a href="https://may.2chan.net/b/futaba.htm" target="cont">重複</a>
            <a href="https://example.2chan.net/ipv6/futaba.htm" target="cont">除外</a>
        """.trimIndent()

        val boards = parseCompatBoardMenu(html, emptyList())

        assertEquals("二次元裏may", boards.first { it.canonicalUrl == "https://may.2chan.net/b/" }.name)
        assertEquals("二次元裏dec", boards.first { it.canonicalUrl == "https://dec.2chan.net/b/" }.name)
        assertTrue(boards.any { it.canonicalUrl == "https://img.2chan.net/b/" })
        assertTrue(boards.any { it.canonicalUrl == "https://dat.2chan.net/b/" })
        assertEquals(4, boards.count { it.canonicalUrl.contains("2chan.net") })
        assertEquals(
            listOf(
                "https://may.2chan.net/b/",
                "https://dec.2chan.net/b/",
                "https://img.2chan.net/b/",
                "https://dat.2chan.net/b/"
            ),
            boards.map(CompatBoard::canonicalUrl)
        )
    }

    @Test
    fun defaultFetchUsesTheCompleteBoardMenuAndReturnsOrdinaryBoards() = runBlocking {
        var requestedDefaultMenu = false
        val client = HttpClient(MockEngine { request ->
            requestedDefaultMenu = request.url.toString() == COMPAT_LEGACY_DEFAULT_BOARD_MENU_URL
            respond(
                content = """
                    <a href="https://may.2chan.net/b/futaba.htm">二次元裏</a>
                    <a href="https://dec.2chan.net/b/futaba.htm">二次元裏</a>
                    <a href="https://jun.2chan.net/jun/futaba.htm">二次元裏</a>
                    <a href="https://may.2chan.net/hobby/futaba.htm">趣味</a>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
            )
        })

        try {
            val boards = fetchDefaultCompatBoardsFromMenu(client, emptyList()).getOrThrow()

            assertTrue(requestedDefaultMenu)
            assertTrue(boards.size > 2)
            assertTrue(boards.any { it.name == "趣味" })
            assertTrue(boards.any { it.name == "二次元裏may" })
            assertTrue(boards.any { it.name == "二次元裏dec" })
            assertTrue(boards.any { it.name == "二次元裏jun" })
        } finally {
            client.close()
        }
    }

    @Test
    fun keepsExistingBoardIdentityAndManualUrl() {
        val existing = CompatBoard(
            key = "old-key",
            name = "手動名",
            canonicalUrl = "https://may.2chan.net/b/",
            originalUrl = "https://may.2chan.net/b/",
            sortOrder = 2
        )
        val boards = parseCompatBoardMenu(
            "<a href=\"https://may.2chan.net/b/futaba.htm\" target=\"cont\">新しい名前</a>",
            listOf(existing)
        )
        val board = boards.first { it.canonicalUrl == existing.canonicalUrl }
        assertEquals(existing.key, board.key)
        assertEquals(existing.sortOrder, board.sortOrder)
        assertEquals("手動名", board.name)
    }

    @Test
    fun appliesReferenceLegacyMenuRewritesAndShelterFallback() {
        val html = """
            <a href="//dec.2chan.net/guro/guro2-enter.html" target="cont">グロ</a>
            <a href="//www.2chan.net/hinan/futaba.htm" target="cont">退避</a>
        """.trimIndent()

        val boards = parseCompatBoardMenu(html, emptyList())

        assertTrue(boards.any { it.canonicalUrl == "https://dec.2chan.net/guro/" && it.name == "グロ" })
        assertTrue(boards.any { it.canonicalUrl == "https://www.2chan.net/hinan/" && it.name == "退避" })
    }

    @Test
    fun malformedMenuIsBoundedBeforeItCanCreateThousandsOfBoards() {
        val longTitle = "長".repeat(1_000)
        val html = (0 until 700).joinToString("\n") { index ->
            "<a href=\"https://server$index.2chan.net/b/futaba.htm\">$longTitle</a>"
        }

        val boards = parseCompatBoardMenu(html, emptyList())

        assertEquals(512, boards.size)
        assertTrue(boards.all { it.name.length <= 256 })
    }
}
