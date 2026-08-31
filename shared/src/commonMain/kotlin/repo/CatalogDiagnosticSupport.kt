package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.PageParseWarning
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger

const val CATALOG_DIAGNOSTIC_HTML_PATH = "diagnostics/debug_cat.html"

private val catalogThreadAnchorRegex = Regex(
    """<td\b[^>]*>\s*<a\b[^>]*href\s*=\s*['\"][^'\"]*res/\d+\.html?""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

fun countCatalogThreadAnchors(html: String): Int =
    catalogThreadAnchorRegex.findAll(html).count()

internal suspend fun attachCatalogDiagnostics(
    html: String,
    parsed: CatalogPageContent,
    fileSystem: FileSystem?
): CatalogPageContent {
    val expectedCount = countCatalogThreadAnchors(html)
    if (expectedCount == 0 || expectedCount == parsed.items.size) return parsed

    val saved = fileSystem
        ?.writeString(CATALOG_DIAGNOSTIC_HTML_PATH, html)
        ?.isSuccess == true
    val reason = buildString {
        append("カタログの全件取得失敗（HTML ")
        append(expectedCount)
        append("件 / 解析 ")
        append(parsed.items.size)
        append("件）")
        if (saved) append("。検証用ログ debug_cat.html を保存しました")
    }
    Logger.w("DefaultBoardRepository", reason)
    return parsed.copy(
        parseWarning = parsed.parseWarning.copy(
            isTruncated = true,
            reason = reason,
            diagnosticPath = CATALOG_DIAGNOSTIC_HTML_PATH
        )
    )
}
