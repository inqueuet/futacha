package com.valoser.futacha.shared.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
        private const val PATH_PREFIX = "path:"
        private const val TREE_URI_PREFIX = "tree:"
        private const val BOOKMARK_PREFIX = "bookmark:"

        /**
         * Creates a SaveLocation from a raw string, attempting to infer the type.
         * - If prefixed with "path:", "tree:", or "bookmark:", uses that explicit type
         * - If starts with "content://", creates TreeUri (legacy)
         * - If it looks like an NSURL bookmark payload, creates Bookmark (legacy)
         * - Otherwise creates Path
         */
        fun fromString(raw: String): SaveLocation {
            val normalized = raw.trim()
            return when {
                normalized.startsWith(PATH_PREFIX, ignoreCase = true) ->
                    Path(normalized.removePrefixIgnoreCase(PATH_PREFIX))
                normalized.startsWith(TREE_URI_PREFIX, ignoreCase = true) ->
                    TreeUri(normalized.removePrefixIgnoreCase(TREE_URI_PREFIX))
                normalized.startsWith(BOOKMARK_PREFIX, ignoreCase = true) ->
                    Bookmark(normalized.removePrefixIgnoreCase(BOOKMARK_PREFIX))
                normalized.startsWith("content://") -> TreeUri(normalized)
                looksLikeLegacyBookmark(normalized) -> Bookmark(normalized)
                else -> Path(normalized)
            }
        }

        /**
         * Extracts the raw string representation for persistence.
         */
        fun SaveLocation.toRawString(): String = when (this) {
            is Path -> path
            is TreeUri -> "$TREE_URI_PREFIX$uri"
            is Bookmark -> "$BOOKMARK_PREFIX$bookmarkData"
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun looksLikeLegacyBookmark(raw: String): Boolean {
            if (raw.isBlank()) return false
            val decoded = decodeBase64Lenient(raw) ?: return false
            if (decoded.size < 8) return false
            val isBookmarkHeader = decoded.startsWithAscii("book")
            val isBinaryPlist = decoded.startsWithAscii("bplist00")
            return isBookmarkHeader || isBinaryPlist
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun decodeBase64Lenient(raw: String): ByteArray? {
            val normalized = raw
                .trim()
                .replace("\n", "")
                .replace("\r", "")
            if (normalized.isEmpty()) return null

            fun decode(candidate: String): ByteArray? = runCatching { Base64.decode(candidate) }.getOrNull()

            decode(normalized)?.let { return it }

            val standardAlphabet = normalized
                .replace('-', '+')
                .replace('_', '/')
            decode(standardAlphabet)?.let { return it }

            val padded = standardAlphabet + "=".repeat((4 - standardAlphabet.length % 4) % 4)
            return decode(padded)
        }

        private fun ByteArray.startsWithAscii(prefix: String): Boolean {
            if (size < prefix.length) return false
            return prefix.indices.all { index ->
                this[index] == prefix[index].code.toByte()
            }
        }

        private fun String.removePrefixIgnoreCase(prefix: String): String {
            if (!startsWith(prefix, ignoreCase = true)) return this
            return substring(prefix.length)
        }
    }
}
