package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fixes of the 2026-10-07 audit that are plain logic: tabs, catalog, NG lists, the shell's decisions. */
class FutaberAuditFixesTest {
    private fun key(b: String, t: String) = FutaberTabKey(b, t)
    private fun entry(b: String, t: String, url: String = "https://x/$b/") = ThreadHistoryEntry(
        threadId = t, boardId = b, title = "題$t", titleImageUrl = "", boardName = "n",
        boardUrl = url, lastVisitedEpochMillis = 1L, replyCount = 3
    )
    private fun item(id: String, title: String = "スレ$id", full: String? = null, thumb: String? = null) = CatalogItem(
        id = id, threadUrl = "https://may.2chan.net/b/res/$id.htm", title = title,
        thumbnailUrl = thumb, fullImageUrl = full, replyCount = 1
    )
    private fun rule(id: String, kind: CompatNgKind, value: String = "v", scope: String = "k", memo: String = "", image: String? = null, at: Long = 1L) =
        CompatNgRule(id = id, kind = kind, scopeKey = scope, normalizedValue = value, createdAtEpochMillis = at, imageUrl = image, memo = memo)

    // ---- the tab list: full, restore, closed memory ----

    @Test fun aFullTabListSaysSoInsteadOfDoingNothing() {
        val full = (1..FUTABER_MAX_TABS).map { key("a", it.toString()) }
        assertEquals(FutaberTabToggleOutcome.Full, futaberTabToggleOutcome(full, key("a", "999")))
        assertEquals(FutaberTabToggleOutcome.Removed, futaberTabToggleOutcome(full, key("a", "1")))
        assertEquals(FutaberTabToggleOutcome.Added, futaberTabToggleOutcome(full.drop(1), key("a", "999")))
        assertTrue(FUTABER_TAB_LIMIT_NOTICE.contains(FUTABER_MAX_TABS.toString()))
    }

    @Test fun restoringIntoAnAlmostFullListWritesAndForgetsOnlyWhatFit() {
        val open = (1..FUTABER_MAX_TABS - 1).map { key("a", it.toString()) }
        val closed = listOf(key("b", "1"), key("b", "2"), key("b", "3"))
        val result = futaberRestoreTabs(open, closed)
        assertEquals(FUTABER_MAX_TABS, result.tabs.size)
        assertEquals(listOf(key("b", "1")), result.restored)
        // The two that did not fit stay in the memory.
        val memory = closed.filter { it !in result.restored }
        assertEquals(listOf(key("b", "2"), key("b", "3")), memory)
    }

    @Test fun restoringIntoAFullListWritesNothing() {
        val full = (1..FUTABER_MAX_TABS).map { key("a", it.toString()) }
        val result = futaberRestoreTabs(full, listOf(key("b", "1")))
        assertEquals(full, result.tabs)
        assertTrue(result.restored.isEmpty())
    }

    @Test fun theClosedMemoryKeepsTheLatestFirstWithoutTheOpenOnesAndIsBounded() {
        val removed = listOf(key("a", "1"), key("a", "2"))
        assertEquals(
            listOf(key("a", "2"), key("a", "1"), key("c", "9")),
            futaberRememberClosedTabs(listOf(key("c", "9")), removed, next = emptyList())
        )
        // A tab that is open again is not "closed".
        assertEquals(listOf(key("a", "2")), futaberRememberClosedTabs(emptyList(), removed, next = listOf(key("a", "1"))))
        val many = (1..FUTABER_MAX_CLOSED_TABS + 5).map { key("a", it.toString()) }
        assertEquals(FUTABER_MAX_CLOSED_TABS, futaberRememberClosedTabs(emptyList(), many, emptyList()).size)
    }

    @Test fun historyRowsOfOldDataWithoutABoardIdDoNotShareAKey() {
        val a = entry("", "100", url = "https://may.2chan.net/b/res/100.htm")
        val b = entry("", "100", url = "https://img.2chan.net/b/res/100.htm")
        assertTrue(futaberHistoryRowKey(a) != futaberHistoryRowKey(b))
        // A true duplicate is shown once, so a lazy list never sees the same key twice.
        assertEquals(listOf(a, b), futaberDistinctHistoryRows(listOf(a, b, a)))
    }

    // ---- safe cutting ----

    @Test fun cuttingNeverLeavesHalfOfASurrogatePair() {
        val smile = "😀" // one emoji, two chars
        assertEquals("あ", futaberSafeTake("あ$smile", 2))
        assertEquals("あ$smile", futaberSafeTake("あ${smile}い", 3))
        assertEquals("abc", futaberSafeTake("abc", 10))
        assertEquals("", futaberSafeTake("abc", 0))
        assertEquals("", futaberSafeTake(smile, 1))
    }

    @Test fun aNewThreadIsNamedAfterWhatWasTypedElseUntitled() {
        assertEquals("雑談", futaberNewThreadTitle("  雑談  "))
        assertEquals("1行目", futaberNewThreadTitle("1行目\n2行目"))
        assertEquals("無題", futaberNewThreadTitle(""))
        assertEquals("無題", futaberNewThreadTitle("   "))
    }

    @Test fun theBoardDeleteMessageTellsWhatGoesWithTheBoard() {
        val message = futaberDeleteBoardMessage("may")
        assertTrue(message.contains("お気に入り"))
        assertTrue(message.contains("タブ"))
        assertTrue(message.contains("「may」"))
    }

    // ---- the catalog: rules, going back, keeping it ----

    @Test fun theDroppedListIsBuiltFromTheCatalogAfterTheRules() {
        val dropped = listOf(item("1", "広告スレ"), item("2"), item("3", full = "https://x/3.jpg"))
        val words = listOf("広告")
        assertEquals(listOf("2", "3"), futaberApplyCatalogRules(dropped, words, null).map { it.id })
        assertEquals(listOf("3"), futaberApplyCatalogRules(dropped, words, null, hiddenIds = setOf("2")).map { it.id })
        assertEquals(dropped, futaberApplyCatalogRules(dropped, emptyList(), null))
    }

    @Test fun goingBackPutsTheThreadsOfThatListBackOutOfTheDroppedOnes() {
        var history = FutaberCatalogHistory()
        history = futaberCatalogHistoryAfterLoad(history, listOf(item("1"), item("2")), listOf(item("2"), item("3")))
        history = futaberCatalogHistoryAfterLoad(history, listOf(item("2"), item("3")), listOf(item("3"), item("4")))
        // Dropped so far: 2 (newest) and 1.
        assertEquals(listOf("2", "1"), history.dropped.map { it.id })
        val (list, rest) = futaberCatalogGoBack(history)!!
        assertEquals(listOf("2", "3"), list.map { it.id })
        // 2 is on screen again; 1 is still gone from the list on screen.
        assertEquals(listOf("1"), rest.dropped.map { it.id })
    }

    @Test fun theCatalogOnHandIsReusedOnlyForTheSameFetchWhileItIsFresh() {
        val k = FutaberCatalogFetchKey("https://may.2chan.net/b/", CatalogMode.Catalog, 0, 0)
        assertTrue(futaberCatalogCanReuse(k, k, hasItems = true, ageMillis = 1_000))
        assertFalse(futaberCatalogCanReuse(null, k, true, 1_000))
        assertFalse(futaberCatalogCanReuse(k, k, hasItems = false, ageMillis = 1_000))
        assertFalse(futaberCatalogCanReuse(k, k.copy(refreshTick = 1), true, 1_000))
        assertFalse(futaberCatalogCanReuse(k, k.copy(refreshSignal = 1), true, 1_000))
        assertFalse(futaberCatalogCanReuse(k, k.copy(sortMode = CatalogMode.New), true, 1_000))
        assertFalse(futaberCatalogCanReuse(k, k, true, FUTABER_CATALOG_REUSE_MILLIS))
        assertFalse(futaberCatalogCanReuse(k, k, true, -1))
    }

    @Test fun onlyAnAskedForRefreshScrollsToTheTop() {
        val k = FutaberCatalogFetchKey("u", CatalogMode.Catalog, 0, 0)
        assertFalse(futaberCatalogIsExplicitRefresh(null, k))
        assertFalse(futaberCatalogIsExplicitRefresh(k, k))
        assertTrue(futaberCatalogIsExplicitRefresh(k, k.copy(refreshTick = 1)))
        assertTrue(futaberCatalogIsExplicitRefresh(k, k.copy(refreshSignal = 1)))
    }

    @Test fun cataloguePicturesAreHashedInCatalogOrderWithTheOriginalFirst() {
        val items = listOf(
            item("1", full = "https://x/1.jpg", thumb = "https://x/1s.jpg"),
            item("2", thumb = "https://x/2s.jpg"),
            item("3"),
            item("4", full = " ", thumb = "https://x/4s.jpg")
        )
        assertEquals(
            listOf("1" to "https://x/1.jpg", "2" to "https://x/2s.jpg", "4" to "https://x/4s.jpg"),
            futaberCatalogPhashCandidates(items)
        )
        val many = (1..FUTABER_CATALOG_PHASH_MAX_CANDIDATES + 20).map { item(it.toString(), thumb = "https://x/$it.jpg") }
        assertEquals(FUTABER_CATALOG_PHASH_MAX_CANDIDATES, futaberCatalogPhashCandidates(many).size)
    }

    @Test fun aLookAlikeHashHidesItsThreadAndAFarOneDoesNot() {
        val ruleHash = "ffffffffffffffff"
        val rules = listOf(rule("r", CompatNgKind.CATALOG_IMAGE_PHASH, value = ruleHash))
        val near = "fffffffffffffffe"
        val far = "0000000000000000"
        assertEquals(setOf("1"), futaberPhashHiddenIds(mapOf("1" to near, "2" to far), rules, CompatImagePhash.DEFAULT_THRESHOLD))
        assertTrue(futaberPhashHiddenIds(mapOf("1" to near), emptyList(), CompatImagePhash.DEFAULT_THRESHOLD).isEmpty())
        assertTrue(futaberPhashHiddenIds(emptyMap(), rules, CompatImagePhash.DEFAULT_THRESHOLD).isEmpty())
    }

    @Test fun catalogRulesOfTheOtherModesAreListedByKindNewestFirst() {
        val rules = listOf(
            rule("w1", CompatNgKind.CATALOG_WORD, value = "広告", at = 1),
            rule("w2", CompatNgKind.CATALOG_IGNORE, value = "宣伝", memo = "宣伝", at = 5),
            rule("t", CompatNgKind.CATALOG_REFUSE),
            rule("i1", CompatNgKind.CATALOG_IMAGE, value = "https://x/a.jpg", at = 2),
            rule("i2", CompatNgKind.CATALOG_IMAGE_PHASH, value = "ffffffffffffffff", image = "https://x/b.jpg?x=1", at = 3),
            rule("p", CompatNgKind.THREAD_WORD)
        )
        assertEquals(listOf("w2", "w1"), futaberCatalogWordRules(rules).map { it.id })
        assertEquals(listOf("i2", "i1"), futaberCatalogImageRules(rules).map { it.id })
        assertEquals("宣伝　この板", futaberCatalogWordRuleLabel(rules[1]))
        assertEquals("広告　この板", futaberCatalogWordRuleLabel(rules[0]))
        assertEquals("b.jpg　この板", futaberCatalogImageRuleLabel(rules[4]))
        assertEquals("a.jpg　全板", futaberCatalogImageRuleLabel(rules[3].copy(scopeKey = "*")))
        assertEquals("見た目が似た画像　この板", futaberCatalogImageRuleLabel(rules[4].copy(imageUrl = null)))
    }

    // ---- the NG lists of the settings ----

    @Test fun twoEntriesWithOneIdAreShownOnce() {
        val rows = futaberNgRows(listOf(FutaberNgRow("a", "A"), FutaberNgRow("a", "A again"), FutaberNgRow("b", "B")))
        assertEquals(listOf("a", "b"), rows.map { it.id })
    }

    // ---- archive search ----

    private fun archive(server: String, board: String, id: String) =
        ArchiveSearchItem(threadId = id, server = server, board = board, title = "t", htmlUrl = "https://x/$id")

    @Test fun archiveResultsOfOneThreadAreKeptOnceSoTheListNeverSeesAKeyTwice() {
        val items = listOf(archive("may", "b", "1"), archive("MAY", "B", "1"), archive("may", "b", "2"), archive("img", "b", "1"))
        val distinct = futaberDistinctArchiveItems(items)
        assertEquals(3, distinct.size)
        assertEquals(distinct.size, distinct.map(::futaberArchiveItemKey).toSet().size)
        assertEquals("may", distinct.first().server)
    }

    // ---- what the shell decides ----

    @Test fun theEdgeSwipeOpensTheBoardListOnlyWhenNothingIsOnTopOfTheThread() {
        assertTrue(futaberDrawerGestureAllowed(false, false, false, false, false, false))
        assertFalse(futaberDrawerGestureAllowed(true, false, false, false, false, false))
        assertFalse(futaberDrawerGestureAllowed(false, true, false, false, false, false))
        assertFalse(futaberDrawerGestureAllowed(false, false, true, false, false, false))
        assertFalse(futaberDrawerGestureAllowed(false, false, false, true, false, false))
        assertFalse(futaberDrawerGestureAllowed(false, false, false, false, true, false))
        assertFalse(futaberDrawerGestureAllowed(false, false, false, false, false, true))
    }

    @Test fun backOnAThreadClosesTheBoardListFirst() {
        assertEquals(FutaberThreadBack.CloseDrawer, futaberThreadBack(drawerOpen = true))
        assertEquals(FutaberThreadBack.LeaveThread, futaberThreadBack(drawerOpen = false))
    }

    @Test fun theWriteScreenKeepsWhatIsOnScreenForTheSameTargetElseUsesTheDraft() {
        val reply = FutaberPostTarget.Reply("b", "1", "題")
        val other = FutaberPostTarget.Reply("b", "2", "別")
        val draft = FutaberDraft(reply.draftKey, "下書き題", "下書き本文")
        // The same target is still open (a quote-mode round trip): what is typed wins, even if the draft is older.
        assertEquals(FutaberPostStart("題", "打った"), futaberPostStart(draft, reply, reply, "題", "打った"))
        // Another target, or none open: the saved draft.
        assertEquals(FutaberPostStart("下書き題", "下書き本文"), futaberPostStart(draft, reply, null, "x", "y"))
        assertEquals(FutaberPostStart("下書き題", "下書き本文"), futaberPostStart(draft, reply, other, "x", "y"))
        assertEquals(FutaberPostStart("", ""), futaberPostStart(null, reply, null, "x", "y"))
    }

    @Test fun theLostAttachmentNoticeIsDueOnlyForARestoredWriteScreenWithoutItsFile() {
        assertTrue(futaberAttachmentLostNoticeDue(wasAttached = true, writing = true, hasAttachment = false))
        assertFalse(futaberAttachmentLostNoticeDue(true, true, true))
        assertFalse(futaberAttachmentLostNoticeDue(false, true, false))
        assertFalse(futaberAttachmentLostNoticeDue(true, false, false))
    }

    @Test fun boardNamesAndAddressesAreCutAtASafePlace() {
        val boards = emptyList<BoardSummary>()
        val smile = "😀"
        val name = "a".repeat(FUTABER_BOARD_NAME_MAX_CHARS - 1) + smile
        val state = futaberAddBoardState(name, "https://may.2chan.net/b/", boards)
        assertEquals(FUTABER_BOARD_NAME_MAX_CHARS - 1, state.resolvedName.length)
    }
}
