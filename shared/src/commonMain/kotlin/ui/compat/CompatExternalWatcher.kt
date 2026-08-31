package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable

/**
 * A row supplied by the optional legacy "にじろぐ(仮)" watcher application.
 *
 * The reference APK does not derive this page from its own history.  It reads
 * the watcher's crawl log through an Android ContentProvider, so keep this
 * source separate from the compatibility mode's built-in watch-word list.
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
 * Android reads the optional legacy provider.  Platforms without a provider
 * receive the compatibility store so they can present an equivalent in-app
 * crawl result list instead of a successful-looking no-op.
 */
internal expect fun rememberCompatExternalWatcher(
    store: com.valoser.futacha.shared.compat.CompatibilityStore
): CompatExternalWatcher
