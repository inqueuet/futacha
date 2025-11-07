package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage

interface HtmlParser {
    fun parseCatalog(html: String): List<CatalogItem>
    fun parseThread(html: String): ThreadPage
}
