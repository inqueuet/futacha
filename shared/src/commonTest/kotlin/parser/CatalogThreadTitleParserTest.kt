package com.valoser.futacha.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogThreadTitleParserTest {
    @Test
    fun extractCatalogDisplayTitleFromThreadHead_prefersBlockquoteFirstLine() {
        val html = """
            <div class="thre" data-res="1">
                <span class="csb">無念</span>
                <blockquote>タイトル行<br>2行目</blockquote>
            </div>
        """.trimIndent()

        assertEquals("タイトル行", extractCatalogDisplayTitleFromThreadHead(html))
    }

    @Test
    fun extractCatalogDisplayTitleFromThreadHead_fallsBackToSubject() {
        val html = """
            <div class="thre" data-res="1">
                <span class="csb">件名テスト</span>
            </div>
        """.trimIndent()

        assertEquals("件名テスト", extractCatalogDisplayTitleFromThreadHead(html))
    }

    @Test
    fun extractCatalogDisplayTitleFromThreadHead_decodesEntitiesAndStripsTags() {
        val html = """
            <blockquote><font color="#789922">&gt;引用</font><br>&#12486;&#12473;&#12488;</blockquote>
        """.trimIndent()

        assertEquals(">引用", extractCatalogDisplayTitleFromThreadHead(html))
    }

    @Test
    fun extractCatalogDisplayTitleFromThreadHead_returnsNullForBlankContent() {
        val html = "<blockquote><br></blockquote>"

        assertNull(extractCatalogDisplayTitleFromThreadHead(html))
    }
}
