package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.flow.first

/**
 * iOS cannot read Android's ContentProvider-based にじろぐ(仮) database.
 * Surface the same useful result -- the app's persisted crawl/history list --
 * in the existing watcher drawer.  This list is populated by foreground and
 * BGTask refreshes, survives relaunch, and supports the same row/all deletion
 * actions as the provider page.
 */
internal class IosCompatExternalWatcher(
    private val store: CompatibilityStore
) : CompatExternalWatcher {
    override suspend fun load(): Result<CompatExternalWatcherSnapshot> = runSuspendCatchingPreservingCancellation {
        val entries = store.history.first()
            .sortedByDescending(CompatHistoryEntry::contentUpdatedAtEpochMillis)
            .take(MAX_IOS_COMPAT_WATCHER_ROWS)
            .map(CompatHistoryEntry::toIosWatcherEntry)
        CompatExternalWatcherSnapshot(
            installed = true,
            available = true,
            message = "アプリ内バックグラウンド巡回の結果",
            entries = entries
        )
    }

    override suspend fun delete(key: String): Result<Unit> = runSuspendCatchingPreservingCancellation {
        store.deleteHistory(key)
    }

    override suspend fun deleteAll(): Result<Unit> = runSuspendCatchingPreservingCancellation {
        store.clearHistory()
    }

    // The surrounding drawer is the manager on iOS.  There is no foreign app
    // to launch, so do not claim that an unavailable manager was opened.
    override fun openManager(): Result<Unit> = Result.failure(
        UnsupportedOperationException("巡回の設定は、このアプリの設定画面から変更してください")
    )
}

@Composable
internal actual fun rememberCompatExternalWatcher(store: CompatibilityStore): CompatExternalWatcher = remember(store) {
    IosCompatExternalWatcher(store)
}

private fun CompatHistoryEntry.toIosWatcherEntry(): CompatExternalWatcherEntry = CompatExternalWatcherEntry(
    key = canonicalUrl,
    active = true,
    title = title,
    replyCount = replyCount,
    threadUrl = originalUrl,
    thumbnailUrl = thumbnailUrl,
    boardKey = boardKey,
    boardName = boardName,
    updatedAtEpochMillis = contentUpdatedAtEpochMillis,
    insertedAtEpochMillis = contentUpdatedAtEpochMillis
)

private const val MAX_IOS_COMPAT_WATCHER_ROWS = 100
