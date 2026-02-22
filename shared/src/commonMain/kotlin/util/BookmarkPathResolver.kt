package com.valoser.futacha.shared.util

/**
 * Resolves iOS bookmark data to a local filesystem path for display usage.
 *
 * Android returns null because bookmark-based SaveLocation is iOS-only.
 */
internal expect fun resolveBookmarkPathForDisplay(bookmarkData: String): String?
