package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatWatchRefreshTest {
    private val board = CompatBoard(
        key = "may-b",
        name = "二次元裏",
        canonicalUrl = "https://may.2chan.net/b/",
        originalUrl = "https://may.2chan.net/b/",
        sortOrder = 0
    )

    @Test
    fun collectCompatWatchMatches_addsOnlyNewMatchingThreadsAndPreservesAnchor() {
        val existing = CompatHistoryEntry(
            canonicalUrl = "https://may.2chan.net/b/res/2.htm",
            originalUrl = "https://may.2chan.net/b/res/2.htm",
            boardKey = board.key,
            boardName = board.name,
            threadNo = "2",
            title = "旧題名",
            replyCount = 1,
            contentUpdatedAtEpochMillis = 1L,
            scrollAnchor = ScrollAnchor(postNo = "42", offsetPx = 7, fallbackIndex = 3)
        )
        val matches = collectCompatWatchMatches(
            board = board,
            items = listOf(
                CatalogItem("1", "https://may.2chan.net/b/res/1.htm", "猫の話", null, null, replyCount = 4),
                CatalogItem("2", "https://may.2chan.net/b/res/2.htm", "猫の続き", null, null, replyCount = 8),
                CatalogItem("3", "https://may.2chan.net/b/res/3.htm", "別の話", null, null, replyCount = 9)
            ),
            watchWords = listOf(" 猫 ", "猫"),
            existingHistory = listOf(existing),
            nowEpochMillis = 100L
        )

        assertEquals(listOf("1", "2"), matches.map { it.history.threadNo })
        assertTrue(matches.first().isNew)
        assertEquals(false, matches[1].isNew)
        assertEquals(8, matches[1].history.replyCount)
        assertEquals("42", matches[1].history.scrollAnchor.postNo)
        assertEquals(7, matches[1].history.scrollAnchor.offsetPx)
    }

    @Test
    fun parseCompatWatchWords_normalizesFullWidthAndRemovesDuplicates() {
        assertEquals(listOf("cat", "猫"), parseCompatWatchWords(" ＣＡＴ\n猫\n猫\n"))
    }
}
