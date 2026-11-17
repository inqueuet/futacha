package com.valoser.futacha.shared.watch

/**
 * Encodes [snapshot] so its UTF-8 payload fits [maxBytes], trimming instead of
 * dropping the whole update: preview posts are removed first (oldest threads
 * first), then threads from the end, then boards. Returns null only when even
 * the empty snapshot does not fit.
 */
fun encodeWatchSnapshotWithinPayload(
    snapshot: WatchSnapshot,
    maxBytes: Int,
    encode: (WatchSnapshot) -> String
): String? {
    fun fits(candidate: WatchSnapshot): String? =
        encode(candidate).takeIf { it.encodeToByteArray().size <= maxBytes }

    fits(snapshot)?.let { return it }

    var threads = snapshot.threads
    fun current(): WatchSnapshot = snapshot.copy(
        threads = threads,
        unreadTotal = threads.sumOf { it.newReplyCount.toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        watchMatchTotal = threads.count { it.isWatchWordMatch }
    )

    for (index in threads.indices.reversed()) {
        if (threads[index].previewPosts.isEmpty()) continue
        threads = threads.toMutableList().also { it[index] = it[index].copy(previewPosts = emptyList()) }
        fits(current())?.let { return it }
    }
    while (threads.isNotEmpty()) {
        threads = threads.dropLast(1)
        fits(current())?.let { return it }
    }
    var boards = snapshot.boards
    while (boards.isNotEmpty()) {
        boards = boards.dropLast(1)
        fits(current().copy(boards = boards))?.let { return it }
    }
    return null
}
