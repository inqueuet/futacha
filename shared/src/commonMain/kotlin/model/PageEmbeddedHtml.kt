package com.valoser.futacha.shared.model

data class EmbeddedHtmlContent(
    val id: String,
    val html: String,
    val estimatedHeightDp: Int,
    val placement: EmbeddedHtmlPlacement
)

enum class EmbeddedHtmlPlacement {
    Header,
    Footer
}

data class CatalogPageContent(
    val items: List<CatalogItem>,
    val embeddedHtml: List<EmbeddedHtmlContent> = emptyList()
)

data class ThreadPageContent(
    val page: ThreadPage,
    val embeddedHtml: List<EmbeddedHtmlContent> = emptyList()
)
