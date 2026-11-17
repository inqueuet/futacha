package com.valoser.futacha

internal const val MAX_PLATFORM_DEEP_LINK_CHARS = 8_192

internal fun String?.boundedPlatformDeepLinkOrNull(): String? =
    this?.takeIf { it.length in 1..MAX_PLATFORM_DEEP_LINK_CHARS }

internal fun isTrustedFutabaDeepLinkHost(host: String?): Boolean {
    val normalized = host?.lowercase() ?: return false
    return normalized == "2chan.net" || normalized.endsWith(".2chan.net")
}

/**
 * Activity-owned inbox for platform navigation intents.
 *
 * Each recognized channel is last-wins. Consumption is compare-and-clear so a
 * callback for an older Compose effect cannot erase a newer onNewIntent value.
 */
internal data class PendingPlatformDeepLinks(
    val ai: String? = null,
    val thread: String? = null
) {
    val hasAny: Boolean get() = ai != null || thread != null

    fun withIncoming(ai: String?, thread: String?): PendingPlatformDeepLinks = copy(
        ai = ai.boundedPlatformDeepLinkOrNull() ?: this.ai,
        thread = thread.boundedPlatformDeepLinkOrNull() ?: this.thread
    )

    fun consumeAi(value: String): PendingPlatformDeepLinks =
        if (ai == value) copy(ai = null) else this

    fun consumeThread(value: String): PendingPlatformDeepLinks =
        if (thread == value) copy(thread = null) else this
}
