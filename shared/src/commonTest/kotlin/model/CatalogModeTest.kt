package com.valoser.futacha.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogModeTest {
    @Test
    fun watchWordsMode_filtersAndSortsMatchedThreads() {
        val items = listOf(
            CatalogItem(
                id = "100",
                threadUrl = "https://may.2chan.net/b/res/100.htm",
                title = "猫と犬",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 5
            ),
            CatalogItem(
                id = "200",
                threadUrl = "https://may.2chan.net/b/res/200.htm",
                title = "猫 夏休み",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 10
            ),
            CatalogItem(
                id = "300",
                threadUrl = "https://may.2chan.net/b/res/300.htm",
                title = "雑談",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 99
            ),
            CatalogItem(
                id = "400",
                threadUrl = "https://may.2chan.net/b/res/400.htm",
                title = "猫",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 20
            )
        )

        val result = CatalogMode.WatchWords.applyClientTransform(
            items = items,
            watchWords = listOf("猫", "夏休み")
        )

        assertEquals(listOf("200", "400", "100"), result.map { it.id })
    }

    @Test
    fun watchWordsMode_returnsEmptyWhenNoWatchWords() {
        val items = listOf(
            CatalogItem(
                id = "100",
                threadUrl = "https://may.2chan.net/b/res/100.htm",
                title = "猫と犬",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = 5
            )
        )

        val result = CatalogMode.WatchWords.applyClientTransform(
            items = items,
            watchWords = emptyList()
        )

        assertEquals(emptyList(), result)
    }
}
