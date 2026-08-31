package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatArchiveSearchTest {
    private val items = listOf(
        ArchiveSearchItem("100", "may", "b", title = "猫 と 空", htmlUrl = "https://may.inqueuet.com/b/res/100.htm"),
        ArchiveSearchItem("200", "may", "b", title = "猫", htmlUrl = "https://may.inqueuet.com/b/res/200.htm"),
        ArchiveSearchItem("300", "may", "b", title = "空", htmlUrl = "https://may.inqueuet.com/b/res/300.htm")
    )

    @Test
    fun orAndSearchModesFilterResultTitlesAndNumbers() {
        assertEquals(
            listOf("100", "200", "300"),
            filterCompatArchiveSearchItems(items, "猫 空", "OR").map { it.threadId }
        )
        assertEquals(
            listOf("100"),
            filterCompatArchiveSearchItems(items, "猫 空", "AND").map { it.threadId }
        )
        assertEquals(listOf("200"), filterCompatArchiveSearchItems(items, "200", "OR").map { it.threadId })
    }

    @Test
    fun archiveResultConvertsToOriginalFutabaThreadUrl() {
        assertEquals(
            "https://may.2chan.net/b/res/100.htm",
            buildCompatArchiveSourceThreadUrl(items.first())
        )
    }

    @Test
    fun archiveResultRejectsUnsafeServerBoardAndThread() {
        assertNull(buildCompatArchiveSourceThreadUrl(items.first().copy(server = "may.evil")))
        assertNull(buildCompatArchiveSourceThreadUrl(items.first().copy(board = "b/res")))
        assertNull(buildCompatArchiveSourceThreadUrl(items.first().copy(threadId = "1/2")))
    }

    @Test
    fun mergeAddsSameBoardDeviceHistoryAndDeduplicatesByThread() {
        val history = listOf(
            CompatHistoryEntry(
                canonicalUrl = "https://may.2chan.net/b/res/200.htm",
                originalUrl = "https://may.2chan.net/b/res/200.htm",
                boardKey = "may-b",
                boardName = "may/b",
                threadNo = "200",
                title = "端末履歴の重複",
                replyCount = 9,
                contentUpdatedAtEpochMillis = 1L
            ),
            CompatHistoryEntry(
                canonicalUrl = "https://may.2chan.net/b/res/400.htm",
                originalUrl = "https://may.2chan.net/b/res/400.htm",
                boardKey = "may-b",
                boardName = "may/b",
                threadNo = "400",
                title = "端末だけ",
                replyCount = 3,
                contentUpdatedAtEpochMillis = 1L
            )
        )
        val merged = mergeCompatArchiveSearchItems(
            remote = items,
            localHistory = history,
            scope = com.valoser.futacha.shared.network.ArchiveSearchScope("may", "b")
        )
        assertEquals(listOf("100", "200", "300", "400"), merged.map { it.threadId })
        assertEquals("猫", merged[1].title)
    }
}
