package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.ScrollAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the 2026-10-07 audit fixes in the としあき(仮) screens. */
class CompatAuditFixes20261007Test {
    // L3/L4/L5: the deleted-posts notice row is not post 0.
    @Test
    fun anchorOnTheNoticeHeaderHoldsNoPostSoItIsRestoredToTheHeader() {
        val atHeader = buildCompatThreadScrollAnchor(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 12,
            headerCount = 1,
            postNoAt = { "never-used" },
            snapshotRevision = 3L
        )
        assertNull(atHeader.postNo)
        assertEquals(12, atHeader.offsetPx)
        assertEquals(3L, atHeader.snapshotRevision)
        assertEquals(
            CompatThreadListTarget(index = 0, offsetPx = 12),
            resolveCompatThreadListTarget(atHeader, listOf("1", "2"), headerCount = 1)
        )
    }

    @Test
    fun anchorOnAPostIsShiftedByTheHeaderOnRestore() {
        val posts = listOf("10", "11", "12")
        val anchor = buildCompatThreadScrollAnchor(
            firstVisibleItemIndex = 2,
            firstVisibleItemScrollOffset = 5,
            headerCount = 1,
            postNoAt = { posts.getOrNull(it) },
            snapshotRevision = 0L
        )
        assertEquals("11", anchor.postNo)
        assertEquals(1, anchor.fallbackIndex)
        assertEquals(CompatThreadListTarget(2, 5), resolveCompatThreadListTarget(anchor, posts, 1))
        // The post moved: its number wins over the stored index.
        assertEquals(
            CompatThreadListTarget(3, 5),
            resolveCompatThreadListTarget(anchor, listOf("9", "10", "11"), 1)
        )
        // Not found at all: the stored index (clamped) is used.
        assertEquals(
            CompatThreadListTarget(1 + 0, 5),
            resolveCompatThreadListTarget(anchor.copy(postNo = "gone", fallbackIndex = 0), posts, 1)
        )
    }

    @Test
    fun threadsWithoutAHeaderKeepTheirOldPositions() {
        val anchor = buildCompatThreadScrollAnchor(0, 7, 0, { "1" }, 0L)
        assertEquals("1", anchor.postNo)
        assertEquals(CompatThreadListTarget(0, 7), resolveCompatThreadListTarget(anchor, listOf("1"), 0))
        // A fresh tab (default anchor) shows the notice header at the top when there is one.
        assertEquals(CompatThreadListTarget(0, 0), resolveCompatThreadListTarget(ScrollAnchor(), listOf("1"), 1))
        assertEquals(CompatThreadListTarget(0, 0), resolveCompatThreadListTarget(ScrollAnchor(), listOf("1"), 0))
        assertNull(resolveCompatThreadListTarget(ScrollAnchor(), emptyList(), 1))
    }

    @Test
    fun listIndexHelpersAccountForHeaderAndFooter() {
        assertEquals(3, compatThreadListIndexOfPost(2, headerCount = 1))
        assertEquals(2, compatThreadListIndexOfPost(2, headerCount = 0))
        assertNull(compatThreadPostIndexOfListIndex(0, headerCount = 1))
        assertEquals(1, compatThreadPostIndexOfListIndex(2, headerCount = 1))
        assertEquals(4, compatThreadLastListIndex(postCount = 3, headerCount = 1, hasFooter = true))
        assertEquals(3, compatThreadLastListIndex(postCount = 3, headerCount = 1, hasFooter = false))
        assertEquals(1, compatThreadLastListIndex(postCount = 0, headerCount = 1, hasFooter = true))
        assertEquals(0, compatThreadLastListIndex(postCount = 0, headerCount = 0, hasFooter = false))
    }

    // L2: pausing while waiting for new replies keeps waiting instead of restarting at reply 1.
    @Test
    fun resumeAtTheEndKeepsWaitingOnlyWhenAskedTo() {
        assertEquals(5, resolveCompatReadAloudStartIndex(requestedIndex = 5, postCount = 5, keepWaitingAtEnd = true))
        assertEquals(0, resolveCompatReadAloudStartIndex(requestedIndex = 5, postCount = 5))
        assertEquals(0, resolveCompatReadAloudStartIndex(requestedIndex = 6, postCount = 5, keepWaitingAtEnd = true))
        assertEquals(3, resolveCompatReadAloudStartIndex(requestedIndex = 3, postCount = 5, keepWaitingAtEnd = true))
        assertEquals(0, resolveCompatReadAloudStartIndex(requestedIndex = 0, postCount = 0, keepWaitingAtEnd = true))
    }

    // M2: a post failure is kept until acknowledged; its text is the server's/engine's message.
    @Test
    fun postFailureMessageUsesTheErrorTextAndFallsBackToAGenericOne() {
        val unknown = "投稿結果を確認できませんでした。スレッドを確認してから再送してください"
        assertEquals(unknown, compatPostFailureMessage(IllegalStateException(unknown), isBuild = false))
        assertEquals("投稿できませんでした", compatPostFailureMessage(IllegalStateException(""), isBuild = false))
        assertEquals("スレッドを立てられませんでした", compatPostFailureMessage(IllegalStateException(), isBuild = true))
    }

    // 低: the board-menu update only adds boards that are new against the boards as they are now.
    @Test
    fun boardMenuUpdateDoesNotRevertOrReAddBoardsChangedMeanwhile() {
        fun board(url: String, order: Int, name: String = url) =
            CompatBoard(key = url, name = name, canonicalUrl = url, originalUrl = url, sortOrder = order)
        val a = board("https://may.2chan.net/b/", 0)
        val b = board("https://img.2chan.net/b/", 1)
        val fresh = board("https://dat.2chan.net/b/", 2)
        // The menu was parsed against [a, b]; meanwhile b was deleted and a renamed and moved.
        val latest = listOf(a.copy(name = "renamed", sortOrder = 5))
        val planned = planCompatBoardMenuUpsert(
            discovered = listOf(a, b, fresh),
            staleExisting = listOf(a, b),
            latest = latest
        )
        assertEquals(listOf("https://dat.2chan.net/b/"), planned.map { it.canonicalUrl })
        assertEquals(6, planned.single().sortOrder)
        assertTrue(planCompatBoardMenuUpsert(listOf(a, b), listOf(a, b), listOf(a, b)).isEmpty())
    }
}
