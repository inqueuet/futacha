package com.valoser.futacha.shared.mix

enum class MixRefreshTrigger { OPEN, MANUAL, ORDER_CHANGED }
enum class MixRefreshDecision { FETCH, USE_CACHED, QUEUE_AFTER_RUNNING, REFUSE_RUNNING, REFUSE_TOO_SOON }

internal const val MIX_MANUAL_REFRESH_GAP_MILLIS = 10_000L
internal const val MIX_CACHE_REUSE_MILLIS = 5 * 60_000L

internal fun mixRefreshDecision(
    trigger: MixRefreshTrigger, running: Boolean, cachedFetchedAt: Long?, lastFinishedAt: Long?, nowMillis: Long
): MixRefreshDecision = when {
    running && trigger == MixRefreshTrigger.ORDER_CHANGED -> MixRefreshDecision.QUEUE_AFTER_RUNNING
    running -> MixRefreshDecision.REFUSE_RUNNING
    trigger == MixRefreshTrigger.ORDER_CHANGED -> MixRefreshDecision.FETCH
    trigger == MixRefreshTrigger.MANUAL && lastFinishedAt != null &&
        nowMillis - lastFinishedAt in 0 until MIX_MANUAL_REFRESH_GAP_MILLIS -> MixRefreshDecision.REFUSE_TOO_SOON
    trigger == MixRefreshTrigger.OPEN && cachedFetchedAt != null &&
        nowMillis - cachedFetchedAt in 0 until MIX_CACHE_REUSE_MILLIS -> MixRefreshDecision.USE_CACHED
    else -> MixRefreshDecision.FETCH
}
