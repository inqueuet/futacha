package com.valoser.futacha.shared.compat

/*
 * Per-thread and per-board bookkeeping stored as plain preferences.  They used
 * to accumulate forever (one row per own reply, one per board sort) and, since
 * the stores load a bounded number of preference rows in key order, pushed real
 * settings sorting after "compat.ownpost.*" out of the loaded set.
 */

internal const val COMPAT_OWN_POST_PREFERENCE_PREFIX = "compat.ownpost."

/** Own-post markers kept per thread; the oldest (lowest) post numbers go first. */
internal const val COMPAT_OWN_POST_MARKERS_PER_TAB = 200

internal fun compatOwnPostPreferencePrefix(tabKey: String): String =
    "$COMPAT_OWN_POST_PREFERENCE_PREFIX$tabKey."

private const val COMPAT_CATALOG_LAST_FETCH_COUNT_PREFERENCE_PREFIX = "compat.catalog.lastFetchThreadCount."

internal fun compatCatalogLastFetchCountPreferenceKey(boardKey: String, sortName: String): String =
    "$COMPAT_CATALOG_LAST_FETCH_COUNT_PREFERENCE_PREFIX$boardKey.$sortName"

/**
 * Preference changes recording [postNo] as the user's own post in [tabKey],
 * evicting the lowest post numbers beyond [cap] (null removes a key).
 */
internal fun compatOwnPostMarkerSave(
    preferences: Map<String, String>,
    tabKey: String,
    postNo: String,
    cap: Int = COMPAT_OWN_POST_MARKERS_PER_TAB
): Map<String, String?> {
    val prefix = compatOwnPostPreferencePrefix(tabKey)
    val newKey = prefix + postNo
    val existing = preferences.keys.filter { it.startsWith(prefix) && it != newKey }
    val evictCount = existing.size + 1 - cap.coerceAtLeast(1)
    val evicted = if (evictCount <= 0) {
        emptyList()
    } else {
        existing
            .sortedWith(compareBy<String> { it.removePrefix(prefix).toLongOrNull() ?: Long.MIN_VALUE }.thenBy { it })
            .take(evictCount)
    }
    return buildMap {
        evicted.forEach { put(it, null) }
        put(newKey, "1")
    }
}

/** Tab keys whose own-post markers are still reachable (open, undo-able or in history). */
internal fun compatReferencedOwnPostTabKeys(
    tabs: List<CompatTab>,
    histories: List<CompatHistoryEntry>,
    pendingClose: ClosedTabBatch?
): Set<String> = buildSet {
    tabs.forEach { add(it.key) }
    pendingClose?.tabs?.forEach { add(it.tab.key) }
    histories.forEach { entry ->
        runCatching { compatTabKey(entry.canonicalUrl) }.getOrNull()?.let(::add)
    }
}

/**
 * Removals for markers and counters of threads/boards that were referenced
 * earlier in this session and no longer are.  Only a disappearance observed
 * in-session is acted on, so a not-yet-loaded (empty) store never wipes data.
 */
internal fun compatDroppedReferencePreferenceDeletions(
    preferences: Map<String, String>,
    droppedTabKeys: Set<String>,
    droppedBoardKeys: Set<String>
): Map<String, String?> {
    if (droppedTabKeys.isEmpty() && droppedBoardKeys.isEmpty()) return emptyMap()
    // Tab and board keys are "compat_tab_<hash>" / "compat_board_<hash>" and
    // contain no '.', so the owner is the segment right after the prefix.
    fun owner(key: String, prefix: String): String? =
        if (key.startsWith(prefix)) key.substring(prefix.length).substringBefore('.') else null
    return preferences.keys
        .filter { key ->
            owner(key, COMPAT_OWN_POST_PREFERENCE_PREFIX)?.let { it in droppedTabKeys }
                ?: owner(key, COMPAT_CATALOG_LAST_FETCH_COUNT_PREFERENCE_PREFIX)?.let { it in droppedBoardKeys }
                ?: false
        }
        .associateWith { null }
}
