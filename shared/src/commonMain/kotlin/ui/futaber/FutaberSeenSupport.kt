package com.valoser.futacha.shared.ui.futaber

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** At most this many threads keep a "last seen" count; the one not opened for the longest drops first. */
internal const val FUTABER_MAX_SEEN_THREADS = 200

/** Under the 20,000 characters the settings store accepts for one value. */
private const val SEEN_STORED_LIMIT = 18_000
private const val SEEN_FIELD_SEPARATOR = '\n'

/**
 * How many posts of a thread the person had on screen when they last left it, kept apart from the history row's
 * count. That count (`replyCount`) is shared with the other modes and moves on whenever a background refresh or
 * another mode reads the thread, so it cannot say "what this person has seen": a thread refreshed in the
 * background would open with nothing marked new. Kept in this mode's own setting (`compat.futaber.seen`), so the
 * other modes' stored history is not touched.
 */
internal data class FutaberSeenCount(val boardId: String, val threadId: String, val seen: Int)

/** Oldest first, newest last. A damaged or unknown stored value reads as nothing seen rather than failing. */
internal fun decodeFutaberSeenCounts(stored: String?): List<FutaberSeenCount> {
    if (stored.isNullOrBlank()) return emptyList()
    val raw = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), stored) }
        .getOrNull() ?: return emptyList()
    val byKey = LinkedHashMap<Pair<String, String>, FutaberSeenCount>()
    raw.forEach { entry ->
        val parts = entry.split(SEEN_FIELD_SEPARATOR)
        val seen = parts.getOrNull(2)?.toIntOrNull()
        if (parts.size == 3 && parts[0].isNotBlank() && parts[1].isNotBlank() && seen != null && seen > 0) {
            val key = parts[0] to parts[1]
            // A repeated key keeps its latest place.
            byKey.remove(key)
            byKey[key] = FutaberSeenCount(parts[0], parts[1], seen)
        }
    }
    return byKey.values.toList().takeLast(FUTABER_MAX_SEEN_THREADS)
}

/** The stored text for [counts]; when it would not fit the stored limit, the oldest ones are dropped. */
internal fun encodeFutaberSeenCounts(counts: List<FutaberSeenCount>): String {
    var kept = counts.takeLast(FUTABER_MAX_SEEN_THREADS)
    while (true) {
        val text = Json.encodeToString(
            ListSerializer(String.serializer()),
            kept.map { "${it.boardId}$SEEN_FIELD_SEPARATOR${it.threadId}$SEEN_FIELD_SEPARATOR${it.seen}" }
        )
        if (text.length <= SEEN_STORED_LIMIT || kept.isEmpty()) return text
        kept = kept.drop(1)
    }
}

/** The posts seen the last time the thread was left, or null when none was recorded. */
internal fun futaberSeenCountFor(stored: String?, boardId: String, threadId: String): Int? =
    decodeFutaberSeenCounts(stored).lastOrNull { it.boardId == boardId && it.threadId == threadId }?.seen

/**
 * The stored value after [boardId]/[threadId] was left with [seen] posts on screen (the thread moves to the newest
 * place); null when the stored value already says so, so nothing needs writing.
 */
internal fun futaberSeenCountsAfter(stored: String?, boardId: String, threadId: String, seen: Int): String? {
    if (seen <= 0 || boardId.isBlank() || threadId.isBlank()) return null
    val current = decodeFutaberSeenCounts(stored)
    if (current.lastOrNull()?.let { it.boardId == boardId && it.threadId == threadId && it.seen == seen } == true) return null
    // Same place as before for a thread already newest; otherwise it moves to the end.
    val next = current.filterNot { it.boardId == boardId && it.threadId == threadId } +
        FutaberSeenCount(boardId, threadId, seen)
    return encodeFutaberSeenCounts(next)
}
