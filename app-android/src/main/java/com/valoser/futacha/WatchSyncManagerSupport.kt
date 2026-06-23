package com.valoser.futacha

internal enum class WatchRefreshRequestDecision {
    StartRefresh,
    RequestSnapshotOnly
}

internal fun resolveWatchRefreshRequestDecision(
    isRefreshInFlight: Boolean,
    lastRefreshStartedElapsedMillis: Long,
    nowElapsedMillis: Long,
    minIntervalMillis: Long
): WatchRefreshRequestDecision {
    if (isRefreshInFlight) return WatchRefreshRequestDecision.RequestSnapshotOnly
    if (lastRefreshStartedElapsedMillis <= 0L) return WatchRefreshRequestDecision.StartRefresh
    val elapsedSinceLastStart = nowElapsedMillis - lastRefreshStartedElapsedMillis
    if (elapsedSinceLastStart < 0L) return WatchRefreshRequestDecision.RequestSnapshotOnly
    return if (elapsedSinceLastStart >= minIntervalMillis.coerceAtLeast(1L)) {
        WatchRefreshRequestDecision.StartRefresh
    } else {
        WatchRefreshRequestDecision.RequestSnapshotOnly
    }
}

internal fun shouldLoadWatchPreviewThreadPages(
    includePreviewThreadPages: Boolean,
    previewSuppressedUntilElapsedMillis: Long,
    nowElapsedMillis: Long
): Boolean {
    return includePreviewThreadPages && nowElapsedMillis >= previewSuppressedUntilElapsedMillis
}
