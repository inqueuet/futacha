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
        val sanitizedThreadId = sanitizeThreadId(threadId)
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

    private fun sanitizeThreadId(threadId: String): String {
        val trimmed = threadId.trim()
        // Only allow digits (thread IDs should be numeric)
        return if (trimmed.all { it.isDigit() }) {
            trimmed
        } else {
            ""
        }
    }

    fun resolveBoardBaseUrl(boardUrl: String): String {
        if (boardUrl.isBlank()) {
            throw IllegalArgumentException("Board URL cannot be blank")
        }

        val url = runCatching { Url(boardUrl) }.getOrNull()
        if (url == null) {
            println("BoardUrlResolver: Failed to parse URL '$boardUrl', using fallback")
            val fallback = boardUrl.substringBefore('#').substringBefore('?').trimEnd('/')
            if (fallback.isBlank()) {
                throw IllegalArgumentException("Invalid board URL: $boardUrl")
            }
            return fallback
        }
        val segments = url.encodedPath
            .split('/')
            .filter { it.isNotBlank() }
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
        }.trimEnd('/')
    }

}
