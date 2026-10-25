package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.AiCommandHoldDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Batch 0 of the mode feature parity work: the AI command notice, the media menu entries, the thread NG list, the tab clean-up. */
class FutaberParityFoundationTest {
    // ---- AI commands (X10-0) ----

    @Test
    fun anAiCommandIsAnsweredAsNotSupportedHereUnlessItWasDropped() {
        val command = FutachaAiCommand(FutachaAiAction.OpenBoardList)
        val answered = futaberAiCommandNotice(command, AiCommandHoldDecision.Run)
        assertTrue(answered!!.contains(FutachaAiAction.OpenBoardList.label))
        assertTrue(answered.contains("ふたばー風モードでは使えません"))
        assertNull(futaberAiCommandNotice(command, AiCommandHoldDecision.DropExpired), "too old for its sender: dropped quietly")
        assertTrue(futaberAiCommandNotice(command, AiCommandHoldDecision.DropHeldByLock)!!.contains("起動ロック"))
    }

    @Test
    fun anAiLinkIsAnsweredToo() {
        assertTrue(futaberAiDeepLinkNotice("futacha://ai?action=open_board_list").contains("ふたばー風モードでは使えません"))
        assertEquals(FUTABER_AI_LINK_UNREADABLE_NOTICE, futaberAiDeepLinkNotice("futacha://ai?action=no_such_action"))
        assertEquals(FUTABER_AI_LINK_UNREADABLE_NOTICE, futaberAiDeepLinkNotice("https://example.com/"))
    }

    /** L121: the watch's "スマホで開く" link is not this mode's AI command: it is sent to ふたちゃ mode. */
    @Test
    fun aWatchRelayLinkIsSentToFutachaMode() {
        val relay = futaberAiDeepLinkNotice("futacha://ai?action=open_thread&boardId=b&threadId=1&relay=wear-os")
        assertTrue(relay.contains("ウォッチ"), relay)
        assertTrue(relay.contains("ふたちゃモードで実行してください"), relay)
        assertTrue(!relay.contains("ふたばー風モードでは使えません"), relay)
    }

    // ---- media features ----

    @Test
    fun theMediaEditEntriesShowOnlyWhileTheirEditorIsOn() {
        var launched = ""
        var dismissed = 0
        assertTrue(futaberMediaEditMenuItems(null, null) { dismissed++ }.isEmpty(), "the defaults leave the menu as it was")
        val both = futaberMediaEditMenuItems({ launched += "i" }, { launched += "v" }, onDismiss = { dismissed++ })
        assertEquals(listOf("画像編集", "動画編集"), both.map { it.label })
        both.forEach { it.onClick() }
        assertEquals("iv", launched)
        assertEquals(2, dismissed, "the menu closes before the picker opens")
        assertEquals(listOf("動画編集"), futaberMediaEditMenuItems(null, {}, onDismiss = {}).map { it.label })
        assertEquals(listOf("画像編集"), futaberMediaEditMenuItems({}, null, onDismiss = {}).map { it.label })
    }

    // ---- thread NG rules (D7) ----

    private fun rule(id: String, kind: CompatNgKind, scope: String, value: String, memo: String = "", at: Long = 1L, imageUrl: String? = null) =
        CompatNgRule(id, kind, scope, value, at, imageUrl, memo)

    @Test
    fun theThreadDetailListHoldsTheKindsThatHidePostsHereNewestFirst() {
        val rules = listOf(
            rule("a", CompatNgKind.THREAD_POST_NO, "*", "12", at = 1),
            rule("b", CompatNgKind.THREAD_WORD, "*", "宣伝", at = 3),
            rule("c", CompatNgKind.THREAD_IMAGE_PHASH, "*", "00ff00ff00ff00ff", at = 9), // has the 画像 list
            rule("d", CompatNgKind.CATALOG_WORD, "*", "x", at = 9), // catalog rule
            rule("e", CompatNgKind.THREAD_POSTER_ID, "*", "abc", at = 2),
            rule("f", CompatNgKind.THREAD_IGNORE, "*", "w", at = 5),
            rule("g", CompatNgKind.THREAD_REFUSE, "*", "h", at = 4),
            rule("h", CompatNgKind.THREAD_IMAGE, "*", "https://x/1.jpg", at = 6)
        )
        assertEquals(listOf("h", "f", "g", "b", "e", "a"), futaberThreadDetailNgRules(rules).map { it.id })
        assertTrue(futaberThreadDetailNgRules(emptyList()).isEmpty())
    }

    @Test
    fun aRuleLineNamesTheKindTheValueAndWhereItApplies() {
        val boards = listOf(BoardSummary("a", "虹裏", "", "https://may.2chan.net/b/", ""))
        val history = listOf(
            ThreadHistoryEntry(
                threadId = "123", boardId = "a", title = "あるスレ", titleImageUrl = "", boardName = "虹裏",
                boardUrl = "https://may.2chan.net/b/", lastVisitedEpochMillis = 1L, replyCount = 1
            )
        )
        val titles = futaberThreadNgScopeTitles(history, boards)
        val threadKey = compatTabKey("https://may.2chan.net/b/res/123.htm")
        assertEquals("あるスレ", titles[threadKey])

        assertEquals(
            "語句（本文・ヘッダー）　宣伝　全スレッド",
            futaberThreadDetailNgRuleLabel(rule("1", CompatNgKind.THREAD_WORD, "*", "宣伝"), titles)
        )
        // A rule scoped to a thread names the thread by its title, and to one it does not know "スレ別".
        assertEquals(
            "レス番号　No.77　あるスレ",
            futaberThreadDetailNgRuleLabel(rule("2", CompatNgKind.THREAD_POST_NO, threadKey, "77"), titles)
        )
        assertEquals(
            "レス番号　No.77　スレ別",
            futaberThreadDetailNgRuleLabel(rule("3", CompatNgKind.THREAD_POST_NO, "unknown-key", "77"), titles)
        )
        // The memo is shown when it says something the value does not; a word rule keeps its word there.
        assertEquals(
            "語句（本文）　ふたば　全スレッド",
            futaberThreadDetailNgRuleLabel(rule("4", CompatNgKind.THREAD_IGNORE, "*", "ふたば", memo = "ふたば"), titles)
        )
        assertEquals(
            "画像のアドレス　1.jpg　全スレッド",
            futaberThreadDetailNgRuleLabel(rule("5", CompatNgKind.THREAD_IMAGE, "*", "https://x/src/1.jpg?x=1"), titles)
        )
        assertEquals(
            "画像のアドレス　迷惑画像　全スレッド",
            futaberThreadDetailNgRuleLabel(rule("6", CompatNgKind.THREAD_IMAGE, "*", "https://x/src/1.jpg", memo = "迷惑画像"), titles)
        )
    }

    /** L110: asking only for the rules' scopes gives the same titles as the full table for those scopes. */
    @Test
    fun theWantedScopesGetTheSameTitlesAsTheFullTable() {
        val boards = listOf(BoardSummary("a", "虹裏", "", "https://may.2chan.net/b/", ""),
            BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", ""))
        val history = (1..50).map { n ->
            ThreadHistoryEntry(threadId = "$n", boardId = if (n % 2 == 0) "a" else "t", title = "スレ$n", titleImageUrl = "",
                boardName = "", boardUrl = "", lastVisitedEpochMillis = n.toLong(), replyCount = 1)
        }
        val full = futaberThreadNgScopeTitles(history, boards)
        val wanted = setOf(compatTabKey("https://may.2chan.net/b/res/10.htm"), futaberNgScopeKey(boards.first())!!, "unknown")
        val narrowed = futaberThreadNgScopeTitles(history, boards, wanted)
        wanted.forEach { assertEquals(full[it], narrowed[it], it) }
        assertEquals("スレ10", narrowed[compatTabKey("https://may.2chan.net/b/res/10.htm")])
        val tutorialKey = futaberCompatTabKey(boards[1], "11")
        assertEquals(full[tutorialKey], futaberThreadNgScopeTitles(history, boards, setOf(tutorialKey))[tutorialKey])
    }

    @Test
    fun aBoardScopeIsNamedByTheBoard() {
        val boards = listOf(BoardSummary("a", "虹裏", "", "https://may.2chan.net/b/", ""))
        val titles = futaberThreadNgScopeTitles(emptyList(), boards)
        val boardKey = futaberNgScopeKey(boards.single())!!
        assertEquals("虹裏", titles[boardKey])
        assertEquals(
            "画像のアドレス　1.jpg　虹裏",
            futaberThreadDetailNgRuleLabel(rule("7", CompatNgKind.THREAD_IMAGE, boardKey, "https://x/src/1.jpg"), titles)
        )
    }

    @Test
    fun theDuplicateIdsOfRulesAreShownOnce() {
        val rows = futaberNgRows(listOf(FutaberNgRow("x", "1"), FutaberNgRow("x", "2"), FutaberNgRow("y", "3")))
        assertEquals(listOf("1", "3"), rows.map { it.label })
    }

    // ---- the tab clean-up (M1) ----

    private fun key(b: String, t: String) = FutaberTabKey(b, t)
    private fun entry(b: String, t: String) = ThreadHistoryEntry(
        threadId = t, boardId = b, title = "題$t", titleImageUrl = "", boardName = "n",
        boardUrl = "https://x/$b/", lastVisitedEpochMillis = 1L, replyCount = 3
    )
    private val boards = listOf(
        BoardSummary("a", "A", "", "https://x/a/", ""),
        BoardSummary("b", "B", "", "https://x/b/", "")
    )

    @Test
    fun onlyARemovedBoardTakesItsTabsWithIt() {
        val keys = listOf(key("a", "1"), key("a", "2"), key("b", "3"), key("gone", "4"))
        // No history row is needed for a tab to stay.
        assertEquals(
            listOf(key("a", "1"), key("a", "2"), key("b", "3")),
            futaberPruneTabsOfRemovedBoards(keys, boards)
        )
        // An equal list comes back (nothing to write) when no board was removed.
        val kept = listOf(key("a", "1"))
        assertEquals(kept, futaberPruneTabsOfRemovedBoards(kept, boards))
        // An empty board list cannot tell a removed board from a list that is not there yet.
        assertEquals(keys, futaberPruneTabsOfRemovedBoards(keys, emptyList()))
    }

    @Test
    fun aFullTabListLetsGoOfOneTabThatIsNotShownBeforeRefusing() {
        val full = (1..FUTABER_MAX_TABS).map { key("a", it.toString()) }
        val newKey = key("a", "999")
        // Every tab has its history row: nothing to let go of.
        assertNull(futaberTabsMakingRoomFor(full, newKey, full.map { entry(it.boardId, it.threadId) }))
        // Tab 3 has no row (the history was cleared in another mode): it goes, the new tab comes in at the end.
        val history = full.filter { it.threadId != "3" }.map { entry(it.boardId, it.threadId) }
        val room = futaberTabsMakingRoomFor(full, newKey, history)!!
        assertEquals(FUTABER_MAX_TABS, room.size)
        assertTrue(key("a", "3") !in room)
        assertEquals(newKey, room.last())
        // Only the oldest hidden tab goes; the other hidden one stays for when its history is back.
        val twoHidden = full.filter { it.threadId != "3" && it.threadId != "4" }.map { entry(it.boardId, it.threadId) }
        val oneLeft = futaberTabsMakingRoomFor(full, newKey, twoHidden)!!
        assertTrue(key("a", "3") !in oneLeft && key("a", "4") in oneLeft)
        // A list with room, or a key that is already a tab, is not this case.
        assertNull(futaberTabsMakingRoomFor(full.drop(1), newKey, emptyList()))
        assertNull(futaberTabsMakingRoomFor(full, full.first(), emptyList()))
    }

    @Test
    fun aTabWithoutHistoryIsNotShownButIsKept() {
        val keys = listOf(key("a", "1"), key("a", "2"))
        val views = futaberTabViews(keys, listOf(entry("a", "1")))
        assertEquals(listOf(key("a", "1")), views.map { it.key })
        assertEquals(keys, futaberPruneTabsOfRemovedBoards(keys, boards))
    }
}
