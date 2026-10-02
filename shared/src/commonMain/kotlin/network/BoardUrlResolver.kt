package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.describeFailureForLog
import com.valoser.futacha.shared.util.describeUrlForLog
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom

internal object BoardUrlResolver {
    private const val TAG = "BoardUrlResolver"

    fun resolveCatalogUrl(boardUrl: String, mode: CatalogMode): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }

        return try {
            val normalized = if (!boardUrl.contains("://")) {
                "https://$boardUrl"
            } else {
                boardUrl
            }
            requireUnambiguousAuthority(normalized)
            val parsed = Url(normalized)
            ensureHttpScheme(parsed)
            requireUnambiguousAuthority(parsed)
            URLBuilder().apply {
                takeFrom(parsed)
                encodedPath = when {
                    encodedPath.endsWith("/futaba.htm", ignoreCase = true) ->
                        encodedPath.dropLast("futaba.htm".length) + "futaba.php"
                    encodedPath.endsWith("/futaba.php", ignoreCase = true) ->
                        encodedPath
                    else ->
                        // Users commonly register a board as its directory
                        // (for example https://img.2chan.net/b/).  The
                        // catalog endpoint is futaba.php, not the directory
                        // index with a mode query appended to it.
                        encodedPath.trimEnd('/') + "/futaba.php"
                }
                parameters.remove("mode")
                parameters.remove("sort")
                parameters.append("mode", "cat")
                mode.sortParam?.let { parameters.append("sort", it) }
            }.buildString()
        } catch (e: Exception) {
            Logger.e(
                TAG,
                "Failed to resolve catalog URL (${describeUrlForLog(boardUrl)}), type=${describeFailureForLog(e)}"
            )
            throw IllegalArgumentException("Invalid board URL: $boardUrl", e)
        }
    }

    fun resolveThreadUrl(boardUrl: String, threadId: String): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }
        if (threadId.isBlank()) {
            throw IllegalArgumentException("Thread ID cannot be blank")
        }

        // Validate threadId to prevent path traversal attacks
        val sanitizedThreadId = sanitizeNumericId(threadId)
        if (sanitizedThreadId.isBlank()) {
            throw IllegalArgumentException("Invalid thread ID: contains unsafe characters")
        }

        return try {
            val base = resolveBoardBaseUrl(boardUrl)
            val sanitizedBase = if (base.endsWith("/")) base.dropLast(1) else base
            buildString {
                append(sanitizedBase)
                if (sanitizedBase.isNotEmpty()) append('/')
                append("res/")
                append(sanitizedThreadId)
                append(".htm")
            }
        } catch (e: Exception) {
            Logger.e(
                TAG,
                "Failed to resolve thread URL (${describeUrlForLog(boardUrl)}), type=${describeFailureForLog(e)}"
            )
            throw IllegalArgumentException("Invalid board URL or thread ID", e)
        }
    }

    internal fun sanitizePostId(postId: String): String = sanitizeNumericId(postId)

    fun resolveBoardSlug(boardUrl: String): String {
        val base = resolveBoardBaseUrl(boardUrl)
        val slug = base.substringAfterLast('/', missingDelimiterValue = base)
        if (slug.isBlank()) {
            throw IllegalArgumentException("Could not extract board slug from URL: $boardUrl")
        }
        return slug
    }

    fun resolveBoardBaseUrl(boardUrl: String): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }

        // Auto-fix missing scheme
        val urlToParse = if (!boardUrl.contains("://")) {
            "https://$boardUrl"
        } else {
            boardUrl
        }

        requireUnambiguousAuthority(urlToParse)
        val url = runCatching { Url(urlToParse) }.getOrElse { error ->
            Logger.e(
                TAG,
                "Failed to parse URL (${describeUrlForLog(urlToParse)}), type=${describeFailureForLog(error)}"
            )
            throw IllegalArgumentException("Invalid board URL: $boardUrl", error)
        }
        ensureHttpScheme(url)
        requireUnambiguousAuthority(url)

        val segments = url.encodedPath
            .split('/')
            .filter { it.isNotBlank() }

        // Thread URLs look like `/board/res/123.htm`; strip both the thread file and `res`.
        // Board URLs like `/board/futaba.php` still only drop the trailing file segment.
        val directorySegments = segments.toMutableList().apply {
            if (lastOrNull()?.contains('.') == true) {
                removeAt(lastIndex)
            }
            if (lastOrNull()?.equals("res", ignoreCase = true) == true) {
                removeAt(lastIndex)
            }
        }
        
        val path = when {
            directorySegments.isEmpty() -> ""
            else -> "/" + directorySegments.joinToString("/")
        }
        
        val portSegment = when {
            url.port == url.protocol.defaultPort -> ""
            else -> ":${url.port}"
        }
        
        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            append(portSegment)
            append(path)
        }
    }

    fun resolveSiteRoot(boardUrl: String): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }

        // Same scheme fix-up as resolveBoardBaseUrl: Url("may.2chan.net/b/")
        // parses as a relative URL on localhost, which sent del/sd requests there.
        val boardUrlWithScheme = if (!boardUrl.contains("://")) "https://$boardUrl" else boardUrl
        requireUnambiguousAuthority(boardUrlWithScheme)
        val url = runCatching { Url(boardUrlWithScheme) }.getOrElse { error ->
            val normalized = boardUrlWithScheme
                .substringBefore('#')
                .substringBefore('?')
            val schemeSeparator = normalized.indexOf("://")
            if (schemeSeparator <= 0) {
                throw IllegalArgumentException("Invalid board URL: $boardUrl", error)
            }
            val scheme = normalized.substring(0, schemeSeparator)
            if (scheme.lowercase() != "http" && scheme.lowercase() != "https") {
                throw IllegalArgumentException("Unsupported URL scheme: $scheme")
            }
            val remainder = normalized.substring(schemeSeparator + 3)
            val host = remainder.substringBefore('/')
            return "$scheme://$host"
        }
        ensureHttpScheme(url)
        requireUnambiguousAuthority(url)

        val portSegment = when {
            url.port == url.protocol.defaultPort -> ""
            else -> ":${url.port}"
        }
        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            append(portSegment)
        }
    }

    private fun sanitizeNumericId(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
            trimmed
        } else {
            ""
        }
    }

    /**
     * S4-1: Ktor and OkHttp end the authority at `\`, a quick check of the
     * string only at `/`, and userinfo hides the host behind `@`:
     * `https://evil.com\@may.2chan.net/b/` was sent to evil.com although it
     * reads as a 2chan.net board. A board URL never needs any of these, so
     * refuse them before a request URL (or its Referer) is built.
     */
    private fun requireUnambiguousAuthority(rawUrl: String) {
        val authority = rawUrl.trim()
            .substringAfter("://")
            .takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.any(::isAmbiguousAuthorityChar)) {
            throw IllegalArgumentException("Ambiguous board URL authority")
        }
    }

    private fun requireUnambiguousAuthority(url: Url) {
        if (url.encodedUser != null ||
            url.encodedPassword != null ||
            url.host.any(::isAmbiguousAuthorityChar) ||
            '\\' in url.encodedPath
        ) {
            throw IllegalArgumentException("Ambiguous board URL authority")
        }
    }

    private fun isAmbiguousAuthorityChar(char: Char): Boolean =
        char == '@' || char == '\\' || char == '%' || char.isWhitespace() || char.isISOControl()

    private fun ensureHttpScheme(url: Url) {
        val scheme = url.protocol.name.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Unsupported URL scheme: $scheme")
        }
    }
}
