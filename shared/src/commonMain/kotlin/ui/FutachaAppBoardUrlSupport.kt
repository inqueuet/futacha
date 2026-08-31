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

internal fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            Logger.w(
                FUTACHA_APP_RUNTIME_LOG_TAG,
                "HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed"
            )
            trimmed
        }
        else -> "https://$trimmed"
    }

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
