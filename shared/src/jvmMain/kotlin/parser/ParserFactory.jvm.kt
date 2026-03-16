package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.EmbeddedHtmlContent

actual fun createHtmlParser(): HtmlParser = object : HtmlParser {
    override suspend fun parseCatalog(html: String, baseUrl: String?) =
        CatalogHtmlParserCore.parseCatalog(html, baseUrl)

    override suspend fun parseThread(html: String) =
        ThreadHtmlParserCore.parseThread(html)

    override fun extractOpImageUrl(html: String, baseUrl: String?) =
        ThreadHtmlParserCore.extractOpImageUrl(html, baseUrl)

    override fun extractCatalogEmbeddedHtml(html: String, baseUrl: String?): List<EmbeddedHtmlContent> =
        PageEmbeddedHtmlParserSupport.extractCatalogEmbeddedHtml(html, baseUrl)

    override fun extractThreadEmbeddedHtml(html: String, baseUrl: String?): List<EmbeddedHtmlContent> =
        PageEmbeddedHtmlParserSupport.extractThreadEmbeddedHtml(html, baseUrl)
}
