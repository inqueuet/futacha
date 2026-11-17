package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

/**
 * A crawl-log row supplied by the built-in watcher or optional にじろぐ provider.
 * Neither source is derived from the browsing history.
 */
internal data class CompatExternalWatcherEntry(
    val key: String,
    val active: Boolean,
    val title: String,
    val replyCount: Int,
    val threadUrl: String,
    val categoryUrl: String? = null,
    val thumbnailUrl: String? = null,
    val extractedKeyword: String? = null,
    val boardKey: String? = null,
    val boardName: String? = null,
    val boardUrl: String? = null,
    val updatedAtEpochMillis: Long = 0L,
    val insertedAtEpochMillis: Long = 0L
)

internal data class CompatExternalWatcherSnapshot(
    val installed: Boolean = false,
    val available: Boolean = false,
    val message: String? = null,
    val entries: List<CompatExternalWatcherEntry> = emptyList()
)

internal interface CompatExternalWatcher {
    suspend fun load(): Result<CompatExternalWatcherSnapshot>
    suspend fun delete(key: String): Result<Unit>
    suspend fun deleteAll(): Result<Unit>
    fun openManager(): Result<Unit>
}

@Composable
/**
 * The built-in log is the default on every platform. Android can additionally
 * select the optional legacy provider without merging either source with history.
 */
internal expect fun rememberCompatExternalWatcher(
    store: com.valoser.futacha.shared.compat.CompatibilityStore
): CompatExternalWatcher
