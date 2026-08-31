package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.HtmlEntityDecoder

/**
 * Extract the first visible title line while dropping Cc/Cf code points.
 * The reference APK applies this before showing or persisting an inferred
 * thread title, preventing bidi/zero-width/control characters from leaking
 * into tabs, history and saved pages.
 */
fun extractFirstUsableTitleLine(text: String, maxLength: Int? = null): String? {
    val line = text.lineSequence()
        .map { raw ->
            raw.filterNot { char ->
                char.category == CharCategory.CONTROL || char.category == CharCategory.FORMAT
            }.trim()
        }
        .firstOrNull(String::isNotEmpty)
        ?: return null
    return maxLength?.takeIf { it >= 0 }?.let(line::take) ?: line
}

/**
 * 投稿本文から最初の1行を抽出する。タグを除去し、HTMLエンティティもデコードする。
 */
fun extractFirstLineFromBody(post: Post?): String? {
    val html = post?.messageHtml ?: return null
    val normalized = replaceHtmlBreakTags(html)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return extractFirstUsableTitleLine(decoded)
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
