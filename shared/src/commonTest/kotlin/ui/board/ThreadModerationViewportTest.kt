package com.valoser.futacha.shared.ui.board

import kotlin.test.*

class ThreadModerationViewportTest {
    @Test fun sendsOnlyVisiblePostsAndEightNeighborsAndWaitsForLayout() {
        val ids = (1..1000).map(Int::toString)
        assertTrue(moderationNearbyPostIds(ids, emptySet()).isEmpty())
        assertEquals((492..511).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("500", "501", "502", "503")))
        assertEquals((1..9).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("1")))
        assertEquals((992..1000).map(Int::toString).toSet(), moderationNearbyPostIds(ids, setOf("1000")))
    }

    @Test fun filteredAndReorderedListsUseIdentitiesAndIgnoreHeaderKeys() {
        val ids = listOf("90", "2", "73", "5", "22")
        assertEquals(setOf("2", "73", "5"), moderationNearbyPostIds(ids, setOf("73", "thread-summary"), radius = 1))
        assertEquals(setOf("90", "2", "5", "22"), moderationNearbyPostIds(ids, setOf("90", "22"), radius = 1))
    }
}
