package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.mix.MixEntry
import com.valoser.futacha.shared.ui.compat.compatCatalogPhashRequestUrl
import com.valoser.futacha.shared.ui.compat.resolveCompatCatalogPhashUrl

/** Spend the shared budget on eligible pictures, round-robin in definition board order. */
internal fun planMixImageNgCandidates(
    entries: List<MixEntry>, rules: List<CompatNgRule>, boardOrder: List<String>, limit: Int = 256
): List<Pair<String, String>> {
    val queues = linkedMapOf<String, ArrayDeque<Pair<String, String>>>()
    boardOrder.forEach { queues.getOrPut(storeCanonicalBoardUrlOf(it) ?: it) { ArrayDeque() } }
    entries.forEach { entry ->
        val board = entry.key.boardUrl
        val scope = compatBoardKey(board)
        if (rules.none { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH && (it.scopeKey == "*" || it.scopeKey == scope) }) return@forEach
        val url = compatCatalogPhashRequestUrl(entry.item) ?: return@forEach
        queues.getOrPut(board) { ArrayDeque() }.addLast(entry.key.asString() to url)
    }
    val result = mutableListOf<Pair<String, String>>()
    while (result.size < limit && queues.values.any { it.isNotEmpty() }) {
        for (queue in queues.values) {
            if (result.size >= limit) break
            queue.removeFirstOrNull()?.let(result::add)
        }
    }
    return result
}

/** Futaba guesses JPEG from catalog thumbnails; confirm the attachment only after a missing-image response. */
internal suspend fun resolveMixMissingImageUrl(
    entry: MixEntry?, fetchOriginal: suspend (board: String, threadId: String) -> String?
): String? {
    entry ?: return null
    if (aimogeBoardShapeOf(entry.board.url) != null) return entry.item.thumbnailUrl
    return resolveCompatCatalogPhashUrl(entry.item.thumbnailUrl) {
        fetchOriginal(entry.board.url, entry.item.id)
    }
}
