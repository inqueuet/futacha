package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.parser.HtmlEntityDecoder

private val archiveMetaTagRegex = Regex("""(?is)<meta\b[^>]{0,4096}>""")
private val archiveAttributeRegex = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private val archiveRefreshUrlRegex = Regex("""(?is)^\s*\d+(?:\.\d+)?\s*;\s*url\s*=\s*(.+?)\s*$""")
private val forestDataStartRegex = Regex("""\${'$'}data\s*=\s*`""")

/** Only a meta refresh is a redirect; links and JavaScript can also contain url=. */
internal fun extractArchiveMetaRefresh(html: String): String? {
    for (tag in archiveMetaTagRegex.findAll(html)) {
        val attributes = archiveAttributeRegex.findAll(tag.value).associate { attribute ->
            attribute.groupValues[1].lowercase() to HtmlEntityDecoder.decode(
                attribute.groups[2]?.value ?: attribute.groups[3]?.value ?: attribute.groupValues[4]
            )
        }
        if (!attributes["http-equiv"].equals("refresh", ignoreCase = true)) continue
        val target = archiveRefreshUrlRegex.find(attributes["content"].orEmpty())
            ?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
        if (!target.isNullOrBlank()) return target
    }
    return null
}

/** Forest stores the OP and replies in a template literal, without the usual OP container.
 * Read the string as data; never execute the page's JavaScript.
 */
internal fun normalizeForestThreadHtml(html: String): String {
    val start = forestDataStartRegex.find(html)?.range?.last?.plus(1) ?: return html
    val body = StringBuilder()
    var index = start
    while (index < html.length) {
        val char = html[index++]
        when (char) {
            '`' -> return "<div class=\"thre\">$body</div>"
            '\\' -> {
                require(index < html.length) { "フォレスト本文が途中で切れています" }
                when (val escaped = html[index++]) {
                    'n' -> body.append('\n')
                    'r' -> body.append('\r')
                    't' -> body.append('\t')
                    'b' -> body.append('\b')
                    'f' -> body.append('\u000C')
                    '\n' -> Unit
                    '\r' -> if (index < html.length && html[index] == '\n') index++
                    'u', 'x' -> {
                        val digits = if (escaped == 'u') 4 else 2
                        require(index + digits <= html.length) { "フォレスト本文のエスケープが不正です" }
                        val value = html.substring(index, index + digits).toIntOrNull(16)
                        require(value != null) { "フォレスト本文のエスケープが不正です" }
                        body.append(value.toChar())
                        index += digits
                    }
                    else -> body.append(escaped) // Includes \\, \`, and \$.
                }
            }
            else -> body.append(char)
        }
    }
    error("フォレスト本文が途中で切れています")
}
