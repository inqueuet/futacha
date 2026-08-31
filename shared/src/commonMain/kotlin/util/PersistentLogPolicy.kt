package com.valoser.futacha.shared.util

const val PERSISTENT_ERROR_LOG_FILE_NAME = "error_log.txt"
const val PERSISTENT_ERROR_LOG_MAX_BYTES = 10L * 1024L * 1024L

fun shouldResetPersistentLog(currentSizeBytes: Long): Boolean =
    currentSizeBytes > PERSISTENT_ERROR_LOG_MAX_BYTES

internal fun formatPersistentLogLine(level: String, tag: String, message: String): String {
    val safeTag = tag.replace('\n', ' ').replace('\r', ' ')
    val safeMessage = message.replace('\r', ' ').replace('\n', ' ')
    return "$level [$safeTag] $safeMessage\n"
}
