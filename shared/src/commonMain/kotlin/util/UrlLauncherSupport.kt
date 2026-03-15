package com.valoser.futacha.shared.util

internal enum class UrlLaunchTarget {
    Browser,
    Mail
}

internal data class UrlLaunchRequest(
    val normalizedUrl: String,
    val target: UrlLaunchTarget
)

internal fun resolveUrlLaunchRequest(url: String): UrlLaunchRequest? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val scheme = trimmed.substringBefore(':', "").lowercase()
    if (scheme.isBlank()) {
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
