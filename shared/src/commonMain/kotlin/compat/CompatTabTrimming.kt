package com.valoser.futacha.shared.compat

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** The one tab limit of the four modes (plan B4, decision Q3); the setting is offered in B9. */
const val COMPAT_SHARED_TAB_LIMIT_KEY = "compat.shared.tabLimit"
const val COMPAT_SHARED_TAB_LIMIT_DEFAULT = 200
const val COMPAT_SHARED_TAB_LIMIT_MIN = 20

/** Stays below Android's 1,000-row read and the backup's 1,000-tab check, with room for the exempt tabs. */
const val COMPAT_SHARED_TAB_LIMIT_MAX = 990

/** The most tabs a store reads back (drafts and closed-tab records included): Android's row cap. */
const val COMPAT_MAX_STORED_TABS = 1_000

/** The limit saved in [COMPAT_SHARED_TAB_LIMIT_KEY], or the default; out-of-range values are brought into range. */
fun compatSharedTabLimit(preferences: Map<String, String>): Int =
    preferences[COMPAT_SHARED_TAB_LIMIT_KEY]?.trim()?.trim('"')?.toIntOrNull()
        ?.coerceIn(COMPAT_SHARED_TAB_LIMIT_MIN, COMPAT_SHARED_TAB_LIMIT_MAX)
        ?: COMPAT_SHARED_TAB_LIMIT_DEFAULT

/** Plan 1-7: the limit that holds [tabCount] tabs and leaves ten over the ★ (and at least [other], at least the default). */
fun compatRaisedTabLimit(current: Int, tabCount: Int, stars: Int, other: Int = 0): Int =
    maxOf(maxOf(current, COMPAT_SHARED_TAB_LIMIT_DEFAULT, tabCount), other, stars + 10)
        .coerceIn(COMPAT_SHARED_TAB_LIMIT_MIN, COMPAT_SHARED_TAB_LIMIT_MAX)

/**
 * The limit to save when a settings restore leaves [tabs] (audit round 4, M1): the saved limit is not restored, so a device
 * on a lower limit would close the tabs it just restored. Null when they fit, so a restore that fits changes nothing.
 */
fun compatTabLimitAfterRestore(preferences: Map<String, String>, tabs: List<CompatTab>): Int? {
    val current = compatSharedTabLimit(preferences)
    val distinct = tabs.distinctBy(CompatTab::key)
    val stars = distinct.count(CompatTab::favorite)
    if (distinct.size <= current && stars + 10 <= current) return null
    return compatRaisedTabLimit(current, distinct.size, stars).takeIf { it > current }
}

/**
 * The tab keys to trim (plan B4, 1-3), oldest first. A tab with a star and a tab in [exemptKeys] (the active tab, the
 * modes' own rows, the tabs a screen holds) is never trimmed, and the decision counts only the other tabs: once they
 * exceed `max(limit − exempt, limit / 2)` they go back to 90% of that. The total stays within `exempt + limit`, and opening
 * one tab while many are exempt never drops all the others.
 *
 * Trimming is not a close by the person: callers keep it out of the closed-tab record, the reconciliation and the
 * favourites (the exempt keys hold every tab those still list).
 */
fun planSharedTabTrim(
    tabs: List<CompatTab>,
    exemptKeys: Set<String>,
    limit: Int,
    tabViews: List<CompatSharedTabView> = emptyList()
): Set<String> {
    val (exempt, eligible) = tabs.distinctBy(CompatTab::key).partition { tab -> tab.favorite || tab.key in exemptKeys }
    val budget = maxOf(limit - exempt.size, limit / 2)
    if (eligible.size <= budget) return emptySet()
    val keep = budget * 9 / 10
    val rank = compatTabTrimRanks(tabViews)
    return eligible
        .sortedWith(compareBy<CompatTab> { rank[it.key] ?: 1 }.thenBy { it.insertedAtEpochMillis }.thenByDescending { it.key })
        .take(eligible.size - keep)
        .mapTo(LinkedHashSet(), CompatTab::key)
}

/** [CompatSharedTabView.addedAtEpochMillis] of a mark that only holds a place: another mode opened the tab (audit L2). */
const val COMPAT_TAB_VIEW_SHOWN_ONLY = 0L

/**
 * The trim order by the modes' marks (plan B6, 1-3; oldest first within each): 0 = only ふたったー風 opened the tab (it
 * only leaves ふたったー風's automatic strip), 1 = any other tab, a tab ふたったー風 merely shows among them (audit L2). A tab without a mark is not "shown by nobody":
 * ふたちゃ and としあき(仮) show every shared tab without marking it, so a tab one of them just opened must not go
 * before ふたったー風's automatic ones (plan B7b; the plan's first order put unmarked tabs first).
 */
internal fun compatTabTrimRanks(tabViews: List<CompatSharedTabView>): Map<String, Int> {
    if (tabViews.isEmpty()) return emptyMap()
    val ftutter = ExperienceProfile.FTUTTER.persistedValue
    return tabViews.groupBy(CompatSharedTabView::tabKey).mapValues { (_, views) ->
        if (views.all { it.profile == ftutter } && views.any { it.addedAtEpochMillis != COMPAT_TAB_VIEW_SHOWN_ONLY }) 0 else 1
    }
}

private const val COMPAT_TAB_PROTECTION_PREFIX = "compat.tabProtection."

internal fun compatTabProtectionKey(tabKey: String) = COMPAT_TAB_PROTECTION_PREFIX + tabKey

internal fun Map<String, String>.isCompatTabProtected(tabKey: String): Boolean =
    this[compatTabProtectionKey(tabKey)] == "ON"

/** The keys [planSharedTabTrim] never trims besides the stars: the active tab, protected tabs, the modes' rows and the held tabs. */
fun compatTabTrimExemptKeys(
    activeTabKey: String?,
    preferences: Map<String, String>,
    boards: List<CompatBoard>,
    screenHeldKeys: Set<String>
): Set<String> = buildSet {
    activeTabKey?.let(::add)
    addAll(compatModeListTabKeys(preferences, boards))
    addAll(screenHeldKeys)
    for ((key, value) in preferences) {
        if (value == "ON" && key.startsWith(COMPAT_TAB_PROTECTION_PREFIX)) {
            add(key.removePrefix(COMPAT_TAB_PROTECTION_PREFIX))
        }
    }
}

/** The tabs newest first, without the ones [planSharedTabTrim] trims. */
fun trimCompatTabs(
    tabs: List<CompatTab>,
    activeTabKey: String?,
    preferences: Map<String, String> = emptyMap(),
    boards: List<CompatBoard> = emptyList(),
    screenHeldKeys: Set<String> = emptySet(),
    tabViews: List<CompatSharedTabView> = emptyList()
): List<CompatTab> {
    val sorted = tabs.sortedWith(compareByDescending<CompatTab> { it.insertedAtEpochMillis }.thenBy { it.key })
    val removed = planSharedTabTrim(
        sorted,
        compatTabTrimExemptKeys(activeTabKey, preferences, boards, screenHeldKeys),
        compatSharedTabLimit(preferences),
        tabViews
    )
    return if (removed.isEmpty()) sorted else sorted.filterNot { tab -> tab.key in removed }
}

/**
 * The tabs a mode's screen shows that are not saved anywhere the trim can read (plan B4, `setScreenHeldTabs`): ふたばー風's
 * open thread and ふたったー風's shown tab. Kept in memory only, per mode.
 */
@OptIn(ExperimentalAtomicApi::class)
class CompatScreenHeldTabs {
    private val held = AtomicReference<Map<ExperienceProfile, Set<String>>>(emptyMap())

    fun set(profile: ExperienceProfile, keys: Set<String>) {
        while (true) {
            val current = held.load()
            val next = if (keys.isEmpty()) current - profile else current + (profile to keys)
            if (held.compareAndSet(current, next)) return
        }
    }

    fun keys(): Set<String> = held.load().values.flatMapTo(HashSet()) { it }
}

/** Plan B9: the lowest limit the settings take: one more tab per ★ plus ten (★ are never trimmed), within the range. */
fun compatTabLimitFloor(tabs: List<CompatTab>): Int =
    (tabs.count(CompatTab::favorite) + 10).coerceIn(COMPAT_SHARED_TAB_LIMIT_MIN, COMPAT_SHARED_TAB_LIMIT_MAX)

/** Why [limit] is refused by the settings, or null when it can be saved. */
fun compatTabLimitRefusal(limit: Int, tabs: List<CompatTab>): String? {
    val floor = compatTabLimitFloor(tabs)
    return when {
        limit > COMPAT_SHARED_TAB_LIMIT_MAX -> "${COMPAT_SHARED_TAB_LIMIT_MAX}件より上にはできません"
        limit < floor -> "${floor}件（★の付いたタブ${tabs.count(CompatTab::favorite)}件＋10件、最低${COMPAT_SHARED_TAB_LIMIT_MIN}件）より下にはできません"
        else -> null
    }
}

/**
 * How many tabs the next trim closes once the limit is [limit] (plan B9: lowering it asks first). The tabs a screen
 * holds are not known here, so the trim may close a few fewer.
 */
fun compatTabLimitTrimCount(
    tabs: List<CompatTab>,
    limit: Int,
    activeTabKey: String?,
    preferences: Map<String, String>,
    boards: List<CompatBoard>,
    tabViews: List<CompatSharedTabView>
): Int = planSharedTabTrim(tabs, compatTabTrimExemptKeys(activeTabKey, preferences, boards, emptySet()), limit, tabViews).size

/** The question shown before a lower limit is saved, or null when nothing would close. */
fun compatTabLimitQuestion(limit: Int, trimCount: Int): String? =
    if (trimCount <= 0) null
    else "タブの上限を${limit}件にすると、次にタブを開いた時か次の起動時に、★の無い古いタブが${trimCount}件閉じます（4つのモードで共通）。よろしいですか？"

/** The text under the setting in both modes that offer it. */
const val COMPAT_TAB_LIMIT_NOTE =
    "4つのモードで共通のタブの数。超えると★の無い古いタブから閉じます（★・開いているタブは閉じません）。下げると次にタブを開いた時か次の起動時に閉じます。"
