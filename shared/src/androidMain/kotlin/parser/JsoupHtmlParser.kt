package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.CatalogHtmlParserCore
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore

class JsoupHtmlParser : HtmlParser {
    override fun parseCatalog(html: String): List<CatalogItem> = CatalogHtmlParserCore.parseCatalog(html)

    override fun parseThread(html: String): ThreadPage = ThreadHtmlParserCore.parseThread(html)
}
