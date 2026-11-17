package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_DRAFT_LIMIT
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_DRAFT_MAX_COMMENT_CHARS
import com.valoser.futacha.shared.ui.ftutter.FtutterDraft
import com.valoser.futacha.shared.ui.ftutter.FtutterExtension
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.FtutterNgMatcher
import com.valoser.futacha.shared.ui.ftutter.FtutterNgRule
import com.valoser.futacha.shared.ui.ftutter.FtutterNgType
import com.valoser.futacha.shared.ui.ftutter.FtutterPostTarget
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_TAB_PROFILE
import com.valoser.futacha.shared.ui.ftutter.FtutterSyncTabBar
import kotlinx.coroutines.flow.first
import com.valoser.futacha.shared.ui.ftutter.decodeFtutterList
import com.valoser.futacha.shared.ui.ftutter.ftutterDraftKey
import com.valoser.futacha.shared.ui.ftutter.ftutterDraftsUpdate
import com.valoser.futacha.shared.ui.ftutter.ftutterLimitedHistoryUrls
import com.valoser.futacha.shared.ui.ftutter.ftutterNgHiddenPosts
import com.valoser.futacha.shared.ui.ftutter.ftutterReadCountAfter
import com.valoser.futacha.shared.ui.ftutter.ftutterStoredFromPostsRead
import com.valoser.futacha.shared.ui.ftutter.ftutterTabNumbers
import com.valoser.futacha.shared.ui.ftutter.parseFtutterDrafts
import com.valoser.futacha.shared.ui.ftutter.parseFtutterExtensions
import com.valoser.futacha.shared.ui.ftutter.parseFtutterReadCounts
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterReadCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ふたったー風 used alone (D-B0, docs/futaber-evolution-data-2026-10-31.md §5): what it stores and shows today. Each name
 * is a promise the batches sharing the data keep. Its tabs, protection, posts read, drafts and NG list are its own
 * settings rows (`compat.ftutter.*`), keyed by the thread's address; the history is AppState's.
 */
class FtutterOnlyCharacterizationTest {
    private fun url(threadId: String) = SharedKeys.thread(may, threadId).ftutterThreadUrl

    /** The tab bar on the stores (plan B7a: a view of the shared tabs; the old rows are only read). */
    private suspend fun tabBar(compat: DesktopCompatibilityStore) = FtutterSyncTabBar(compat, SingleMode.boards).start()

    @Test fun ftutterOnlyNewTabGoesFirst() = SingleMode.withStores { compat, _ ->
        val bar = tabBar(compat)
        listOf("1", "2", "3").forEach { bar.state.open(url(it)); bar.settle() }
        assertEquals(listOf(url("3"), url("2"), url("1")), bar.state.urls)
        bar.state.open(url("1"))
        bar.settle()
        assertEquals(listOf(url("3"), url("2"), url("1")), bar.state.urls, "a tab opened again keeps its place")
        assertEquals(bar.state.urls, compat.loadTabViews(FTUTTER_TAB_PROFILE).map { view ->
            compat.tabs.first().single { it.key == view.tabKey }.canonicalUrl
        }, "the order is this mode's marks on the shared tabs")
        assertNull(bar.rows[FtutterKeys.TABS], "the old row is not written any more")
        assertEquals(url("1"), bar.state.opening)
        assertEquals("https://may.2chan.net/b/res/1.htm", url("1"), "kept by the thread's address")
    }

    @Test fun ftutterOnlyHistoryLimitOnlyHidesUnprotectedTabs() = SingleMode.withStores { compat, _ ->
        val bar = tabBar(compat)
        bar.state.open(url("1"))
        bar.settle()
        bar.state.toggleProtect(url("1"))
        bar.settle()
        bar.state.limit = 2
        bar.state.historyOrder = listOf(url("4"), url("3"), url("2"), url("1"))
        listOf("2", "3", "4").forEach { bar.state.open(url(it)); bar.settle() }
        assertEquals(listOf(url("4"), url("3"), url("1")), bar.state.urls, "the protected tab is not counted; the oldest others are hidden")
        assertEquals(4, compat.tabs.first().size, "the limit hides, it closes nothing (the shared tab limit trims)")
    }

    @Test fun ftutterOnlyTabsWithoutHistoryStayOpen() = SingleMode.withStores { compat, _ ->
        val bar = tabBar(compat)
        listOf("3", "2", "1").forEach { bar.state.open(url(it)); bar.settle() }
        bar.state.historyOrder = listOf(url("1"))
        bar.settle()
        assertEquals(listOf(url("1"), url("2"), url("3")), bar.state.urls, "a tab is not closed because its history row went")
        assertEquals(0, compat.countClosedTabBatches(FTUTTER_TAB_PROFILE, bar.nowMillis))
    }

    @Test fun ftutterOnlyClosingATabTakesItsProtectionAndUndoPutsItBackInPlace() = SingleMode.withStores { compat, _ ->
        val bar = tabBar(compat)
        listOf("3", "2", "1").forEach { bar.state.open(url(it)); bar.settle() }
        bar.state.toggleProtect(url("2"))
        bar.settle()
        assertTrue(compat.tabs.first().single { it.canonicalUrl == url("2") }.favorite, "the protection is the shared ★")
        bar.state.close(listOf(url("2")))
        assertEquals(listOf(url("1"), url("3")), bar.state.urls)
        bar.settle()
        assertFalse(url("2") in bar.state.protected)
        var restored: String? = null
        bar.state.restore { restored = it?.url }
        bar.settle()
        assertEquals(url("2"), restored)
        assertEquals(listOf(url("1"), url("2"), url("3")), bar.state.urls)
        assertFalse(url("2") in bar.state.protected, "the protection does not come back")
    }

    @Test fun ftutterOnlyReadCountOnlyGrowsAndCountsRepliesWithoutTheOp() {
        assertEquals(9, ftutterStoredFromPostsRead(10), "10 posts read are 9 replies")
        assertEquals(9, ftutterReadCountAfter(9, ftutterStoredFromPostsRead(6)), "a shorter visit never lowers it")
        assertEquals(12, ftutterReadCountAfter(9, 12))
        val stored = encodeFtutterReadCounts(mapOf(url("1") to 9))
        assertEquals(mapOf(url("1") to 9), parseFtutterReadCounts(stored))
        val entry = ThreadHistoryEntry("1", may.id, "スレ1", "", "may", url("1"), 1L, replyCount = 12)
        val numbers = ftutterTabNumbers(entry, storedRead = 9)
        assertEquals(11, numbers.replies, "the history's 12 posts are 11 replies")
        assertEquals(2, numbers.unread)
        assertNull(ftutterTabNumbers(entry, storedRead = 11).unread, "nothing unread shows nothing")
    }

    @Test fun ftutterOnlyDraftsAreKeyedByAddressAndKeepTwenty() {
        assertEquals("res:may.2chan.net/b/res/1.htm", ftutterDraftKey(FtutterPostTarget.Reply(may.id, "1"), may.url))
        assertEquals("board:img.2chan.net/b/", ftutterDraftKey(FtutterPostTarget.Create(img.id), img.url))
        var drafts = emptyMap<String, FtutterDraft>()
        (1..FTUTTER_DRAFT_LIMIT + 1).forEach { i ->
            drafts = parseFtutterDrafts(ftutterDraftsUpdate(drafts, "res:may.2chan.net/b/res/$i.htm", FtutterDraft(comment = "本文$i")).encoded)
        }
        assertEquals(FTUTTER_DRAFT_LIMIT, drafts.size)
        assertEquals("res:may.2chan.net/b/res/21.htm", drafts.keys.first(), "the newest first")
        assertFalse("res:may.2chan.net/b/res/1.htm" in drafts, "the oldest goes")
        val long = ftutterDraftsUpdate(emptyMap(), "k", FtutterDraft(comment = "あ".repeat(5_000)))
        assertTrue(long.truncated)
        assertEquals(FTUTTER_DRAFT_MAX_COMMENT_CHARS, parseFtutterDrafts(long.encoded).getValue("k").comment.length)
        assertFalse("res:may.2chan.net/b/res/21.htm" in parseFtutterDrafts(
            ftutterDraftsUpdate(drafts, "res:may.2chan.net/b/res/21.htm", FtutterDraft()).encoded), "an empty draft (sent) is removed")
    }

    @Test fun ftutterOnlyNgListHidesByTypeAndPickupHidesNothing() {
        val rules = listOf(
            FtutterNgRule("荒らし", FtutterNgType.WORD.stored),
            FtutterNgRule("ID:abc", FtutterNgType.ID.stored),
            FtutterNgRule("普通", FtutterNgType.PICKUP.stored),
            FtutterNgRule("無効", FtutterNgType.WORD.stored, isActive = false)
        )
        val posts = listOf(
            post("1", "スレ"), post("2", "荒らしです"), post("3", "普通", posterId = "ID:abc"), post("4", "普通"), post("5", "無効")
        )
        assertEquals(setOf(1, 2), ftutterNgHiddenPosts(posts, FtutterNgMatcher(rules, may.url), chain = false))
        assertEquals("普通", FtutterNgMatcher(rules, may.url).pickupWord("普通のスレ"), "a pickup marks a catalog title")
    }

    @Test fun ftutterOnlyCommonNgIsOnUntilTheExtensionIsSwitchedOff() { // D-B7 (J4): was off until switched on
        assertTrue(FtutterExtension.COMMON_NG in parseFtutterExtensions(emptyMap()))
        assertFalse(FtutterExtension.COMMON_NG in parseFtutterExtensions(mapOf(FtutterExtension.COMMON_NG.key to "false")))
        assertTrue(FtutterExtension.COMMON_NG in parseFtutterExtensions(mapOf(FtutterExtension.COMMON_NG.key to "true")))
        assertTrue(FtutterExtension.HISTORY_TOOLS in parseFtutterExtensions(emptyMap()), "the history tools are on until turned off")
    }

    @Test fun ftutterOnlyHistoryLimitNarrowsTheListNotTheSharedHistory() {
        val history = (1..5).map { url("$it") }
        assertEquals(listOf(url("1"), url("2"), url("4")), ftutterLimitedHistoryUrls(history, protected = setOf(url("4")), limit = 2))
        assertEquals(5, history.size, "the shared history is only read")
    }
}
