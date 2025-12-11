package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

internal object BoardUrlResolver {
    fun resolveCatalogUrl(boardUrl: String, mode: CatalogMode): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }

        return try {
            URLBuilder().apply {
                takeFrom(boardUrl)
                parameters.remove("mode")
                parameters.remove("sort")
                parameters.append("mode", "cat")
                mode.sortParam?.let { parameters.append("sort", it) }
            }.buildString()
        } catch (e: Exception) {
            println("BoardUrlResolver: Failed to resolve catalog URL for '$boardUrl': ${e.message}")
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
            println("BoardUrlResolver: Failed to resolve thread URL for board='$boardUrl', thread='$threadId': ${e.message}")
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

        val url = runCatching { Url(urlToParse) }.getOrElse { error ->
            println("BoardUrlResolver: Failed to parse URL '$urlToParse': ${error.message}")
            throw IllegalArgumentException("Invalid board URL: $boardUrl", error)
        }

        val segments = url.encodedPath
            .split('/')
            .filter { it.isNotBlank() }
        
        // If the path ends with a file (e.g. futaba.php), drop it.
        // If it looks like a directory, keep it.
        // Heuristic: extensions imply files.
        val lastSegment = segments.lastOrNull()
        val directorySegments = when {
            lastSegment == null -> emptyList()
            lastSegment.contains('.') -> segments.dropLast(1)
            else -> segments
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

        val url = runCatching { Url(boardUrl) }.getOrElse { error ->
            val normalized = boardUrl
                .substringBefore('#')
                .substringBefore('?')
            val schemeSeparator = normalized.indexOf("://")
            if (schemeSeparator <= 0) {
                throw IllegalArgumentException("Invalid board URL: $boardUrl", error)
            }
            val scheme = normalized.substring(0, schemeSeparator)
            val remainder = normalized.substring(schemeSeparator + 3)
            val host = remainder.substringBefore('/')
            return "$scheme://$host"
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
}
