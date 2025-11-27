package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.HtmlEntityDecoder

private val lineBreakRegex = Regex("(?i)<br\\s*/?>")
private val paragraphEndRegex = Regex("(?i)</p>")
private val htmlTagRegex = Regex("<[^>]+>")

/**
 * 投稿本文から最初の1行を抽出する。タグを除去し、HTMLエンティティもデコードする。
 */
fun extractFirstLineFromBody(post: Post?): String? {
    val html = post?.messageHtml ?: return null
    val normalized = html
        .replace(lineBreakRegex, "\n")
        .replace(paragraphEndRegex, "\n\n")
    val withoutTags = htmlTagRegex.replace(normalized, "")
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded
        .lines()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.ifBlank { null }
}

/**
 * スレッドタイトルの候補を決定する。本文の先頭行 → 件名 → フォールバックの順に選ぶ。
 */
fun resolveThreadTitle(firstPost: Post?, vararg fallbacks: String?): String {
    val firstLine = extractFirstLineFromBody(firstPost)
    val subject = firstPost?.subject?.takeIf { it.isNotBlank() }
    val fallback = fallbacks.firstOrNull { it != null && it.isNotBlank() }
    return firstLine ?: subject ?: fallback ?: "無題"
}
