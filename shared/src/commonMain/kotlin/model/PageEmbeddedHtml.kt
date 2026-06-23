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
    val embeddedHtml: List<EmbeddedHtmlContent> = emptyList(),
    val parseWarning: PageParseWarning = PageParseWarning()
)

data class ThreadPageContent(
    val page: ThreadPage,
    val embeddedHtml: List<EmbeddedHtmlContent> = emptyList()
)

data class PageParseWarning(
    val isTruncated: Boolean = false,
    val reason: String? = null,
    val skippedItemCount: Int = 0,
    val oversizedBlockCount: Int = 0,
    val parseTimeoutCount: Int = 0
)

fun mergePageParseWarnings(warnings: List<PageParseWarning>): PageParseWarning {
    if (warnings.isEmpty()) return PageParseWarning()
    return PageParseWarning(
        isTruncated = warnings.any { it.isTruncated },
        reason = warnings.firstOrNull { it.reason?.isNotBlank() == true }?.reason,
        skippedItemCount = warnings.sumOf { it.skippedItemCount },
        oversizedBlockCount = warnings.sumOf { it.oversizedBlockCount },
        parseTimeoutCount = warnings.sumOf { it.parseTimeoutCount }
    )
}
