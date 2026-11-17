package com.valoser.futacha.shared.ui.image

/** Keep a recoverable remote URL when an offline page exposes a temporary save-generation path. */
internal fun updatedHistoryThumbnailUrl(current: String?, candidate: String?): String? {
    val previous = current?.takeIf(String::isNotBlank)
    val updated = candidate?.takeIf(String::isNotBlank) ?: return previous
    fun String.isRemote() = startsWith("https://", true) || startsWith("http://", true)
    return if (!updated.isRemote() && previous?.isRemote() == true) previous else updated
}
