package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatCatalogDroppedProbeTest {
    private fun item(id: String) = CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", null, null, null, replyCount = 0)

    @Test
    fun timeoutKeepsConfirmedAliveAndTreatsUnprobedAsUnknown() = runBlocking {
        val vanished = listOf(item("1"), item("2"), item("3"), item("4"))
        val result = probeCompatDroppedThreadsNotDeleted(vanished, totalTimeoutMillis = 200) {
            when (it.id) {
                "1" -> true
                "2" -> false
                else -> awaitCancellation()
            }
        }
        // 1 is alive, 2 is confirmed gone, 3 timed out mid-probe and 4 was never probed.
        assertEquals(setOf("1", "3", "4"), result)
    }

    @Test
    fun completedProbeMarksOnlyAliveThreads() = runBlocking {
        val vanished = listOf(item("1"), item("2"))
        val result = probeCompatDroppedThreadsNotDeleted(vanished, totalTimeoutMillis = 5_000) { it.id == "2" }
        assertEquals(setOf("2"), result)
    }
}
