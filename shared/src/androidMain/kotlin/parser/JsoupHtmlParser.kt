package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage

class JsoupHtmlParser : HtmlParser {
    override fun parseCatalog(html: String): List<CatalogItem> = emptyList()

    override fun parseThread(html: String): ThreadPage = ThreadPage(
        threadId = "",
        posts = emptyList()
    )
}
