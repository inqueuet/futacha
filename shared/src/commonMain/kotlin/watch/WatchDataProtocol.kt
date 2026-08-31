package com.valoser.futacha.shared.watch

const val WATCH_SNAPSHOT_PATH = "/futacha/watch_snapshot"
const val WATCH_SNAPSHOT_ACK_PATH = "/futacha/watch_snapshot_ack"
const val WATCH_REQUEST_SNAPSHOT_PATH = "/futacha/request_snapshot"
const val WATCH_COMMAND_PATH = "/futacha/command"
const val WATCH_READ_ALOUD_STATUS_PATH = "/futacha/read_aloud_status"
const val WATCH_ALERT_PATH = "/futacha/watch_alert"
const val WATCH_SNAPSHOT_KEY = "snapshot"
const val WATCH_SNAPSHOT_ACK_KEY = "snapshotAck"
const val WATCH_COMMAND_KEY = "command"
const val WATCH_READ_ALOUD_STATUS_KEY = "readAloudStatus"
const val WATCH_ALERT_KEY = "watchAlert"
const val WATCH_UPDATED_AT_KEY = "updatedAtMillis"
const val WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS = 10 * 60 * 1000L
const val WATCH_SNAPSHOT_STALE_AGE_MILLIS = 30 * 60 * 1000L
const val WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1000L
const val WATCH_SNAPSHOT_MAX_BOARDS = 80
const val WATCH_SNAPSHOT_MAX_THREADS = 20
const val WATCH_SNAPSHOT_MAX_WATCH_WORDS = 50
const val WATCH_SNAPSHOT_MAX_PREVIEW_POSTS_PER_THREAD = 5

fun shouldAcceptWatchSnapshot(
    currentGeneratedAtMillis: Long?,
    incomingGeneratedAtMillis: Long,
    nowMillis: Long,
    maxFutureSkewMillis: Long = WATCH_SNAPSHOT_MAX_FUTURE_SKEW_MILLIS
): Boolean {
    if (nowMillis < 0L || maxFutureSkewMillis < 0L || incomingGeneratedAtMillis <= 0L) {
        return false
    }
    val latestPlausibleMillis = if (nowMillis > Long.MAX_VALUE - maxFutureSkewMillis) {
        Long.MAX_VALUE
    } else {
        nowMillis + maxFutureSkewMillis
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
