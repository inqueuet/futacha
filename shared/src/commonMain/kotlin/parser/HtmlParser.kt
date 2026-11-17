package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.ThreadPage

interface HtmlParser {
    suspend fun parseCatalog(html: String, baseUrl: String? = null): List<CatalogItem>
    suspend fun parseCatalogPage(html: String, baseUrl: String? = null): CatalogPageContent =
        CatalogPageContent(items = parseCatalog(html, baseUrl))
    suspend fun parseThread(html: String, baseUrl: String? = null): ThreadPage
    fun extractOpImageUrl(html: String, baseUrl: String? = null): String?
    fun extractCatalogEmbeddedHtml(html: String, baseUrl: String? = null): List<EmbeddedHtmlContent> = emptyList()
    fun extractThreadEmbeddedHtml(html: String, baseUrl: String? = null): List<EmbeddedHtmlContent> = emptyList()
}
