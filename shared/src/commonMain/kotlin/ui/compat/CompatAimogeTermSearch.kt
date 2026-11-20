package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.withTimeoutOrNull

internal data class CompatAimogeTermMatches(
    val rows: List<ArchiveSearchItem>,
    val termsByThread: Map<String, Set<String>>,
    val failure: Throwable?
)

internal suspend fun searchCompatAimogeTerms(
    terms: List<String>,
    timeoutMillis: Long = COMPAT_CACHE_SEARCH_TOTAL_TIMEOUT_MILLIS,
    search: suspend (String) -> List<ArchiveSearchItem>
): CompatAimogeTermMatches {
    val rows = linkedMapOf<String, ArchiveSearchItem>()
    val matches = mutableMapOf<String, MutableSet<String>>()
    var failure: Throwable? = null
    val complete = withTimeoutOrNull(timeoutMillis) {
        for (term in terms) {
            runSuspendCatchingPreservingCancellation { search(term) }.fold(
                onSuccess = { results ->
                    for (item in results) {
                        if (item.threadId !in rows) rows[item.threadId] = item
                        matches.getOrPut(item.threadId) { mutableSetOf() }.add(term)
                    }
                },
                onFailure = { if (failure == null) failure = it }
            )
        }
        true
    }
    return CompatAimogeTermMatches(
        rows.values.toList(),
        matches,
        if (complete == null) CompatCacheSearchTimeoutException() else failure
    )
}
