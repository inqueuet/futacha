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
private val refusedCustomUrlSchemes = setOf("javascript", "file", "data", "content", "intent", "blob", "vbscript")
private val allowedExternalUrlSchemes = setOf("http", "https", "mailto", "futaba")

internal fun resolveUrlLaunchRequest(url: String, allowAnyScheme: Boolean = false): UrlLaunchRequest? {
    val trimmed = url.trim()
    if (
        trimmed.isEmpty() || trimmed.length > MAX_EXTERNAL_URL_LENGTH ||
        trimmed.any { it.code < 0x20 || it.code == 0x7f }
    ) {
        return null
    }

    val scheme = trimmed.substringBefore(':', "").lowercase()
    // allowAnyScheme (L-B12, C05): the person's own URL scheme (「chromeで開く」...); scripts, local files and data stay refused.
    val customOk = allowAnyScheme && scheme.isNotEmpty() && scheme.length <= 32 &&
        scheme.all { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' } &&
        scheme[0] in 'a'..'z' && scheme !in refusedCustomUrlSchemes
    if (scheme !in allowedExternalUrlSchemes && !customOk) {
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
