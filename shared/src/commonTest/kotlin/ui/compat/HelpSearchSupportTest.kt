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
}
