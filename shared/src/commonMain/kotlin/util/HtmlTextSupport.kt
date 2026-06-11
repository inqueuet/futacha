package com.valoser.futacha.shared.util

private const val MAX_HTML_TAG_SCAN_CHARS = 768

internal fun replaceHtmlBreakTags(
    value: String,
    lineBreakReplacement: String = "\n",
    paragraphReplacement: String = "\n\n"
): String {
    if (value.indexOf('<') == -1) return value
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '<') {
            val tagEnd = findBoundedHtmlTagEnd(value, index)
            if (tagEnd != -1) {
                val tag = value.substring(index + 1, tagEnd)
                    .trim()
                    .trimEnd('/')
                    .trim()
                when {
                    tag.equals("br", ignoreCase = true) ||
                        tag.startsWith("br ", ignoreCase = true) -> {
                        builder.append(lineBreakReplacement)
                        index = tagEnd + 1
                        continue
                    }

                    tag.equals("/p", ignoreCase = true) -> {
                        builder.append(paragraphReplacement)
                        index = tagEnd + 1
                        continue
                    }
                }
            }
        }
        builder.append(value[index])
        index += 1
    }
    return builder.toString()
}

internal fun stripHtmlTagsLinear(value: String): String {
    if (value.indexOf('<') == -1) return value
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '<') {
            val tagEnd = findBoundedHtmlTagEnd(value, index)
            if (tagEnd != -1) {
                index = tagEnd + 1
                continue
            }
        }
        builder.append(value[index])
        index += 1
    }
    return builder.toString()
}

private fun findBoundedHtmlTagEnd(value: String, startIndex: Int): Int {
    val limit = minOf(value.length, startIndex + MAX_HTML_TAG_SCAN_CHARS + 1)
    var index = startIndex + 1
    while (index < limit) {
        when (value[index]) {
            '>' -> return index
            '<' -> return -1
        }
        index += 1
    }
    return -1
}
