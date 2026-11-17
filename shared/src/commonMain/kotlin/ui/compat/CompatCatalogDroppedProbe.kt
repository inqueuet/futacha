package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ids of vanished catalog threads that must not be recorded as deleted: those a
 * probe confirmed alive, plus those left unprobed when [totalTimeoutMillis] ran
 * out (unknown). Results confirmed before the timeout are kept rather than
 * discarded, so a slow probe no longer turns every vanished thread into DELETED.
 */
internal suspend fun probeCompatDroppedThreadsNotDeleted(
    vanished: List<CatalogItem>,
    totalTimeoutMillis: Long,
    probe: suspend (CatalogItem) -> Boolean
): Set<String> {
    val confirmedAlive = mutableSetOf<String>()
    var probed = 0
    withTimeoutOrNull(totalTimeoutMillis) {
        vanished.forEach { item ->
            if (probe(item)) confirmedAlive.add(item.id)
            probed += 1
        }
    }
    return confirmedAlive + vanished.drop(probed).map { it.id }
}
