package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

data class CompatBackgroundRefreshResult(
    val updatedTabs: Int = 0,
    val deadTabs: Int = 0,
    val skippedTabs: Int = 0,
    val failures: Int = 0,
    val newWatchMatches: List<CompatWatchMatch> = emptyList()
)

/**
 * Refreshes compatibility tabs without requiring the compatibility UI to be alive.
 * The worker deliberately does not mark a tab as read: checkedReplyCount remains
 * unchanged so the next foreground visit still shows the unread count.
 */
suspend fun refreshCompatTabsInBackground(
    store: CompatibilityStore,
    repository: BoardRepository,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    maxTabs: Int = 20,
    checkUpdates: Boolean = true,
    checkExistence: Boolean = true,
    existenceStaleMillis: Long = COMPAT_THREAD_EXISTENCE_STALE_MILLIS,
    checkWatchWords: Boolean = false,
    /**
     * Platform hosts use this to reject writes after an experience-profile
     * switch.  The default preserves the existing Android call semantics.
     */
    commitGate: suspend (suspend () -> Unit) -> Boolean = { commit -> commit(); true }
): CompatBackgroundRefreshResult {
    var updated = 0
    var dead = 0
    var skipped = 0
    var failures = 0
    val newWatchMatches = mutableListOf<CompatWatchMatch>()

    val tabs = store.tabs.firstCompatBackgroundTabs(maxTabs)
    val boards = store.boards.first()

    if (checkUpdates) {
        val histories = store.history.first()
        val historiesByCanonicalUrl = histories.associateBy(CompatHistoryEntry::canonicalUrl)
        boards.forEach boardLoop@{ board ->
            val boardTabs = tabs.filter { it.boardKey == board.key }
            if (boardTabs.isEmpty()) return@boardLoop
            try {
                val catalog = repository.getCatalog(board.originalUrl, CatalogMode.Catalog)
                val byCanonicalUrl = catalog.mapNotNull { item ->
                    com.valoser.futacha.shared.compat.canonicalizeThreadUrl(item.threadUrl)
                        ?.canonicalUrl
                        ?.let { it to item }
                }.toMap()
                val byThreadId = catalog.associateBy { it.id }
                boardTabs.forEach tabLoop@{ tab ->
                    val item = byCanonicalUrl[tab.canonicalUrl]
                        ?: byThreadId[tab.threadNo]
                        ?: return@tabLoop
                    if (item.replyCount == tab.replyCount) return@tabLoop
                    val committed = commitGate {
                        store.updateTab(tab.copy(replyCount = item.replyCount))
                        historiesByCanonicalUrl[tab.canonicalUrl]?.let { history ->
                            store.upsertHistory(history.copy(replyCount = item.replyCount))
                        }
                    }
                    if (committed) updated++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failures++
            }
        }
    }

    if (checkExistence) {
        tabs.filter { tab ->
            hasEpochIntervalElapsed(
                nowMillis = nowEpochMillis,
                startedAtMillis = tab.contentUpdatedAtEpochMillis,
                intervalMillis = existenceStaleMillis
            )
        }.forEach { tab ->
            try {
                if (repository.probeThreadGone(tab.originalUrl)) {
                    if (commitGate { store.updateTab(tab.copy(isDead = true)) }) dead++
                } else {
                    skipped++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failures++
            }
        }
    }

    if (checkWatchWords) {
        val watchWords = parseCompatWatchWords(
            store.preferences.first()["compat.catalog.監視ワード"]
        )
        if (watchWords.isNotEmpty()) {
            val existingHistory = store.history.first()
            boards.forEach { board ->
                listOf(CatalogMode.New, CatalogMode.Old).forEach { mode ->
                    try {
                        val catalog = repository.getCatalog(board.originalUrl, mode)
                        val matches = collectCompatWatchMatches(
                            board = board,
                            items = catalog,
                            watchWords = watchWords,
                            existingHistory = existingHistory + newWatchMatches.map(CompatWatchMatch::history),
                            nowEpochMillis = nowEpochMillis
                        )
                        matches.forEach { match ->
                            if (commitGate { store.upsertHistory(match.history) } && match.isNew) {
                                newWatchMatches += match
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        failures++
                    }
                }
            }
        }
    }

    return CompatBackgroundRefreshResult(updated, dead, skipped, failures, newWatchMatches)
}

private suspend fun kotlinx.coroutines.flow.Flow<List<CompatTab>>.firstCompatBackgroundTabs(
    maxTabs: Int
): List<CompatTab> = first()
    .asSequence()
    .filterNot(CompatTab::isDead)
    .sortedBy(CompatTab::contentUpdatedAtEpochMillis)
    .take(maxTabs.coerceAtLeast(1))
    .toList()
