package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import kotlin.test.Test
import kotlin.test.assertEquals

class PastThreadSearchSupportTest {
    @Test
    fun normalizePastThreadSearchQuery_trimsWhitespace() {
        assertEquals("猫", normalizePastThreadSearchQuery("  猫  "))
    }

    @Test
    fun buildPastThreadSearchDialogInitialQuery_prefersArchiveQuery() {
        assertEquals(
            "archive",
            buildPastThreadSearchDialogInitialQuery(
                archiveSearchQuery = "archive",
                searchQuery = "search"
            )
        )
        assertEquals(
            "search",
            buildPastThreadSearchDialogInitialQuery(
                archiveSearchQuery = "",
                searchQuery = "search"
            )
        )
    }

    @Test
    fun buildPastThreadSearchStartState_trimsAndShowsSheet() {
        val scope = ArchiveSearchScope(server = "may", board = "img")

        val state = buildPastThreadSearchStartState("  cat  ", scope)

        assertEquals("cat", state.normalizedQuery)
        assertEquals(scope, state.scope)
        assertEquals(false, state.shouldShowDialog)
        assertEquals(true, state.shouldShowSheet)
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

    @Test
    fun dismissPastThreadSearchSheet_hidesSheetAndIncrementsGeneration() {
        val state = dismissPastThreadSearchSheet(currentGeneration = 4L)

        assertEquals(5L, state.nextGeneration)
        assertEquals(false, state.shouldShowSheet)
        assertEquals(true, state.shouldClearRunningJob)
    }

    @Test
    fun selectPastThreadSearchItem_hidesSheetAndMapsSelectedItem() {
        val item = ArchiveSearchItem(
            threadId = "123",
            server = "may",
            board = "b",
            title = "タイトル",
            htmlUrl = "https://may.2chan.net/b/res/123.htm",
            thumbUrl = "https://img.example/123s.jpg"
        )

        val state = selectPastThreadSearchItem(currentGeneration = 7L, item = item)

        assertEquals(8L, state.sheetState.nextGeneration)
        assertEquals(false, state.sheetState.shouldShowSheet)
        assertEquals(true, state.sheetState.shouldClearRunningJob)
        assertEquals("123", state.selectedCatalogItem.id)
        assertEquals("https://may.2chan.net/b/res/123.htm", state.selectedCatalogItem.threadUrl)
    }
}
