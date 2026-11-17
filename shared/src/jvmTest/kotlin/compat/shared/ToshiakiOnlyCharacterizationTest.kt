package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatCatalogReplyCountUpdate
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.filterCompatThreadPosts
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.page
import com.valoser.futacha.shared.compat.shared.SingleMode.post
import com.valoser.futacha.shared.compat.shared.SingleMode.tab
import com.valoser.futacha.shared.compat.shared.SingleMode.withStores
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.compatCommonNgApplyEnabled
import com.valoser.futacha.shared.ui.compat.compatCommonNgFilterRules
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * としあき(仮) used alone (D-B0, docs/futaber-evolution-data-2026-10-31.md §5): what it stores and shows today. Each name is
 * a promise the batches sharing the data keep.
 */
class ToshiakiOnlyCharacterizationTest {
    private fun history(threadId: String, visitedAt: Long) = SingleMode.threadUrl(may, threadId).let { url ->
        CompatHistoryEntry(url, url, SharedKeys.board(may).compatBoardKey!!, "may", threadId, "スレ$threadId",
            replyCount = 3, contentUpdatedAtEpochMillis = visitedAt, lastVisitedEpochMillis = visitedAt)
    }

    @Test fun toshiakiOnlyTabTrimKeepsTheSharedLimit() = withStores { compat, _ ->
        compat.openTab(tab(may, "1", insertedAt = 1, favorite = true))
        (2..201).forEach { compat.openTab(tab(may, "$it", insertedAt = it.toLong())) }
        val kept = compat.tabs.first()
        // B4 (decision Q3): the limit of the four modes is 200 (it was 100 -> 90).
        assertEquals(180, kept.size)
        assertTrue(kept.any { it.threadNo == "1" }, "a favourite is never trimmed")
        assertTrue(kept.any { it.threadNo == "201" }, "nor the active one")
        assertEquals((201 downTo 23).map(Int::toString) + "1", kept.map { it.threadNo }, "the oldest without a star go")
    }

    @Test fun toshiakiOnlyNewTabsComeFirstAndTheOpenedOneIsActive() = withStores { compat, _ ->
        compat.openTab(tab(may, "1", insertedAt = 10))
        compat.openTab(tab(img, "2", insertedAt = 20))
        compat.openTab(tab(may, "3", insertedAt = 30))
        assertEquals(listOf("3", "2", "1"), compat.tabs.first().map { it.threadNo })
        assertEquals(tab(may, "3", 30).key, compat.workspace.first().activeTabKey)
        // Opening a tab again keeps its place.
        compat.openTab(tab(may, "1", insertedAt = 10))
        assertEquals(listOf("3", "2", "1"), compat.tabs.first().map { it.threadNo })
    }

    @Test fun toshiakiOnlyClosedTabsComeBackWithUndo() = withStores { compat, _ ->
        compat.openTab(tab(may, "1", insertedAt = 10, favorite = true))
        compat.openTab(tab(may, "2", insertedAt = 20))
        val batch = assertNotNull(compat.closeTabs(setOf(tab(may, "1", 10).key), nowEpochMillis = 100))
        assertEquals(listOf("2"), compat.tabs.first().map { it.threadNo })
        compat.restoreClosedTabs(batch)
        val restored = compat.tabs.first()
        assertEquals(listOf("2", "1"), restored.map { it.threadNo })
        assertTrue(restored.first { it.threadNo == "1" }.favorite, "the star comes back with the tab")
    }

    @Test fun toshiakiOnlyClosingATabTakesItsReplyDraftAndKeepsTheBoardsNewThreadDraft() = withStores { compat, _ ->
        val opened = tab(may, "1", insertedAt = 10)
        compat.openTab(opened)
        compat.saveDraft(CompatReplyDraft(opened.key, name = "名無し", comment = "返信", deleteKey = "abcd1234", updatedAtEpochMillis = 20))
        val boardKey = SharedKeys.board(may).compatBoardKey!!
        compat.saveBuildDraft(CompatBuildDraft(boardKey, subject = "スレ立て", comment = "本文", updatedAtEpochMillis = 30))
        assertEquals("abcd1234", compat.loadDraft(opened.key)?.deleteKey, "としあき(仮) keeps the delete key in its draft")
        compat.closeTabs(setOf(opened.key), nowEpochMillis = 100)
        assertNull(compat.loadDraft(opened.key))
        assertEquals("本文", compat.loadBuildDraft(boardKey)?.comment)
    }

    @Test fun toshiakiOnlyHistoryKeepsTheNewestHundredNinety() = withStores { compat, _ ->
        (1..201).forEach { compat.recordHistoryVisit(history("$it", visitedAt = it.toLong())) }
        val kept = compat.history.first()
        assertEquals(190, kept.size, "200 -> 190 after the 201st")
        assertEquals((201 downTo 12).map(Int::toString), kept.sortedByDescending { it.lastVisitedEpochMillis }.map { it.threadNo })
    }

    @Test fun toshiakiOnlyCatalogRaisesTheTabsCountButNotTheReadOne() = withStores { compat, _ ->
        val opened = tab(may, "1", insertedAt = 10, replyCount = 5)
        compat.openTab(opened)
        compat.applyCatalogReplyCounts(listOf(CompatCatalogReplyCountUpdate(opened.key, opened.canonicalUrl, 9)))
        val probed = compat.tabs.first().single()
        assertEquals(Triple(9, 5, 4), Triple(probed.replyCount, probed.checkedReplyCount, probed.unreadCount))
        compat.applyCatalogReplyCounts(listOf(CompatCatalogReplyCountUpdate(opened.key, opened.canonicalUrl, 7)))
        assertEquals(9, compat.tabs.first().single().replyCount, "a smaller count never lowers it")
    }

    @Test fun toshiakiOnlyReadPositionIsTheTabsAnchorAndTheHistoryKeepsItAfterClosing() = withStores { compat, _ ->
        val opened = tab(may, "1", insertedAt = 10)
        compat.openTab(opened, history("1", visitedAt = 10))
        val anchor = ScrollAnchor(postNo = "104", offsetPx = 12, fallbackIndex = 4)
        compat.updateScrollAnchor(opened.key, anchor)
        assertEquals(anchor, compat.tabs.first().single().scrollAnchor)
        compat.closeTabs(setOf(opened.key), nowEpochMillis = 100)
        assertEquals(anchor, compat.history.first().single().scrollAnchor)
    }

    @Test fun toshiakiOnlyCommonNgIsOnUntilSwitchedOff() = withStores { compat, state -> // D-B7 (J4): was off until switched on
        assertTrue(compatCommonNgApplyEnabled(emptyMap()))
        assertFalse(compatCommonNgApplyEnabled(mapOf(COMPAT_COMMON_NG_APPLY_STORAGE_KEY to "OFF")))
        assertTrue(compatCommonNgApplyEnabled(mapOf(COMPAT_COMMON_NG_APPLY_STORAGE_KEY to "ON")))
        val opened = tab(may, "1", insertedAt = 10)
        compat.openTab(opened)
        assertTrue(compat.upsertNgRule(CompatNgRule("post-3", CompatNgKind.THREAD_POST_NO, opened.key, "3", createdAtEpochMillis = 1)))
        state.setNgWords(listOf("荒らし"))
        val posts = page("1", listOf(post("1", "スレ"), post("2", "荒らしです"), post("3", "普通"), post("4", "普通"))).toCompatThreadSnapshot(opened.key, 1).posts
        suspend fun shown(commonOn: Boolean) = filterCompatThreadPosts(posts, ngEnabled = true,
            index = buildCompatThreadNgRuleIndex(
                compat.ngRules.first() + if (commonOn) compatCommonNgFilterRules(emptyList(), listOf("荒らし"), emptyList()) else emptyList(),
                opened.key, opened.boardKey
            )
        ).map { it.postNo }
        assertEquals(listOf("1", "2", "4"), shown(commonOn = false), "only its own rules while the switch is off")
        assertEquals(listOf("1", "4"), shown(commonOn = true))
    }

    @Test fun toshiakiOnlyDeletingABoardTakesItsTabsDraftsHistoryAndRules() = withStores { compat, _ ->
        val mayTab = tab(may, "1", insertedAt = 10)
        val imgTab = tab(img, "2", insertedAt = 20)
        compat.openTab(mayTab, history("1", 10))
        compat.openTab(imgTab)
        compat.saveDraft(CompatReplyDraft(mayTab.key, comment = "返信", updatedAtEpochMillis = 30))
        val mayKey = SharedKeys.board(may).compatBoardKey!!
        assertTrue(compat.upsertNgRule(CompatNgRule("board-word", CompatNgKind.CATALOG_WORD, mayKey, "荒らし", createdAtEpochMillis = 1)))
        compat.deleteBoard(mayKey)
        assertEquals(listOf("2"), compat.tabs.first().map { it.threadNo })
        assertNull(compat.loadDraft(mayTab.key))
        assertTrue(compat.history.first().isEmpty())
        assertTrue(compat.ngRules.first().none { it.id == "board-word" })
    }
}
