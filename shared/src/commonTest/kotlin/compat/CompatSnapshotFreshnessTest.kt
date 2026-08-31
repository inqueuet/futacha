package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatSnapshotFreshnessTest {
    @Test
    fun catalogTimestampIsHiddenOnlyForRevisionRefreshedInCurrentSession() {
        assertNull(compatCatalogCachedAtForSession(7L, 123L, 7L))
        assertEquals(123L, compatCatalogCachedAtForSession(7L, 123L, null))
        assertEquals(123L, compatCatalogCachedAtForSession(7L, 123L, 6L))
    }

    @Test
    fun fullerLocalSnapshotWinsWhenServerCacheEndsEarly() {
        val local = snapshot(listOf("1", "2", "3"))
        val server = snapshot(listOf("1", "2"))
        assertTrue(shouldPreferLocalCompatSnapshot(local, server))
    }

    @Test
    fun completeLocalSnapshotWinsOverTruncatedServerAtSameCount() {
        val local = snapshot(listOf("1", "2"), truncated = false)
        val server = snapshot(listOf("1", "2"), truncated = true)
        assertTrue(shouldPreferLocalCompatSnapshot(local, server))
    }

    @Test
    fun newerServerSnapshotWinsWhenItHasMoreReplies() {
        val local = snapshot(listOf("1", "2"))
        val server = snapshot(listOf("1", "2", "3"))
        assertFalse(shouldPreferLocalCompatSnapshot(local, server))
    }

    @Test
    fun unrelatedPostSequencesDoNotPreferLocal() {
        val local = snapshot(listOf("1", "2", "3"))
        val server = snapshot(listOf("1", "9"))
        assertFalse(shouldPreferLocalCompatSnapshot(local, server))
    }

    @Test
    fun nonEmptyLocalSnapshotWinsOverEmptyRefresh() {
        val local = snapshot(listOf("1", "2"))
        val emptyServer = snapshot(emptyList())
        assertTrue(shouldPreferLocalCompatSnapshot(local, emptyServer))
    }

    private fun snapshot(ids: List<String>, truncated: Boolean = false) =
        CompatThreadSnapshot(
            tabKey = "tab",
            revision = 1L,
            fetchedAtEpochMillis = 1L,
            posts = ids.mapIndexed { index, id ->
                CompatPostSnapshot(
                    position = index,
                    postNo = id,
                    timestamp = "",
                    messageHtml = ""
                )
            },
            isTruncated = truncated
        )
}
