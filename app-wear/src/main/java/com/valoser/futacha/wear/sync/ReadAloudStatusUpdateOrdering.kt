package com.valoser.futacha.wear.sync

import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS
import com.valoser.futacha.shared.watch.WatchReadAloudStatusUpdate
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.withReadAloudStatusUpdate

/**
 * Orders read-aloud status updates by the phone's send time. Each update arrives
 * twice (Data Layer item and message) on separate coroutines, so an older one can
 * be applied after a newer one; a newer "stopped" update would otherwise be
 * replaced by the older "speaking" one. Not thread-safe: call under the store's
 * save lock, together with reading the current snapshot.
 */
internal class ReadAloudStatusUpdateOrdering {
    private var latestAppliedAtMillis = 0L

    /** Returns the snapshot with [update] applied, or null when a newer update was already applied. */
    fun apply(base: WatchSnapshot, update: WatchReadAloudStatusUpdate, nowMillis: Long): WatchSnapshot? {
        // A watermark beyond plausible clock skew (phone clock moved back) must not block every later update.
        if (latestAppliedAtMillis > nowMillis + WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS) latestAppliedAtMillis = 0L
        if (update.updatedAtMillis > 0L) {
            if (update.updatedAtMillis < latestAppliedAtMillis) return null
            latestAppliedAtMillis = update.updatedAtMillis
        }
        return base.withReadAloudStatusUpdate(update = update, nowMillis = nowMillis)
    }
}
