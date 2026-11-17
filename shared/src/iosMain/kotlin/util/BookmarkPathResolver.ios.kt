@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.withContext
import platform.Foundation.*

private val mediaBookmarkLock = NSLock()
private val mediaBookmarkDirectories = linkedMapOf<String, NSURL>()

/**
 * Keep the security-scoped URL, not just its display path, while a saved thread is open.
 * May resolve a remembered bookmark (a file-system / File Provider round trip): call it off the
 * main thread, or use [resolveBookmarkedMediaDirectory] from UI code.
 */
internal fun bookmarkedMediaDirectoryForPath(path: String): NSURL? {
    registeredMediaDirectoryForPath(path)?.let { return it }
    // A path kept from an earlier process (restored page, history) whose bookmark this
    // process has not resolved yet: resolve the remembered bookmark of that folder now.
    val bookmark = persistedMediaBookmarkForPath(path) ?: return null
    val resolved = resolveBookmarkPathUncached(bookmark)?.let { registeredMediaDirectoryForPath(path) }
    if (resolved == null) {
        // Also when it resolves to another folder (moved or renamed): this path can never match
        // it in this process, so it is not resolved again on every call.
        mediaBookmarkLock.lock()
        try { unresolvablePersistedBookmarks.add(bookmark) } finally { mediaBookmarkLock.unlock() }
    }
    return resolved
}

/** [bookmarkedMediaDirectoryForPath] for UI callers: a needed bookmark resolution runs on the IO dispatcher (G4-5). */
internal suspend fun resolveBookmarkedMediaDirectory(path: String): NSURL? =
    registeredMediaDirectoryForPath(path) ?: withContext(AppDispatchers.io) { bookmarkedMediaDirectoryForPath(path) }

private fun registeredMediaDirectoryForPath(path: String): NSURL? {
    mediaBookmarkLock.lock()
    return try {
        mediaBookmarkDirectories.entries
            .filter { path.startsWith(it.key.trimEnd('/') + "/") }
            .maxByOrNull { it.key.length }?.value
    } finally { mediaBookmarkLock.unlock() }
}

/** Folder path -> its bookmark, so a later process can regain access from a path alone. */
private const val PERSISTED_MEDIA_BOOKMARKS_KEY = "futacha.mediaBookmarkDirectories.v1"
private const val PERSISTED_MEDIA_BOOKMARK_MAX_CHARS = 64 * 1024
private const val MEDIA_BOOKMARK_MAX_DIRECTORIES = 32
private val unresolvablePersistedBookmarks = mutableSetOf<String>()
/** Serializes the read-modify-write of the persisted dictionary across resolving threads (B4-3). */
private val persistedMediaBookmarkLock = NSLock()

private fun persistedMediaBookmarks(): Map<String, String> =
    NSUserDefaults.standardUserDefaults().dictionaryForKey(PERSISTED_MEDIA_BOOKMARKS_KEY).orEmpty().entries
        .mapNotNull { (key, value) -> (key as? String)?.let { k -> (value as? String)?.let { k to it } } }
        .toMap()

private fun persistedMediaBookmarkForPath(path: String): String? {
    val bookmark = persistedMediaBookmarks().entries
        .filter { path.startsWith(it.key.trimEnd('/') + "/") }
        .maxByOrNull { it.key.length }?.value ?: return null
    mediaBookmarkLock.lock()
    return try { bookmark.takeUnless { it in unresolvablePersistedBookmarks } } finally { mediaBookmarkLock.unlock() }
}

private fun rememberMediaBookmark(path: String, bookmark: String) {
    if (bookmark.length > PERSISTED_MEDIA_BOOKMARK_MAX_CHARS) return
    persistedMediaBookmarkLock.lock()
    try {
        val current = persistedMediaBookmarks()
        if (current[path] == bookmark) return
        val updated = LinkedHashMap(current - path)
        while (updated.size >= MEDIA_BOOKMARK_MAX_DIRECTORIES) updated.remove(updated.keys.first())
        updated[path] = bookmark
        NSUserDefaults.standardUserDefaults().setObject(updated.toMap<Any?, Any?>(), forKey = PERSISTED_MEDIA_BOOKMARKS_KEY)
    } finally { persistedMediaBookmarkLock.unlock() }
}

/** Test hook: [rememberMediaBookmark] as concurrent resolutions of different folders call it. */
internal fun rememberMediaBookmarkForTest(path: String, bookmark: String) = rememberMediaBookmark(path, bookmark)
internal fun persistedMediaBookmarksForTest(): Map<String, String> = persistedMediaBookmarks()
internal fun replacePersistedMediaBookmarksForTest(bookmarks: Map<String, String>) {
    NSUserDefaults.standardUserDefaults().setObject(bookmarks.toMap<Any?, Any?>(), forKey = PERSISTED_MEDIA_BOOKMARKS_KEY)
}

/** Test hook: forget what this process resolved, as a new launch would. */
internal fun forgetResolvedMediaBookmarksForTest() {
    mediaBookmarkLock.lock()
    try {
        mediaBookmarkDirectories.clear()
        resolvedBookmarkPaths.clear()
        unresolvablePersistedBookmarks.clear()
    } finally { mediaBookmarkLock.unlock() }
}

private const val RESOLVED_BOOKMARK_CACHE_TTL_SECONDS = 30.0
private const val RESOLVED_BOOKMARK_CACHE_MAX_ENTRIES = 8

private class ResolvedBookmarkPath(val path: String?, val resolvedAt: Double)

/**
 * Opening a saved thread converts every media path of every post, and each
 * conversion used to resolve the same bookmark again (a file-system round trip).
 * The display path of one bookmark is reused for a short time instead.
 */
private val resolvedBookmarkPaths = linkedMapOf<String, ResolvedBookmarkPath>()

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal actual fun resolveBookmarkPathForDisplay(bookmarkData: String): String? {
    if (bookmarkData.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS) return null
    val normalized = bookmarkData.trim()
        .replace("\n", "")
        .replace("\r", "")
    if (normalized.isEmpty() || normalized.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS) return null
    val now = NSDate().timeIntervalSince1970
    mediaBookmarkLock.lock()
    try {
        resolvedBookmarkPaths[normalized]?.let { cached ->
            val age = now - cached.resolvedAt
            if (age >= 0.0 && age < RESOLVED_BOOKMARK_CACHE_TTL_SECONDS && cached.path != null) {
                return cached.path
            }
        }
    } finally { mediaBookmarkLock.unlock() }
    val resolved = resolveBookmarkPathUncached(normalized)
    mediaBookmarkLock.lock()
    try {
        resolvedBookmarkPaths.remove(normalized)
        if (resolved != null) {
            resolvedBookmarkPaths[normalized] = ResolvedBookmarkPath(resolved, now)
            while (resolvedBookmarkPaths.size > RESOLVED_BOOKMARK_CACHE_MAX_ENTRIES) {
                resolvedBookmarkPaths.remove(resolvedBookmarkPaths.keys.first())
            }
        }
    } finally { mediaBookmarkLock.unlock() }
    return resolved
}

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private fun resolveBookmarkPathUncached(normalized: String): String? {

    fun decode(candidate: String): ByteArray? {
        return runCatching { Base64.decode(candidate) }.getOrNull()
    }

    val standard = normalized
        .replace('-', '+')
        .replace('_', '/')
    val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
    val bytes = decode(normalized)
        ?: decode(standard)
        ?: decode(padded)
        ?: return null
    if (bytes.isEmpty()) return null

    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }

    return memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val isStale = alloc<BooleanVar>()
        val url = NSURL.URLByResolvingBookmarkData(
            data,
            options = NSURLBookmarkResolutionWithSecurityScope,
            relativeToURL = null,
            bookmarkDataIsStale = isStale.ptr,
            error = error.ptr
        ) ?: return@memScoped null

        val started = url.startAccessingSecurityScopedResource()
        try {
            url.path?.let { path ->
                mediaBookmarkLock.lock()
                try {
                    mediaBookmarkDirectories.remove(path)
                    mediaBookmarkDirectories[path] = url
                    while (mediaBookmarkDirectories.size > MEDIA_BOOKMARK_MAX_DIRECTORIES) {
                        mediaBookmarkDirectories.remove(mediaBookmarkDirectories.keys.first())
                    }
                } finally { mediaBookmarkLock.unlock() }
                rememberMediaBookmark(path, normalized)
            }
            url.path ?: url.absoluteString?.removePrefix("file://")
        } finally {
            if (started) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}
