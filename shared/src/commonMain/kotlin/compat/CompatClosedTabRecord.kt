package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.saturatingEpochAdd

/*
 * The record of closed tabs shared by the 4 modes (plan B6, docs/shared-history-tabs-plan-2026-11-09.md §1-4).
 *
 * A record lives 24 hours ([ClosedTabBatch.expiresAtEpochMillis]) and at most [COMPAT_CLOSED_BATCH_MAX] are kept.
 * The undo notice of ふたちゃ / としあき(仮) is a separate, shorter deadline ([ClosedTabBatch.noticeUntilEpochMillis],
 * 7 seconds): before this batch the record itself was deleted after 7 seconds.
 */

const val COMPAT_CLOSED_BATCH_MAX = 20
const val COMPAT_CLOSED_BATCH_KEEP_MILLIS = 24L * 60 * 60 * 1000
const val COMPAT_UNDO_NOTICE_MILLIS = 7_000L

/** Where a tab goes in a mode's order when [CompatibilityStore.openSharedTab] adds its view. */
enum class CompatTabPlacement { FIRST, LAST }

/** The undo notice's deadline. A record written before plan B6 has no notice deadline: its 7-second expiry is it. */
val ClosedTabBatch.noticeDeadlineEpochMillis: Long
    get() = if (noticeUntilEpochMillis > 0L) noticeUntilEpochMillis else expiresAtEpochMillis

/** A new record: kept 24 hours, noticed 7 seconds. */
fun newCompatClosedTabBatch(
    tabs: List<ClosedCompatTab>,
    selectedTabKey: String?,
    nowEpochMillis: Long,
    closedBy: String?
): ClosedTabBatch = ClosedTabBatch(
    id = "close-$nowEpochMillis-${tabs.joinToString("-") { it.tab.key }}",
    tabs = tabs,
    selectedTabKey = selectedTabKey,
    expiresAtEpochMillis = saturatingEpochAdd(nowEpochMillis, COMPAT_CLOSED_BATCH_KEEP_MILLIS),
    closedBy = closedBy,
    noticeUntilEpochMillis = saturatingEpochAdd(nowEpochMillis, COMPAT_UNDO_NOTICE_MILLIS)
)

/** The records still kept: not expired, newest first, at most [COMPAT_CLOSED_BATCH_MAX]. */
fun compatKeptClosedBatches(batches: List<ClosedTabBatch>, nowEpochMillis: Long): List<ClosedTabBatch> =
    batches.asSequence()
        .filter { it.expiresAtEpochMillis > nowEpochMillis && it.tabs.isNotEmpty() }
        .distinctBy(ClosedTabBatch::id)
        .sortedByDescending { it.noticeDeadlineEpochMillis }
        .take(COMPAT_CLOSED_BATCH_MAX)
        .toList()

/** The record whose undo notice is still showing (the newest), or null. */
fun compatPendingClosedBatch(batches: List<ClosedTabBatch>, nowEpochMillis: Long): ClosedTabBatch? =
    batches.filter { it.expiresAtEpochMillis > nowEpochMillis && it.noticeDeadlineEpochMillis > nowEpochMillis }
        .maxByOrNull { it.noticeDeadlineEpochMillis }

/** The newest record [profile] may bring back: one it closed, or one with a tab it showed (§1-4). */
fun compatClosedBatchFor(
    batches: List<ClosedTabBatch>,
    profile: String,
    nowEpochMillis: Long
): ClosedTabBatch? = compatKeptClosedBatches(batches, nowEpochMillis).firstOrNull { batch ->
    batch.closedBy == profile || batch.tabs.any { closed -> closed.views.any { it.profile == profile } }
}

/**
 * The tab a restore writes. A tab open again meanwhile is not overwritten: only the star is added back. A tab
 * ふたったー風 closed comes back without its star (the original's protection does not come back).
 */
fun compatRestoredTab(closed: ClosedCompatTab, existing: CompatTab?, closedBy: String?): CompatTab? {
    val starred = closed.tab.favorite && closedBy != ExperienceProfile.FTUTTER.persistedValue
    if (existing == null) return if (starred == closed.tab.favorite) closed.tab else closed.tab.copy(favorite = starred)
    return if (starred && !existing.favorite) existing.copy(favorite = true) else null
}

/** [views] of one mode with [tabKey] added at [placement] (an existing view keeps its place), renumbered from 0. */
fun compatViewsWith(
    views: List<CompatSharedTabView>,
    profile: String,
    tabKey: String,
    placement: CompatTabPlacement,
    nowEpochMillis: Long
): List<CompatSharedTabView> {
    val ordered = views.filter { it.profile == profile }.sortedBy(CompatSharedTabView::ordinal)
    if (ordered.any { it.tabKey == tabKey }) return ordered.mapIndexed { index, view -> view.copy(ordinal = index) }
    val added = CompatSharedTabView(tabKey, profile, 0, nowEpochMillis)
    val next = if (placement == CompatTabPlacement.FIRST) listOf(added) + ordered else ordered + added
    return next.mapIndexed { index, view -> view.copy(ordinal = index) }
}

/** The views a restore puts back: each mode gets its closed tabs' views again at their old places (an existing view stays). */
fun compatRestoredViews(
    current: List<CompatSharedTabView>,
    batch: ClosedTabBatch,
    restoredKeys: Set<String>
): List<CompatSharedTabView> {
    val back = batch.tabs.filter { it.tab.key in restoredKeys }.flatMap(ClosedCompatTab::views)
    if (back.isEmpty()) return current
    val profiles = back.mapTo(LinkedHashSet(), CompatSharedTabView::profile)
    val untouched = current.filter { it.profile !in profiles }
    val rebuilt = profiles.flatMap { profile ->
        val ordered = current.filter { it.profile == profile }.sortedBy(CompatSharedTabView::ordinal).toMutableList()
        back.filter { it.profile == profile }.sortedBy(CompatSharedTabView::ordinal).forEach { view ->
            if (ordered.none { it.tabKey == view.tabKey }) ordered.add(view.ordinal.coerceIn(0, ordered.size), view)
        }
        ordered.mapIndexed { index, view -> view.copy(ordinal = index) }
    }
    return untouched + rebuilt
}
