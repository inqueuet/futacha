package com.valoser.futacha.shared.ui.board

internal const val THREAD_SCREEN_TAG = "ThreadScreen"
internal const val THREAD_ACTION_LOG_TAG = "ThreadActions"
internal const val DEFAULT_DEL_REASON_CODE = "110"
internal const val AUTO_SAVE_INTERVAL_MS = 60_000L
internal const val THREAD_AUTO_SAVE_TAG = "ThreadAutoSave"
internal const val ARCHIVE_FALLBACK_TIMEOUT_MS = 20_000L
internal const val OFFLINE_FALLBACK_TIMEOUT_MS = 10_000L
internal const val THREAD_SEARCH_DEBOUNCE_MILLIS = 180L
internal const val THREAD_FILTER_DEBOUNCE_MILLIS = 120L
internal const val THREAD_FILTER_CACHE_MAX_ENTRIES = 8
internal const val ACTION_BUSY_NOTICE_INTERVAL_MS = 1_000L

internal fun isSaveLocationPermissionIssue(error: Throwable): Boolean =
    isThreadSaveLocationPermissionIssue(error)
