package com.valoser.futacha

import com.valoser.futacha.shared.watch.WatchReadAloudStatus

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

internal fun shouldSendWatchReadAloudStatusUpdate(
    status: WatchReadAloudStatus?,
    lastSentStatus: WatchReadAloudStatus?,
    lastSentElapsedMillis: Long,
    nowElapsedMillis: Long,
    minIntervalMillis: Long
): Boolean {
    if (status == null) return true
    if (lastSentStatus == null) return true
    if (!status.hasSameReadAloudSessionState(lastSentStatus)) return true
    if (lastSentElapsedMillis <= 0L) return true
    val elapsedSinceLastSend = nowElapsedMillis - lastSentElapsedMillis
    if (elapsedSinceLastSend < 0L) return false
    return elapsedSinceLastSend >= minIntervalMillis.coerceAtLeast(1L)
}

private fun WatchReadAloudStatus.hasSameReadAloudSessionState(
    other: WatchReadAloudStatus
): Boolean {
    return boardId == other.boardId &&
        boardUrl == other.boardUrl &&
        threadId == other.threadId &&
        state == other.state
}
