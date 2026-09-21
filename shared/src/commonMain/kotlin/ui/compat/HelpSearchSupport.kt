package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.parser.HtmlEntityDecoder

internal data class HelpSearchSection(
    val title: String,
    val body: String,
    val links: List<Pair<String, String>> = emptyList()
)

/** Index the displayed document, including closed accordion sections, but not CSS or images. */
internal fun helpSearchSections(html: String): List<HelpSearchSection> {
    val body = html.substringAfter("<body>", html).substringBefore("</body>")
        .replace(Regex("<(script|style)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
    var section = "ヘルプ"
    var subsection = ""
    var title = ""
    return buildList {
        Regex("<(label|h[1-6]|p)\\b([^>]*)>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
            .findAll(body).forEach { block ->
                val tag = block.groupValues[1].lowercase()
                val attributes = block.groupValues[2]
                val content = block.groupValues[3]
                val text = helpPlainText(content)
                if (text.isBlank()) return@forEach
                when {
                    tag == "label" || tag == "h1" || tag == "h2" -> {
                        section = text; subsection = ""; title = ""
                    }
                    "midashi" in attributes -> { subsection = text; title = "" }
                    tag.startsWith("h") || "class=\"title\"" in attributes -> title = text
                    else -> {
                        val links = Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
                            .findAll(content).mapNotNull { link ->
                                val url = HtmlEntityDecoder.decode(link.groupValues[1])
                                if (url.startsWith("https://") || url.startsWith("http://")) {
                                    helpPlainText(link.groupValues[2]).ifBlank { "リンクを開く" } to url
                                } else null
                            }.toList()
                        add(HelpSearchSection(listOf(section, subsection, title).filter(String::isNotBlank).joinToString(" › "), text, links))
                    }
                }
            }
    }
}

private fun helpPlainText(html: String): String = HtmlEntityDecoder.decode(
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
).replace(Regex("[ \\t\\r]+"), " ").trim()

internal fun searchHelp(sections: List<HelpSearchSection>, query: String): List<HelpSearchSection> {
    val word = query.trim()
    return if (word.isEmpty()) sections else sections.filter {
        it.title.contains(word, ignoreCase = true) || it.body.contains(word, ignoreCase = true)
    }
}
