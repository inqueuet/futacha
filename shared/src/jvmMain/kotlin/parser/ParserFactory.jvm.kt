package com.valoser.futacha.shared.parser

actual fun createHtmlParser(): HtmlParser = object : HtmlParser {
    override suspend fun parseCatalog(html: String, baseUrl: String?) =
        CatalogHtmlParserCore.parseCatalog(html, baseUrl)

    override suspend fun parseThread(html: String) =
        ThreadHtmlParserCore.parseThread(html)

    override fun extractOpImageUrl(html: String, baseUrl: String?) =
        ThreadHtmlParserCore.extractOpImageUrl(html, baseUrl)
}
