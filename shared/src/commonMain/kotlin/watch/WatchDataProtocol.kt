package com.valoser.futacha.shared.watch

import kotlinx.serialization.Serializable

const val WATCH_SNAPSHOT_PATH = "/futacha/watch_snapshot"
const val WATCH_SNAPSHOT_ACK_PATH = "/futacha/watch_snapshot_ack"
const val WATCH_REQUEST_SNAPSHOT_PATH = "/futacha/request_snapshot"
const val WATCH_COMMAND_PATH = "/futacha/command"
const val WATCH_READ_ALOUD_STATUS_PATH = "/futacha/read_aloud_status"
const val WATCH_ALERT_PATH = "/futacha/watch_alert"
/**
 * Phone to watch message. Answers a snapshot request with the phone clock (so the watch can
 * estimate its own clock offset) and tells the watch when the phone cannot serve it.
 */
const val WATCH_PHONE_STATUS_PATH = "/futacha/phone_status"
const val WATCH_SNAPSHOT_KEY = "snapshot"
const val WATCH_SNAPSHOT_ACK_KEY = "snapshotAck"
const val WATCH_COMMAND_KEY = "command"
const val WATCH_READ_ALOUD_STATUS_KEY = "readAloudStatus"
const val WATCH_ALERT_KEY = "watchAlert"
const val WATCH_UPDATED_AT_KEY = "updatedAtMillis"
const val WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS = 10 * 60 * 1000L
const val WATCH_SNAPSHOT_STALE_AGE_MILLIS = 30 * 60 * 1000L
const val WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1000L
/** A clock sample is only trusted when the request/response round trip was this quick. */
const val WATCH_CLOCK_MAX_ROUND_TRIP_MILLIS = 10_000L
/** The learned offset is dropped after this long (the watch clock may have been changed). */
const val WATCH_CLOCK_OFFSET_MAX_AGE_MILLIS = 6 * 60 * 60 * 1000L
const val WATCH_SNAPSHOT_MAX_BOARDS = 80
const val WATCH_SNAPSHOT_MAX_THREADS = 20
const val WATCH_SNAPSHOT_MAX_WATCH_WORDS = 50
const val WATCH_SNAPSHOT_MAX_PREVIEW_POSTS_PER_THREAD = 5

/**
 * [tolerateIncomingClockSkew] is for a watch that has not learned the phone clock offset (see
 * [estimateWatchClockOffsetMillis]): a watch clock more than the allowed skew behind the phone
 * made every snapshot look "from the future" and rejected all of them for good. Ordering is still
 * enforced against the stored snapshot.
 */
fun shouldAcceptWatchSnapshot(
    currentGeneratedAtMillis: Long?,
    incomingGeneratedAtMillis: Long,
    nowMillis: Long,
    maxFutureSkewMillis: Long = WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS,
    tolerateIncomingClockSkew: Boolean = false
): Boolean {
    if (nowMillis < 0L || maxFutureSkewMillis < 0L || incomingGeneratedAtMillis <= 0L) {
        return false
    }
    val latestPlausibleMillis = if (nowMillis > Long.MAX_VALUE - maxFutureSkewMillis) {
        Long.MAX_VALUE
    } else {
        nowMillis + maxFutureSkewMillis
    }
    if (tolerateIncomingClockSkew) {
        // Without a trustworthy clock only the stored snapshot's order can be checked, so an
        // out-of-order older snapshot is taken only when the stored one is itself implausible.
        return currentGeneratedAtMillis == null ||
            currentGeneratedAtMillis <= 0L ||
            currentGeneratedAtMillis > latestPlausibleMillis ||
            incomingGeneratedAtMillis >= currentGeneratedAtMillis
    }
    if (incomingGeneratedAtMillis > latestPlausibleMillis) {
        return false
    }
    val currentIsPlausible = currentGeneratedAtMillis != null &&
        currentGeneratedAtMillis > 0L &&
        currentGeneratedAtMillis <= latestPlausibleMillis
    return !currentIsPlausible || incomingGeneratedAtMillis >= currentGeneratedAtMillis
}

fun WatchSnapshot.hasValidTransportShape(): Boolean {
    if (
        generatedAtMillis <= 0L ||
        boards.size > WATCH_SNAPSHOT_MAX_BOARDS ||
        threads.size > WATCH_SNAPSHOT_MAX_THREADS ||
        watchWords.size > WATCH_SNAPSHOT_MAX_WATCH_WORDS ||
        unreadTotal < 0 ||
        watchMatchTotal < 0
    ) {
        return false
    }
    if (threads.any { thread ->
            thread.replyCount < 0 ||
                (thread.previousReplyCount != null && thread.previousReplyCount < 0) ||
                thread.newReplyCount < 0 ||
                thread.lastVisitedEpochMillis < 0L ||
                thread.previewPosts.size > WATCH_SNAPSHOT_MAX_PREVIEW_POSTS_PER_THREAD
        }
    ) {
        return false
    }
    val expectedUnreadTotal = threads
        .sumOf { it.newReplyCount.toLong() }
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return unreadTotal == expectedUnreadTotal &&
        watchMatchTotal == threads.count { it.isWatchWordMatch }
}

/** Sent by the phone as the [WATCH_PHONE_STATUS_PATH] message. New fields must stay optional. */
@Serializable
data class WatchPhoneStatus(
    val phoneNowMillis: Long,
    /** The watch clock value carried by the request being answered; 0 when it had none. */
    val watchSentAtMillis: Long = 0L,
    /** False when the phone is not in a mode that serves the watch (only ふたちゃ does). */
    val isSupported: Boolean = true
)

private const val WATCH_CLOCK_REQUEST_PAYLOAD_MAX_BYTES = 24

/** The snapshot request message carries the watch clock so the phone can echo it back. */
fun encodeWatchClockRequestPayload(watchNowMillis: Long): ByteArray =
    watchNowMillis.toString().encodeToByteArray()

/** Returns 0 for an empty (older watch build) or malformed payload. */
fun decodeWatchClockRequestPayload(payload: ByteArray): Long {
    if (payload.isEmpty() || payload.size > WATCH_CLOCK_REQUEST_PAYLOAD_MAX_BYTES) return 0L
    return payload.decodeToString().trim().toLongOrNull()?.takeIf { it > 0L } ?: 0L
}

/**
 * Phone clock minus watch clock, from one request/response exchange (the phone is assumed to have
 * read its clock halfway through). Null when the sample is unusable: no echo, a slow round trip
 * (the message sat in a queue) or a watch clock that moved backwards meanwhile.
 */
fun estimateWatchClockOffsetMillis(
    watchSentAtMillis: Long,
    watchReceivedAtMillis: Long,
    phoneNowMillis: Long
): Long? {
    if (watchSentAtMillis <= 0L || phoneNowMillis <= 0L) return null
    val roundTripMillis = watchReceivedAtMillis - watchSentAtMillis
    if (roundTripMillis !in 0L..WATCH_CLOCK_MAX_ROUND_TRIP_MILLIS) return null
    return phoneNowMillis - (watchSentAtMillis + roundTripMillis / 2L)
}

/**
 * The offset to add to the watch clock, or null when none was learned or it is too old to trust
 * (the learned time is ahead of the watch clock, or older than [WATCH_CLOCK_OFFSET_MAX_AGE_MILLIS]).
 */
fun resolveWatchClockOffsetMillis(
    learnedOffsetMillis: Long?,
    learnedAtWatchMillis: Long,
    watchNowMillis: Long
): Long? {
    if (learnedOffsetMillis == null || learnedAtWatchMillis <= 0L) return null
    val ageMillis = watchNowMillis - learnedAtWatchMillis
    if (ageMillis !in 0L..WATCH_CLOCK_OFFSET_MAX_AGE_MILLIS) return null
    return learnedOffsetMillis
}

enum class WatchSnapshotFreshness {
    Fresh,
    Stale,
    /** The snapshot is dated further in the future than the allowed skew: the watch clock is behind. */
    ClockSkew
}

/**
 * A snapshot a few seconds "ahead" of the watch clock (the clocks differ slightly) is fresh; the
 * previous check treated every negative age as stale.
 */
fun classifyWatchSnapshotFreshness(
    generatedAtMillis: Long,
    nowMillis: Long,
    maxFutureSkewMillis: Long = WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS
): WatchSnapshotFreshness {
    if (generatedAtMillis <= 0L) return WatchSnapshotFreshness.Stale
    val ageMillis = nowMillis - generatedAtMillis
    return when {
        ageMillis < -maxFutureSkewMillis -> WatchSnapshotFreshness.ClockSkew
        ageMillis > WATCH_SNAPSHOT_STALE_AGE_MILLIS -> WatchSnapshotFreshness.Stale
        else -> WatchSnapshotFreshness.Fresh
    }
}

/** Same tolerance as [classifyWatchSnapshotFreshness] for the "読み上げ中" status of a thread. */
fun isWatchReadAloudStatusFreshOnWatch(
    updatedAtMillis: Long,
    nowMillis: Long,
    maxFutureSkewMillis: Long = WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS
): Boolean {
    if (updatedAtMillis <= 0L) return false
    val ageMillis = nowMillis - updatedAtMillis
    return ageMillis in -maxFutureSkewMillis..WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
}
