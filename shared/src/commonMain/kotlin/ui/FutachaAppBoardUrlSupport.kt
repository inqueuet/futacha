package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.Logger
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath

private const val FUTACHA_APP_RUNTIME_LOG_TAG = "FutachaApp"

internal fun resolveFutachaBoardRepository(
    board: BoardSummary,
    sharedRepository: BoardRepository
): BoardRepository? {
    return board.takeUnless { it.isMockBoard() }?.let { sharedRepository }
}

internal fun BoardSummary.isMockBoard(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

// A pasted board/thread page (futaba.htm, res/123.htm) names a document inside the board directory.
private val BOARD_URL_DOCUMENT_SUFFIX = Regex("(?i)/(?:futaba\\.html?|res/\\d+\\.html?)$")

/**
 * Turns `https://host/b/futaba.htm` or `https://host/b/res/123.htm` (query/fragment included) into the
 * board directory `https://host/b/`. A thread's own query/fragment is dropped with it. Other URLs are returned as is.
 */
private fun stripBoardUrlDocument(url: String): String {
    val path = url.substringBefore('#').substringBefore('?')
    if (!BOARD_URL_DOCUMENT_SUFFIX.containsMatchIn(path)) return url
    return path.replace(BOARD_URL_DOCUMENT_SUFFIX, "/")
}

internal fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = stripBoardUrlDocument(when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            Logger.w(
                FUTACHA_APP_RUNTIME_LOG_TAG,
                "HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed"
            )
            trimmed
        }
        else -> "https://$trimmed"
    })

    if (withScheme.contains("futaba.php", ignoreCase = true)) {
        return withScheme
    }

    return runCatching {
        val parsed = Url(withScheme)
        val normalizedPath = when {
            parsed.encodedPath.isBlank() || parsed.encodedPath == "/" -> "/futaba.php"
            parsed.encodedPath.endsWith("/") -> "${parsed.encodedPath}futaba.php"
            else -> "${parsed.encodedPath}/futaba.php"
        }
        URLBuilder(parsed).apply { encodedPath = normalizedPath }.buildString()
    }.getOrElse {
        val fragment = withScheme.substringAfter('#', missingDelimiterValue = "")
        val withoutFragment = withScheme.substringBefore('#')
        val base = withoutFragment.substringBefore('?').trimEnd('/')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        buildString {
            append(base)
            append("/futaba.php")
            if (query.isNotEmpty()) {
                append('?')
                append(query)
            }
            if (fragment.isNotEmpty()) {
                append('#')
                append(fragment)
            }
        }
    }
}
