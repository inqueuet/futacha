package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.formatLastVisited
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.board.formatSize
import kotlin.io.encoding.Base64
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.encoding.ExperimentalEncodingApi

/** What the saved box needs to show and handle its MHT files. */
internal data class FutaberMhtBox(
    /** False where files cannot be kept (no file system), so the section is left out. */
    val available: Boolean = false,
    val entries: List<FutaberMhtEntry> = emptyList(),
    /** Set while a file is being read or taken in; the buttons wait. */
    val busyMessage: String? = null,
    val onOpen: (FutaberMhtEntry) -> Unit = {},
    val onShare: (FutaberMhtEntry) -> Unit = {},
    val onDelete: (FutaberMhtEntry) -> Unit = {},
    val onImport: () -> Unit = {}
)

/** "154KB 1項目の合計", as the original app words it; a note replaces it while something is being done. */
internal fun futaberMhtSummary(entries: List<FutaberMhtEntry>, busyMessage: String?): String =
    busyMessage ?: "MHTファイル　${formatSize(entries.sumOf { it.sizeBytes })} ${entries.size}項目の合計"

/** Right of a row's second line: when it was saved, and how big the file is. */
internal fun futaberMhtDetail(entry: FutaberMhtEntry): String =
    (if (entry.savedAtMillis > 0) formatLastVisited(entry.savedAtMillis) + "  " else "") + formatSize(entry.sizeBytes)

/**
 * The thumbnail kept in the file's header, as an address the image loader can show. The address is built once per thumbnail
 * (see [FutaberMhtThumbnailUriCache]) so that a recomposition or a scroll does not encode every row's picture again.
 */
internal fun futaberMhtThumbnailUri(entry: FutaberMhtEntry): String = futaberMhtThumbnailUriCache.uriOf(entry) ?: ""

internal const val FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE = 100

/** Encodes thumbnail bytes as a data address (the work the cache saves). */
@OptIn(ExperimentalEncodingApi::class)
internal fun futaberMhtEncodeThumbnailUri(bytes: ByteArray): String {
    val mime = FutaberMhtLibrary.mimeOf("", bytes)
    return "data:$mime;base64," + Base64.Default.encode(bytes)
}

/**
 * A least-recently-used memory cache of the data addresses made from the thumbnails. A key is the file name, the time it was
 * saved and the thumbnail's size and content hash, so a file saved again or deleted and replaced never gets an old picture, while
 * the same thumbnail always gets the same text (the image loader uses the whole address as its key). Safe to call from any thread:
 * the map is only touched under a short spin lock, and the encoding is done outside it.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class FutaberMhtThumbnailUriCache(
    private val capacity: Int = FUTABER_MHT_THUMBNAIL_URI_CACHE_SIZE,
    private val encode: (ByteArray) -> String = ::futaberMhtEncodeThumbnailUri
) {
    private data class Key(val fileName: String, val savedAtMillis: Long, val size: Int, val contentHash: Int)

    private val lock = AtomicInt(0)
    private val map = LinkedHashMap<Key, String>()

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) { /* spin: the guarded work is a few map operations */ }
        try {
            return block()
        } finally {
            lock.store(0)
        }
    }

    /** The data address of the entry's thumbnail, or null when the entry has none. */
    fun uriOf(entry: FutaberMhtEntry): String? {
        val bytes = entry.thumbnail ?: return null
        val key = Key(entry.fileName, entry.savedAtMillis, bytes.size, bytes.contentHashCode())
        locked {
            val hit = map.remove(key)
            if (hit != null) {
                map[key] = hit // most recently used goes last
                return hit
            }
        }
        val uri = encode(bytes)
        locked {
            map.remove(key)
            map[key] = uri
            while (map.size > capacity) {
                val eldest = map.keys.iterator().next()
                map.remove(eldest)
            }
        }
        return uri
    }

    internal fun sizeForTest(): Int = locked { map.size }

    internal fun clear() = locked { map.clear() }
}

private val futaberMhtThumbnailUriCache = FutaberMhtThumbnailUriCache()

/** The id prefix of the stand-in board made for a file whose board is not registered. */
internal const val FUTABER_MHT_STANDIN_BOARD_PREFIX = "mht:"

/**
 * Whether [board] is a stand-in made by [futaberBoardForMht] (the file's board is not one of the registered boards). A stand-in only
 * labels the page: a reply or a new thread cannot be posted to it, since the board is not known to the app.
 */
internal fun futaberMhtBoardIsStandIn(board: BoardSummary): Boolean = board.id.startsWith(FUTABER_MHT_STANDIN_BOARD_PREFIX)

/** True when something may be posted to the board of an opened file: the board is a registered one, not a stand-in. */
internal fun futaberMhtCanPost(board: BoardSummary): Boolean = !futaberMhtBoardIsStandIn(board)

/**
 * The board an opened MHT file belongs to: a registered board with the same key or address, else a stand-in
 * made from what the file says (so a file from another device or tool can still be read). Nothing is
 * registered by this; the stand-in only labels the page.
 */
internal fun futaberBoardForMht(thread: FutaberMhtThread, boards: List<BoardSummary>): BoardSummary {
    fun normalize(url: String) = url.trim().substringAfter("://").trimEnd('/').lowercase()
    thread.boardKey?.let { key -> boards.firstOrNull { futaberCompatBoardKey(it) == key || it.id == key }?.let { return it } }
    thread.boardUrl?.let { url -> boards.firstOrNull { normalize(it.url) == normalize(url) }?.let { return it } }
    // The address comes from the file: only a Futaba host's address is used for the stand-in, so a file cannot name another host as its board.
    val url = (thread.boardUrl ?: thread.threadUrl?.substringBefore("/res/")?.plus("/"))
        ?.takeIf { FutaberMhtLibrary.isOfficialMediaUrl(it) } ?: "https://invalid.local/"
    return BoardSummary(
        id = FUTABER_MHT_STANDIN_BOARD_PREFIX + normalize(url),
        name = thread.boardName ?: normalize(url).substringBefore('/'),
        category = "",
        url = url,
        description = ""
    )
}
