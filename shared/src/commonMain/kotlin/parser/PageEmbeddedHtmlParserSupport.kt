package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement

internal object PageEmbeddedHtmlParserSupport {
    private const val DEFAULT_BASE_URL = "https://www.example.com"
    private const val MAX_EMBEDS_PER_PLACEMENT = 3
    private const val MIN_IFRAME_WIDTH = 200
    private const val MAX_IFRAME_HEIGHT = 320
    private const val TARGET_DISPLAY_WIDTH_DP = 360f

    private val iframeRegex = Regex(
        pattern = "<iframe[^>]{1,1000}src=['\"]([^'\"]+)['\"][^>]{0,1000}>",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val canonicalRegex = Regex(
        pattern = "<link[^>]{1,500}rel=['\"]canonical['\"][^>]{1,500}href=['\"]([^'\"]+)['\"]",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val widthAttrRegex = Regex(
        pattern = "width\\s*=\\s*['\"]?(\\d+)['\"]?",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val heightAttrRegex = Regex(
        pattern = "height\\s*=\\s*['\"]?(\\d+)['\"]?",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extractCatalogEmbeddedHtml(
        html: String,
        baseUrl: String?
    ): List<EmbeddedHtmlContent> {
        val resolvedBaseUrl = normalizeBaseUrl(baseUrl)
        val contentStart = findCatalogContentStartIndex(html)
        return extractEmbeddedHtml(
            html = html,
            resolvedBaseUrl = resolvedBaseUrl,
            contentStartIndex = contentStart,
            contentEndIndex = contentStart
        )
    }

    fun extractThreadEmbeddedHtml(
        html: String,
        baseUrl: String? = null
    ): List<EmbeddedHtmlContent> {
        val resolvedBaseUrl = normalizeBaseUrl(baseUrl ?: extractCanonicalBaseUrl(html))
        val contentStart = html.indexOf("<div class=\"thre\"", ignoreCase = true)
        val contentEnd = html.indexOf("<!--スレッド終了", startIndex = contentStart.coerceAtLeast(0), ignoreCase = true)
            .takeIf { it >= 0 }
            ?: html.indexOf("</div><!--スレッド終了", startIndex = contentStart.coerceAtLeast(0), ignoreCase = true)
        return extractEmbeddedHtml(
            html = html,
            resolvedBaseUrl = resolvedBaseUrl,
            contentStartIndex = contentStart,
            contentEndIndex = contentEnd
        )
    }

    private fun extractEmbeddedHtml(
        html: String,
        resolvedBaseUrl: String,
        contentStartIndex: Int,
        contentEndIndex: Int?
    ): List<EmbeddedHtmlContent> {
        if (html.isBlank()) return emptyList()
        val normalized = html.replace("\r\n", "\n")
        val grouped = LinkedHashMap<EmbeddedHtmlPlacement, MutableList<EmbeddedHtmlContent>>()
        val seenKeys = LinkedHashSet<String>()

        iframeRegex.findAll(normalized).forEachIndexed { index, match ->
            val iframeTag = match.value
            val rawSrc = match.groupValues.getOrNull(1).orEmpty()
            val src = resolveUrl(rawSrc, resolvedBaseUrl)
            val width = widthAttrRegex.find(iframeTag)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed
            val height = heightAttrRegex.find(iframeTag)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed
            if (width < MIN_IFRAME_WIDTH || height <= 0 || height > MAX_IFRAME_HEIGHT) return@forEachIndexed

            val placement = when {
                contentStartIndex < 0 -> EmbeddedHtmlPlacement.Footer
                match.range.first < contentStartIndex -> EmbeddedHtmlPlacement.Header
                contentEndIndex != null && contentEndIndex >= 0 && match.range.first > contentEndIndex -> EmbeddedHtmlPlacement.Footer
                else -> return@forEachIndexed
            }
            val dedupeKey = "$placement|$src|$width|$height"
            if (!seenKeys.add(dedupeKey)) return@forEachIndexed

            val currentPlacementItems = grouped.getOrPut(placement) { mutableListOf() }
            if (currentPlacementItems.size >= MAX_EMBEDS_PER_PLACEMENT) return@forEachIndexed

            currentPlacementItems += EmbeddedHtmlContent(
                id = "embed-${placement.name.lowercase()}-$index",
                html = buildEmbeddedIframeHtml(src = src, width = width, height = height),
                estimatedHeightDp = computeEstimatedHeightDp(width = width, height = height),
                placement = placement
            )
        }

        return buildList {
            addAll(grouped[EmbeddedHtmlPlacement.Header].orEmpty())
            addAll(grouped[EmbeddedHtmlPlacement.Footer].orEmpty())
        }
    }

    private fun findCatalogContentStartIndex(html: String): Int {
        val doubleQuote = html.indexOf("""id="cattable"""", ignoreCase = true)
        if (doubleQuote >= 0) {
            return html.lastIndexOf("<table", startIndex = doubleQuote, ignoreCase = true)
        }
        val singleQuote = html.indexOf("id='cattable'", ignoreCase = true)
        if (singleQuote >= 0) {
            return html.lastIndexOf("<table", startIndex = singleQuote, ignoreCase = true)
        }
        return -1
    }

    private fun extractCanonicalBaseUrl(html: String): String? {
        val canonical = canonicalRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
        return try {
            val scheme = canonical.substringBefore("://", "")
            if (scheme.isBlank() || !canonical.contains("://")) return null
            val hostAndPath = canonical.substringAfter("://")
            val host = hostAndPath.substringBefore('/')
            "$scheme://$host"
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeBaseUrl(baseUrl: String?): String {
        return baseUrl?.trim()?.takeIf { it.contains("://") } ?: DEFAULT_BASE_URL
    }

    private fun computeEstimatedHeightDp(width: Int, height: Int): Int {
        val scaledHeight = if (width > 0) {
            (height.toFloat() * minOf(1f, TARGET_DISPLAY_WIDTH_DP / width.toFloat()))
        } else {
            height.toFloat()
        }
        return scaledHeight.toInt().coerceIn(56, MAX_IFRAME_HEIGHT)
    }

    private fun buildEmbeddedIframeHtml(
        src: String,
        width: Int,
        height: Int
    ): String {
        val escapedSrc = escapeHtmlAttribute(src)
        val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        return """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
            <style>
            html,body{margin:0;padding:0;background:transparent;overflow:hidden;}
            .frame{width:100%;aspect-ratio:${aspectRatio};overflow:hidden;background:transparent;}
            iframe{border:0;display:block;width:100%;height:100%;}
            </style>
            </head>
            <body>
            <div class="frame">
            <iframe src="$escapedSrc" loading="eager" allowfullscreen referrerpolicy="no-referrer-when-downgrade"></iframe>
            </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun resolveUrl(path: String, baseUrl: String): String = when {
        path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> path
        path.startsWith("//") -> "${extractScheme(baseUrl)}:$path"
        path.startsWith("/") -> extractOrigin(baseUrl).trimEnd('/') + path
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun extractScheme(url: String): String = url.substringBefore("://", "https")

    private fun extractOrigin(url: String): String {
        val scheme = extractScheme(url)
        val remainder = url.substringAfter("://", "")
        val host = remainder.substringBefore('/')
        return "$scheme://$host"
    }

    private fun escapeHtmlAttribute(value: String): String {
        return buildString(value.length + 16) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }
}
