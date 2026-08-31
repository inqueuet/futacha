package com.valoser.futacha.shared.util

import io.ktor.http.Url

internal enum class UrlLaunchTarget {
    Browser,
    Mail
}

internal data class UrlLaunchRequest(
    val normalizedUrl: String,
    val target: UrlLaunchTarget
)

private const val MAX_EXTERNAL_URL_LENGTH = 8_192
private val allowedExternalUrlSchemes = setOf("http", "https", "mailto", "futaba")

internal fun resolveUrlLaunchRequest(url: String): UrlLaunchRequest? {
    val trimmed = url.trim()
    if (
        trimmed.isEmpty() || trimmed.length > MAX_EXTERNAL_URL_LENGTH ||
        trimmed.any { it.code < 0x20 || it.code == 0x7f }
    ) {
        return null
    }

    val scheme = trimmed.substringBefore(':', "").lowercase()
    if (scheme !in allowedExternalUrlSchemes) {
        return null
    }

    val target = if (scheme == "mailto") {
        UrlLaunchTarget.Mail
    } else {
        UrlLaunchTarget.Browser
    }
    return UrlLaunchRequest(
        normalizedUrl = trimmed,
        target = target
    )
}

internal fun describeUrlForLog(url: String): String {
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore(':', "")
        .lowercase()
        .takeIf { candidate ->
            candidate.isNotEmpty() && candidate.length <= 32 &&
                candidate.first().isLetter() &&
                candidate.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        }
        ?: "unknown"
    val host = if (scheme == "http" || scheme == "https") {
        runCatching { Url(trimmed).host.lowercase().take(255) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return buildString {
        append("scheme=")
        append(scheme)
        host?.let {
            append(", host=")
            append(it)
        }
        append(", length=")
        append(url.length)
    }
}

internal fun describeFailureForLog(error: Throwable): String =
    error::class.simpleName
        ?.take(96)
        ?.takeIf { it.isNotBlank() }
        ?: "Throwable"
