package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

internal object BoardUrlResolver {
    fun resolveCatalogUrl(boardUrl: String, mode: CatalogMode): String {
        return URLBuilder().apply {
            takeFrom(boardUrl)
            parameters.remove("mode")
            parameters.remove("sort")
            parameters.append("mode", "cat")
            mode.sortParam?.let { parameters.append("sort", it) }
        }.buildString()
    }

    fun resolveThreadUrl(boardUrl: String, threadId: String): String {
        val base = resolveBoardBaseUrl(boardUrl)
        val sanitizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        return buildString {
            append(sanitizedBase)
            if (sanitizedBase.isNotEmpty()) append('/')
            append("res/")
            append(threadId.trim())
            append(".htm")
        }
    }

    fun resolveBoardBaseUrl(boardUrl: String): String {
        val url = runCatching { Url(boardUrl) }.getOrNull()
        if (url == null) {
            return boardUrl.substringBefore('#').substringBefore('?').trimEnd('/')
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
