package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.ArchiveSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals

class PastThreadSearchSupportTest {
    @Test
    fun normalizePastThreadSearchQuery_trimsWhitespace() {
        assertEquals("猫", normalizePastThreadSearchQuery("  猫  "))
    }

    @Test
    fun buildPastThreadSearchErrorMessage_fallsBackWhenMessageIsNull() {
        assertEquals("検索に失敗しました", buildPastThreadSearchErrorMessage(IllegalStateException()))
        assertEquals("boom", buildPastThreadSearchErrorMessage(IllegalStateException("boom")))
    }

    @Test
    fun archiveSearchItemToCatalogItem_mapsArchiveFields() {
        val item = ArchiveSearchItem(
            threadId = "123",
            server = "may",
            board = "b",
            title = "タイトル",
            htmlUrl = "https://may.2chan.net/b/res/123.htm",
            thumbUrl = "https://img.example/123s.jpg"
        )

        val catalogItem = archiveSearchItemToCatalogItem(item)

        assertEquals("123", catalogItem.id)
        assertEquals("https://may.2chan.net/b/res/123.htm", catalogItem.threadUrl)
        assertEquals("タイトル", catalogItem.title)
        assertEquals("https://img.example/123s.jpg", catalogItem.thumbnailUrl)
        assertEquals("https://img.example/123s.jpg", catalogItem.fullImageUrl)
        assertEquals(0, catalogItem.replyCount)
    }

    @Test
    fun pastThreadSearchMessages_matchUiCopy() {
        assertEquals("検索ワードを入力するとここに結果が表示されます。", buildPastThreadSearchIdleMessage())
        assertEquals("見つかりませんでした", buildPastThreadSearchEmptyMessage())
    }
}
