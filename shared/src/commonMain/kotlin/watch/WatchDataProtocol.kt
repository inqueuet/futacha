package com.valoser.futacha.shared.watch

const val WATCH_SNAPSHOT_PATH = "/futacha/watch_snapshot"
const val WATCH_SNAPSHOT_ACK_PATH = "/futacha/watch_snapshot_ack"
const val WATCH_REQUEST_SNAPSHOT_PATH = "/futacha/request_snapshot"
const val WATCH_COMMAND_PATH = "/futacha/command"
const val WATCH_SNAPSHOT_KEY = "snapshot"
const val WATCH_SNAPSHOT_ACK_KEY = "snapshotAck"
const val WATCH_COMMAND_KEY = "command"
const val WATCH_UPDATED_AT_KEY = "updatedAtMillis"
const val WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS = 10 * 60 * 1000L
const val WATCH_SNAPSHOT_STALE_AGE_MILLIS = 30 * 60 * 1000L
