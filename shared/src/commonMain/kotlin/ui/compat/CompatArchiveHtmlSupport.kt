package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.parser.HtmlEntityDecoder

private val archiveMetaTagRegex = Regex("""(?is)<meta\b[^>]{0,4096}>""")
private val archiveAttributeRegex = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private val archiveRefreshUrlRegex = Regex("""(?is)^\s*(\d+)(?:\.\d+)?\s*;\s*url\s*=\s*(.+?)\s*$""")

/** A refresh after longer than this is a periodic reload of the page, not a redirect. */
private const val ARCHIVE_REDIRECT_MAX_DELAY_SECONDS = 10
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
        val match = archiveRefreshUrlRegex.find(attributes["content"].orEmpty()) ?: continue
        val delayDigits = match.groupValues[1].trimStart('0')
        val delaySeconds = if (delayDigits.length > 6) Int.MAX_VALUE else delayDigits.toIntOrNull() ?: 0
        if (delaySeconds > ARCHIVE_REDIRECT_MAX_DELAY_SECONDS) continue
        val target = match.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
        if (target.isNotBlank()) return target
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
                    'u', 'x' -> if (escaped == 'u' && index < html.length && html[index] == '{') {
                        // ES2015 code point escape: \u{1F600}.
                        val close = html.indexOf('}', startIndex = index + 1)
                        require(close in (index + 2)..(index + 7)) { "フォレスト本文のエスケープが不正です" }
                        val codePoint = html.substring(index + 1, close).toIntOrNull(16)
                        require(codePoint != null && codePoint in 0..0x10FFFF) { "フォレスト本文のエスケープが不正です" }
                        appendForestCodePoint(body, codePoint)
                        index = close + 1
                    } else {
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

private fun appendForestCodePoint(body: StringBuilder, codePoint: Int) {
    if (codePoint < 0x10000) {
        body.append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        body.append((0xD800 + (offset shr 10)).toChar())
        body.append((0xDC00 + (offset and 0x3FF)).toChar())
    }
}
