package com.valoser.futacha.compat

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.ScrollAnchor

/**
 * The closed-tab expiry job is rescheduled after each DB observation of the
 * pending closed batch. Observations are numbered inside the store mutex, so
 * only one newer than the last applied one may replace the job. An expiry run
 * that read "nothing pending" before a new batch was closed finishes later and
 * must not cancel the job just scheduled for that batch.
 */
internal fun shouldApplyClosedBatchObservation(observation: Long, lastAppliedObservation: Long): Boolean =
    observation > lastAppliedObservation

/**
 * Patches only [tabKey]'s scroll anchor. The receiver is returned unchanged
 * when nothing differs, and every other element keeps its identity, so a
 * scroll stop does not look like a change to the whole tab list.
 */
internal fun List<CompatTab>.withCompatTabScrollAnchor(tabKey: String, anchor: ScrollAnchor): List<CompatTab> {
    val index = indexOfFirst { it.key == tabKey }
    if (index < 0 || this[index].scrollAnchor == anchor) return this
    return toMutableList().also { it[index] = it[index].copy(scrollAnchor = anchor) }
}

/** History counterpart of [withCompatTabScrollAnchor], matched by canonical URL. */
internal fun List<CompatHistoryEntry>.withCompatHistoryScrollAnchor(
    canonicalUrl: String,
    anchor: ScrollAnchor
): List<CompatHistoryEntry> {
    val index = indexOfFirst { it.canonicalUrl == canonicalUrl }
    if (index < 0 || this[index].scrollAnchor == anchor) return this
    return toMutableList().also { it[index] = it[index].copy(scrollAnchor = anchor) }
}

/**
 * Preference keys written per thread or per board (own-post markers, last
 * catalog fetch counts). They accumulate without a bound, so the store reads
 * them after the real settings and trims the oldest. Must match the store's
 * `TRANSIENT_PREFERENCE_SQL` GLOB patterns.
 */
internal fun isTransientPreferenceKey(key: String): Boolean =
    key.startsWith("compat.ownpost.") || key.startsWith("compat.catalog.lastFetchThreadCount.")
