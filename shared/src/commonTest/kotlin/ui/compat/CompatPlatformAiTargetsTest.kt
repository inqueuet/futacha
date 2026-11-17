package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatibilityWorkspaceState
import com.valoser.futacha.shared.compat.compatBoardKey
import kotlin.test.*

class CompatPlatformAiTargetsTest {
    private val img = CompatBoard("img", "二次元裏", "https://img.2chan.net/b/", "https://img.2chan.net/b/futaba.htm", 0)
    private val may = CompatBoard("may", "may二次元", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 1)
    private val boards = listOf(img, may)

    private fun command(vararg parameters: Pair<String, String>) =
        FutachaAiCommand(FutachaAiAction.OpenBoard, parameters.toMap())

    private fun tab(boardKey: String) = CompatTab(
        key = "tab-$boardKey", canonicalUrl = "", originalUrl = "", boardKey = boardKey, boardName = "",
        threadNo = "1", title = "", insertedAtEpochMillis = 0L, contentUpdatedAtEpochMillis = 0L
    )

    @Test fun boardWithoutSelectorFollowsActiveTabThenShownCatalog() {
        val withTab = CompatibilityWorkspaceState(host = CompatHost.Catalog("img"), activeTabKey = "tab-may", tabs = listOf(tab("may")))
        assertEquals(may, resolvePlatformAiBoard(command(), withTab, boards))
        assertEquals(img, resolvePlatformAiBoard(command(), CompatibilityWorkspaceState(host = CompatHost.Catalog("img")), boards))
        assertNull(resolvePlatformAiBoard(command(), CompatibilityWorkspaceState(), boards))
    }

    @Test fun boardSelectorMatchesExactFieldsBeforePartialNames() {
        val state = CompatibilityWorkspaceState()
        assertEquals(may, resolvePlatformAiBoard(command("board" to "MAY"), state, boards))
        assertEquals(img, resolvePlatformAiBoard(command("name" to "二次元裏"), state, boards))
        assertEquals(img, resolvePlatformAiBoard(command("url" to "https://img.2chan.net/b/futaba.htm"), state, boards))
        assertEquals(may, resolvePlatformAiBoard(command("url" to "https://may.2chan.net/b/futaba.php"), state, boards))
        assertEquals(img, resolvePlatformAiBoard(command("q" to "裏"), state, boards))
        assertNull(resolvePlatformAiBoard(command("board" to "none"), state, boards))
    }

    @Test fun threadResolvesUrlOrBoardAndNumberAndCreatesUnknownBoards() {
        val state = CompatibilityWorkspaceState()
        val (byNumber, board) = assertNotNull(resolvePlatformAiThread(command("board" to "may", "threadId" to "123"), state, boards))
        assertEquals("https://may.2chan.net/b/res/123.htm", byNumber.canonicalUrl)
        assertEquals(may, board)
        assertNull(resolvePlatformAiThread(command("board" to "may", "threadId" to "12a"), state, boards))
        assertNull(resolvePlatformAiThread(command("board" to "none", "threadId" to "123"), state, boards))

        val (byUrl, unknown) = assertNotNull(resolvePlatformAiThread(command("threadUrl" to "https://dec.2chan.net/up2/res/9.htm"), state, boards))
        assertEquals("https://dec.2chan.net/up2/res/9.htm", byUrl.canonicalUrl)
        assertEquals(CompatBoard(compatBoardKey("https://dec.2chan.net/up2/"), "up2", "https://dec.2chan.net/up2/", "https://dec.2chan.net/up2/", 2), unknown)
        assertNull(resolvePlatformAiThread(command("threadUrl" to "https://example.com/b/res/9.htm"), state, boards))
    }

    @Test fun catalogSortAcceptsNamesLabelsAndAliases() {
        assertEquals(CompatCatalogSort.MANY, resolvePlatformAiCatalogSort(command("mode" to "MANY")))
        assertEquals(CompatCatalogSort.NEW, resolvePlatformAiCatalogSort(command("sort" to "新しい順")))
        assertEquals(CompatCatalogSort.LIVELY, resolvePlatformAiCatalogSort(command("order" to "momentum")))
        assertEquals(CompatCatalogSort.CATALOG, resolvePlatformAiCatalogSort(command("mode" to " cat ")))
        assertNull(resolvePlatformAiCatalogSort(command("mode" to "random")))
        assertNull(resolvePlatformAiCatalogSort(command()))
    }
}

class CompatPlatformAiThreadRouteTest {
    private val may = CompatBoard("may", "may二次元", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val activeUrl = "https://may.2chan.net/b/res/123.htm"
    private val active = CompatTab(
        key = com.valoser.futacha.shared.compat.compatTabKey(activeUrl), canonicalUrl = activeUrl, originalUrl = activeUrl,
        boardKey = "may", boardName = "may二次元", threadNo = "123", title = "", insertedAtEpochMillis = 0L,
        contentUpdatedAtEpochMillis = 0L
    )
    private val workspace = CompatibilityWorkspaceState(
        host = CompatHost.ThreadWorkspace(com.valoser.futacha.shared.compat.CompatThreadOrigin.MAIN),
        activeTabKey = active.key,
        tabs = listOf(active)
    )

    private fun command(vararg parameters: Pair<String, String>) =
        FutachaAiCommand(FutachaAiAction.ScrollThreadToTop, parameters.toMap())

    @Test fun commandWithoutThreadTargetsTheActiveTab() {
        assertEquals(CompatPlatformAiThreadRoute.Active(active), resolvePlatformAiThreadRoute(command(), workspace, listOf(may)))
        assertNull(resolvePlatformAiThreadRoute(command(), CompatibilityWorkspaceState(), listOf(may)))
    }

    @Test fun namedActiveThreadIsDeliveredInPlaceAndOthersAreOpened() {
        assertEquals(
            CompatPlatformAiThreadRoute.Active(active),
            resolvePlatformAiThreadRoute(command("threadId" to "123"), workspace, listOf(may))
        )
        assertEquals(
            CompatPlatformAiThreadRoute.Active(active),
            resolvePlatformAiThreadRoute(command("url" to activeUrl), workspace, listOf(may))
        )
        val other = assertIs<CompatPlatformAiThreadRoute.Open>(
            resolvePlatformAiThreadRoute(command("threadId" to "456"), workspace, listOf(may))
        )
        assertEquals("https://may.2chan.net/b/res/456.htm", other.thread.canonicalUrl)
        assertEquals(may, other.board)
    }

    @Test fun unresolvableNamedThreadDoesNotFallBackToTheActiveTab() {
        assertNull(resolvePlatformAiThreadRoute(command("threadId" to "12a"), workspace, listOf(may)))
        assertTrue(platformAiCommandNamesThread(command("threadId" to "12a")))
        assertFalse(platformAiCommandNamesThread(command("query" to "x")))
    }

    @Test fun deliveryGuardRunsEachDeliveryOnce() {
        val guard = CompatPlatformAiDeliveryGuard()
        val next = FutachaAiCommand(FutachaAiAction.NextSearchResult)
        assertTrue(guard.claim(next))
        assertFalse(guard.claim(next), "an effect restart must not advance the search twice")
        // An equal but separate delivery (a second tap) still runs.
        assertTrue(guard.claim(FutachaAiCommand(FutachaAiAction.NextSearchResult)))
    }

    // C4-6: draft_reply leaves its values in the target thread's stored draft.
    @Test fun replyDraftMergesCommandValuesIntoTheStoredDraft() {
        val stored = com.valoser.futacha.shared.compat.CompatReplyDraft(
            tabKey = "t", name = "old", comment = "old body", attachmentUri = "file:a", deleteKey = "k", updatedAtEpochMillis = 1L
        )
        val draft = FutachaAiCommand(
            FutachaAiAction.DraftReply,
            mapOf("comment" to "こんにちは", "email" to "sage", "threadId" to "123")
        )
        val merged = assertNotNull(compatPlatformAiReplyDraft(draft, "t", stored, 9L))
        assertEquals("こんにちは", merged.comment)
        assertEquals("sage", merged.email)
        assertEquals("old", merged.name)
        assertEquals("file:a", merged.attachmentUri)
        assertEquals("k", merged.deleteKey)
        assertEquals(9L, merged.updatedAtEpochMillis)
        val fresh = assertNotNull(compatPlatformAiReplyDraft(draft, "t", null, 9L))
        assertEquals("t", fresh.tabKey)
        assertEquals("こんにちは", fresh.comment)
        // Without draft values the stored draft is left alone.
        assertNull(compatPlatformAiReplyDraft(FutachaAiCommand(FutachaAiAction.DraftReply, mapOf("threadId" to "1")), "t", stored, 9L))
    }

    // C4-6: a draft_reply naming another thread opens that thread (not the active tab).
    @Test fun draftReplyNamingAnotherThreadRoutesToThatThread() {
        val route = resolvePlatformAiThreadRoute(
            FutachaAiCommand(FutachaAiAction.DraftReply, mapOf("url" to "https://may.2chan.net/b/res/77.htm")),
            workspace,
            listOf(may)
        )
        val open = assertIs<CompatPlatformAiThreadRoute.Open>(route)
        assertEquals("77", open.thread.threadNo)
        assertEquals(may, open.board)
    }
}
