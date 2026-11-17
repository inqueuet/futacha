package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutachaBatch1Test {
    private fun entry(keyword: String? = null, board: String? = "may", replies: Int = 12, active: Boolean = true) =
        CompatExternalWatcherEntry(
            key = "k", active = active, title = "t", replyCount = replies, threadUrl = "https://may.2chan.net/b/res/1.htm",
            extractedKeyword = keyword, boardName = board
        )

    @Test fun watcherSubtitleShowsMatchedWordBoardAndReplyCount() {
        assertEquals("「ねこ」may・12レス", futachaWatcherSubtitle(entry("ねこ")))
    }

    @Test fun watcherSubtitleMarksDroppedThreads() {
        assertEquals("「ねこ」may・12レス・落ち", futachaWatcherSubtitle(entry("ねこ", active = false)))
    }

    @Test fun watcherSubtitleOmitsBlankWordAndMissingBoard() {
        assertEquals("may・12レス", futachaWatcherSubtitle(entry("  ")))
        assertEquals("「x」・3レス", futachaWatcherSubtitle(entry("x", board = null, replies = 3)))
        assertEquals("・0レス・落ち", futachaWatcherSubtitle(entry(null, board = null, replies = 0, active = false)))
    }

    @Test fun deleteAllConfirmationMentionsSharedResults() {
        assertTrue(FUTACHA_WATCHER_DELETE_ALL_CONFIRMATION.startsWith("巡回結果をすべて削除しますか？"))
        assertTrue("共有" in FUTACHA_WATCHER_DELETE_ALL_CONFIRMATION)
    }

    private fun item(id: String = "123456", url: String = "res/123456.htm") =
        CatalogItem(id, url, "題", null, null, replyCount = 1)

    @Test fun catalogThreadUrlIsResolvedFromTheBoardUrl() {
        assertEquals("https://may.2chan.net/b/res/123456.htm", futachaCatalogThreadUrl("https://may.2chan.net/b/", item()))
        assertEquals("https://may.2chan.net/b/res/123456.htm", futachaCatalogThreadUrl("https://may.2chan.net/b/futaba.php", item()))
    }

    @Test fun catalogThreadUrlFallsBackToTheCatalogsUrlWhenItCannotBeResolved() {
        assertEquals("res/abc.htm", futachaCatalogThreadUrl("https://may.2chan.net/b/", item(id = "abc", url = "res/abc.htm")))
        assertEquals("res/1.htm", futachaCatalogThreadUrl("", item(id = "1", url = "res/1.htm")))
    }

    @Test fun galleryKeepsTwoColumnsUnlessTheUserSavedACount() {
        assertEquals(2, futachaGalleryColumnCount(null))
        assertEquals(2, futachaGalleryColumnCount(""))
        assertEquals(2, futachaGalleryColumnCount("abc"))
        assertEquals(2, futachaGalleryColumnCount("2.5"))
    }

    @Test fun galleryUsesTheSavedCountWithinTwoToFour() {
        assertEquals(3, futachaGalleryColumnCount("3"))
        assertEquals(4, futachaGalleryColumnCount(" 4 "))
        assertEquals(4, futachaGalleryColumnCount("5"))
        assertEquals(4, futachaGalleryColumnCount("16"))
        assertEquals(2, futachaGalleryColumnCount("1"))
        assertEquals(2, futachaGalleryColumnCount("-3"))
    }
}
