package com.valoser.futacha.shared.ui.compat

import kotlin.test.*

class HelpSearchSupportTest {
    @Test fun searchesClosedSectionsJapaneseEntitiesAndLiteralWords() {
        val html = """<html><style>invisible-style</style><body><label>巡回</label><input type="checkbox"><div>
            <p class="title">Wi-Fi と通信</p><p>にじろぐは不要です。A&amp;B<br/>[検索]できます<img src="data:image/png;base64,private-image"/></p>
            <p class="title">保存</p><p>保存方法はこちら <a href="https://example.com/?a=1&amp;b=2">詳細</a></p></div></body></html>"""
        val sections = helpSearchSections(html)
        assertEquals(2, sections.size)
        assertEquals(sections.take(1), searchHelp(sections, " にじろぐ "))
        assertEquals(sections.take(1), searchHelp(sections, "wi-fi"))
        assertEquals(sections.take(1), searchHelp(sections, "A&B"))
        assertEquals(sections.take(1), searchHelp(sections, "[検索]"))
        assertEquals(sections, searchHelp(sections, " "))
        assertTrue(searchHelp(sections, "存在しない語").isEmpty())
        assertTrue(searchHelp(sections, "private-image").isEmpty())
        assertTrue(searchHelp(sections, "invisible-style").isEmpty())
        assertEquals(listOf("詳細" to "https://example.com/?a=1&b=2"), sections.last().links)
    }

    @Test fun currentHelpExplainsInternalExternalAndOsExecutionAndIndexesMedia() {
        val html = compatibilityReferenceHelpHtml(compatibilityPaletteFor(null))
        val sections = helpSearchSections(html)
        assertTrue(sections.size > 100)
        assertTrue(searchHelp(sections, "にじろぐ").any { "不要" in it.body })
        assertTrue(searchHelp(sections, "強制停止").any { "OS" in it.body })
        assertTrue(searchHelp(sections, "閲覧順").any { "通信" in it.body })
        assertTrue(searchHelp(sections, "ONNX").isNotEmpty())
        assertFalse(html.contains("にじろぐ(仮) バージョン1.0.5以上が必要です"))
        assertFalse(html.contains("別アプリ にじろぐ(仮)が行います"))
    }

    @Test fun helpStartsWithBasicOperationsAndAllSectionsCanCollapse() {
        val html = compatibilityReferenceHelpHtml(compatibilityPaletteFor(null))
        val headings = Regex("<label for=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals("board", headings.first())
        assertEquals(headings.indexOf("drawer") + 1, headings.indexOf("watcher-help"))
        assertEquals(headings.indexOf("viewer") + 1, headings.indexOf("media-help"))
        for (id in headings) {
            assertTrue(html.contains("<input type=\"checkbox\" id=\"$id\" class=\"on-off\" />"), id)
        }
        assertFalse(html.contains(" checked"))
    }

    @Test fun searchOpensClosedMatchesHighlightsWordsAndResetsOnClear() {
        val html = compatibilityReferenceHelpHtml(compatibilityPaletteFor(null))
        val result = searchedHelpHtml(html, " 強制停止 ")
        assertTrue(result.contains("<input checked type=\"checkbox\" id=\"watcher-help\""))
        assertTrue(result.contains("<mark>強制停止</mark>"))
        assertFalse(result.contains("<label for=\"board\""))
        val changed = searchedHelpHtml(html, "onnx")
        assertTrue(changed.contains("<input checked type=\"checkbox\" id=\"media-help\""))
        assertTrue(changed.contains("<mark>ONNX</mark>"))
        assertFalse(changed.contains("id=\"watcher-help\""))
        assertEquals(html, searchedHelpHtml(html, " "))
        assertFalse(searchedHelpHtml(html, "no-such-help-word").contains("<label"))
    }

    @Test fun highlightPreservesImagesLinksAndEscapesLiteralSearchText() {
        val html = """<label for="test">A&amp;B</label><input type="checkbox" id="test" class="on-off" />
            <div><p><a href="https://example.com/?a=1&amp;b=2">A&amp;B</a> a&amp;b &lt;tag&gt; [検索]
            <img src="data:image/png;base64,A&amp;B" /></p></div>"""
        val result = searchedHelpHtml(html, "a&b")
        assertEquals(3, Regex("<mark>").findAll(result).count())
        assertTrue(result.contains("href=\"https://example.com/?a=1&amp;b=2\""))
        assertTrue(result.contains("src=\"data:image/png;base64,A&amp;B\""))
        assertTrue(searchedHelpHtml(html, "<tag>").contains("<mark>&lt;tag&gt;</mark>"))
        assertTrue(searchedHelpHtml(html, "[検索]").contains("<mark>[検索]</mark>"))
    }
}
