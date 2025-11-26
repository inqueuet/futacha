package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.ThreadPage

interface HtmlParser {
    suspend fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem>
    suspend fun parseThread(html: String): ThreadPage
    fun extractOpImageUrl(html: String, baseUrl: String? = null): String?
}
