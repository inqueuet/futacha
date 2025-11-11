package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage

interface HtmlParser {
    fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem>
    fun parseThread(html: String): ThreadPage
    fun extractOpImageUrl(html: String, baseUrl: String? = null): String?
}
