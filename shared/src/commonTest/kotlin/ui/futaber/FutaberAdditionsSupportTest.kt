package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The small additions that bring ふたちゃ / としあき(仮) behaviour to the ふたばー風 screens without changing them. */
class FutaberAdditionsSupportTest {
    private fun item(id: String) = CatalogItem(
        id = id, threadUrl = "https://may.2chan.net/b/res/$id.htm", title = "スレ$id",
        thumbnailUrl = null, fullImageUrl = null, replyCount = 1
    )
    private fun items(vararg ids: String) = ids.map(::item)
    private fun board(id: String, url: String, name: String = "板$id") = BoardSummary(id, name, "", url, "")
    private fun post(id: String, author: String? = null, posterId: String? = null) = Post(
        id = id, author = author, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = null, thumbnailUrl = null, posterId = posterId
    )

    // ---- catalog history ("更新前に戻す" / "消えたスレ") ----

    @Test fun aFirstLoadHasNothingToGoBackTo() {
        val history = futaberCatalogHistoryAfterLoad(FutaberCatalogHistory(), emptyList(), items("1", "2"))
        assertEquals(FutaberCatalogHistory(), history)
        assertNull(futaberCatalogGoBack(history))
    }

    @Test fun aReloadThatChangedTheListKeepsTheOldOneAndTheThreadsThatDroppedOut() {
        val history = futaberCatalogHistoryAfterLoad(FutaberCatalogHistory(), items("1", "2", "3"), items("2", "3", "4"))
        assertEquals(listOf(items("1", "2", "3")), history.earlier)
        assertEquals(listOf("1"), history.dropped.map { it.id })
        val (list, rest) = futaberCatalogGoBack(history)!!
        assertEquals(listOf("1", "2", "3"), list.map { it.id })
        assertTrue(rest.earlier.isEmpty())
        // Thread 1 is on screen again, so it is no longer one that "dropped out".
        assertTrue(rest.dropped.isEmpty())
    }

    @Test fun aReloadThatChangedNothingLeavesTheHistoryAlone() {
        val before = futaberCatalogHistoryAfterLoad(FutaberCatalogHistory(), items("1", "2"), items("2", "3"))
        val after = futaberCatalogHistoryAfterLoad(before, items("2", "3"), items("2", "3"))
        assertSame(before, after)
    }

    @Test fun onlyTheLatestFewListsAreKeptAndAThreadThatCameBackIsNoLongerGone() {
        var history = FutaberCatalogHistory()
        var shown = items("1", "2")
        listOf(items("2", "3"), items("3", "4"), items("4", "5"), items("5", "6"), items("6", "7")).forEach { next ->
            history = futaberCatalogHistoryAfterLoad(history, shown, next)
            shown = next
        }
        assertEquals(FUTABER_CATALOG_EARLIER_MAX, history.earlier.size)
        assertEquals(items("5", "6"), history.earlier.first())
        // 1 to 5 each dropped out at some point, newest first.
        assertEquals(listOf("5", "4", "3", "2", "1"), history.dropped.map { it.id })
        history = futaberCatalogHistoryAfterLoad(history, shown, items("6", "7", "1"))
        assertTrue("1" !in history.dropped.map { it.id })
    }

    // ---- NG registration of a long-pressed post ----

    @Test fun theIdIsOfferedFirstThenTheNameButNeverADefaultName() {
        assertEquals("AbC123", futaberNgHeaderOf(post("1", author = "太郎", posterId = " AbC123 ")))
        assertEquals("太郎", futaberNgHeaderOf(post("1", author = " 太郎 ")))
        assertEquals("", futaberNgHeaderOf(post("1", author = "としあき")))
        assertEquals("", futaberNgHeaderOf(post("1", author = "無念")))
        assertEquals("", futaberNgHeaderOf(post("1")))
    }

    @Test fun anNgThreadRuleIsShownAsItsTitleAndAddress() {
        val rule = CompatNgRule("id", CompatNgKind.CATALOG_REFUSE, "k", "https://may.2chan.net/b/res/1.htm", 1L, memo = "スレタ")
        assertEquals("スレタ　https://may.2chan.net/b/res/1.htm", futaberCatalogThreadRuleLabel(rule))
        assertEquals("https://may.2chan.net/b/res/1.htm", futaberCatalogThreadRuleLabel(rule.copy(memo = "")))
    }

    // ---- boards ----

    @Test fun renamingChangesOnlyThatBoardAndRefusesAnEmptyOrUnchangedName() {
        val boards = listOf(board("a", "https://may.2chan.net/b/futaba.php"), board("b", "https://img.2chan.net/b/futaba.php"))
        assertEquals(listOf("新しい名前", "板b"), futaberRenameBoard(boards, "a", "  新しい名前 ").map { it.name })
        assertSame(boards, futaberRenameBoard(boards, "a", "   "))
        assertSame(boards, futaberRenameBoard(boards, "a", "板a"))
        assertSame(boards, futaberRenameBoard(boards, "zzz", "x"))
        assertEquals(FUTABER_BOARD_NAME_MAX_CHARS, futaberRenameBoard(boards, "a", "あ".repeat(100))[0].name.length)
    }

    @Test fun discoveredBoardsAreAddedOnceInOrderAndRegisteredOnesAreSkipped() {
        val boards = listOf(board("a", "https://may.2chan.net/b/futaba.php", "may"))
        val discovered = listOf(
            "may" to "https://may.2chan.net/b/",
            "img" to "https://img.2chan.net/b/",
            "dat" to "https://dat.2chan.net/b/",
            "img again" to "https://img.2chan.net/b/",
            "bad" to "not an address"
        )
        val result = futaberAddDiscoveredBoards(boards, discovered)
        assertEquals(listOf("may", "img", "dat"), result.map { it.name })
        // Nothing new: the list is the same one.
        assertEquals(result, futaberAddDiscoveredBoards(result, discovered))
    }

    @Test fun aLockPasswordNeedsTheMinimumLengthAndAMatchingConfirmation() {
        assertTrue(futaberAppLockPasswordError("abc", "abc")!!.contains("4文字以上"))
        assertEquals("確認用パスワードが一致しません。", futaberAppLockPasswordError("abcd", "abce"))
        assertNull(futaberAppLockPasswordError("abcd", "abcd"))
    }
}
