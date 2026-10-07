package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.compatCatalogManagementDisplayValue
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.normalizeCatalogSearchText

/** Keys of ふたばー-only settings. The store accepts only `compat.`-prefixed keys. */
internal object FutaberPreferenceKeys {
    const val CATALOG_DISPLAY_STYLE = "compat.futaber.catalog.displayStyle"
    const val CATALOG_SORT = "compat.futaber.catalog.sort"
    const val THEME = "compat.futaber.theme"
    const val LAST_BOARD_ID = "compat.futaber.lastBoardId"
    const val TABS = "compat.futaber.tabs"
    const val DRAFTS = "compat.futaber.drafts"
    const val FAVORITES = "compat.futaber.favorites"
    /** The posts of each thread this person had seen when they last left it (see [FutaberSeenCount]). */
    const val SEEN = "compat.futaber.seen"
    const val POST_NAME = "compat.futaber.post.name"
    const val POST_EMAIL = "compat.futaber.post.email"
    const val POST_CONFIRM = "compat.futaber.post.confirm"
}

/** The six catalog layouts offered by the display-style menu. */
internal enum class FutaberCatalogDisplayStyle(
    val persistedValue: String,
    val label: String,
    /** Grid column count, or null for the single-column row layouts. */
    val columns: Int?,
    /** Title lines shown under (grid) or beside (row) the thumbnail. */
    val titleLines: Int
) {
    Row1("row1", "行1", null, 1),
    Row2("row2", "行2", null, 2),
    Grid3("grid3", "横3", 3, 1),
    Grid4("grid4", "横4", 4, 1),
    Grid6("grid6", "横6", 6, 1),
    Grid8("grid8", "横8", 8, 1);

    val isGrid: Boolean get() = columns != null

    companion object {
        val default = Grid4

        fun fromPersistedValue(value: String?): FutaberCatalogDisplayStyle =
            entries.firstOrNull { it.persistedValue == value } ?: default
    }
}

/** The sort menu entries and the existing catalog mode each one fetches. */
internal data class FutaberCatalogSort(val label: String, val mode: CatalogMode)

internal val futaberCatalogSorts: List<FutaberCatalogSort> = listOf(
    FutaberCatalogSort("通常", CatalogMode.Catalog),
    FutaberCatalogSort("新順", CatalogMode.New),
    FutaberCatalogSort("古順", CatalogMode.Old),
    FutaberCatalogSort("多順", CatalogMode.Many),
    FutaberCatalogSort("少順", CatalogMode.Few),
    FutaberCatalogSort("勢い", CatalogMode.Momentum)
)

internal val futaberDefaultCatalogMode: CatalogMode = CatalogMode.Catalog

internal fun futaberCatalogModeFromPersisted(value: String?): CatalogMode =
    futaberCatalogSorts.firstOrNull { it.mode.name == value }?.mode ?: futaberDefaultCatalogMode

/** Title match, ignoring width, case and kana type; a blank query keeps every thread. */
internal fun filterFutaberCatalog(items: List<CatalogItem>, query: String): List<CatalogItem> {
    val needle = normalizeCatalogSearchText(query)
    if (needle.isEmpty()) return items
    return items.filter { normalizeCatalogSearchText(it.title.orEmpty()).contains(needle) }
}

/** The titles of [items] folded the way the search folds them, one per item; computed once, then reused for every typed word. */
internal fun futaberCatalogTitleKeys(items: List<CatalogItem>): List<String> =
    items.map { normalizeCatalogSearchText(it.title.orEmpty()) }

/** [filterFutaberCatalog] over titles already folded ([titleKeys], one per item): the same threads, nothing folded again. */
internal fun filterFutaberCatalogByKeys(items: List<CatalogItem>, titleKeys: List<String>, query: String): List<CatalogItem> {
    val needle = normalizeCatalogSearchText(query)
    if (needle.isEmpty()) return items
    return items.filterIndexed { index, _ -> titleKeys[index].contains(needle) }
}

/** Drops the threads whose title contains a catalog NG word (compared the way the search compares). */
internal fun futaberApplyCatalogNg(items: List<CatalogItem>, words: List<String>): List<CatalogItem> {
    val needles = words.map(::normalizeCatalogSearchText).filter { it.isNotEmpty() }
    if (needles.isEmpty()) return items
    return items.filterNot { item ->
        val title = normalizeCatalogSearchText(item.title.orEmpty())
        needles.any { title.contains(it) }
    }
}

/**
 * [text] cut to at most [max] chars without leaving half of a surrogate pair at the end (an emoji or a rare
 * kanji would otherwise be split into a lone surrogate, which shows as a broken glyph and cannot be encoded).
 */
internal fun futaberSafeTake(text: String, max: Int): String {
    if (max <= 0) return ""
    if (text.length <= max) return text
    val end = if (text[max - 1].isHighSurrogate() && text[max].isLowSurrogate()) max - 1 else max
    return text.substring(0, end)
}

/**
 * The catalog as shown: the catalog NG words of ふたちゃ, the NG threads / words / images of the other modes
 * ([index]) and the threads whose picture looks like a registered NG image ([hiddenIds], found by hashing) left out.
 */
internal fun futaberApplyCatalogRules(
    items: List<CatalogItem>,
    words: List<String>,
    index: CompatCatalogRuleIndex?,
    hiddenIds: Set<String> = emptySet()
): List<CatalogItem> {
    val listed = futaberApplyCatalogNg(items, words)
    val byRules = if (index == null) listed else listed.filterNot(index::hides)
    return if (hiddenIds.isEmpty()) byRules else byRules.filterNot { it.id in hiddenIds }
}

/** `https://may.2chan.net/b/` -> `may.2chan.net/b` for the title bar. */
internal fun futaberBoardAddress(boardUrl: String): String =
    boardUrl.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Catalog title with a placeholder so a thread without a title is still tappable. */
internal fun futaberCatalogTitle(item: CatalogItem): String =
    item.title?.trim()?.takeIf { it.isNotEmpty() } ?: "(無題)"


/**
 * The catalogs shown before the last reloads ("更新前に戻す") and the threads that dropped out of them ("消えたスレ").
 * Both are kept only while the catalog of one board and sort stays open: the lists of different sorts are not comparable.
 */
internal data class FutaberCatalogHistory(
    /** Newest first; at most [FUTABER_CATALOG_EARLIER_MAX]. */
    val earlier: List<List<CatalogItem>> = emptyList(),
    /** Threads that were in an earlier list and are not in the latest one, newest first. */
    val dropped: List<CatalogItem> = emptyList()
)

internal const val FUTABER_CATALOG_EARLIER_MAX = 4
internal const val FUTABER_CATALOG_DROPPED_MAX = 50

/** What the history becomes when [loaded] replaces [shown] on screen. A reload that changed nothing leaves it as it was. */
internal fun futaberCatalogHistoryAfterLoad(
    history: FutaberCatalogHistory,
    shown: List<CatalogItem>,
    loaded: List<CatalogItem>
): FutaberCatalogHistory {
    if (shown.isEmpty()) return history
    if (shown.map { it.id } == loaded.map { it.id }) return history
    val loadedIds = loaded.mapTo(HashSet()) { it.id }
    val vanished = shown.filter { it.id !in loadedIds }
    return FutaberCatalogHistory(
        earlier = (listOf(shown) + history.earlier).take(FUTABER_CATALOG_EARLIER_MAX),
        // A thread that came back is no longer "gone".
        dropped = (vanished + history.dropped).filter { it.id !in loadedIds }.distinctBy { it.id }.take(FUTABER_CATALOG_DROPPED_MAX)
    )
}

/** Goes back one reload: the list to show and the history without it; null when there is nothing to go back to. */
internal fun futaberCatalogGoBack(history: FutaberCatalogHistory): Pair<List<CatalogItem>, FutaberCatalogHistory>? {
    val previous = history.earlier.firstOrNull() ?: return null
    // The threads that dropped out of the list being left are on screen again, so they are no longer "gone".
    val shownIds = previous.mapTo(HashSet()) { it.id }
    return previous to history.copy(
        earlier = history.earlier.drop(1),
        dropped = history.dropped.filter { it.id !in shownIds }
    )
}

/** What identifies one catalog fetch: the same key means the catalog on hand is the one that would be fetched. */
internal data class FutaberCatalogFetchKey(
    val boardUrl: String,
    val sortMode: CatalogMode,
    val refreshTick: Int,
    val refreshSignal: Int
)

/** How long the catalog kept while a thread was open counts as current when coming back (no refetch, no spinner). */
internal const val FUTABER_CATALOG_REUSE_MILLIS = 5L * 60L * 1000L

/**
 * Coming back from a thread keeps the catalog that is on hand: the same fetch, with items, and not older than
 * [FUTABER_CATALOG_REUSE_MILLIS]. Anything else (another board or sort, a refresh, an old list) fetches again.
 */
internal fun futaberCatalogCanReuse(
    heldKey: FutaberCatalogFetchKey?,
    key: FutaberCatalogFetchKey,
    hasItems: Boolean,
    ageMillis: Long
): Boolean = hasItems && heldKey == key && ageMillis in 0 until FUTABER_CATALOG_REUSE_MILLIS

/** True when a fetch with [key] after one with [previous] was asked for by the person (a refresh), not a first load. */
internal fun futaberCatalogIsExplicitRefresh(previous: FutaberCatalogFetchKey?, key: FutaberCatalogFetchKey): Boolean =
    previous != null && (previous.refreshTick != key.refreshTick || previous.refreshSignal != key.refreshSignal)

/** Up to this many catalog pictures are hashed for the perceptual-hash NG rules (the cost is one request each). */
internal const val FUTABER_CATALOG_PHASH_MAX_CANDIDATES = 256

/** (thread id, picture address) of the threads whose picture can be hashed, in catalog order. */
internal fun futaberCatalogPhashCandidates(items: List<CatalogItem>): List<Pair<String, String>> =
    items.take(FUTABER_CATALOG_PHASH_MAX_CANDIDATES).mapNotNull { item ->
        (item.fullImageUrl?.takeIf { it.isNotBlank() } ?: item.thumbnailUrl?.takeIf { it.isNotBlank() })?.let { item.id to it }
    }

/** The ids of the threads whose hash is close to the hash of one of the [rules] (their value is the 16-digit hash). */
internal fun futaberPhashHiddenIds(
    hashes: Map<String, String>,
    rules: List<CompatNgRule>,
    threshold: Int
): Set<String> {
    if (rules.isEmpty() || hashes.isEmpty()) return emptySet()
    return hashes.filterValues { hash -> rules.any { CompatImagePhash.isSimilar(hash, it.normalizedValue, threshold) } }.keys
}

/** The rules the other modes made that hide catalog threads by a word of the title (and are not shown as ふたちゃ NG words). */
internal fun futaberCatalogWordRules(rules: List<CompatNgRule>): List<CompatNgRule> =
    rules.filter { it.kind == CompatNgKind.CATALOG_WORD || it.kind == CompatNgKind.CATALOG_IGNORE }
        .sortedByDescending { it.createdAtEpochMillis }

/** The rules the other modes made that hide catalog threads by their picture (the picture's address or its look). */
internal fun futaberCatalogImageRules(rules: List<CompatNgRule>): List<CompatNgRule> =
    rules.filter { it.kind == CompatNgKind.CATALOG_IMAGE || it.kind == CompatNgKind.CATALOG_IMAGE_PHASH }
        .sortedByDescending { it.createdAtEpochMillis }

/** One line for a catalog word rule in the settings: the word, and where it applies. */
internal fun futaberCatalogWordRuleLabel(rule: CompatNgRule): String {
    val scope = if (rule.scopeKey == "*") "全板" else "この板"
    return listOf(compatCatalogManagementDisplayValue(rule).trim(), scope).filter { it.isNotEmpty() }.joinToString("　")
}

/** One line for a catalog picture rule in the settings: the note (else the file name or "見た目が似た画像"), and where it applies. */
internal fun futaberCatalogImageRuleLabel(rule: CompatNgRule): String {
    val scope = if (rule.scopeKey == "*") "全板" else "この板"
    val what = rule.memo.trim().ifEmpty {
        if (rule.kind == CompatNgKind.CATALOG_IMAGE_PHASH) {
            rule.imageUrl.orEmpty().substringBefore('?').substringAfterLast('/').ifEmpty { "見た目が似た画像" }
        } else rule.normalizedValue.substringBefore('?').substringAfterLast('/').ifEmpty { rule.normalizedValue }
    }
    return listOf(what, scope).filter { it.isNotEmpty() }.joinToString("　")
}

/** The title of a new thread the person just posted, until the thread is read: the subject they wrote, else "無題". */
internal fun futaberNewThreadTitle(subject: String): String =
    subject.trim().lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "無題" }
