package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

/**
 * Represents a location where files can be saved.
 * Supports three forms:
 * - Path: Traditional file system path (e.g., "/storage/emulated/0/DCIM")
 * - TreeUri: Android SAF document tree URI (e.g., "content://...")
 * - Bookmark: iOS secure bookmark data (serialized URL bookmark)
 */
@Serializable
sealed class SaveLocation {
    /**
     * Traditional absolute file system path.
     * Works on both Android (primary storage) and iOS.
     */
    @Serializable
    data class Path(val path: String) : SaveLocation()

    /**
     * Android SAF (Storage Access Framework) document tree URI.
     * Enables write access to external storage, SD cards, and third-party file providers.
     * The URI string can be persisted and reused via takePersistableUriPermission.
     */
    @Serializable
    data class TreeUri(val uri: String) : SaveLocation()

    /**
     * iOS secure bookmark data.
     * Enables persistent access to user-selected directories across app restarts.
     * The bookmarkData is a Base64-encoded string of the bookmark bytes.
     */
    @Serializable
    data class Bookmark(val bookmarkData: String) : SaveLocation()

    companion object {
        /**
         * Creates a SaveLocation from a raw string, attempting to infer the type.
         * - If starts with "content://", creates TreeUri
         * - If contains base64-like characters and length > 200, creates Bookmark
         * - Otherwise creates Path
         */
        fun fromString(raw: String): SaveLocation {
            return when {
                raw.startsWith("content://") -> TreeUri(raw)
                raw.length > 200 && raw.all { it.isLetterOrDigit() || it in "+/=" } -> Bookmark(raw)
                else -> Path(raw)
            }
        }

        /**
         * Extracts the raw string representation for persistence.
         */
        fun SaveLocation.toRawString(): String = when (this) {
            is Path -> path
            is TreeUri -> uri
            is Bookmark -> bookmarkData
        }
    }
}
