package com.valoser.futacha.wear.tile

import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_STALE_AGE_MILLIS
import com.valoser.futacha.shared.watch.WatchSnapshot

/** The system refreshes tiles at most about once a minute. */
internal const val TILE_MIN_FRESHNESS_INTERVAL_MILLIS = 60_000L

/**
 * When the tile's text changes on its own: a "読上げ中" status stops being
 * fresh, or the snapshot becomes "同期古い". Updates are otherwise requested
 * only when a new snapshot arrives, so without a freshness interval those
 * labels stayed on the tile indefinitely. Returns 0 (no automatic refresh)
 * when nothing will expire.
 */
internal fun tileFreshnessIntervalMillis(snapshot: WatchSnapshot?, nowMillis: Long): Long {
    if (snapshot == null) return 0L
    val expiries = buildList {
        snapshot.threads.forEach { thread ->
            val status = thread.readAloudStatus ?: return@forEach
            if (status.updatedAtMillis <= 0L) return@forEach
            val expiresAt = status.updatedAtMillis + WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
            if (nowMillis in status.updatedAtMillis..expiresAt) add(expiresAt)
        }
        if (snapshot.generatedAtMillis > 0L) {
            val staleAt = snapshot.generatedAtMillis + WATCH_SNAPSHOT_STALE_AGE_MILLIS
            if (nowMillis in snapshot.generatedAtMillis..staleAt) add(staleAt)
        }
    }
    val next = expiries.minOrNull() ?: return 0L
    // One second past the boundary so the refreshed tile shows the new state.
    return (next - nowMillis + 1_000L).coerceAtLeast(TILE_MIN_FRESHNESS_INTERVAL_MILLIS)
}
