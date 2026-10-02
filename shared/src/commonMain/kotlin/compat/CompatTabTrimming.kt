package com.valoser.futacha.shared.compat

const val COMPAT_TAB_LIMIT_TRIGGER = 100
const val COMPAT_TAB_LIMIT_AFTER_TRIM = 90

/**
 * Same rule as Android's SQLite trim (ThreadTabCleanService in both APKs):
 * only after the 100th tab, drop the oldest tabs back to 90, never removing a
 * favourite or the active tab. Returns the tabs newest first; when favourites
 * and the active tab alone exceed 90, more than 90 remain.
 */
fun trimCompatTabs(tabs: List<CompatTab>, activeTabKey: String?): List<CompatTab> {
    val sorted = tabs.sortedWith(
        compareByDescending<CompatTab> { it.insertedAtEpochMillis }.thenBy { it.key }
    )
    if (sorted.size <= COMPAT_TAB_LIMIT_TRIGGER) return sorted
    val removed = sorted.asReversed()
        .filter { tab -> !tab.favorite && tab.key != activeTabKey }
        .take((sorted.size - COMPAT_TAB_LIMIT_AFTER_TRIM).coerceAtLeast(0))
        .mapTo(mutableSetOf(), CompatTab::key)
    return sorted.filterNot { tab -> tab.key in removed }
}
