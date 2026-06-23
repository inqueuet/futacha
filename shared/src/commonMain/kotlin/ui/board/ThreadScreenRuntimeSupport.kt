package com.valoser.futacha.shared.ui.board

internal const val THREAD_SCREEN_TAG = "ThreadScreen"
internal const val THREAD_ACTION_LOG_TAG = "ThreadActions"
internal const val DEFAULT_DEL_REASON_CODE = "110"
internal const val AUTO_SAVE_INTERVAL_MS = 60_000L
internal const val THREAD_MANUAL_SAVE_TIMEOUT_MS = 3 * 60_000L
internal const val THREAD_SINGLE_MEDIA_SAVE_TIMEOUT_MS = 150_000L
internal const val THREAD_REMOTE_LOAD_TIMEOUT_MS = 20_000L
internal const val THREAD_LOCAL_STALE_LOAD_TIMEOUT_MS = 500L
internal const val THREAD_AUTO_SAVE_TAG = "ThreadAutoSave"
internal const val ARCHIVE_FALLBACK_TIMEOUT_MS = 8_000L
internal const val OFFLINE_FALLBACK_TIMEOUT_MS = 5_000L
internal const val THREAD_SEARCH_DEBOUNCE_MILLIS = 180L
internal const val THREAD_FILTER_DEBOUNCE_MILLIS = 120L
internal const val THREAD_FILTER_CACHE_MAX_ENTRIES = 8
internal const val ACTION_BUSY_NOTICE_INTERVAL_MS = 1_000L

internal fun resolveThreadDebouncedSearchQuery(query: String): String {
    return query.trim()
}

internal fun resolveThreadSearchDebounceMillis(query: String): Long {
    return if (query.isEmpty()) 0L else THREAD_SEARCH_DEBOUNCE_MILLIS
}

internal fun isSaveLocationPermissionIssue(error: Throwable): Boolean =
    isThreadSaveLocationPermissionIssue(error)
