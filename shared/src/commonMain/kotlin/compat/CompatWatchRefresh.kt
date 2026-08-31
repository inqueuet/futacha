package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.normalizeWatchWords

/**
 * One catalog item which matched the compatibility profile's watch words.
 * [isNew] is true only when the item was not already present in compatibility
 * history before this catalog pass.
 */
data class CompatWatchMatch(
    val history: CompatHistoryEntry,
    val isNew: Boolean
)

/**
 * Parse the legacy setting without changing the stored text.  The target APK
 * treats one non-empty line as one word and ignores duplicate/blank entries.
 */
fun parseCompatWatchWords(raw: String?): List<String> = normalizeWatchWords(
    raw.orEmpty().lineSequence().toList()
)

/**
 * Convert catalog matches into history entries.  The history entry is also
 * the source for the compatibility drawer's WATCHER page, so this keeps the
 * foreground refresh, background worker, and manual catalog refresh in sync.
 */
fun collectCompatWatchMatches(
    board: CompatBoard,
    items: List<CatalogItem>,
    watchWords: List<String>,
    existingHistory: List<CompatHistoryEntry>,
    nowEpochMillis: Long
): List<CompatWatchMatch> {
    val normalizedWords = normalizeWatchWords(watchWords)
    if (normalizedWords.isEmpty()) return emptyList()

    val existingByUrl = existingHistory.associateBy(CompatHistoryEntry::canonicalUrl)
    val seen = mutableSetOf<String>()
    return items.asSequence()
        .distinctBy { it.id.ifBlank { it.threadUrl } }
        .filter { item ->
            val title = item.title.orEmpty()
            val normalizedTitle = title
                .map { char ->
                    when (char) {
                        '\u3000' -> ' '
                        in '\uFF01'..'\uFF5E' -> char - 0xFEE0
                        else -> char
                    }
                }
                .joinToString("")
                .trim()
                .lowercase()
            normalizedWords.any { normalizedTitle.contains(it) }
        }
        .mapNotNull { item ->
            val parsed = canonicalizeThreadUrl(item.threadUrl) ?: return@mapNotNull null
            if (!seen.add(parsed.canonicalUrl)) return@mapNotNull null
            val previous = existingByUrl[parsed.canonicalUrl]
            CompatWatchMatch(
                history = CompatHistoryEntry(
                    canonicalUrl = parsed.canonicalUrl,
                    originalUrl = item.threadUrl,
                    boardKey = board.key,
                    boardName = board.name,
                    threadNo = parsed.threadNo,
                    title = item.title.orEmpty().ifBlank { "No.${parsed.threadNo}" },
                    thumbnailUrl = item.thumbnailUrl,
                    replyCount = item.replyCount,
                    contentUpdatedAtEpochMillis = nowEpochMillis,
                    scrollAnchor = previous?.scrollAnchor ?: ScrollAnchor()
                ),
                isNew = previous == null
            )
        }
        .toList()
}
