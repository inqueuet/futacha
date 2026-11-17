package com.valoser.futacha

import com.valoser.futacha.shared.watch.WatchPhoneStatus
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchReadAloudStatusUpdate
import kotlinx.serialization.json.Json
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

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

internal enum class WatchRefreshCommandAction {
    LaunchRefresh,
    CoalesceIntoRunningRefresh
}

/**
 * A watch Refresh command never cancels a refresh that is still running.
 * Cancelling it left the replacement seeing the old in-flight state (and the
 * minimum interval) so the watch got only a snapshot and no refresh at all.
 */
internal fun resolveWatchRefreshCommandAction(isRefreshJobActive: Boolean): WatchRefreshCommandAction =
    if (isRefreshJobActive) {
        WatchRefreshCommandAction.CoalesceIntoRunningRefresh
    } else {
        WatchRefreshCommandAction.LaunchRefresh
    }

/** A command DataItem older than this was queued while the watch was disconnected. */
internal const val WATCH_COMMAND_DATA_ITEM_MAX_AGE_MILLIS = 2 * 60 * 1000L

/**
 * The Data Layer delivers a command DataItem put while the phone was out of
 * range whenever the devices reconnect, possibly hours later; starting a read
 * aloud or opening a thread then is unexpected. A missing timestamp (older
 * watch builds) or one ahead of the phone clock is accepted.
 */
internal fun isStaleWatchCommandDataItem(
    updatedAtMillis: Long,
    nowMillis: Long,
    maxAgeMillis: Long = WATCH_COMMAND_DATA_ITEM_MAX_AGE_MILLIS
): Boolean = updatedAtMillis > 0L && nowMillis - updatedAtMillis > maxAgeMillis

/**
 * The reply to a watch snapshot request: the phone clock (echoing the watch clock the request
 * carried, so the watch can learn its clock offset) and whether this mode serves the watch.
 */
internal fun buildWatchPhoneStatusPayload(
    phoneNowMillis: Long,
    watchSentAtMillis: Long,
    isSupported: Boolean
): ByteArray = Json.encodeToString(
    WatchPhoneStatus.serializer(),
    WatchPhoneStatus(
        phoneNowMillis = phoneNowMillis,
        watchSentAtMillis = watchSentAtMillis,
        isSupported = isSupported
    )
).encodeToByteArray()

/**
 * Numbers read-aloud status updates for the watch: a per-process [sessionId] and a
 * monotonic sequence, so the watch can order them without trusting either device's
 * clock (the watch's learned clock offset may not be calibrated yet).
 */
internal class WatchReadAloudSequenceSource(val sessionId: Long) {
    private val counter = AtomicLong(0L)

    fun next(): Long = counter.incrementAndGet()
}

internal val watchReadAloudSequenceSource = WatchReadAloudSequenceSource(
    sessionId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE)
)

internal fun buildWatchReadAloudStatusUpdate(
    status: WatchReadAloudStatus?,
    nowMillis: Long,
    sequenceSource: WatchReadAloudSequenceSource = watchReadAloudSequenceSource
): WatchReadAloudStatusUpdate = WatchReadAloudStatusUpdate(
    status = status,
    updatedAtMillis = nowMillis,
    sessionId = sequenceSource.sessionId,
    sequence = sequenceSource.next()
)
