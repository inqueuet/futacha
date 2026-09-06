package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class CompatInternalWatcher(private val store: CompatibilityStore) : CompatExternalWatcher {
    private val repository = CompatWatcherRepository(store)

    @OptIn(ExperimentalTime::class)
    override suspend fun load() = runSuspendCatchingPreservingCancellation {
        CompatExternalWatcherSnapshot(
            installed = true, available = true, message = "アプリ内巡回の結果（7日間・最大500件）",
            entries = repository.load(Clock.System.now().toEpochMilliseconds()).map { result ->
                val entry = result.history
                CompatExternalWatcherEntry(
                    key = entry.canonicalUrl, active = result.active, title = entry.title,
                    replyCount = entry.replyCount, threadUrl = entry.originalUrl,
                    thumbnailUrl = entry.thumbnailUrl, extractedKeyword = result.keyword,
                    boardKey = entry.boardKey, boardName = entry.boardName,
                    updatedAtEpochMillis = entry.contentUpdatedAtEpochMillis,
                    insertedAtEpochMillis = result.insertedAtEpochMillis
                )
            }
        )
    }

    override suspend fun delete(key: String) = runSuspendCatchingPreservingCancellation { repository.delete(key) }
    override suspend fun deleteAll() = runSuspendCatchingPreservingCancellation { repository.deleteAll() }
    override fun openManager(): Result<Unit> = Result.failure(UnsupportedOperationException("アプリ内の巡回管理を開いてください"))
}

/** Preserve optional Android provider access; use the built-in log by default. */
internal class CompatSelectableWatcher(
    private val store: CompatibilityStore,
    private val external: CompatExternalWatcher
) : CompatExternalWatcher {
    private val local = CompatInternalWatcher(store)
    private suspend fun selected() = if (store.preferences.first()[COMPAT_WATCH_EXTERNAL_KEY] == "ON") external else local
    override suspend fun load() = selected().load()
    override suspend fun delete(key: String) = selected().delete(key)
    override suspend fun deleteAll() = selected().deleteAll()
    override fun openManager() = external.openManager()
}
