package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.parser.CatalogHtmlParserCore
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore

class AppleHtmlParser : HtmlParser {
    override suspend fun parseCatalog(html: String, baseUrl: String?): List<CatalogItem> =
        CatalogHtmlParserCore.parseCatalog(html, baseUrl)

    override suspend fun parseThread(html: String): ThreadPage = ThreadHtmlParserCore.parseThread(html)

    override fun extractOpImageUrl(html: String, baseUrl: String?): String? =
        ThreadHtmlParserCore.extractOpImageUrl(html, baseUrl)
}
