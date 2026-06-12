package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear

internal fun messageHtmlToLines(html: String): List<String> {
    val normalized = replaceHtmlBreakTags(html)
    val withoutTags = stripHtmlTagsLinear(normalized)
    val decoded = HtmlEntityDecoder.decode(withoutTags)
    return decoded.lines()
}

internal fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}
